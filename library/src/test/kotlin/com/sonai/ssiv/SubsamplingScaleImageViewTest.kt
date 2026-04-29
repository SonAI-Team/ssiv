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
        assertEquals(0, view.orientation)
        assertEquals(0f, view.scale)
    }

    @Test
    fun `test orientation settings`() {
        view.orientation = SubsamplingScaleImageView.ORIENTATION_90
        assertEquals(90, view.orientation)

        view.orientation = SubsamplingScaleImageView.ORIENTATION_180
        assertEquals(180, view.orientation)
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
        view.maxScale = 5f
        assertEquals(5f, view.maxScale)

        view.minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_CUSTOM
        view.minScale = 0.1f
        assertEquals(0.1f, view.minScale)
    }

    @Test
    fun `test hasImage initial`() {
        assertFalse(view.hasImage())
    }
}
