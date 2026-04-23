package com.sonai.ssiv.test.imagedisplay;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.sonai.ssiv.ImageSource;
import com.sonai.ssiv.SubsamplingScaleImageView;
import com.sonai.ssiv.decoder.CompatDecoderFactory;
import com.sonai.ssiv.decoder.SSIVImageDecoder;
import com.sonai.ssiv.decoder.ImageRegionDecoder;
import com.sonai.ssiv.decoder.SkiaSSIVImageDecoder;
import com.sonai.ssiv.decoder.SkiaImageRegionDecoder;
import com.sonai.ssiv.test.R.id;
import com.sonai.ssiv.test.R.layout;

public class ImageDisplayRegionFragment extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(layout.imagedisplay_region_fragment, container, false);
        final SubsamplingScaleImageView imageView = rootView.findViewById(id.imageView);
        imageView.setBitmapDecoderFactory(new CompatDecoderFactory<SSIVImageDecoder>(SkiaSSIVImageDecoder.class, Bitmap.Config.ARGB_8888));
        imageView.setRegionDecoderFactory(new CompatDecoderFactory<ImageRegionDecoder>(SkiaImageRegionDecoder.class, Bitmap.Config.ARGB_8888));
        imageView.setOrientation(SubsamplingScaleImageView.ORIENTATION_90);
        imageView.setImage(ImageSource.asset("card.png").region(new Rect(5200, 651, 8200, 3250)));
        final ImageDisplayActivity activity = (ImageDisplayActivity)getActivity();
        if (activity != null) {
            rootView.findViewById(id.previous).setOnClickListener(v -> activity.previous());
        }
        rootView.findViewById(id.rotate).setOnClickListener(v -> imageView.setOrientation((imageView.getOrientation() + 90) % 360));
        return rootView;
    }

}
