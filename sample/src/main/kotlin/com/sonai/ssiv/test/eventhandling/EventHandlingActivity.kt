package com.sonai.ssiv.test.eventhandling

import android.os.Bundle
import android.widget.Toast
import com.sonai.ssiv.ImageSource
import com.sonai.ssiv.SubsamplingScaleImageView
import com.sonai.ssiv.test.AbstractPagesActivity
import com.sonai.ssiv.test.Page
import com.sonai.ssiv.test.R

class EventHandlingActivity : AbstractPagesActivity(
    R.string.event_title, R.layout.pages_activity, listOf(
        Page(R.string.event_p1_subtitle, R.string.event_p1_text),
        Page(R.string.event_p2_subtitle, R.string.event_p2_text),
        Page(R.string.event_p3_subtitle, R.string.event_p3_text)
    )
) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageView = findViewById<SubsamplingScaleImageView>(R.id.imageView)
        imageView.setImage(ImageSource.asset("sanmartino.jpg"))
        imageView.setOnClickListener { v ->
            Toast.makeText(v.context, "Clicked", Toast.LENGTH_SHORT).show()
        }
        imageView.setOnLongClickListener { v ->
            Toast.makeText(v.context, "Long clicked", Toast.LENGTH_SHORT).show()
            true
        }
    }
}
