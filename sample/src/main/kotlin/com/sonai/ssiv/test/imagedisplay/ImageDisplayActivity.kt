package com.sonai.ssiv.test.imagedisplay

import android.util.Log
import com.sonai.ssiv.test.AbstractFragmentsActivity
import com.sonai.ssiv.test.Page
import com.sonai.ssiv.test.R

class ImageDisplayActivity : AbstractFragmentsActivity(
    R.string.display_title, R.layout.fragments_activity, listOf(
        Page(R.string.display_p1_subtitle, R.string.display_p1_text),
        Page(R.string.display_p2_subtitle, R.string.display_p2_text),
        Page(R.string.display_p3_subtitle, R.string.display_p3_text)
    )
) {

    override fun onPageChanged(page: Int) {
        try {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.frame, FRAGMENTS[page].getDeclaredConstructor().newInstance())
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load fragment", e)
        }
    }

    companion object {
        private val TAG = ImageDisplayActivity::class.java.simpleName
        private val FRAGMENTS = listOf(
            ImageDisplayLargeFragment::class.java,
            ImageDisplayRotateFragment::class.java,
            ImageDisplayRegionFragment::class.java
        )
    }
}
