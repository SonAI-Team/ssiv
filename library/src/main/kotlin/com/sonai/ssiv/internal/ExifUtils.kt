package com.sonai.ssiv.internal

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.AnyThread
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.sonai.ssiv.ImageSource
import com.sonai.ssiv.SubsamplingScaleImageView

internal object ExifUtils {
    private val TAG = ExifUtils::class.java.simpleName

    @Suppress(
        "LongMethod",
        "CyclomaticComplexMethod",
        "NestedBlockDepth",
        "TooGenericExceptionCaught",
        "SwallowedException",
        "MaxLineLength"
    )
    @AnyThread
    fun getExifOrientation(context: Context, sourceUri: String): Int {
        var exifOrientation = SubsamplingScaleImageView.ORIENTATION_0
        if (sourceUri.startsWith(ContentResolver.SCHEME_CONTENT)) {
            var cursor: Cursor? = null
            try {
                val columns = arrayOf(MediaStore.Images.Media.ORIENTATION)
                cursor = context.contentResolver.query(sourceUri.toUri(), columns, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val orientationValue = cursor.getInt(0)
                    val validOrientations = listOf(
                        SubsamplingScaleImageView.ORIENTATION_0,
                        SubsamplingScaleImageView.ORIENTATION_90,
                        SubsamplingScaleImageView.ORIENTATION_180,
                        SubsamplingScaleImageView.ORIENTATION_270,
                        SubsamplingScaleImageView.ORIENTATION_USE_EXIF
                    )
                    if (validOrientations.contains(orientationValue) &&
                        orientationValue != SubsamplingScaleImageView.ORIENTATION_USE_EXIF
                    ) {
                        exifOrientation = orientationValue
                    } else {
                        Log.w(TAG, "Unsupported orientation: $orientationValue")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not get orientation of image from media store")
            } finally {
                cursor?.close()
            }
        } else if (sourceUri.startsWith(ImageSource.FILE_SCHEME) &&
            !sourceUri.startsWith(ImageSource.ASSET_SCHEME)
        ) {
            try {
                val exifInterface =
                    ExifInterface(sourceUri.substring(ImageSource.FILE_SCHEME.length - 1))
                val orientationAttr = exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                when (orientationAttr) {
                    ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED -> {
                        exifOrientation = SubsamplingScaleImageView.ORIENTATION_0
                    }

                    ExifInterface.ORIENTATION_ROTATE_90 -> {
                        exifOrientation = SubsamplingScaleImageView.ORIENTATION_90
                    }

                    ExifInterface.ORIENTATION_ROTATE_180 -> {
                        exifOrientation = SubsamplingScaleImageView.ORIENTATION_180
                    }

                    ExifInterface.ORIENTATION_ROTATE_270 -> {
                        exifOrientation = SubsamplingScaleImageView.ORIENTATION_270
                    }

                    else -> {
                        Log.w(TAG, "Unsupported EXIF orientation: $orientationAttr")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not get EXIF orientation of image")
            }
        }
        return exifOrientation
    }
}
