package com.sonai.ssiv.test.imagedisplay

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.sonai.ssiv.ImageSource
import com.sonai.ssiv.SubsamplingScaleImageView
import com.sonai.ssiv.test.R

class ImageDisplayLargeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.imagedisplay_large_fragment, container, false)
        val activity = activity as? ImageDisplayActivity
        if (activity != null) {
            rootView.findViewById<View>(R.id.next).setOnClickListener { activity.next() }
        }
        val imageView = rootView.findViewById<SubsamplingScaleImageView>(R.id.imageView)
        imageView.setImage(ImageSource.asset("card.png"))
        return rootView
    }
}
