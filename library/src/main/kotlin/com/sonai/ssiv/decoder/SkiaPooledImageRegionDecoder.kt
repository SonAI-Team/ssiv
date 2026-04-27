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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * <p>
 * An implementation of [ImageRegionDecoder] using a pool of [BitmapRegionDecoder]s,
 * to provide true parallel loading of tiles.
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
@Suppress("TooManyFunctions")
open class SkiaPooledImageRegionDecoder @Keep constructor(bitmapConfig: Bitmap.Config? = null) :
    ImageRegionDecoder {

    private var decoderPool: Channel<BitmapRegionDecoder>? = Channel(32)
    private val decoderLock: ReadWriteLock = ReentrantReadWriteLock(true)
    private val allDecoders = ArrayList<BitmapRegionDecoder>()

    private var bitmapConfig: Bitmap.Config = bitmapConfig
        ?: SubsamplingScaleImageView.getPreferredBitmapConfig()
        ?: Bitmap.Config.HARDWARE

    private var context: Context? = null
    private var uri: Uri? = null
    private var buffer: ByteBuffer? = null

    private var fileLength = Long.MAX_VALUE
    private val imageDimensions = Point(0, 0)
    private val lazyInited = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var maxDecoders = 4
    private var memoryThresholdMb = 20
    private var memoryThreshold = 20L * 1024 * 1024

    override fun init(context: Context, uri: Uri): Point {
        this.context = context
        this.uri = uri
        this.buffer = null
        calculateLimits(context)
        initialiseDecoder()
        return this.imageDimensions
    }

    override fun init(context: Context, buffer: ByteBuffer): Point {
        this.context = context
        this.buffer = buffer
        this.uri = null
        calculateLimits(context)
        initialiseDecoder()
        return this.imageDimensions
    }

    private fun calculateLimits(context: Context) {
        val activityManager = context.getSystemService(ACTIVITY_SERVICE) as? ActivityManager
        if (activityManager != null) {
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            val totalRamGb = memoryInfo.totalMem.toDouble() / (BYTES_PER_KB * BYTES_PER_KB * BYTES_PER_KB)

            when {
                totalRamGb > 6 -> {
                    maxDecoders = 16
                    memoryThresholdMb = 512
                }

                totalRamGb > 4 -> {
                    maxDecoders = 12
                    memoryThresholdMb = 256
                }

                totalRamGb > 2 -> {
                    maxDecoders = 8
                    memoryThresholdMb = 128
                }

                else -> {
                    maxDecoders = 4
                    memoryThresholdMb = 64
                }
            }
            memoryThreshold = memoryThresholdMb.toLong() * BYTES_PER_KB * BYTES_PER_KB
            debug("Device RAM: ${"%.2f".format(totalRamGb)}GB. Limits: maxDecoders=$maxDecoders, threshold=${memoryThresholdMb}MB")
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun lazyInit() {
        if (lazyInited.compareAndSet(false, true) && fileLength < Long.MAX_VALUE) {
            debug("Starting lazy init of additional decoders")
            scope.launch {
                while (isActive && decoderPool != null && allowAdditionalDecoder(
                        allDecoders.size,
                        fileLength
                    )
                ) {
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
        val context = this.context ?: return
        var fileLength = Long.MAX_VALUE

        val decoder = if (buffer != null) {
            val buf = buffer!!
            buf.position(0)
            fileLength = buf.remaining().toLong()
            val stream = object : java.io.InputStream() {
                override fun read(): Int {
                    return if (!buf.hasRemaining()) -1 else buf.get().toInt() and 0xFF
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (!buf.hasRemaining()) return -1
                    val count = minOf(len, buf.remaining())
                    buf.get(b, off, count)
                    return count
                }
            }
            BitmapRegionDecoder.newInstance(stream)
        } else {
            val uri = this.uri ?: return
            val uriString = uri.toString()
            when {
                uriString.startsWith(RESOURCE_PREFIX) -> {
                    val id = uri.pathSegments.firstOrNull { it.isDigitsOnly() }?.toIntOrNull() ?: 0
                    runCatching {
                        context.resources.openRawResourceFd(id).use { fileLength = it.length }
                    }
                    BitmapRegionDecoder.newInstance(context.resources.openRawResource(id))
                }

                uriString.startsWith(ASSET_PREFIX) -> {
                    val assetName = uriString.substring(ASSET_PREFIX.length)
                    runCatching { context.assets.openFd(assetName).use { fileLength = it.length } }
                    BitmapRegionDecoder.newInstance(
                        context.assets.open(
                            assetName,
                            AssetManager.ACCESS_RANDOM
                        )
                    )
                }

                else -> {
                    val contentResolver = context.contentResolver
                    runCatching {
                        contentResolver.openAssetFileDescriptor(uri, "r")
                            ?.use { fileLength = it.length }
                    }
                    contentResolver.openInputStream(uri)?.use {
                        BitmapRegionDecoder.newInstance(it)
                    }
                }
            }
        } ?: throw IllegalArgumentException("Unable to initialise decoder")

        this.fileLength = fileLength
        this.imageDimensions.set(decoder.width, decoder.height)
        decoderLock.writeLock().lock()
        try {
            allDecoders.add(decoder)
            decoderPool?.trySend(decoder)
        } finally {
            decoderLock.writeLock().unlock()
        }
    }

    override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
        debug("Decode region $sRect on thread ${Thread.currentThread().name}")
        if (sRect.width() < imageDimensions.x || sRect.height() < imageDimensions.y) {
            lazyInit()
        }

        val pool = decoderPool ?: error("Cannot decode region after decoder has been recycled")
        val decoder = runBlocking { pool.receive() }

        try {
            if (!decoder.isRecycled) {
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = bitmapConfig
                }
                return decoder.decodeRegion(sRect, options) ?: error(
                    "Skia image decoder returned null bitmap - image format may not be supported"
                )
            }
            error("Decoder is recycled")
        } finally {
            pool.trySend(decoder)
        }
    }

    override fun isReady(): Boolean {
        decoderLock.readLock().lock()
        try {
            return allDecoders.isNotEmpty() && allDecoders.all { !it.isRecycled }
        } finally {
            decoderLock.readLock().unlock()
        }
    }

    override fun recycle() {
        scope.cancel()
        decoderLock.writeLock().lock()
        try {
            allDecoders.forEach { it.recycle() }
            allDecoders.clear()
            decoderPool?.close()
            decoderPool = null
            context = null
            uri = null
            buffer = null
        } finally {
            decoderLock.writeLock().unlock()
        }
    }

    override fun setBitmapConfig(config: Bitmap.Config) {
        this.bitmapConfig = config
    }

    protected open fun allowAdditionalDecoder(numberOfDecoders: Int, fileLength: Long): Boolean {
        return when {
            numberOfDecoders >= maxDecoders -> {
                debug("No additional decoders allowed, reached hard limit ($maxDecoders)")
                false
            }

            numberOfDecoders * fileLength > memoryThreshold -> {
                debug("No additional decoders allowed, reached hard memory limit ($memoryThresholdMb Mb)")
                false
            }

            numberOfDecoders >= getNumberOfCores() -> {
                debug("No additional decoders allowed, limited by CPU cores (${getNumberOfCores()})")
                false
            }

            isLowMemory() -> {
                debug("No additional decoders allowed, memory is low")
                false
            }

            else -> {
                val estimatedMemoryMb =
                    (fileLength * numberOfDecoders) / (BYTES_PER_KB * BYTES_PER_KB)
                debug(
                    "Additional decoder allowed, current count is $numberOfDecoders, " +
                            "estimated native memory ${estimatedMemoryMb}Mb"
                )
                true
            }
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

        private const val BYTES_PER_KB = 1024

        private const val FILE_PREFIX = "file://"
        private const val ASSET_PREFIX = "${FILE_PREFIX}/android_asset/"
        private const val RESOURCE_PREFIX = "${ContentResolver.SCHEME_ANDROID_RESOURCE}://"
    }
}
