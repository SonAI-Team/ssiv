package com.sonai.ssiv.coil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.sonai.ssiv.decoder.ImageRegionDecoder
import com.sonai.ssiv.decoder.SkiaImageRegionDecoder
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.ByteBuffer

class CoilImageRegionDecoder(
    private val imageLoader: ImageLoader,
    private val bitmapConfig: Bitmap.Config? = null
) : ImageRegionDecoder {

    private var delegate: SkiaImageRegionDecoder? = null

    override fun init(context: Context, uri: Uri): Point {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .build()

        val result = runBlocking { imageLoader.execute(request) }
        
        return when (result) {
            is SuccessResult -> {
                val file = getFileFromCache(result)
                if (file != null && file.exists()) {
                    delegate = SkiaImageRegionDecoder(bitmapConfig)
                    delegate!!.init(context, Uri.fromFile(file))
                } else {
                    // Fallback or handle error if no file in cache
                    throw IllegalStateException("Coil could not provide a local file for the image")
                }
            }
            is ErrorResult -> throw result.throwable
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    private fun getFileFromCache(result: SuccessResult): File? {
        val cacheKey = result.diskCacheKey ?: return null
        val diskCache = imageLoader.diskCache ?: return null
        val snapshot = diskCache.openSnapshot(cacheKey) ?: return null
        return snapshot.use { 
            it.data.toFile()
        }
    }

    override fun init(context: Context, buffer: ByteBuffer): Point {
        delegate = SkiaImageRegionDecoder(bitmapConfig)
        return delegate!!.init(context, buffer)
    }

    override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap? {
        return delegate?.decodeRegion(sRect, sampleSize)
    }

    override fun setBitmapConfig(config: Bitmap.Config) {
        delegate?.setBitmapConfig(config)
    }

    override fun isReady(): Boolean {
        return delegate?.isReady() == true
    }

    override fun recycle() {
        delegate?.recycle()
        delegate = null
    }
}
