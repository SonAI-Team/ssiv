package com.sonai.ssiv.decoder

import android.app.ActivityManager
import android.content.ContentResolver
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.Keep
import androidx.core.text.isDigitsOnly
import com.sonai.ssiv.SubsamplingScaleImageView
import java.io.File
import java.io.FileFilter
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.regex.Pattern

/**
 * <p>
 * An implementation of {@link ImageRegionDecoder} using a pool of {@link BitmapRegionDecoder}s,
 * to provide true parallel loading of tiles. This is only effective if parallel loading has been
 * enabled in the view by calling {@link com.sonai.ssiv.SubsamplingScaleImageView#setExecutor(Executor)}
 * with a multithreaded {@link Executor} instance.
 * </p><p>
 * One decoder is initialized when the class is initialized. This is enough to decode base layer tiles.
 * Additional decoders are initialized when a subregion of the image is first requested, which indicates
 * interaction with the view. Creation of additional encoders stops when {@link #allowAdditionalDecoder(int, long)}
 * returns false. The default implementation takes into account the file size, number of CPU cores,
 * low memory status and a hard limit of 4. Extend this class to customize this.
 * </p><p>
 * <b>WARNING:</b> This class is highly experimental and not proven to be stable on a wide range of
 * devices. You are advised to test it thoroughly on all available devices, and code your app to use
 * {@link SkiaImageRegionDecoder} on old or low powered devices you could not test.
 * </p>
 */
open class SkiaPooledImageRegionDecoder @Keep constructor(bitmapConfig: Bitmap.Config? = null) : ImageRegionDecoder {

    private var decoderPool: DecoderPool? = DecoderPool()
    private val decoderLock: ReadWriteLock = ReentrantReadWriteLock(true)

    private val bitmapConfig: Bitmap.Config

    private var context: Context? = null
    private var uri: Uri? = null

    private var fileLength = Long.MAX_VALUE
    private val imageDimensions = Point(0, 0)
    private val lazyInited = AtomicBoolean(false)

    init {
        val globalBitmapConfig = SubsamplingScaleImageView.getPreferredBitmapConfig()
        this.bitmapConfig = when {
            bitmapConfig != null -> bitmapConfig
            globalBitmapConfig != null -> globalBitmapConfig
            else -> Bitmap.Config.RGB_565
        }
    }

    @Throws(Exception::class)
    override fun init(context: Context, uri: Uri): Point {
        this.context = context
        this.uri = uri
        initialiseDecoder()
        return this.imageDimensions
    }

    private fun lazyInit() {
        if (lazyInited.compareAndSet(false, true) && fileLength < Long.MAX_VALUE) {
            debug("Starting lazy init of additional decoders")
            val thread = object : Thread() {
                override fun run() {
                    while (decoderPool != null && allowAdditionalDecoder(decoderPool?.size() ?: 0, fileLength)) {
                        try {
                            if (decoderPool != null) {
                                val start = System.currentTimeMillis()
                                debug("Starting decoder")
                                initialiseDecoder()
                                val end = System.currentTimeMillis()
                                debug("Started decoder, took ${end - start}ms")
                            }
                        } catch (e: Exception) {
                            debug("Failed to start decoder: ${e.message}")
                        }
                    }
                }
            }
            thread.start()
        }
    }

    @Throws(Exception::class)
    private fun initialiseDecoder() {
        val uriString = uri!!.toString()
        var decoder: BitmapRegionDecoder?
        var fileLength = Long.MAX_VALUE
        val context = this.context!!
        if (uriString.startsWith(RESOURCE_PREFIX)) {
            val res: Resources
            val packageName = uri!!.authority
            if (context.packageName == packageName) {
                res = context.resources
            } else {
                val pm = context.packageManager
                res = pm.getResourcesForApplication(packageName!!)
            }

            var id = 0
            val segments = uri!!.pathSegments
            val size = segments.size
            if (size == 2 && segments[0] == "drawable") {
                val resName = segments[1]
                id = res.getIdentifier(resName, "drawable", packageName)
            } else if (size == 1 && segments[0].isDigitsOnly()) {
                try {
                    id = segments[0].toInt()
                } catch (ignored: NumberFormatException) {
                }
            }
            try {
                val descriptor = context.resources.openRawResourceFd(id)
                fileLength = descriptor.length
            } catch (e: Exception) {
                // Pooling disabled
            }
            decoder = BitmapRegionDecoder.newInstance(context.resources.openRawResource(id), false)
        } else if (uriString.startsWith(ASSET_PREFIX)) {
            val assetName = uriString.substring(ASSET_PREFIX.length)
            try {
                val descriptor = context.assets.openFd(assetName)
                fileLength = descriptor.length
            } catch (e: Exception) {
                // Pooling disabled
            }
            decoder = BitmapRegionDecoder.newInstance(context.assets.open(assetName, AssetManager.ACCESS_RANDOM), false)
        } else if (uriString.startsWith(FILE_PREFIX)) {
            decoder = BitmapRegionDecoder.newInstance(uriString.substring(FILE_PREFIX.length), false)
            try {
                val file = File(uriString.substring(FILE_PREFIX.length))
                if (file.exists()) {
                    fileLength = file.length()
                }
            } catch (e: Exception) {
                // Pooling disabled
            }
        } else {
            var inputStream: InputStream? = null
            try {
                val contentResolver = context.contentResolver
                inputStream = contentResolver.openInputStream(uri!!)
                if (inputStream == null) {
                    throw Exception("Content resolver returned null stream. Unable to initialise with uri.")
                }
                decoder = BitmapRegionDecoder.newInstance(inputStream, false)
                try {
                    val descriptor = contentResolver.openAssetFileDescriptor(uri!!, "r")
                    if (descriptor != null) {
                        fileLength = descriptor.length
                    }
                } catch (e: Exception) {
                    // Stick with MAX_LENGTH
                }
            } finally {
                inputStream?.let {
                    try {
                        it.close()
                    } catch (e: Exception) { /* Ignore */
                    }
                }
            }
        }

        this.fileLength = fileLength
        this.imageDimensions.set(decoder!!.width, decoder.height)
        decoderLock.writeLock().lock()
        try {
            decoderPool?.add(decoder)
        } finally {
            decoderLock.writeLock().unlock()
        }
    }

    override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap? {
        debug("Decode region $sRect on thread ${Thread.currentThread().name}")
        if (sRect.width() < imageDimensions.x || sRect.height() < imageDimensions.y) {
            lazyInit()
        }
        decoderLock.readLock().lock()
        try {
            if (decoderPool != null) {
                val decoder = decoderPool!!.acquire()
                try {
                    if (decoder != null && !decoder.isRecycled) {
                        val options = BitmapFactory.Options()
                        options.inSampleSize = sampleSize
                        options.inPreferredConfig = bitmapConfig
                        val bitmap = decoder.decodeRegion(sRect, options) ?: throw RuntimeException(
                            "Skia image decoder returned null bitmap - image format may not be supported"
                        )
                        return bitmap
                    }
                } finally {
                    decoder?.let {
                        decoderPool?.release(it)
                    }
                }
            }
            throw IllegalStateException("Cannot decode region after decoder has been recycled")
        } finally {
            decoderLock.readLock().unlock()
        }
    }

    @Synchronized
    override fun isReady(): Boolean {
        return decoderPool != null && !decoderPool!!.isEmpty()
    }

    @Synchronized
    override fun recycle() {
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
        if (numberOfDecoders >= 4) {
            debug("No additional decoders allowed, reached hard limit (4)")
            return false
        } else if (numberOfDecoders * fileLength > 20 * 1024 * 1024) {
            debug("No additional encoders allowed, reached hard memory limit (20Mb)")
            return false
        } else if (numberOfDecoders >= getNumberOfCores()) {
            debug("No additional encoders allowed, limited by CPU cores (${getNumberOfCores()})")
            return false
        } else if (isLowMemory()) {
            debug("No additional encoders allowed, memory is low")
            return false
        }
        debug("Additional decoder allowed, current count is $numberOfDecoders, estimated native memory ${(fileLength * numberOfDecoders) / (1024 * 1024)}Mb")
        return true
    }

    private class DecoderPool {
        private val available = Semaphore(0, true)
        private val decoders = ConcurrentHashMap<BitmapRegionDecoder, Boolean>()

        @Synchronized
        fun isEmpty(): Boolean {
            return decoders.isEmpty()
        }

        @Synchronized
        fun size(): Int {
            return decoders.size
        }

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

        @Synchronized
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

    private fun getNumberOfCores(): Int {
        return if (Build.VERSION.SDK_INT >= 17) {
            Runtime.getRuntime().availableProcessors()
        } else {
            getNumCoresOldPhones()
        }
    }

    private fun getNumCoresOldPhones(): Int {
        class CpuFilter : FileFilter {
            override fun accept(pathname: File): Boolean {
                return Pattern.matches("cpu[0-9]+", pathname.name)
            }
        }
        return try {
            val dir = File("/sys/devices/system/cpu/")
            val files = dir.listFiles(CpuFilter())
            files?.size ?: 1
        } catch (e: Exception) {
            1
        }
    }

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

        private const val FILE_PREFIX = "file://"
        private const val ASSET_PREFIX = "$FILE_PREFIX/android_asset/"
        private const val RESOURCE_PREFIX = "${ContentResolver.SCHEME_ANDROID_RESOURCE}://"

        @JvmStatic
        @Keep
        fun setDebug(debug: Boolean) {
            this.debug = debug
        }
    }
}
