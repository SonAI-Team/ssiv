package com.sonai.ssiv.internal

import android.graphics.Bitmap
import android.graphics.Rect

class Tile {
    var sRect: Rect? = null
    var sampleSize = 0
    var bitmap: Bitmap? = null
    var loading = false
    var visible = false

    // Volatile as these are used between the rendering and loading threads
    var vRect: Rect? = null
    var fileSRect: Rect? = null
    
    // Performance monitoring
    var enhancementTimeMs: Long = 0
}
