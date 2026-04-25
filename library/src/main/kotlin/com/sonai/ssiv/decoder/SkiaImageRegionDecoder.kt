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

    private val bitmapConfig: Bitmap.Config = bitmapConfig
        ?: SubsamplingScaleImageView.getPreferredBitmapConfig()
        ?: Bitmap.Config.RGB_565

    override fun init(context: Context, uri: Uri): Point {
        val uriString = uri.toString()
        val newDecoder = when {
            uriString.startsWith(RESOURCE_PREFIX) -> {
                val id = uri.pathSegments.firstOrNull { it.isDigitsOnly() }?.toIntOrNull() ?: 0
                BitmapRegionDecoder.newInstance(context.resources.openRawResource(id))
            }
            uriString.startsWith(ASSET_PREFIX) -> {
                val assetName = uriString.substring(ASSET_PREFIX.length)
                BitmapRegionDecoder.newInstance(context.assets.open(assetName, AssetManager.ACCESS_RANDOM))
            }
            uriString.startsWith(FILE_PREFIX) -> {
                BitmapRegionDecoder.newInstance(uriString.substring(FILE_PREFIX.length))
            }
            else -> {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapRegionDecoder.newInstance(it)
                } ?: throw Exception("Content resolver returned null stream. Unable to initialise with uri.")
            }
        }
        decoder = newDecoder
        return Point(newDecoder!!.width, newDecoder.height)
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
                    ?: throw RuntimeException("Skia image decoder returned null bitmap - image format may not be supported")
            } else {
                throw IllegalStateException("Cannot decode region after decoder has been recycled")
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

    private fun getDecodeLock(): Lock {
        return decoderLock.readLock()
    }

    companion object {
        private const val FILE_PREFIX = "file://"
        private const val ASSET_PREFIX = "${FILE_PREFIX}/android_asset/"
        private const val RESOURCE_PREFIX = "${ContentResolver.SCHEME_ANDROID_RESOURCE}://"
    }
}
