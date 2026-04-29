package com.sonai.ssiv.test.extension.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import com.sonai.ssiv.SubsamplingScaleImageView

class CircleView @JvmOverloads constructor(
    context: Context,
    attr: AttributeSet? = null
) : SubsamplingScaleImageView(context, attr) {

    private var strokeWidth: Int = 0
    private val sCenter = PointF()
    private val vCenter = PointF()
    private val paint = Paint()

    init {
        val density = resources.displayMetrics.densityDpi
        strokeWidth = (density / 60f).toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Don't draw pin before image is ready so it doesn't move around during setup.
        if (!isReady) {
            return
        }

        sCenter.set(sWidth.toFloat() / 2, sHeight.toFloat() / 2)
        sourceToViewCoord(sCenter, vCenter)
        val radius = (scale * sWidth) * 0.25f

        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = (strokeWidth * 2).toFloat()
        paint.color = Color.BLACK
        canvas.drawCircle(vCenter.x, vCenter.y, radius, paint)
        paint.strokeWidth = strokeWidth.toFloat()
        paint.color = Color.argb(255, 51, 181, 229)
        canvas.drawCircle(vCenter.x, vCenter.y, radius, paint)
    }
}
