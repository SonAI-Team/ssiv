package com.sonai.ssiv.decoder

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.Keep
import androidx.core.text.isDigitsOnly
import com.sonai.ssiv.SubsamplingScaleImageView
import java.io.InputStream

/**
 * Default implementation of [SSIVImageDecoder]
 * using Android's [BitmapFactory], based on the Skia library. This
 * works well in most circumstances and has reasonable performance, however it has some problems
 * with grayscale, indexed and CMYK images.
 */
class SkiaSSIVImageDecoder @Keep constructor(bitmapConfig: Bitmap.Config? = null) : SSIVImageDecoder {

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
    override fun decode(context: Context, uri: Uri): Bitmap {
        val uriString = uri.toString()
        val options = BitmapFactory.Options()
        var bitmap: Bitmap?
        options.inPreferredConfig = bitmapConfig
        if (uriString.startsWith(RESOURCE_PREFIX)) {
            val res: Resources
            val packageName = uri.authority
            if (context.packageName == packageName) {
                res = context.resources
            } else {
                val pm = context.packageManager
                res = pm.getResourcesForApplication(packageName!!)
            }

            var id = 0
            val segments = uri.pathSegments
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

            bitmap = BitmapFactory.decodeResource(context.resources, id, options)
        } else if (uriString.startsWith(ASSET_PREFIX)) {
            val assetName = uriString.substring(ASSET_PREFIX.length)
            bitmap = BitmapFactory.decodeStream(context.assets.open(assetName), null, options)
        } else if (uriString.startsWith(FILE_PREFIX)) {
            bitmap = BitmapFactory.decodeFile(uriString.substring(FILE_PREFIX.length), options)
        } else {
            var inputStream: InputStream? = null
            try {
                val contentResolver = context.contentResolver
                inputStream = contentResolver.openInputStream(uri)
                bitmap = BitmapFactory.decodeStream(inputStream, null, options)
            } finally {
                inputStream?.let {
                    try {
                        it.close()
                    } catch (e: Exception) { /* Ignore */
                    }
                }
            }
        }
        if (bitmap == null) {
            throw RuntimeException("Skia image region decoder returned null bitmap - image format may not be supported")
        }
        return bitmap
    }

    companion object {
        private const val FILE_PREFIX = "file://"
        private const val ASSET_PREFIX = "$FILE_PREFIX/android_asset/"
        private const val RESOURCE_PREFIX = "${ContentResolver.SCHEME_ANDROID_RESOURCE}://"
    }
}
