package com.sonai.ssiv

import android.graphics.PointF
import java.io.Serializable

// Wraps the scale, center and orientation of a displayed image for easy restoration on screen rotate.
class ImageViewState(val scale: Float, center: PointF, val orientation: Int) : Serializable {

    companion object {
        private const val serialVersionUID = 1L
    }

    private val centerX: Float = center.x
    private val centerY: Float = center.y

    val center: PointF
        get() = PointF(centerX, centerY)

}
