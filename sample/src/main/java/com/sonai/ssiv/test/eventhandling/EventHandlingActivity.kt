package com.sonai.ssiv.test.eventhandling;

import android.os.Bundle;
import android.widget.Toast;

import com.sonai.ssiv.ImageSource;
import com.sonai.ssiv.SubsamplingScaleImageView;
import com.sonai.ssiv.test.AbstractPagesActivity;
import com.sonai.ssiv.test.Page;
import com.sonai.ssiv.test.R.id;

import java.util.Arrays;

import static com.sonai.ssiv.test.R.layout.*;
import static com.sonai.ssiv.test.R.string.*;

public class EventHandlingActivity extends AbstractPagesActivity {

    public EventHandlingActivity() {
        super(event_title, pages_activity, Arrays.asList(
                new Page(event_p1_subtitle, event_p1_text),
                new Page(event_p2_subtitle, event_p2_text),
                new Page(event_p3_subtitle, event_p3_text)
        ));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SubsamplingScaleImageView imageView = findViewById(id.imageView);
        imageView.setImage(ImageSource.asset("sanmartino.jpg"));
        imageView.setOnClickListener(v -> Toast.makeText(v.getContext(), "Clicked", Toast.LENGTH_SHORT).show());
        imageView.setOnLongClickListener(v -> { Toast.makeText(v.getContext(), "Long clicked", Toast.LENGTH_SHORT).show(); return true; });
    }

}
