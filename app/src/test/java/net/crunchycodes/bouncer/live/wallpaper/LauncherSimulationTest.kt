package net.crunchycodes.bouncer.live.wallpaper

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherSimulationTest {
    @Test
    fun updateUsesElapsedTimeAndKeepsBallsInBounds() {
        val ball = LauncherBall(
            x = 5f,
            y = 5f,
            dx = -100f,
            dy = -50f,
            radius = 5f,
            color = Color.Red,
        )

        LauncherSimulation.update(listOf(ball), width = 100f, height = 100f, deltaSeconds = 0.5f)

        assertTrue(ball.x in ball.radius..(100f - ball.radius))
        assertTrue(ball.y in ball.radius..(100f - ball.radius))
        assertEquals(100f, ball.dx, 0f)
        assertEquals(50f, ball.dy, 0f)
    }
}
