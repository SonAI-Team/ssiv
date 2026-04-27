package com.sonai.ssiv.decoder

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.nio.ByteBuffer

/**
 * Interface for image decoding classes, allowing the default [android.graphics.BitmapFactory]
 * based on the Skia library to be replaced with a custom class.
 */
interface SSIVImageDecoder {

    /**
     * Decode an image. The URI can be in one of the following formats:
     * <br>
     * File: `file:///sdcard/picture.jpg`
     * <br>
     * Asset: `file:///android_asset/picture.png`
     * <br>
     * Resource: `android.resource://com.example.app/drawable/picture`
     *
     * @param context Application context
     * @param uri URI of the image
     * @return the decoded bitmap
     * @throws Exception if decoding fails.
     */
    @Throws(Exception::class)
    fun decode(context: Context, uri: Uri): Bitmap

    /**
     * Decode an image from a [ByteBuffer].
     *
     * @param context Application context
     * @param buffer ByteBuffer containing the image data
     * @return the decoded bitmap
     * @throws Exception if decoding fails.
     */
    @Throws(Exception::class)
    fun decode(context: Context, buffer: ByteBuffer): Bitmap {
        throw UnsupportedOperationException("ByteBuffer decoding not supported")
    }

}
