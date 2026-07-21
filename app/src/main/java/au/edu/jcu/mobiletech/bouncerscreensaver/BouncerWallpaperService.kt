package au.edu.jcu.mobiletech.bouncerscreensaver

import android.graphics.*
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.*
import kotlin.random.Random

class BouncerWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = BouncerEngine()

    inner class BouncerEngine : Engine() {
        private var renderThread: RenderThread? = null

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                startThread()
            } else {
                stopThread()
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            startThread()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            stopThread()
            super.onSurfaceDestroyed(holder)
        }

        private fun startThread() {
            if (renderThread == null) {
                renderThread = RenderThread(surfaceHolder).apply {
                    isRunning = true
                    start()
                }
            }
        }

        private fun stopThread() {
            renderThread?.let {
                it.isRunning = false
                it.join()
            }
            renderThread = null
        }

        private inner class RenderThread(private val surfaceHolder: SurfaceHolder) : Thread() {
            @Volatile
            var isRunning = false
            private val balls = mutableListOf<Ball>()
            private val TARGET_BALL_COUNT = 50
            private val paint = Paint().apply {
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            override fun run() {
                var lastTime = System.currentTimeMillis()

                while (isRunning) {
                    val currentTime = System.currentTimeMillis()
                    val deltaTime = (currentTime - lastTime) / 1000f
                    lastTime = currentTime

                    var canvas: Canvas? = null
                    try {
                        canvas = surfaceHolder.lockCanvas()
                        if (canvas != null) {
                            // Update
                            updateState(canvas.width, canvas.height, deltaTime)

                            // Draw
                            canvas.drawColor(Color.BLACK)
                            for (ball in balls) {
                                // Optimization: Only create shader if necessary
                                // Note: RadialGradient is still allocated per-ball-per-frame here 
                                // to keep the glowing effect centered on the moving ball.
                                val shader = RadialGradient(
                                    ball.x - ball.radius * 0.3f,
                                    ball.y - ball.radius * 0.3f,
                                    ball.radius * 1.5f,
                                    intArrayOf(ball.color, Color.TRANSPARENT),
                                    null,
                                    Shader.TileMode.CLAMP
                                )
                                paint.shader = shader
                                paint.alpha = (ball.alpha * 255).toInt()
                                canvas.drawCircle(ball.x, ball.y, ball.radius, paint)
                            }
                            paint.shader = null
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        if (canvas != null) {
                            surfaceHolder.unlockCanvasAndPost(canvas)
                        }
                    }

                    // Cap frame rate to ~60 FPS to save battery
                    val elapsed = System.currentTimeMillis() - currentTime
                    if (elapsed < 16) {
                        try {
                            sleep(16 - elapsed)
                        } catch (e: InterruptedException) {
                        }
                    }
                }
            }

            private fun updateState(width: Int, height: Int, deltaTime: Float) {
                val currentTime = System.currentTimeMillis()

                // Spawn balls
                if (balls.size < TARGET_BALL_COUNT && Random.nextFloat() < 0.1) {
                    balls.add(createRandomBall(width, height))
                }

                // Lifespan and size updates
                val iterator = balls.iterator()
                while (iterator.hasNext()) {
                    val ball = iterator.next()
                    if (currentTime > ball.expiryTime) {
                        iterator.remove()
                    } else {
                        if (ball.alpha < 1.0f) {
                            ball.alpha = min(1.0f, ball.alpha + 0.02f)
                        }
                        val remainingLife = (ball.expiryTime - currentTime).toFloat()
                        val totalLife = (ball.expiryTime - ball.startTime).toFloat()
                        val lifeRatio = (remainingLife / totalLife).coerceIn(0f, 1f)
                        ball.radius = max(30f, ball.initialRadius * lifeRatio.pow(ball.shrinkExponent))
                        ball.mass = PI.toFloat() * ball.radius * ball.radius
                    }
                }

                // Physics movement
                for (ball in balls) {
                    ball.x += ball.dx * (deltaTime * 60) // Normalize to ~60fps speed
                    ball.y += ball.dy * (deltaTime * 60)

                    if (ball.x + ball.radius > width || ball.x - ball.radius < 0) {
                        ball.dx = -ball.dx
                        ball.x = ball.x.coerceIn(ball.radius, width - ball.radius)
                    }
                    if (ball.y + ball.radius > height || ball.y - ball.radius < 0) {
                        ball.dy = -ball.dy
                        ball.y = ball.y.coerceIn(ball.radius, height - ball.radius)
                    }
                }

                // Collisions (O(N^2))
                for (i in 0 until balls.size) {
                    for (j in i + 1 until balls.size) {
                        val b1 = balls[i]
                        val b2 = balls[j]
                        val dx = b2.x - b1.x
                        val dy = b2.y - b1.y
                        val distSq = dx * dx + dy * dy
                        val minDist = b1.radius + b2.radius
                        if (distSq < minDist * minDist) {
                            val dist = sqrt(distSq)
                            if (dist == 0f) continue
                            val overlap = minDist - dist
                            val nx = dx / dist
                            val ny = dy / dist
                            val totalMass = b1.mass + b2.mass
                            b1.x -= nx * overlap * (b2.mass / totalMass)
                            b1.y -= ny * overlap * (b2.mass / totalMass)
                            b2.x += nx * overlap * (b1.mass / totalMass)
                            b2.y += ny * overlap * (b1.mass / totalMass)

                            val v1n = b1.dx * nx + b1.dy * ny
                            val v2n = b2.dx * nx + b2.dy * ny
                            val v1nAfter = (v1n * (b1.mass - b2.mass) + 2 * b2.mass * v2n) / totalMass
                            val v2nAfter = (v2n * (b2.mass - b1.mass) + 2 * b1.mass * v1n) / totalMass
                            b1.dx += (v1nAfter - v1n) * nx
                            b1.dy += (v1nAfter - v1n) * ny
                            b2.dx += (v2nAfter - v2n) * nx
                            b2.dy += (v2nAfter - v2n) * ny
                        }
                    }
                }
            }

            private fun createRandomBall(width: Int, height: Int): Ball {
                val initialRadius = Random.nextFloat() * 100f + 20f
                val lifespan = Random.nextLong(5000, 30000)
                val speed = Random.nextFloat() * 8f + 2f // Slightly slower base speed
                val angle = Random.nextFloat() * 2f * PI.toFloat()
                val now = System.currentTimeMillis()
                return Ball(
                    x = Random.nextFloat() * (width - 2 * initialRadius) + initialRadius,
                    y = Random.nextFloat() * (height - 2 * initialRadius) + initialRadius,
                    dx = cos(angle.toDouble()).toFloat() * speed,
                    dy = sin(angle.toDouble()).toFloat() * speed,
                    radius = initialRadius,
                    initialRadius = initialRadius,
                    color = Color.argb(255, Random.nextInt(256), Random.nextInt(256), Random.nextInt(256)),
                    startTime = now,
                    expiryTime = now + lifespan,
                    shrinkExponent = Random.nextFloat() * 1.8f + 0.2f,
                    alpha = 0f
                ).apply { mass = PI.toFloat() * radius * radius }
            }
        }
    }

    data class Ball(
        var x: Float,
        var y: Float,
        var dx: Float,
        var dy: Float,
        var radius: Float,
        val initialRadius: Float,
        val color: Int,
        val startTime: Long,
        val expiryTime: Long,
        val shrinkExponent: Float,
        var alpha: Float = 1f
    ) {
        var mass: Float = 1.0f
    }
}
