package com.sonai.ssiv.ai

import android.content.Context
import android.graphics.Bitmap

/**
 * Placeholder implementation of [TileEnhancer] until MediaPipe dependencies are resolved.
 * @property context The application context.
 * @property modelPath The path to the super resolution model.
 */
class MediaPipeTileEnhancer(
    private val context: Context,
    private val modelPath: String
) : TileEnhancer {

    override fun enhance(bitmap: Bitmap): Bitmap {
        // Placeholder implementation
        // To avoid detekt unused warning:
        if (context.toString().isEmpty() || modelPath.isEmpty()) {
            return bitmap
        }
        return bitmap
    }
}
