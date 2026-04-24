package com.sonai.ssiv.test.imagedisplay;

import androidx.fragment.app.Fragment;
import android.util.Log;

import com.sonai.ssiv.test.AbstractFragmentsActivity;
import com.sonai.ssiv.test.Page;
import com.sonai.ssiv.test.R.id;

import java.util.Arrays;
import java.util.List;

import static com.sonai.ssiv.test.R.string.*;
import static com.sonai.ssiv.test.R.layout.*;

public class ImageDisplayActivity extends AbstractFragmentsActivity {

    private static final List<Class<? extends Fragment>> FRAGMENTS = Arrays.asList(
            ImageDisplayLargeFragment.class,
            ImageDisplayRotateFragment.class,
            ImageDisplayRegionFragment.class
    );

    public ImageDisplayActivity() {
        super(display_title, fragments_activity, Arrays.asList(
                new Page(display_p1_subtitle, display_p1_text),
                new Page(display_p2_subtitle, display_p2_text),
                new Page(display_p3_subtitle, display_p3_text)
        ));
    }

    @Override
    protected void onPageChanged(int page) {
        try {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(id.frame, FRAGMENTS.get(page).newInstance())
                    .commit();
        } catch (Exception e) {
            Log.e(ImageDisplayActivity.class.getName(), "Failed to load fragment", e);
        }
    }

}
