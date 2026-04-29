package com.sonai.ssiv.internal

import android.graphics.PointF
import com.sonai.ssiv.OnAnimationEventListener
import com.sonai.ssiv.SubsamplingScaleImageView

class Anim {
    var scaleStart = 0f
    var scaleEnd = 0f
    var sCenterStart: PointF? = null
    var sCenterEnd: PointF? = null
    var sCenterEndRequested: PointF? = null
    var vFocusStart: PointF? = null
    var vFocusEnd: PointF? = null
    var duration = DEFAULT_DURATION
    var interruptible = true
    var easing = SubsamplingScaleImageView.EASE_IN_OUT_QUAD
    var interpolator: android.view.animation.Interpolator? = null
    var origin = SubsamplingScaleImageView.ORIGIN_ANIM
    var time = System.currentTimeMillis()
    var listener: OnAnimationEventListener? = null

    companion object {
        private const val DEFAULT_DURATION = 500L
    }
}
