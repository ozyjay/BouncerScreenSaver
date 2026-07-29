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
    const val BALL_COUNT = 15

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
