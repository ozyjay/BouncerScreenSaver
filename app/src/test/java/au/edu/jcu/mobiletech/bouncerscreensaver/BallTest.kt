package au.edu.jcu.mobiletech.bouncerscreensaver

import org.junit.Test
import org.junit.Assert.*

class BallTest {
    @Test
    fun testBallInitialization() {
        val now = System.currentTimeMillis()
        val ball = BouncerWallpaperService.Ball(
            x = 100f,
            y = 200f,
            dx = 5f,
            dy = -5f,
            radius = 30f,
            initialRadius = 30f,
            color = 0xFF00FF,
            startTime = now,
            expiryTime = now + 10000,
            sizeVariability = 1.0f,
            alpha = 0f
        )

        assertEquals(100f, ball.x)
        assertEquals(200f, ball.y)
        assertEquals(5f, ball.dx)
        assertEquals(-5f, ball.dy)
        assertEquals(30f, ball.radius)
        assertEquals(now + 10000, ball.expiryTime)
        assertEquals(0f, ball.alpha)
    }

    @Test
    fun testBallMassCalculation() {
        val now = System.currentTimeMillis()
        val ball = BouncerWallpaperService.Ball(
            x = 0f, y = 0f, dx = 0f, dy = 0f,
            radius = 10f, initialRadius = 10f, color = 0,
            startTime = now, expiryTime = now, sizeVariability = 1f
        )
        ball.mass = ball.radius * ball.radius
        assertEquals(100f, ball.mass)
    }
}
