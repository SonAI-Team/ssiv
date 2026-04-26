package com.sonai.ssiv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(manifest = Config.NONE, sdk = [34])
class SubsamplingScaleImageViewTest {

    private lateinit var context: Context
    private lateinit var view: SubsamplingScaleImageView

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        view = SubsamplingScaleImageView(context)
    }

    @Test
    fun `test initial state`() {
        assertFalse(view.isReady)
        assertFalse(view.isImageLoaded)
        assertEquals(0, view.getOrientation())
        assertEquals(0f, view.getScale())
    }

    @Test
    fun `test orientation settings`() {
        view.setOrientation(SubsamplingScaleImageView.ORIENTATION_90)
        assertEquals(90, view.getOrientation())

        view.setOrientation(SubsamplingScaleImageView.ORIENTATION_180)
        assertEquals(180, view.getOrientation())
    }

    @Test
    fun `test pan and zoom enabling`() {
        view.setPanEnabled(true)
        assertTrue(view.panEnabled())

        view.setPanEnabled(false)
        assertFalse(view.panEnabled())

        view.setZoomEnabled(true)
        assertTrue(view.zoomEnabled())

        view.setZoomEnabled(false)
        assertFalse(view.zoomEnabled())
    }

    @Test
    fun `test max and min scale`() {
        view.setMaxScale(5f)
        assertEquals(5f, view.getMaxScale())

        view.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CUSTOM)
        view.setMinScale(0.1f)
        assertEquals(0.1f, view.getMinScale())
    }

    @Test
    fun `test hasImage initial`() {
        assertFalse(view.hasImage())
    }
}
