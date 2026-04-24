package com.sonai.ssiv.test.extension.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.sonai.ssiv.SubsamplingScaleImageView
import kotlin.math.abs

class FreehandView @JvmOverloads constructor(
    context: Context,
    attr: AttributeSet? = null
) : SubsamplingScaleImageView(context, attr), View.OnTouchListener {

    private val paint = Paint()
    private val vPath = Path()
    private var vPrev = PointF()
    private var vPoint = PointF()
    private var vPrevious: PointF? = null
    private var vStart: PointF? = null
    private var drawing = false
    private var strokeWidth: Int = 0
    private var sPoints: MutableList<PointF>? = null

    init {
        setOnTouchListener(this)
        val density = resources.displayMetrics.densityDpi
        strokeWidth = (density / 60f).toInt()
    }

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (sPoints != null && !drawing) {
            return super.onTouchEvent(event)
        }
        var consumed = false
        val touchCount = event.pointerCount
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.actionIndex == 0) {
                    vStart = PointF(event.x, event.y)
                    vPrevious = PointF(event.x, event.y)
                } else {
                    vStart = null
                    vPrevious = null
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val sCurrentF = viewToSourceCoord(event.x, event.y)
                val sCurrent = sCurrentF?.let { PointF(it.x, it.y) }
                val sStart = vStart?.let {
                    val converted = viewToSourceCoord(it.x, it.y)
                    converted?.let { p -> PointF(p.x, p.y) }
                }

                if (touchCount == 1 && vStart != null && vPrevious != null && sCurrent != null) {
                    val vDX = abs(event.x - vPrevious!!.x)
                    val vDY = abs(event.y - vPrevious!!.y)
                    if (vDX >= strokeWidth * 5 || vDY >= strokeWidth * 5) {
                        if (sPoints == null) {
                            sPoints = ArrayList()
                            sStart?.let { sPoints?.add(it) }
                        }
                        sPoints?.add(sCurrent)
                        vPrevious?.x = event.x
                        vPrevious?.y = event.y
                        drawing = true
                    }
                    consumed = true
                    invalidate()
                } else if (touchCount == 1) {
                    // Consume all one touch drags to prevent odd panning effects handled by the superclass.
                    consumed = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                invalidate()
                drawing = false
                vPrevious = null
                vStart = null
            }
        }
        // Use parent to handle pinch and two-finger pan.
        return consumed || super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Don't draw anything before image is ready.
        if (!isReady) {
            return
        }

        paint.isAntiAlias = true

        val points = sPoints
        if (points != null && points.size >= 2) {
            vPath.reset()
            sourceToViewCoord(points[0].x, points[0].y, vPrev)
            vPath.moveTo(vPrev.x, vPrev.y)
            for (i in 1 until points.size) {
                sourceToViewCoord(points[i].x, points[i].y, vPoint)
                vPath.quadTo(vPrev.x, vPrev.y, (vPoint.x + vPrev.x) / 2, (vPoint.y + vPrev.y) / 2)
                vPrev.set(vPoint)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = (strokeWidth * 2).toFloat()
            paint.color = Color.BLACK
            canvas.drawPath(vPath, paint)
            paint.strokeWidth = strokeWidth.toFloat()
            paint.color = Color.argb(255, 51, 181, 229)
            canvas.drawPath(vPath, paint)
        }
    }

    fun reset() {
        this.sPoints = null
        invalidate()
    }
}
