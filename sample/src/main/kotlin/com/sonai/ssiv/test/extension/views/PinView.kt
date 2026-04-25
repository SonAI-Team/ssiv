package com.sonai.ssiv.test.extension.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import androidx.core.graphics.scale
import com.sonai.ssiv.SubsamplingScaleImageView
import com.sonai.ssiv.test.R

class PinView @JvmOverloads constructor(
    context: Context,
    attr: AttributeSet? = null
) : SubsamplingScaleImageView(context, attr) {

    private val paint = Paint()
    private val vPin = PointF()
    private var sPin: PointF? = null
    private var pin: Bitmap? = null

    init {
        initialise()
    }

    fun setPin(sPin: PointF) {
        this.sPin = sPin
        initialise()
        invalidate()
    }

    private fun initialise() {
        val density = resources.displayMetrics.densityDpi
        val originalPin = BitmapFactory.decodeResource(resources, R.drawable.pushpin_blue)
        if (originalPin != null) {
            val w = (density / 420f) * originalPin.width
            val h = (density / 420f) * originalPin.height
            pin = originalPin.scale(w.toInt(), h.toInt())
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Don't draw pin before image is ready so it doesn't move around during setup.
        if (!isReady) {
            return
        }

        paint.isAntiAlias = true

        val currentPin = pin
        val currentSPin = sPin
        if (currentSPin != null && currentPin != null) {
            sourceToViewCoord(currentSPin, vPin)
            val vX = vPin.x - (currentPin.width.toFloat() / 2)
            val vY = vPin.y - currentPin.height
            canvas.drawBitmap(currentPin, vX, vY, paint)
        }
    }
}
