package com.sonai.ssiv.decoder

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.Keep
import androidx.core.text.isDigitsOnly
import com.sonai.ssiv.SubsamplingScaleImageView

/**
 * Default implementation of [SSIVImageDecoder]
 * using Android's [BitmapFactory], based on the Skia library. This
 * works well in most circumstances and has reasonable performance, however it has some problems
 * with grayscale, indexed and CMYK images.
 */
class SkiaSSIVImageDecoder @Keep constructor(bitmapConfig: Bitmap.Config? = null) : SSIVImageDecoder {

    private val bitmapConfig: Bitmap.Config = bitmapConfig
        ?: SubsamplingScaleImageView.getPreferredBitmapConfig()
        ?: Bitmap.Config.HARDWARE

    override fun decode(context: Context, uri: Uri): Bitmap {
        val uriString = uri.toString()
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = bitmapConfig
        }
        
        val bitmap = when {
            uriString.startsWith(RESOURCE_PREFIX) -> {
                val id = uri.pathSegments.firstOrNull { it.isDigitsOnly() }?.toIntOrNull() ?: 0
                BitmapFactory.decodeResource(context.resources, id, options)
            }
            uriString.startsWith(ASSET_PREFIX) -> {
                val assetName = uriString.substring(ASSET_PREFIX.length)
                context.assets.open(assetName).use { 
                    BitmapFactory.decodeStream(it, null, options)
                }
            }
            else -> {
                context.contentResolver.openInputStream(uri)?.use { 
                    BitmapFactory.decodeStream(it, null, options)
                }
            }
        }

        return bitmap ?: throw IllegalStateException("Skia image decoder returned null bitmap - image format may not be supported")
    }

    companion object {
        private const val FILE_PREFIX = "file://"
        private const val ASSET_PREFIX = "${FILE_PREFIX}/android_asset/"
        private const val RESOURCE_PREFIX = "${ContentResolver.SCHEME_ANDROID_RESOURCE}://"
    }
}
