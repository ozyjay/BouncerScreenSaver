package net.crunchycodes.bouncer.live.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

class BallAppearanceTest {
    @Test
    fun brightnessScalesRgbWithoutChangingAlpha() {
        val adjusted = BallAppearance.adjustBrightness(0x80204080.toInt(), 0.5f)

        assertEquals(0x80102040.toInt(), adjusted)
    }

    @Test
    fun brightnessClampsChannelsAndConfiguredRange() {
        val adjusted = BallAppearance.adjustBrightness(0xffc08040.toInt(), 2f)

        assertEquals(0xffffc060.toInt(), adjusted)
    }

    @Test
    fun transparencyConvertsToOpacityAndClamps() {
        assertEquals(1f, BallAppearance.opacityForTransparency(-1f), 0f)
        assertEquals(0.6f, BallAppearance.opacityForTransparency(0.4f), 0.0001f)
        assertEquals(0f, BallAppearance.opacityForTransparency(2f), 0f)
    }
}
