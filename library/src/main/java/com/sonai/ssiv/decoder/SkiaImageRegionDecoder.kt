package com.sonai.ssiv.decoder

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import androidx.annotation.Keep
import androidx.core.text.isDigitsOnly
import com.sonai.ssiv.SubsamplingScaleImageView
import java.io.InputStream
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
class SkiaImageRegionDecoder @Keep constructor(bitmapConfig: Bitmap.Config? = null) : ImageRegionDecoder {

    private var decoder: BitmapRegionDecoder? = null
    private val decoderLock: ReadWriteLock = ReentrantReadWriteLock(true)

    private val bitmapConfig: Bitmap.Config

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
        val uriString = uri.toString()
        if (uriString.startsWith(RESOURCE_PREFIX)) {
            val res = if (context.packageName == uri.authority) {
                context.resources
            } else {
                context.packageManager.getResourcesForApplication(uri.authority!!)
            }

            var id = 0
            val segments = uri.pathSegments
            val size = segments.size
            if (size == 2 && segments[0] == "drawable") {
                id = res.getIdentifier(segments[1], "drawable", uri.authority)
            } else if (size == 1 && segments[0].isDigitsOnly()) {
                try {
                    id = segments[0].toInt()
                } catch (ignored: NumberFormatException) {
                }
            }

            decoder = BitmapRegionDecoder.newInstance(context.resources.openRawResource(id), false)
        } else if (uriString.startsWith(ASSET_PREFIX)) {
            val assetName = uriString.substring(ASSET_PREFIX.length)
            decoder = BitmapRegionDecoder.newInstance(context.assets.open(assetName, android.content.res.AssetManager.ACCESS_RANDOM), false)
        } else if (uriString.startsWith(FILE_PREFIX)) {
            decoder = BitmapRegionDecoder.newInstance(uriString.substring(FILE_PREFIX.length), false)
        } else {
            var inputStream: InputStream? = null
            try {
                inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    throw Exception("Content resolver returned null stream. Unable to initialise with uri.")
                }
                decoder = BitmapRegionDecoder.newInstance(inputStream, false)
            } finally {
                inputStream?.let {
                    try {
                        it.close()
                    } catch (e: Exception) { // Ignore
                    }
                }
            }
        }
        return Point(decoder!!.width, decoder!!.height)
    }

    override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
        val lock = getDecodeLock()
        lock.lock()
        try {
            if (decoder != null && !decoder!!.isRecycled) {
                val options = BitmapFactory.Options()
                options.inSampleSize = sampleSize
                options.inPreferredConfig = bitmapConfig
                val bitmap = decoder!!.decodeRegion(sRect, options)
                    ?: throw RuntimeException("Skia image decoder returned null bitmap - image format may not be supported")
                return bitmap
            } else {
                throw IllegalStateException("Cannot decode region after decoder has been recycled")
            }
        } finally {
            lock.unlock()
        }
    }

    @Synchronized
    override fun isReady(): Boolean {
        return decoder != null && !decoder!!.isRecycled
    }

    @Synchronized
    override fun recycle() {
        decoderLock.writeLock().lock()
        try {
            decoder?.recycle()
            decoder = null
        } finally {
            decoderLock.writeLock().unlock()
        }
    }

    private fun getDecodeLock(): Lock {
        return decoderLock.readLock()
    }

    companion object {
        private const val FILE_PREFIX = "file://"
        private const val ASSET_PREFIX = "$FILE_PREFIX/android_asset/"
        private const val RESOURCE_PREFIX = "${ContentResolver.SCHEME_ANDROID_RESOURCE}://"
    }
}
