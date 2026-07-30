package net.crunchycodes.bouncer.live.wallpaper

import androidx.compose.ui.graphics.Color

internal data class LauncherBall(
    var x: Float,
    var y: Float,
    var dx: Float,
    var dy: Float,
    val radius: Float,
    val color: Color,
)

internal object LauncherSimulation {
    const val DEFAULT_DASHBOARD_BALL_COUNT = 18
    const val MAX_DASHBOARD_BALL_COUNT = 48
    const val MAX_SPAWN_BATCH = 8
    const val FRAME_INTERVAL_NANOS = 33_000_000L
    const val GLOW_RENDER_THRESHOLD = 36

    fun clampDashboardBallCount(value: Int): Int =
        value.coerceIn(BouncerPhysics.MIN_BALL_COUNT, MAX_DASHBOARD_BALL_COUNT)

    fun ballsToSpawn(currentCount: Int, targetCount: Int): Int =
        (clampDashboardBallCount(targetCount) - currentCount.coerceAtLeast(0))
            .coerceAtLeast(0)
            .coerceAtMost(MAX_SPAWN_BATCH)

    fun update(balls: List<LauncherBall>, width: Float, height: Float, deltaSeconds: Float) {
        if (width <= 0f || height <= 0f) return
        for (ball in balls) {
            // The dashboard animation is intentionally simpler than the wallpaper engine:
            // just enough motion to give the launcher screen some life without duplicating
            // the full collision system.
            ball.x += ball.dx * deltaSeconds
            ball.y += ball.dy * deltaSeconds

            if (ball.x - ball.radius < 0f || ball.x + ball.radius > width) {
                ball.dx *= -1f
                ball.x = ball.x.coerceIn(ball.radius, width - ball.radius)
            }
            if (ball.y - ball.radius < 0f || ball.y + ball.radius > height) {
                ball.dy *= -1f
                ball.y = ball.y.coerceIn(ball.radius, height - ball.radius)
            }
        }
    }
}
