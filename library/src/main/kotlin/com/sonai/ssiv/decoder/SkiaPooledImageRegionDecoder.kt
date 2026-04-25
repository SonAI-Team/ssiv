package com.sonai.ssiv.decoder

import android.app.ActivityManager
import android.content.ContentResolver
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import androidx.annotation.Keep
import androidx.core.text.isDigitsOnly
import com.sonai.ssiv.SubsamplingScaleImageView
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlinx.coroutines.*

/**
 * <p>
 * An implementation of [ImageRegionDecoder] using a pool of [BitmapRegionDecoder]s,
 * to provide true parallel loading of tiles. This is only effective if parallel loading has been
 * enabled in the view by calling [com.sonai.ssiv.SubsamplingScaleImageView.setExecutor]
 * with a multithreaded [java.util.concurrent.Executor] instance.
 * </p><p>
 * One decoder is initialized when the class is initialized. This is enough to decode base layer tiles.
 * Additional decoders are initialized when a subregion of the image is first requested, which indicates
 * interaction with the view. Creation of additional encoders stops when [allowAdditionalDecoder]
 * returns false. The default implementation takes into account the file size, number of CPU cores,
 * low memory status and a hard limit of 4. Extend this class to customize this.
 * </p><p>
 * <b>WARNING:</b> This class is highly experimental and not proven to be stable on a wide range of
 * devices. You are advised to test it thoroughly on all available devices, and code your app to use
 * [SkiaImageRegionDecoder] on old or low powered devices you could not test.
 * </p>
 */
open class SkiaPooledImageRegionDecoder @Keep constructor(bitmapConfig: Bitmap.Config? = null) : ImageRegionDecoder {

    private var decoderPool: DecoderPool? = DecoderPool()
    private val decoderLock: ReadWriteLock = ReentrantReadWriteLock(true)

    private val bitmapConfig: Bitmap.Config = bitmapConfig
        ?: SubsamplingScaleImageView.getPreferredBitmapConfig()
        ?: Bitmap.Config.RGB_565

    private var context: Context? = null
    private var uri: Uri? = null

    private var fileLength = Long.MAX_VALUE
    private val imageDimensions = Point(0, 0)
    private val lazyInited = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun init(context: Context, uri: Uri): Point {
        this.context = context
        this.uri = uri
        initialiseDecoder()
        return this.imageDimensions
    }

    private fun lazyInit() {
        if (lazyInited.compareAndSet(false, true) && fileLength < Long.MAX_VALUE) {
            debug("Starting lazy init of additional decoders")
            scope.launch {
                while (isActive && decoderPool != null && allowAdditionalDecoder(decoderPool?.size() ?: 0, fileLength)) {
                    try {
                        val start = System.currentTimeMillis()
                        debug("Starting decoder")
                        initialiseDecoder()
                        val end = System.currentTimeMillis()
                        debug("Started decoder, took ${end - start}ms")
                    } catch (e: Exception) {
                        debug("Failed to start decoder: ${e.message}")
                    }
                }
            }
        }
    }

    private fun initialiseDecoder() {
        val uri = this.uri ?: return
        val context = this.context ?: return
        val uriString = uri.toString()
        var fileLength = Long.MAX_VALUE
        
        val decoder = when {
            uriString.startsWith(RESOURCE_PREFIX) -> {
                val id = uri.pathSegments.firstOrNull { it.isDigitsOnly() }?.toIntOrNull() ?: 0
                runCatching { context.resources.openRawResourceFd(id).use { fileLength = it.length } }
                BitmapRegionDecoder.newInstance(context.resources.openRawResource(id))
            }
            uriString.startsWith(ASSET_PREFIX) -> {
                val assetName = uriString.substring(ASSET_PREFIX.length)
                runCatching { context.assets.openFd(assetName).use { fileLength = it.length } }
                BitmapRegionDecoder.newInstance(context.assets.open(assetName, AssetManager.ACCESS_RANDOM))
            }
            else -> {
                val contentResolver = context.contentResolver
                runCatching {
                    contentResolver.openAssetFileDescriptor(uri, "r")?.use { fileLength = it.length }
                }
                contentResolver.openInputStream(uri)?.use { 
                    BitmapRegionDecoder.newInstance(it)
                }
            }
        } ?: throw IllegalArgumentException("Unable to initialise decoder for $uri")

        this.fileLength = fileLength
        this.imageDimensions.set(decoder.width, decoder.height)
        decoderLock.writeLock().lock()
        try {
            decoderPool?.add(decoder)
        } finally {
            decoderLock.writeLock().unlock()
        }
    }

    override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
        debug("Decode region $sRect on thread ${Thread.currentThread().name}")
        if (sRect.width() < imageDimensions.x || sRect.height() < imageDimensions.y) {
            lazyInit()
        }
        decoderLock.readLock().lock()
        try {
            val pool = decoderPool ?: throw IllegalStateException("Cannot decode region after decoder has been recycled")
            val decoder = pool.acquire()
            try {
                if (decoder != null && !decoder.isRecycled) {
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = bitmapConfig
                    }
                    return decoder.decodeRegion(sRect, options) ?: throw IllegalStateException(
                        "Skia image decoder returned null bitmap - image format may not be supported"
                    )
                }
            } finally {
                decoder?.let { pool.release(it) }
            }
            throw IllegalStateException("Decoder is null or recycled")
        } finally {
            decoderLock.readLock().unlock()
        }
    }

    override fun isReady(): Boolean {
        return decoderPool?.isEmpty() == false
    }

    override fun recycle() {
        scope.cancel()
        decoderLock.writeLock().lock()
        try {
            decoderPool?.let {
                it.recycle()
                decoderPool = null
                context = null
                uri = null
            }
        } finally {
            decoderLock.writeLock().unlock()
        }
    }

    protected open fun allowAdditionalDecoder(numberOfDecoders: Int, fileLength: Long): Boolean {
        return when {
            numberOfDecoders >= MAX_DECODERS -> {
                debug("No additional decoders allowed, reached hard limit ($MAX_DECODERS)")
                false
            }
            numberOfDecoders * fileLength > MEMORY_THRESHOLD -> {
                debug("No additional encoders allowed, reached hard memory limit ($MEMORY_THRESHOLD_MB Mb)")
                false
            }
            numberOfDecoders >= getNumberOfCores() -> {
                debug("No additional encoders allowed, limited by CPU cores (${getNumberOfCores()})")
                false
            }
            isLowMemory() -> {
                debug("No additional encoders allowed, memory is low")
                false
            }
            else -> {
                debug("Additional decoder allowed, current count is $numberOfDecoders, estimated native memory ${(fileLength * numberOfDecoders) / (1024 * 1024)}Mb")
                true
            }
        }
    }

    private class DecoderPool {
        private val available = Semaphore(0, true)
        private val decoders = ConcurrentHashMap<BitmapRegionDecoder, Boolean>()

        fun isEmpty(): Boolean = decoders.isEmpty()

        fun size(): Int = decoders.size

        fun acquire(): BitmapRegionDecoder? {
            available.acquireUninterruptibly()
            return getNextAvailable()
        }

        fun release(decoder: BitmapRegionDecoder) {
            if (markAsUnused(decoder)) {
                available.release()
            }
        }

        @Synchronized
        fun add(decoder: BitmapRegionDecoder) {
            decoders[decoder] = false
            available.release()
        }

        fun recycle() {
            while (!decoders.isEmpty()) {
                val decoder = acquire()
                decoder?.recycle()
                decoders.remove(decoder)
            }
        }

        @Synchronized
        private fun getNextAvailable(): BitmapRegionDecoder? {
            for (entry in decoders.entries) {
                if (!entry.value) {
                    entry.setValue(true)
                    return entry.key
                }
            }
            return null
        }

        @Synchronized
        private fun markAsUnused(decoder: BitmapRegionDecoder): Boolean {
            for (entry in decoders.entries) {
                if (decoder === entry.key) {
                    return if (entry.value) {
                        entry.setValue(false)
                        true
                    } else {
                        false
                    }
                }
            }
            return false
        }
    }

    private fun getNumberOfCores(): Int = Runtime.getRuntime().availableProcessors()

    private fun isLowMemory(): Boolean {
        val activityManager = context?.getSystemService(ACTIVITY_SERVICE) as? ActivityManager
        return if (activityManager != null) {
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.lowMemory
        } else {
            true
        }
    }

    private fun debug(message: String) {
        if (debug) {
            Log.d(TAG, message)
        }
    }

    companion object {
        private val TAG = SkiaPooledImageRegionDecoder::class.java.simpleName
        private var debug = false

        private const val MAX_DECODERS = 4
        private const val MEMORY_THRESHOLD_MB = 20
        private const val MEMORY_THRESHOLD = MEMORY_THRESHOLD_MB * 1024 * 1024

        private const val FILE_PREFIX = "file://"
        private const val ASSET_PREFIX = "${FILE_PREFIX}/android_asset/"
        private const val RESOURCE_PREFIX = "${ContentResolver.SCHEME_ANDROID_RESOURCE}://"
    }
}
