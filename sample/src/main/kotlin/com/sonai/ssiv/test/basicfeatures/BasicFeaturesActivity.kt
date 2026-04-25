package com.sonai.ssiv.test.basicfeatures

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.sonai.ssiv.ImageSource
import com.sonai.ssiv.SubsamplingScaleImageView
import com.sonai.ssiv.ai.MediaPipeTileEnhancer
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

    private var view: SubsamplingScaleImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = findViewById(R.id.imageView)
        view?.setTileEnhancer(MediaPipeTileEnhancer(this, "assets/model.tflite"))
        view?.setImage(ImageSource.asset("sanmartino.jpg"))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_basic, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_ai) {
            item.isChecked = !item.isChecked
            if (item.isChecked) {
                view?.setTileEnhancer(MediaPipeTileEnhancer(this, "assets/model.tflite"))
            } else {
                view?.setTileEnhancer(null)
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
