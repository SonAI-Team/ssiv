package com.sonai.ssiv.test.configuration

import android.graphics.PointF
import android.os.Bundle
import com.sonai.ssiv.ImageSource
import com.sonai.ssiv.SubsamplingScaleImageView
import com.sonai.ssiv.test.AbstractPagesActivity
import com.sonai.ssiv.test.Page
import com.sonai.ssiv.test.R

class ConfigurationActivity : AbstractPagesActivity(
    R.string.configuration_title, R.layout.pages_activity, listOf(
        Page(R.string.configuration_p1_subtitle, R.string.configuration_p1_text),
        Page(R.string.configuration_p2_subtitle, R.string.configuration_p2_text),
        Page(R.string.configuration_p3_subtitle, R.string.configuration_p3_text),
        Page(R.string.configuration_p4_subtitle, R.string.configuration_p4_text),
        Page(R.string.configuration_p5_subtitle, R.string.configuration_p5_text),
        Page(R.string.configuration_p6_subtitle, R.string.configuration_p6_text),
        Page(R.string.configuration_p7_subtitle, R.string.configuration_p7_text),
        Page(R.string.configuration_p8_subtitle, R.string.configuration_p8_text),
        Page(R.string.configuration_p9_subtitle, R.string.configuration_p9_text),
        Page(R.string.configuration_p10_subtitle, R.string.configuration_p10_text)
    )
) {

    private lateinit var view: SubsamplingScaleImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = findViewById(R.id.imageView)
        view.setImage(ImageSource.asset("card.png"))
    }

    override fun onPageChanged(page: Int) {
        if (page == 0) {
            view.setMinimumDpi(50)
        } else {
            view.setMaxScale(2F)
        }
        if (page == 1) {
            view.setMinimumTileDpi(50)
        } else {
            view.setMinimumTileDpi(320)
        }
        when (page) {
            4 -> view.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
            5 -> view.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER_IMMEDIATE)
            else -> view.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_FIXED)
        }
        if (page == 6) {
            view.setDoubleTapZoomDpi(240)
        } else {
            view.setDoubleTapZoomScale(1F)
        }
        when (page) {
            7 -> view.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_CENTER)
            8 -> view.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_OUTSIDE)
            else -> view.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
        }
        view.setDebug(page == 9)
        if (page == 2) {
            view.setScaleAndCenter(0f, PointF(3900f, 3120f))
            view.setPanEnabled(false)
        } else {
            view.setPanEnabled(true)
        }
        if (page == 3) {
            view.setScaleAndCenter(1f, PointF(3900f, 3120f))
            view.setZoomEnabled(false)
        } else {
            view.setZoomEnabled(true)
        }
    }
}
