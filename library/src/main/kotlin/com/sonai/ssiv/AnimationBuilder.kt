package com.sonai.ssiv

import android.graphics.PointF
import android.util.Log
import com.sonai.ssiv.internal.Anim

class AnimationBuilder {
    private val view: SubsamplingScaleImageView
    private val targetScale: Float
    private val targetSCenter: PointF
    private val vFocus: PointF?
    private var duration: Long = DEFAULT_DURATION
    private var easing = SubsamplingScaleImageView.EASE_IN_OUT_QUAD
    private var interpolator: android.view.animation.Interpolator? = null
    private var origin = SubsamplingScaleImageView.ORIGIN_ANIM
    private var interruptible = true
    private var panLimited = true
    private var listener: OnAnimationEventListener? = null

    internal constructor(view: SubsamplingScaleImageView, sCenter: PointF) {
        this.view = view
        this.targetScale = view.scale
        this.targetSCenter = sCenter
        this.vFocus = null
    }

    internal constructor(view: SubsamplingScaleImageView, scale: Float, sCenter: PointF) {
        this.view = view
        this.targetScale = scale
        this.targetSCenter = sCenter
        this.vFocus = null
    }

    internal constructor(
        view: SubsamplingScaleImageView,
        scale: Float,
        sCenter: PointF,
        vFocus: PointF
    ) {
        this.view = view
        this.targetScale = scale
        this.targetSCenter = sCenter
        this.vFocus = vFocus
    }

    fun withDuration(duration: Long): AnimationBuilder {
        this.duration = duration
        return this
    }

    fun withInterruptible(interruptible: Boolean): AnimationBuilder {
        this.interruptible = interruptible
        return this
    }

    fun withEasing(easing: Int): AnimationBuilder {
        val validEasingStyles = listOf(
            SubsamplingScaleImageView.EASE_IN_OUT_QUAD,
            SubsamplingScaleImageView.EASE_OUT_QUAD
        )
        require(validEasingStyles.contains(easing)) { "Unknown easing type: $easing" }
        this.easing = easing
        return this
    }

    fun withInterpolator(interpolator: android.view.animation.Interpolator?): AnimationBuilder {
        this.interpolator = interpolator
        return this
    }

    fun withOnAnimationEventListener(listener: OnAnimationEventListener?): AnimationBuilder {
        this.listener = listener
        return this
    }

    fun withPanLimited(panLimited: Boolean): AnimationBuilder {
        this.panLimited = panLimited
        return this
    }

    internal fun withOrigin(origin: Int): AnimationBuilder {
        this.origin = origin
        return this
    }

    @Suppress("TooGenericExceptionCaught")
    fun start() {
        view.anim?.let {
            it.listener?.let { listener ->
                try {
                    listener.onInterruptedByNewAnim()
                } catch (e: Exception) {
                    Log.w("SSIV", "Error thrown by animation listener", e)
                }
            }
        }

        val vxCenter = view.paddingLeft + (view.width - view.paddingRight - view.paddingLeft) / 2
        val vyCenter = view.paddingTop + (view.height - view.paddingBottom - view.paddingTop) / 2
        val targetScale = view.limitedScale(this.targetScale)
        val targetSCenter = if (panLimited) {
            view.limitedSCenter(this.targetSCenter.x, this.targetSCenter.y, targetScale, PointF())
        } else {
            this.targetSCenter
        }

        val anim = Anim().apply {
            this.scaleStart = view.scale
            this.scaleEnd = targetScale
            this.time = System.currentTimeMillis()
            this.sCenterEndRequested = targetSCenter
            this.sCenterStart = view.center
            this.sCenterEnd = targetSCenter
            this.vFocusStart = view.sourceToViewCoord(targetSCenter)
            this.vFocusEnd = PointF(vxCenter.toFloat(), vyCenter.toFloat())
            this.duration = this@AnimationBuilder.duration
            this.interruptible = this@AnimationBuilder.interruptible
            this.easing = this@AnimationBuilder.easing
            this.interpolator = this@AnimationBuilder.interpolator
            this.origin = this@AnimationBuilder.origin
            this.listener = this@AnimationBuilder.listener
        }

        if (vFocus != null) {
            val vTranslateXEnd = vFocus.x - targetScale * (anim.sCenterStart?.x ?: 0f)
            val vTranslateYEnd = vFocus.y - targetScale * (anim.sCenterStart?.y ?: 0f)
            val satEnd = ScaleAndTranslate(targetScale, PointF(vTranslateXEnd, vTranslateYEnd))
            view.fitToBounds(true, satEnd)
            anim.vFocusEnd = PointF(
                vFocus.x + (satEnd.vTranslate.x - vTranslateXEnd),
                vFocus.y + (satEnd.vTranslate.y - vTranslateYEnd)
            )
        }

        view.anim = anim
        view.invalidate()
    }

    companion object {
        private const val DEFAULT_DURATION = 500L
    }
}
