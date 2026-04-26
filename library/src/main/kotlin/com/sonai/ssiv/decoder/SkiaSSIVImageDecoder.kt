package com.sonai.ssiv.decoder

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.annotation.Keep
import androidx.core.text.isDigitsOnly
import com.sonai.ssiv.SubsamplingScaleImageView
import java.io.IOException

/**
 * Default implementation of [SSIVImageDecoder]
 * using Android's [ImageDecoder]. This provides high performance and supports
 * modern image formats and hardware acceleration.
 */
class SkiaSSIVImageDecoder @Keep constructor(bitmapConfig: Bitmap.Config? = null) :
    SSIVImageDecoder {
    private val preferredConfig: Bitmap.Config = bitmapConfig
        ?: SubsamplingScaleImageView.getPreferredBitmapConfig()
        ?: Bitmap.Config.HARDWARE

    override fun decode(context: Context, uri: Uri): Bitmap {
        val source = when {
            uri.toString().startsWith(RESOURCE_PREFIX) -> {
                val id = uri.pathSegments.firstOrNull { it.isDigitsOnly() }?.toIntOrNull() ?: 0
                ImageDecoder.createSource(context.resources, id)
            }

            uri.toString().startsWith(ASSET_PREFIX) -> {
                val assetName = uri.toString().substring(ASSET_PREFIX.length)
                ImageDecoder.createSource(context.assets, assetName)
            }

            else -> {
                ImageDecoder.createSource(context.contentResolver, uri)
            }
        }

        return try {
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                if (preferredConfig == Bitmap.Config.HARDWARE) {
                    decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE
                }
                decoder.isMutableRequired = false
            }
        } catch (e: IOException) {
            throw IllegalStateException("ImageDecoder failed to decode bitmap", e)
        }
    }

    companion object {
        private const val FILE_PREFIX = "file://"
        private const val ASSET_PREFIX = "${FILE_PREFIX}/android_asset/"
        private const val RESOURCE_PREFIX = "${ContentResolver.SCHEME_ANDROID_RESOURCE}://"
    }
}
