package com.sonai.ssiv.ai

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.collection.LruCache

/**
 * A simple cache for enhanced tiles to avoid re-processing the same area.
 */
class TileCache(maxSize: Int = 20) {
    
    private val cache = object : LruCache<String, Bitmap>(maxSize) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            // Optional: we don't recycle here because the bitmap might still be in use by SSIV
        }
    }

    private fun generateKey(rect: Rect, sampleSize: Int): String {
        return "${rect.left}-${rect.top}-${rect.right}-${rect.bottom}-$sampleSize"
    }

    fun get(rect: Rect, sampleSize: Int): Bitmap? {
        return cache.get(generateKey(rect, sampleSize))
    }

    fun put(rect: Rect, sampleSize: Int, bitmap: Bitmap) {
        cache.put(generateKey(rect, sampleSize), bitmap)
    }

    fun clear() {
        cache.evictAll()
    }
}
