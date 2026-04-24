package com.sonai.ssiv.test.imagedisplay;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.sonai.ssiv.ImageSource;
import com.sonai.ssiv.SubsamplingScaleImageView;
import com.sonai.ssiv.test.R.id;
import com.sonai.ssiv.test.R.layout;

public class ImageDisplayRotateFragment extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(layout.imagedisplay_rotate_fragment, container, false);
        final SubsamplingScaleImageView imageView = rootView.findViewById(id.imageView);
        imageView.setImage(ImageSource.asset("swissroad.jpg"));
        imageView.setOrientation(90);
        final ImageDisplayActivity activity = (ImageDisplayActivity)getActivity();
        if (activity != null) {
            rootView.findViewById(id.previous).setOnClickListener(v -> activity.previous());
            rootView.findViewById(id.next).setOnClickListener(v -> activity.next());
        }
        rootView.findViewById(id.rotate).setOnClickListener(v -> imageView.setOrientation((imageView.getOrientation() + 90) % 360));
        return rootView;
    }

}
