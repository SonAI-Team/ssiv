package com.sonai.ssiv

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import androidx.core.net.toUri
import java.io.File
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.nio.ByteBuffer

/**
 * Helper class used to set the source and additional attributes from a variety of sources. Supports
 * use of a bitmap, asset, resource, external file or any other URI.
 *
 * When you are using a preview image, you must set the dimensions of the full size image on the
 * ImageSource object for the full size image using the {@link #dimensions(int, int)} method.
 */
class ImageSource {

    internal val uri: Uri?
    internal val bitmap: Bitmap?
    internal val buffer: ByteBuffer?
    internal val resource: Int?
    internal var tile: Boolean = false
        private set
    internal var sWidth: Int = 0
        private set
    internal var sHeight: Int = 0
        private set
    internal var sRegion: Rect? = null
        private set
    internal var isCached: Boolean = false
        private set

    private constructor(bitmap: Bitmap, cached: Boolean) {
        this.bitmap = bitmap
        this.uri = null
        this.buffer = null
        this.resource = null
        this.tile = false
        this.sWidth = bitmap.width
        this.sHeight = bitmap.height
        this.isCached = cached
    }

    private constructor(buffer: ByteBuffer) {
        this.bitmap = null
        this.uri = null
        this.buffer = buffer
        this.resource = null
        this.tile = true
    }

    private constructor(uri: Uri) {
        var mutableUri = uri
        // #114 If file doesn't exist, attempt to url decode the URI and try again
        val uriString = mutableUri.toString()
        if (uriString.startsWith(FILE_SCHEME)) {
            val uriFile = File(uriString.substring(FILE_SCHEME.length - 1))
            if (!uriFile.exists()) {
                try {
                    mutableUri = URLDecoder.decode(uriString, "UTF-8").toUri()
                } catch (_: UnsupportedEncodingException) {
                    // Fallback to encoded URI. This exception is not expected.
                }
            }
        }
        this.bitmap = null
        this.uri = mutableUri
        this.buffer = null
        this.resource = null
        this.tile = true
    }

    private constructor(resource: Int) {
        this.bitmap = null
        this.uri = null
        this.buffer = null
        this.resource = resource
        this.tile = true
    }

    companion object {
        const val FILE_SCHEME = "file:///"
        const val ASSET_SCHEME = "file:///android_asset/"

        @JvmStatic
        fun resource(resId: Int): ImageSource = ImageSource(resId)

        @JvmStatic
        fun asset(assetName: String): ImageSource {
            return uri(ASSET_SCHEME + assetName)
        }

        @JvmStatic
        fun uri(uri: String): ImageSource {
            var mutableUri = uri
            if (!mutableUri.contains("://")) {
                if (mutableUri.startsWith("/")) {
                    mutableUri = mutableUri.substring(1)
                }
                mutableUri = FILE_SCHEME + mutableUri
            }
            return ImageSource(mutableUri.toUri())
        }

        @JvmStatic
        @Suppress("unused")
        fun uri(uri: Uri): ImageSource = ImageSource(uri)

        @JvmStatic
        fun buffer(buffer: ByteBuffer): ImageSource = ImageSource(buffer)

        @JvmStatic
        fun bitmap(bitmap: Bitmap): ImageSource = ImageSource(bitmap, false)

        @JvmStatic
        @Suppress("unused")
        fun cachedBitmap(bitmap: Bitmap): ImageSource = ImageSource(bitmap, true)
    }

    fun tilingEnabled(): ImageSource = tiling(true)

    @Suppress("unused")
    fun tilingDisabled(): ImageSource = tiling(false)

    fun tiling(tile: Boolean): ImageSource {
        this.tile = tile
        return this
    }

    fun region(sRegion: Rect?): ImageSource {
        this.sRegion = sRegion
        setInvariants()
        return this
    }

    fun dimensions(sWidth: Int, sHeight: Int): ImageSource {
        if (bitmap == null) {
            this.sWidth = sWidth
            this.sHeight = sHeight
        }
        setInvariants()
        return this
    }

    private fun setInvariants() {
        sRegion?.let {
            this.tile = true
            this.sWidth = it.width()
            this.sHeight = it.height()
        }
    }
}
