package com.sonai.ssiv.test.extension

import android.util.Log
import com.sonai.ssiv.test.AbstractFragmentsActivity
import com.sonai.ssiv.test.Page
import com.sonai.ssiv.test.R

class ExtensionActivity : AbstractFragmentsActivity(
    R.string.extension_title, R.layout.fragments_activity, listOf(
        Page(R.string.extension_p1_subtitle, R.string.extension_p1_text),
        Page(R.string.extension_p2_subtitle, R.string.extension_p2_text),
        Page(R.string.extension_p3_subtitle, R.string.extension_p3_text)
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
        private val TAG = ExtensionActivity::class.java.simpleName
        private val FRAGMENTS = listOf(
            ExtensionPinFragment::class.java,
            ExtensionCircleFragment::class.java,
            ExtensionFreehandFragment::class.java
        )
    }
}
