package net.crunchycodes.bouncer.live.wallpaper

import android.graphics.*
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.core.graphics.toColorInt
import kotlin.math.*
import kotlin.random.Random

class BouncerWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = BouncerEngine()

    inner class BouncerEngine : Engine() {
        @Volatile private var renderThread: RenderThread? = null

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) startThread() else stopThread()
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                renderThread?.handleTouch(event.x, event.y)
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
            if (renderThread?.isAlive != true) {
                renderThread = RenderThread(surfaceHolder).apply {
                    isRunning = true
                    start()
                }
            }
        }

        private fun stopThread() {
            val thread = renderThread ?: return
            thread.isRunning = false
            thread.interrupt()
            try {
                thread.join(RENDER_THREAD_STOP_TIMEOUT_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            if (!thread.isAlive && renderThread === thread) {
                renderThread = null
            }
        }

        private inner class RenderThread(private val surfaceHolder: SurfaceHolder) : Thread() {
            @Volatile var isRunning = false
            private val balls = mutableListOf<Ball>()
            
            @Volatile private var targetBallCount = 50
            @Volatile private var baseSpeed = 5f
            @Volatile private var currentPalette = "Random"
            @Volatile private var physicsEnabled = true
            @Volatile private var sizeBehavior = -0.5f
            @Volatile private var lifespanBase = 15f
            @Volatile private var destroyOnTouch = false
            
            private val lastTouch = java.util.concurrent.atomic.AtomicReference<PointF?>(null)
            private var nextBallId = 1L

            // Optimization: Cache glow as a Bitmap to avoid per-frame RadialGradient allocations
            private val glowBitmap: Bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            private val glowPaint = Paint(Paint.FILTER_BITMAP_FLAG)
            private val glowDestination = RectF()

            // Lists are retained between frames to avoid rebuilding the grid's allocation graph.
            private val grid = mutableMapOf<Int, MutableList<Ball>>()

            private val settings = SettingsManager(this@BouncerWallpaperService)
            private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                when (key) {
                    SettingsManager.KEY_BALL_COUNT -> targetBallCount = settings.ballCount
                    SettingsManager.KEY_BALL_SPEED -> baseSpeed = settings.ballSpeed
                    SettingsManager.KEY_PALETTE -> currentPalette = settings.palette
                    SettingsManager.KEY_PHYSICS -> physicsEnabled = settings.physicsEnabled
                    SettingsManager.KEY_SIZE_BEHAVIOR -> sizeBehavior = settings.sizeBehavior
                    SettingsManager.KEY_LIFESPAN -> lifespanBase = settings.lifespanBase
                    SettingsManager.KEY_DESTROY_ON_TOUCH -> destroyOnTouch = settings.destroyOnTouch
                }
            }

            init {
                targetBallCount = settings.ballCount
                baseSpeed = settings.ballSpeed
                currentPalette = settings.palette
                physicsEnabled = settings.physicsEnabled
                sizeBehavior = settings.sizeBehavior
                lifespanBase = settings.lifespanBase
                destroyOnTouch = settings.destroyOnTouch
                settings.registerListener(prefListener)

                // Initialize the master glow bitmap (white, we will tint it)
                val canvas = Canvas(glowBitmap)
                val paint = Paint().apply {
                    isAntiAlias = true
                    shader = RadialGradient(100f, 100f, 100f, 
                        intArrayOf(Color.WHITE, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                }
                canvas.drawCircle(100f, 100f, 100f, paint)
            }

            override fun run() {
                var lastFrameNanos = SystemClock.elapsedRealtimeNanos()
                try {
                    while (isRunning && !isInterrupted) {
                        val frameStartNanos = SystemClock.elapsedRealtimeNanos()
                        val deltaTime = ((frameStartNanos - lastFrameNanos) / NANOS_PER_SECOND)
                            .toFloat()
                            .coerceIn(0f, MAX_DELTA_SECONDS)
                        lastFrameNanos = frameStartNanos

                        var canvas: Canvas? = null
                        try {
                            canvas = surfaceHolder.lockCanvas()
                            if (canvas != null) {
                                updateState(canvas.width, canvas.height, deltaTime)
                                drawFrame(canvas)
                            }
                        } catch (error: Exception) {
                            Log.e(TAG, "Unable to render wallpaper frame", error)
                        } finally {
                            if (canvas != null) {
                                try {
                                    surfaceHolder.unlockCanvasAndPost(canvas)
                                } catch (error: Exception) {
                                    Log.e(TAG, "Unable to post wallpaper frame", error)
                                }
                            }
                        }

                        val elapsedMillis =
                            (SystemClock.elapsedRealtimeNanos() - frameStartNanos) / NANOS_PER_MILLISECOND
                        if (elapsedMillis < FRAME_INTERVAL_MILLIS) {
                            sleep(FRAME_INTERVAL_MILLIS - elapsedMillis)
                        }
                    }
                } catch (_: InterruptedException) {
                    // Interruption is the normal, prompt shutdown path.
                    interrupt()
                } finally {
                    settings.unregisterListener(prefListener)
                    glowBitmap.recycle()
                    if (renderThread === this) renderThread = null
                }
            }

            fun handleTouch(x: Float, y: Float) {
                if (destroyOnTouch) {
                    lastTouch.set(PointF(x, y))
                }
            }

            private fun drawFrame(canvas: Canvas) {
                canvas.drawColor(Color.BLACK)
                for (ball in balls) {
                    glowPaint.colorFilter = ball.colorFilter ?: PorterDuffColorFilter(
                        ball.color,
                        PorterDuff.Mode.SRC_IN,
                    ).also { ball.colorFilter = it }
                    glowPaint.alpha = (ball.alpha * 255).toInt()

                    glowDestination.set(
                        ball.x - ball.radius * 1.5f,
                        ball.y - ball.radius * 1.5f,
                        ball.x + ball.radius * 1.5f,
                        ball.y + ball.radius * 1.5f,
                    )
                    canvas.drawBitmap(glowBitmap, null, glowDestination, glowPaint)
                }
            }

            private fun updateState(width: Int, height: Int, deltaTime: Float) {
                if (width <= 0 || height <= 0) return

                val currentTime = SystemClock.elapsedRealtime()

                // Process touches
                val touch = lastTouch.getAndSet(null)

                val desiredBallCount = targetBallCount.coerceIn(1, 1_000)
                while (balls.size > desiredBallCount) balls.removeAt(balls.lastIndex)

                val iterator = balls.iterator()
                while (iterator.hasNext()) {
                    val ball = iterator.next()
                    
                    // Gamification: Destroy on proximity
                    val isTouched = touch != null && 
                        sqrt((ball.x - touch.x).pow(2) + (ball.y - touch.y).pow(2)) < ball.radius * 2f
                    
                    if (currentTime > ball.expiryTime || isTouched) {
                        iterator.remove()
                    } else {
                        if (ball.alpha < 1.0f) ball.alpha = min(1.0f, ball.alpha + 0.02f)
                        val remainingLife = (ball.expiryTime - currentTime).toFloat()
                        val totalLife = (ball.expiryTime - ball.startTime).toFloat()
                        val lifeRatio = (remainingLife / totalLife).coerceIn(0f, 1f)
                        
                        ball.radius = BouncerPhysics.radiusForSurface(
                            initialRadius = ball.initialRadius,
                            sizeBehavior = sizeBehavior,
                            sizeVariability = ball.sizeVariability,
                            lifeRatio = lifeRatio,
                            width = width,
                            height = height,
                        )
                        
                        ball.mass = ball.radius * ball.radius // PI is constant, skip it for perf
                        
                        ball.x += ball.dx * (deltaTime * 60)
                        ball.y += ball.dy * (deltaTime * 60)

                        if (ball.x + ball.radius > width || ball.x - ball.radius < 0) {
                            ball.dx = -ball.dx
                            ball.x = ball.x.coerceIn(ball.radius, width.toFloat() - ball.radius)
                        }
                        if (ball.y + ball.radius > height || ball.y - ball.radius < 0) {
                            ball.dy = -ball.dy
                            ball.y = ball.y.coerceIn(ball.radius, height.toFloat() - ball.radius)
                        }
                    }
                }

                repeat(BouncerPhysics.ballsToSpawn(balls.size, desiredBallCount)) {
                    balls.add(createRandomBall(width, height))
                }

                if (physicsEnabled) resolveCollisions(width, height)
            }

            private fun resolveCollisions(width: Int, height: Int) {
                grid.values.forEach(MutableList<Ball>::clear)
                val cellSize = BouncerPhysics.collisionCellSize(
                    balls.maxOfOrNull(Ball::radius) ?: 0f,
                )
                val cols = (width / cellSize).toInt() + 1
                val rows = (height / cellSize).toInt() + 1
                
                // Assign balls to grid cells
                for (ball in balls) {
                    val gx = (ball.x / cellSize).toInt().coerceIn(0, cols - 1)
                    val gy = (ball.y / cellSize).toInt().coerceIn(0, rows - 1)
                    val key = gx + gy * cols
                    grid.getOrPut(key) { mutableListOf() }.add(ball)
                }

                // Check collisions only in neighboring cells
                for (ball in balls) {
                    val gx = (ball.x / cellSize).toInt().coerceIn(0, cols - 1)
                    val gy = (ball.y / cellSize).toInt().coerceIn(0, rows - 1)
                    
                    for (ix in -1..1) {
                        for (iy in -1..1) {
                            val neighborX = gx + ix
                            val neighborY = gy + iy
                            if (neighborX !in 0 until cols || neighborY !in 0 until rows) continue
                            val key = neighborX + neighborY * cols
                            grid[key]?.forEach { other ->
                                if (ball.id < other.id) checkCollision(ball, other)
                            }
                        }
                    }
                }
            }

            private fun checkCollision(b1: Ball, b2: Ball) {
                val dx = b2.x - b1.x
                val dy = b2.y - b1.y
                val distSq = dx * dx + dy * dy
                val minDist = b1.radius + b2.radius
                if (distSq < minDist * minDist) {
                    val dist = sqrt(distSq)
                    val overlap = (minDist - dist)
                    val nx = if (dist > 0f) dx / dist else 1f
                    val ny = if (dist > 0f) dy / dist else 0f
                    val totalMass = b1.mass + b2.mass
                    
                    b1.x -= nx * overlap * (b2.mass / totalMass)
                    b1.y -= ny * overlap * (b2.mass / totalMass)
                    b2.x += nx * overlap * (b1.mass / totalMass)
                    b2.y += ny * overlap * (b1.mass / totalMass)

                    val v1n = b1.dx * nx + b1.dy * ny
                    val v2n = b2.dx * nx + b2.dy * ny
                    if (!BouncerPhysics.areApproaching(
                            b1.dx,
                            b1.dy,
                            b2.dx,
                            b2.dy,
                            nx,
                            ny,
                        )
                    ) return
                    val v1nAfter = (v1n * (b1.mass - b2.mass) + 2 * b2.mass * v2n) / totalMass
                    val v2nAfter = (v2n * (b2.mass - b1.mass) + 2 * b1.mass * v1n) / totalMass
                    b1.dx += (v1nAfter - v1n) * nx
                    b1.dy += (v1nAfter - v1n) * ny
                    b2.dx += (v2nAfter - v2n) * nx
                    b2.dy += (v2nAfter - v2n) * ny
                }
            }

            private fun createRandomBall(width: Int, height: Int): Ball {
                // Slightly smaller balls for high-density support
                val initialRadius = (Random.nextFloat() * 40f + 10f)
                    .coerceAtMost(min(width, height) / 2f)
                // Apply a random variance to the lifespan (50% to 150% of base)
                val variance = Random.nextFloat() * 1.0f + 0.5f
                val lifespan = (variance * lifespanBase * 1000).toLong()
                val speed = Random.nextFloat() * baseSpeed + (baseSpeed / 2f)
                val angle = Random.nextFloat() * 2f * PI.toFloat()
                val sizeVariability = Random.nextFloat() * 0.8f + 0.6f // 60% to 140% variability
                val now = SystemClock.elapsedRealtime()
                
                val color = when (currentPalette) {
                    "Neon" -> intArrayOf("#FF00FF".toColorInt(), "#00FFFF".toColorInt(), "#00FF00".toColorInt(), "#FFFF00".toColorInt(), "#FF0000".toColorInt()).random()
                    "Ocean" -> intArrayOf("#0077be".toColorInt(), "#00a3cc".toColorInt(), "#00d9e8".toColorInt(), "#005f6b".toColorInt(), "#add8e6".toColorInt()).random()
                    "Fire" -> intArrayOf("#e25822".toColorInt(), "#f28c28".toColorInt(), "#ff4500".toColorInt(), "#ffd700".toColorInt(), "#800000".toColorInt()).random()
                    "Pastel" -> intArrayOf("#ffb7ce".toColorInt(), "#c5e0b4".toColorInt(), "#b4c6e7".toColorInt(), "#f8cbad".toColorInt(), "#d9d9d9".toColorInt()).random()
                    "Forest" -> intArrayOf("#228b22".toColorInt(), "#006400".toColorInt(), "#8b4513".toColorInt(), "#556b2f".toColorInt(), "#9acd32".toColorInt()).random()
                    else -> Color.argb(255, Random.nextInt(256), Random.nextInt(256), Random.nextInt(256))
                }

                return Ball(
                    x = Random.nextFloat() * (width - 2f * initialRadius) + initialRadius,
                    y = Random.nextFloat() * (height - 2f * initialRadius) + initialRadius,
                    dx = cos(angle.toDouble()).toFloat() * speed,
                    dy = sin(angle.toDouble()).toFloat() * speed,
                    radius = initialRadius,
                    initialRadius = initialRadius,
                    color = color,
                    startTime = now,
                    expiryTime = now + lifespan,
                    sizeVariability = sizeVariability,
                    alpha = 0f,
                    id = nextBallId++,
                ).apply { mass = radius * radius }
            }
        }
    }

    data class Ball(
        var x: Float, var y: Float, var dx: Float, var dy: Float,
        var radius: Float, val initialRadius: Float, val color: Int,
        val startTime: Long, val expiryTime: Long, 
        val sizeVariability: Float, var alpha: Float = 1f,
        val id: Long = 0L,
    ) {
        var mass: Float = 1.0f
        var colorFilter: ColorFilter? = null
    }

    private companion object {
        const val TAG = "BouncerWallpaper"
        const val FRAME_INTERVAL_MILLIS = 16L
        const val RENDER_THREAD_STOP_TIMEOUT_MS = 500L
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val MAX_DELTA_SECONDS = 0.05f
    }
}
