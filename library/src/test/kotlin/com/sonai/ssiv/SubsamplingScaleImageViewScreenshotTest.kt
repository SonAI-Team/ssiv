package com.sonai.ssiv

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class SubsamplingScaleImageViewScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5
    )

    @Test
    fun testBasicDisplay() {
        val view = SubsamplingScaleImageView(paparazzi.context)
        // Note: Loading assets might be tricky in Paparazzi if they are not in the test module's assets
        // For now, we just test if the view renders its initial state or a simple color if we could mock image loading
        paparazzi.snapshot(view)
    }
}
