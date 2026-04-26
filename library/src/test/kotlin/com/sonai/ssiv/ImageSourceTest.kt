package com.sonai.ssiv

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(manifest = Config.NONE, sdk = [34])
class ImageSourceTest {

    @Test
    fun `test resource source`() {
        val resId = 123
        val source = ImageSource.resource(resId)
        assertEquals(resId, source.resource)
        assertTrue(source.tile)
    }

    @Test
    fun `test asset source`() {
        val assetName = "test.png"
        val source = ImageSource.asset(assetName)
        assertEquals("file:///android_asset/$assetName", source.uri.toString())
        assertTrue(source.tile)
    }

    @Test
    fun `test uri source`() {
        val uriStr = "content://media/external/images/media/1"
        val source = ImageSource.uri(uriStr)
        assertEquals(uriStr, source.uri.toString())
        assertTrue(source.tile)
    }

    @Test
    fun `test file uri source auto scheme`() {
        val path = "/sdcard/test.jpg"
        val source = ImageSource.uri(path)
        assertEquals("file://$path", source.uri.toString())
    }

    @Test
    fun `test tiling configuration`() {
        val source = ImageSource.resource(1).tiling(false)
        assertFalse(source.tile)

        source.tilingEnabled()
        assertTrue(source.tile)
    }

    @Test
    fun `test dimensions and region`() {
        val source = ImageSource.resource(1)
            .dimensions(1000, 2000)

        assertEquals(1000, source.sWidth)
        assertEquals(2000, source.sHeight)
    }

    @Test
    fun `test region setting`() {
        val region = android.graphics.Rect(0, 0, 100, 100)
        val source = ImageSource.resource(1).region(region)

        assertEquals(region, source.sRegion)
        assertTrue(source.tile)
        assertEquals(100, source.sWidth)
        assertEquals(100, source.sHeight)
    }
}
