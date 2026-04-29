package com.sonai.ssiv.test.imagedisplay

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import com.sonai.ssiv.ImageSource
import com.sonai.ssiv.SubsamplingScaleImageView
import com.sonai.ssiv.decoder.DecoderFactory
import com.sonai.ssiv.decoder.SkiaImageRegionDecoder
import com.sonai.ssiv.decoder.SkiaSSIVImageDecoder
import com.sonai.ssiv.test.R

class ImageDisplayRegionFragment : Fragment() {

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.imagedisplay_region_fragment, container, false)
        val imageView = rootView.findViewById<SubsamplingScaleImageView>(R.id.imageView)
        imageView.bitmapDecoderFactory =
            DecoderFactory { SkiaSSIVImageDecoder(Bitmap.Config.ARGB_8888) }
        imageView.regionDecoderFactory =
            DecoderFactory { SkiaImageRegionDecoder(Bitmap.Config.ARGB_8888) }
        imageView.orientation = SubsamplingScaleImageView.ORIENTATION_90
        imageView.setImage(ImageSource.asset("card.png").region(Rect(5200, 651, 8200, 3250)))
        val activity = activity as? ImageDisplayActivity
        if (activity != null) {
            rootView.findViewById<View>(R.id.previous).setOnClickListener { activity.previous() }
        }
        rootView.findViewById<View>(R.id.rotate).setOnClickListener {
            imageView.orientation = (imageView.orientation + 90) % 360
        }
        return rootView
    }
}
