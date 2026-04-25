package com.sonai.ssiv.ai

import android.graphics.Bitmap
import android.graphics.Rect

// Interface for AI-based image enhancement on tiles.
interface TileEnhancer {
    /**
     * Enhances the given bitmap.
     * @param bitmap The bitmap to enhance.
     * @param sRect The source rectangle of the tile.
     * @param sampleSize The sample size of the tile.
     * @return The enhanced bitmap, or the original if enhancement failed.
     */
    fun enhance(bitmap: Bitmap, sRect: Rect, sampleSize: Int): Bitmap
}
