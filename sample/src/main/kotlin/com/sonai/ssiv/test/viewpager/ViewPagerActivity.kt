package com.sonai.ssiv.test.viewpager

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
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
        val horizontalPager = findViewById<ViewPager2>(R.id.horizontal_pager)
        horizontalPager.adapter = ScreenSlidePagerAdapter(this)
        val verticalPager = findViewById<ViewPager2>(R.id.vertical_pager)
        verticalPager.adapter = ScreenSlidePagerAdapter(this)
        verticalPager.orientation = ViewPager2.ORIENTATION_VERTICAL

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val viewPager = findViewById<ViewPager2>(
                    if (getPage() == 0) R.id.horizontal_pager else R.id.vertical_pager
                )
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

    private class ScreenSlidePagerAdapter(activity: FragmentActivity) :
        FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = IMAGES.size

        override fun createFragment(position: Int): Fragment {
            val fragment = ViewPagerFragment()
            fragment.setAsset(IMAGES[position])
            return fragment
        }
    }

    companion object {
        private val IMAGES = arrayOf("sanmartino.jpg", "swissroad.jpg")
    }
}
