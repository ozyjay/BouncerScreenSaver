package net.crunchycodes.bouncer.live.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

class BallTest {
    @Test
    fun ballMassDefaultsCanBeUpdatedFromRadius() {
        val ball = BallState(
            x = 100f,
            y = 200f,
            dx = 5f,
            dy = -5f,
            radius = 30f,
            initialRadius = 30f,
            color = 0xFF00FF,
            startTime = 0L,
            expiryTime = 10_000L,
            sizeVariability = 1f,
            alpha = 0f,
        )

        ball.mass = ball.radius * ball.radius

        assertEquals(900f, ball.mass, 0f)
    }
}
