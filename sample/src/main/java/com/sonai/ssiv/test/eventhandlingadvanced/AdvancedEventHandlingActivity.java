package com.sonai.ssiv.test.eventhandlingadvanced;

import android.annotation.SuppressLint;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.Toast;

import com.sonai.ssiv.ImageSource;
import com.sonai.ssiv.SubsamplingScaleImageView;
import com.sonai.ssiv.test.AbstractPagesActivity;
import com.sonai.ssiv.test.Page;
import com.sonai.ssiv.test.R.id;

import java.util.Arrays;

import static com.sonai.ssiv.test.R.string.*;
import static com.sonai.ssiv.test.R.layout.*;

import androidx.annotation.NonNull;

public class AdvancedEventHandlingActivity extends AbstractPagesActivity {

    public AdvancedEventHandlingActivity() {
        super(advancedevent_title, pages_activity, Arrays.asList(
                new Page(advancedevent_p1_subtitle, advancedevent_p1_text),
                new Page(advancedevent_p2_subtitle, advancedevent_p2_text),
                new Page(advancedevent_p3_subtitle, advancedevent_p3_text),
                new Page(advancedevent_p4_subtitle, advancedevent_p4_text),
                new Page(advancedevent_p5_subtitle, advancedevent_p5_text)
        ));
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final SubsamplingScaleImageView imageView = findViewById(id.imageView);
        final GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                if (imageView.isReady()) {
                    PointF sCoord = imageView.viewToSourceCoord(e.getX(), e.getY());
                    assert sCoord != null;
                    Toast.makeText(getApplicationContext(), "Single tap: " + ((int)sCoord.x) + ", " + ((int)sCoord.y), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Single tap: Image not ready", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            @Override
            public void onLongPress(@NonNull MotionEvent e) {
                if (imageView.isReady()) {
                    PointF sCoord = imageView.viewToSourceCoord(e.getX(), e.getY());
                    assert sCoord != null;
                    Toast.makeText(getApplicationContext(), "Long press: " + ((int)sCoord.x) + ", " + ((int)sCoord.y), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Long press: Image not ready", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                if (imageView.isReady()) {
                    PointF sCoord = imageView.viewToSourceCoord(e.getX(), e.getY());
                    assert sCoord != null;
                    Toast.makeText(getApplicationContext(), "Double tap: " + ((int)sCoord.x) + ", " + ((int)sCoord.y), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Double tap: Image not ready", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });

        imageView.setImage(ImageSource.asset("sanmartino.jpg"));
        imageView.setOnTouchListener((view, motionEvent) -> gestureDetector.onTouchEvent(motionEvent));
    }

}
