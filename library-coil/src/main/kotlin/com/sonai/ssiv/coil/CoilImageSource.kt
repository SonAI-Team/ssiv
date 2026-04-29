package com.sonai.ssiv.coil

import android.net.Uri
import coil3.ImageLoader
import com.sonai.ssiv.ImageSource
import com.sonai.ssiv.SubsamplingScaleImageView
import com.sonai.ssiv.decoder.DecoderFactory

@Suppress("unused")
fun SubsamplingScaleImageView.setCoilImage(uri: Uri, imageLoader: ImageLoader) {
    regionDecoderFactory = DecoderFactory { CoilImageRegionDecoder(imageLoader) }
    setImage(ImageSource.uri(uri))
}
