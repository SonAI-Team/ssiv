package com.sonai.ssiv.test.viewpager

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.ViewPager
import androidx.activity.OnBackPressedCallback
import com.sonai.ssiv.test.AbstractPagesActivity
import com.sonai.ssiv.test.Page
import com.sonai.ssiv.test.R

class ViewPagerActivity : AbstractPagesActivity(
    R.string.pager_title, R.layout.view_pager, listOf(
        Page(R.string.pager_p1_subtitle, R.string.pager_p1_text),
        Page(R.string.pager_p2_subtitle, R.string.pager_p2_text)
    )
) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val horizontalPager = findViewById<ViewPager>(R.id.horizontal_pager)
        horizontalPager.adapter = ScreenSlidePagerAdapter(supportFragmentManager)
        val verticalPager = findViewById<ViewPager>(R.id.vertical_pager)
        verticalPager.adapter = ScreenSlidePagerAdapter(supportFragmentManager)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val viewPager = findViewById<ViewPager>(if (getPage() == 0) R.id.horizontal_pager else R.id.vertical_pager)
                if (viewPager.currentItem == 0) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                } else {
                    viewPager.currentItem -= 1
                }
            }
        })
    }

    override fun onPageChanged(page: Int) {
        if (getPage() == 0) {
            findViewById<View>(R.id.horizontal_pager).visibility = View.VISIBLE
            findViewById<View>(R.id.vertical_pager).visibility = View.GONE
        } else {
            findViewById<View>(R.id.horizontal_pager).visibility = View.GONE
            findViewById<View>(R.id.vertical_pager).visibility = View.VISIBLE
        }
    }

    @Suppress("DEPRECATION")
    private class ScreenSlidePagerAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun getItem(position: Int): Fragment {
            val fragment = ViewPagerFragment()
            fragment.setAsset(IMAGES[position])
            return fragment
        }

        override fun getCount(): Int {
            return IMAGES.size
        }
    }

    companion object {
        private val IMAGES = arrayOf("sanmartino.jpg", "swissroad.jpg")
    }
}
