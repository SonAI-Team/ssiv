package com.sonai.ssiv.internal

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.AnyThread
import androidx.annotation.CheckResult
import androidx.exifinterface.media.ExifInterface
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
    fun getExifOrientation(context: Context, sourceUri: Uri): Int = runCatching {
        var exifOrientation = SubsamplingScaleImageView.ORIENTATION_0
        when (sourceUri.scheme) {
            URI_SCHEME_CONTENT -> {
                val columns = arrayOf(MediaStore.Images.Media.ORIENTATION)
                context.contentResolver.query(sourceUri, columns, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
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
                }
            }

            URI_SCHEME_FILE -> {
                val path = sourceUri.schemeSpecificPart
                if (path != null && path.startsWith(URI_PATH_ASSET)) {
                    context.assets.openFd(path.substring(URI_PATH_ASSET.length)).use {
                        exifOrientation = ExifInterface(it.fileDescriptor).getSsivOrientation(exifOrientation)
                    }
                } else if (path != null) {
                    exifOrientation = ExifInterface(path).getSsivOrientation(exifOrientation)
                }
            }

            URI_SCHEME_ZIP -> {
                exifOrientation = sourceUri.useZipEntry { file, entry ->
                    file.getInputStream(entry).use {
                        ExifInterface(it).getSsivOrientation(exifOrientation)
                    }
                }
            }
        }
        exifOrientation
    }.onFailure { e ->
        Log.w(TAG, "Could not get EXIF orientation of image: $e")
    }.getOrDefault(SubsamplingScaleImageView.ORIENTATION_0)

    @CheckResult
    private fun ExifInterface.getSsivOrientation(fallback: Int) = when (
        val exifAttr = getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    ) {
        ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED -> SubsamplingScaleImageView.ORIENTATION_0

        ExifInterface.ORIENTATION_ROTATE_90 -> SubsamplingScaleImageView.ORIENTATION_90
        ExifInterface.ORIENTATION_ROTATE_180 -> SubsamplingScaleImageView.ORIENTATION_180
        ExifInterface.ORIENTATION_ROTATE_270 -> SubsamplingScaleImageView.ORIENTATION_270
        else -> {
            Log.w(TAG, "Unsupported EXIF orientation: $exifAttr")
            fallback
        }
    }
}
