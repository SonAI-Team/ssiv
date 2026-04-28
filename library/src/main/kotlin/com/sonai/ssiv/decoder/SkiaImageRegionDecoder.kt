package com.sonai.ssiv.decoder

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import androidx.annotation.Keep
import androidx.core.text.isDigitsOnly
import com.sonai.ssiv.SubsamplingScaleImageView
import java.nio.ByteBuffer
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Default implementation of [com.sonai.ssiv.decoder.ImageRegionDecoder]
 * using Android's [android.graphics.BitmapRegionDecoder], based on the Skia library. This
 * works well in most circumstances and has reasonable performance due to the cached decoder instance,
 * however it has some problems with grayscale, indexed and CMYK images.
 *
 * A [ReadWriteLock] is used to delegate responsibility for multi threading behavior to the
 * [BitmapRegionDecoder] instance on SDK >= 21, whilst allowing this class to block until no
 * tiles are being loaded before recycling the decoder. In practice, [BitmapRegionDecoder] is
 * synchronized internally so this has no real impact on performance.
 */
class SkiaImageRegionDecoder @Keep constructor(bitmapConfig: Bitmap.Config? = null) :
    ImageRegionDecoder {

    private var decoder: BitmapRegionDecoder? = null
    private val decoderLock: ReadWriteLock = ReentrantReadWriteLock(true)

    private var bitmapConfig: Bitmap.Config = bitmapConfig
        ?: SubsamplingScaleImageView.getPreferredBitmapConfig()
        ?: defaultBitmapConfig()

    private fun defaultBitmapConfig(): Bitmap.Config {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Bitmap.Config.HARDWARE
        } else {
            Bitmap.Config.ARGB_8888
        }
    }

    @Suppress("DEPRECATION")
    override fun init(context: Context, uri: Uri): Point {
        val uriString = uri.toString()
        val newDecoder = when {
            uriString.startsWith(RESOURCE_PREFIX) -> {
                val id = uri.pathSegments.firstOrNull { it.isDigitsOnly() }?.toIntOrNull() ?: 0
                context.resources.openRawResource(id).use {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        BitmapRegionDecoder.newInstance(it)
                    } else {
                        BitmapRegionDecoder.newInstance(it, false)
                    }
                }
            }

            uriString.startsWith(ASSET_PREFIX) -> {
                val assetName = uriString.substring(ASSET_PREFIX.length)
                context.assets.open(assetName, AssetManager.ACCESS_RANDOM).use {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        BitmapRegionDecoder.newInstance(it)
                    } else {
                        BitmapRegionDecoder.newInstance(it, false)
                    }
                }
            }

            else -> {
                context.contentResolver.openInputStream(uri)?.use {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        BitmapRegionDecoder.newInstance(it)
                    } else {
                        BitmapRegionDecoder.newInstance(it, false)
                    }
                } ?: throw IllegalArgumentException(
                    "Content resolver returned null stream. Unable to initialise with uri."
                )
            }
        }
        decoder = newDecoder
        return Point(newDecoder!!.width, newDecoder.height)
    }

    @Suppress("DEPRECATION")
    override fun init(context: Context, buffer: ByteBuffer): Point {
        val stream = object : java.io.InputStream() {
            override fun read(): Int {
                return if (!buffer.hasRemaining()) -1 else buffer.get().toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (!buffer.hasRemaining()) return -1
                val count = minOf(len, buffer.remaining())
                buffer.get(b, off, count)
                return count
            }
        }
        val newDecoder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            BitmapRegionDecoder.newInstance(stream)
        } else {
            BitmapRegionDecoder.newInstance(stream, false)
        } ?: throw IllegalArgumentException("Unable to initialise with ByteBuffer")
        decoder = newDecoder
        return Point(newDecoder.width, newDecoder.height)
    }

    override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
        val lock = getDecodeLock()
        lock.lock()
        try {
            val d = decoder
            if (d != null && !d.isRecycled) {
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = bitmapConfig
                }
                return d.decodeRegion(sRect, options)
                    ?: error("Skia image decoder returned null bitmap - image format may not be supported")
            } else {
                error("Cannot decode region after decoder has been recycled")
            }
        } finally {
            lock.unlock()
        }
    }

    override fun isReady(): Boolean {
        decoderLock.readLock().lock()
        try {
            return decoder?.isRecycled == false
        } finally {
            decoderLock.readLock().unlock()
        }
    }

    override fun recycle() {
        decoderLock.writeLock().lock()
        try {
            decoder?.recycle()
            decoder = null
        } finally {
            decoderLock.writeLock().unlock()
        }
    }

    override fun setBitmapConfig(config: Bitmap.Config) {
        this.bitmapConfig = config
    }

    private fun getDecodeLock(): Lock {
        return decoderLock.readLock()
    }

    companion object {
        private const val FILE_PREFIX = "file://"
        private const val ASSET_PREFIX = "${FILE_PREFIX}/android_asset/"
        private const val RESOURCE_PREFIX = "${ContentResolver.SCHEME_ANDROID_RESOURCE}://"
    }
}
