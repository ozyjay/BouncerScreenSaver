package net.crunchycodes.bouncer.live.wallpaper

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherSimulationTest {
    @Test
    fun clampDashboardBallCountUsesLauncherSpecificCap() {
        assertEquals(BouncerPhysics.MIN_BALL_COUNT, LauncherSimulation.clampDashboardBallCount(0))
        assertEquals(
            LauncherSimulation.MAX_DASHBOARD_BALL_COUNT,
            LauncherSimulation.clampDashboardBallCount(BouncerPhysics.MAX_BALL_COUNT),
        )
    }

    @Test
    fun ballsToSpawnAddsDashboardBallsInBatches() {
        assertEquals(
            LauncherSimulation.MAX_SPAWN_BATCH,
            LauncherSimulation.ballsToSpawn(
                currentCount = 0,
                targetCount = LauncherSimulation.MAX_DASHBOARD_BALL_COUNT,
            ),
        )
        assertEquals(0, LauncherSimulation.ballsToSpawn(currentCount = 48, targetCount = 48))
    }

    @Test
    fun ballStyleControlsDashboardGlowRendering() {
        assertTrue(LauncherSimulation.useGlow(ballCount = 12, ballStyle = BallStyle.AUTO))
        assertTrue(LauncherSimulation.useGlow(ballCount = 48, ballStyle = BallStyle.GLOW))
        assertEquals(false, LauncherSimulation.useGlow(ballCount = 12, ballStyle = BallStyle.FLAT))
        assertEquals(false, LauncherSimulation.useGlow(ballCount = 48, ballStyle = BallStyle.AUTO))
    }

    // The dashboard background deliberately stays simple, but its timing still needs coverage.
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
