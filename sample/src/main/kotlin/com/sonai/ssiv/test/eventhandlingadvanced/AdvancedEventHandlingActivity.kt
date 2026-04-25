package com.sonai.ssiv.test.eventhandlingadvanced

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import com.sonai.ssiv.ImageSource
import com.sonai.ssiv.SubsamplingScaleImageView
import com.sonai.ssiv.test.AbstractPagesActivity
import com.sonai.ssiv.test.Page
import com.sonai.ssiv.test.R

class AdvancedEventHandlingActivity : AbstractPagesActivity(
    R.string.advancedevent_title, R.layout.pages_activity, listOf(
        Page(R.string.advancedevent_p1_subtitle, R.string.advancedevent_p1_text),
        Page(R.string.advancedevent_p2_subtitle, R.string.advancedevent_p2_text),
        Page(R.string.advancedevent_p3_subtitle, R.string.advancedevent_p3_text),
        Page(R.string.advancedevent_p4_subtitle, R.string.advancedevent_p4_text),
        Page(R.string.advancedevent_p5_subtitle, R.string.advancedevent_p5_text)
    )
) {

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageView = findViewById<SubsamplingScaleImageView>(R.id.imageView)
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (imageView.isReady) {
                    val sCoord = imageView.viewToSourceCoord(e.x, e.y)
                    sCoord?.let {
                        Toast.makeText(applicationContext, "Single tap: ${it.x.toInt()}, ${it.y.toInt()}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(applicationContext, "Single tap: Image not ready", Toast.LENGTH_SHORT).show()
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (imageView.isReady) {
                    val sCoord = imageView.viewToSourceCoord(e.x, e.y)
                    sCoord?.let {
                        Toast.makeText(applicationContext, "Long press: ${it.x.toInt()}, ${it.y.toInt()}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(applicationContext, "Long press: Image not ready", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (imageView.isReady) {
                    val sCoord = imageView.viewToSourceCoord(e.x, e.y)
                    sCoord?.let {
                        Toast.makeText(applicationContext, "Double tap: ${it.x.toInt()}, ${it.y.toInt()}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(applicationContext, "Double tap: Image not ready", Toast.LENGTH_SHORT).show()
                }
                return true
            }
        })

        imageView.setImage(ImageSource.asset("sanmartino.jpg"))
        imageView.setOnTouchListener { _, motionEvent -> gestureDetector.onTouchEvent(motionEvent) }
    }
}
