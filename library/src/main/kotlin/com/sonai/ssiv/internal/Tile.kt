package com.sonai.ssiv.internal

import android.graphics.Bitmap
import android.graphics.Rect

class Tile {
    var sRect: Rect? = null
    var sampleSize = 0

    @Volatile
    var bitmap: Bitmap? = null

    @Volatile
    var loading = false

    @Volatile
    var visible = false

    // Volatile as these are used between the rendering and loading threads
    @Volatile
    var vRect: Rect? = null

    @Volatile
    var fileSRect: Rect? = null
}
