package com.sonai.ssiv.test.animation

import android.graphics.PointF
import android.os.Bundle
import android.view.View
import com.sonai.ssiv.ImageSource
import com.sonai.ssiv.SubsamplingScaleImageView
import com.sonai.ssiv.test.AbstractPagesActivity
import com.sonai.ssiv.test.Page
import com.sonai.ssiv.test.R
import com.sonai.ssiv.test.extension.views.PinView
import java.util.Random

class AnimationActivity : AbstractPagesActivity(
    R.string.animation_title, R.layout.animation_activity, listOf(
        Page(R.string.animation_p1_subtitle, R.string.animation_p1_text),
        Page(R.string.animation_p2_subtitle, R.string.animation_p2_text),
        Page(R.string.animation_p3_subtitle, R.string.animation_p3_text),
        Page(R.string.animation_p4_subtitle, R.string.animation_p4_text)
    )
) {

    private lateinit var view: PinView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<View>(R.id.play).setOnClickListener { play() }
        view = findViewById(R.id.imageView)
        view.setImage(ImageSource.asset("sanmartino.jpg"))
    }

    override fun onPageChanged(page: Int) {
        if (page == 2) {
            view.panLimit = SubsamplingScaleImageView.PAN_LIMIT_CENTER
        } else {
            view.panLimit = SubsamplingScaleImageView.PAN_LIMIT_INSIDE
        }
    }

    private fun play() {
        val random = Random()
        if (view.isReady) {
            val maxScale = view.maxScale
            val minScale = view.minScale
            val scale = (random.nextFloat() * (maxScale - minScale)) + minScale
            val center = PointF(
                random.nextInt(view.sWidth).toFloat(),
                random.nextInt(view.sHeight).toFloat()
            )
            view.setPin(center)
            val animationBuilder = view.animateScaleAndCenter(scale, center)
            if (getPage() == 3) {
                animationBuilder?.withDuration(2000)
                    ?.withEasing(SubsamplingScaleImageView.EASE_OUT_QUAD)
                    ?.withInterruptible(false)
                    ?.start()
            } else {
                animationBuilder?.withDuration(750)?.start()
            }
        }
    }
}
