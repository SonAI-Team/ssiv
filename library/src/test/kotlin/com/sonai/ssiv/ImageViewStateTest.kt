package com.sonai.ssiv

import android.graphics.PointF
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(manifest = Config.NONE, sdk = [34])
class ImageViewStateTest {

    @Test
    fun `test state properties are preserved`() {
        val scale = 1.5f
        val center = PointF(100f, 200f)
        val orientation = 90
        
        val state = ImageViewState(scale, center, orientation)
        
        assertEquals(scale, state.scale)
        assertEquals(orientation, state.orientation)
        assertEquals(100f, state.center.x)
        assertEquals(200f, state.center.y)
    }
}
