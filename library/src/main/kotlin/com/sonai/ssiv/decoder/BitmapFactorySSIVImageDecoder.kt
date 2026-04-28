package com.sonai.ssiv.decoder

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.Keep
import androidx.core.text.isDigitsOnly
import com.sonai.ssiv.SubsamplingScaleImageView
import java.nio.ByteBuffer

/**
 * An implementation of [SSIVImageDecoder] that uses [BitmapFactory] for older Android versions.
 */
class BitmapFactorySSIVImageDecoder @Keep constructor(bitmapConfig: Bitmap.Config? = null) :
    SSIVImageDecoder {

    private val preferredConfig: Bitmap.Config = bitmapConfig
        ?: SubsamplingScaleImageView.getPreferredBitmapConfig()
        ?: Bitmap.Config.ARGB_8888

    override fun decode(context: Context, uri: Uri): Bitmap {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = preferredConfig
        }

        val bitmap = when {
            uri.toString().startsWith(RESOURCE_PREFIX) -> {
                val id = uri.pathSegments.firstOrNull { it.isDigitsOnly() }?.toIntOrNull() ?: 0
                BitmapFactory.decodeResource(context.resources, id, options)
            }
            uri.toString().startsWith(ASSET_PREFIX) -> {
                val assetName = uri.toString().substring(ASSET_PREFIX.length)
                context.assets.open(assetName).use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            }
            else -> {
                context.contentResolver.openInputStream(uri).use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            }
        }

        return bitmap ?: throw IllegalStateException("BitmapFactory failed to decode bitmap")
    }

    override fun decode(context: Context, buffer: ByteBuffer): Bitmap {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = preferredConfig
        }
        val bytes = if (buffer.hasArray()) {
            buffer.array()
        } else {
            val b = ByteArray(buffer.remaining())
            buffer.get(b)
            b
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return bitmap ?: throw IllegalStateException("BitmapFactory failed to decode bitmap from ByteBuffer")
    }

    companion object {
        private const val FILE_PREFIX = "file://"
        private const val ASSET_PREFIX = "${FILE_PREFIX}/android_asset/"
        private const val RESOURCE_PREFIX = "${ContentResolver.SCHEME_ANDROID_RESOURCE}://"
    }
}
