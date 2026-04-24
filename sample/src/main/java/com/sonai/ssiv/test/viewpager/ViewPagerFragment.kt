package com.sonai.ssiv.test.viewpager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.sonai.ssiv.ImageSource
import com.sonai.ssiv.SubsamplingScaleImageView
import com.sonai.ssiv.test.R

class ViewPagerFragment : Fragment() {

    private var asset: String? = null

    fun setAsset(asset: String) {
        this.asset = asset
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.view_pager_page, container, false)

        if (savedInstanceState != null) {
            if (asset == null && savedInstanceState.containsKey(BUNDLE_ASSET)) {
                asset = savedInstanceState.getString(BUNDLE_ASSET)
            }
        }
        asset?.let {
            val imageView = rootView.findViewById<SubsamplingScaleImageView>(R.id.imageView)
            imageView.setImage(ImageSource.asset(it))
        }

        return rootView
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(BUNDLE_ASSET, asset)
    }

    companion object {
        private const val BUNDLE_ASSET = "asset"
    }
}
