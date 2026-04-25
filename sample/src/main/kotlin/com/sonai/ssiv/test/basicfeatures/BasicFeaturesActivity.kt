package com.sonai.ssiv.test.basicfeatures

import android.os.Bundle
import com.sonai.ssiv.ImageSource
import com.sonai.ssiv.SubsamplingScaleImageView
import com.sonai.ssiv.test.AbstractPagesActivity
import com.sonai.ssiv.test.Page
import com.sonai.ssiv.test.R

class BasicFeaturesActivity : AbstractPagesActivity(
    R.string.basic_title, R.layout.pages_activity, listOf(
        Page(R.string.basic_p1_subtitle, R.string.basic_p1_text),
        Page(R.string.basic_p2_subtitle, R.string.basic_p2_text),
        Page(R.string.basic_p3_subtitle, R.string.basic_p3_text),
        Page(R.string.basic_p4_subtitle, R.string.basic_p4_text),
        Page(R.string.basic_p5_subtitle, R.string.basic_p5_text)
    )
) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = findViewById<SubsamplingScaleImageView>(R.id.imageView)
        view.setImage(ImageSource.asset("sanmartino.jpg"))
    }
}
