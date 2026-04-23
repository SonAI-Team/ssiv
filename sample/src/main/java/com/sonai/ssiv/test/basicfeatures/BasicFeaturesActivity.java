package com.sonai.ssiv.test.basicfeatures;

import android.os.Bundle;
import androidx.annotation.Nullable;

import com.sonai.ssiv.ImageSource;
import com.sonai.ssiv.SubsamplingScaleImageView;
import com.sonai.ssiv.test.AbstractPagesActivity;
import com.sonai.ssiv.test.Page;
import com.sonai.ssiv.test.R.id;

import java.util.Arrays;

import static com.sonai.ssiv.test.R.string.*;
import static com.sonai.ssiv.test.R.layout.*;

public class BasicFeaturesActivity extends AbstractPagesActivity {

    public BasicFeaturesActivity() {
        super(basic_title, pages_activity, Arrays.asList(
                new Page(basic_p1_subtitle, basic_p1_text),
                new Page(basic_p2_subtitle, basic_p2_text),
                new Page(basic_p3_subtitle, basic_p3_text),
                new Page(basic_p4_subtitle, basic_p4_text),
                new Page(basic_p5_subtitle, basic_p5_text)
        ));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SubsamplingScaleImageView view = findViewById(id.imageView);
        view.setImage(ImageSource.asset("sanmartino.jpg"));
    }

}
