package com.sonai.ssiv.ai

import android.graphics.Bitmap

// Interface for AI-based image enhancement on tiles.
interface TileEnhancer {
    /**
     * Enhances the given bitmap.
     * @param bitmap The bitmap to enhance.
     * @return The enhanced bitmap, or the original if enhancement failed.
     */
    fun enhance(bitmap: Bitmap): Bitmap
}
