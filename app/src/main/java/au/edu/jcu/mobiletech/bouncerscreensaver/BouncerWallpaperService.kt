package au.edu.jcu.mobiletech.bouncerscreensaver

import android.graphics.*
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.core.graphics.toColorInt
import kotlin.math.*
import kotlin.random.Random

class BouncerWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = BouncerEngine()

    inner class BouncerEngine : Engine() {
        private var renderThread: RenderThread? = null

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

            // Optimization: Cache glow as a Bitmap to avoid per-frame RadialGradient allocations
            private val glowBitmap: Bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            private val glowPaint = Paint(Paint.FILTER_BITMAP_FLAG)

            // Spatial Grid for O(N) collisions
            private val cellSize = 100f
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
                var lastTime = System.currentTimeMillis()
                while (isRunning) {
                    val currentTime = System.currentTimeMillis()
                    val deltaTime = (currentTime - lastTime) / 1000f
                    lastTime = currentTime

                    var canvas: Canvas? = null
                    try {
                        canvas = surfaceHolder.lockCanvas()
                        if (canvas != null) {
                            updateState(canvas.width, canvas.height, deltaTime)
                            drawFrame(canvas)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        if (canvas != null) surfaceHolder.unlockCanvasAndPost(canvas)
                    }

                    val elapsed = System.currentTimeMillis() - currentTime
                    if (elapsed < 16) sleep(16 - elapsed)
                }
                settings.unregisterListener(prefListener)
            }

            fun handleTouch(x: Float, y: Float) {
                if (destroyOnTouch) {
                    lastTouch.set(PointF(x, y))
                }
            }

            private fun drawFrame(canvas: Canvas) {
                canvas.drawColor(Color.BLACK)
                for (ball in balls) {
                    glowPaint.colorFilter = PorterDuffColorFilter(ball.color, PorterDuff.Mode.SRC_IN)
                    glowPaint.alpha = (ball.alpha * 255).toInt()
                    
                    val dest = RectF(
                        ball.x - ball.radius * 1.5f,
                        ball.y - ball.radius * 1.5f,
                        ball.x + ball.radius * 1.5f,
                        ball.y + ball.radius * 1.5f
                    )
                    canvas.drawBitmap(glowBitmap, null, dest, glowPaint)
                }
            }

            private fun updateState(width: Int, height: Int, deltaTime: Float) {
                val currentTime = System.currentTimeMillis()

                // Process touches
                val touch = lastTouch.getAndSet(null)

                // More aggressive spawning when far from target
                val deficit = targetBallCount - balls.size
                val spawnProbability = when {
                    deficit > 100 -> 0.8f
                    deficit > 0 -> 0.2f
                    else -> 0f
                }
                
                if (deficit > 0 && Random.nextFloat() < spawnProbability) {
                    balls.add(createRandomBall(width, height))
                }

                while (balls.size > targetBallCount) balls.removeAt(0)

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
                        
                        // Incorporate per-ball size variability into the global size behavior
                        val effectiveSizeBehavior = sizeBehavior * ball.sizeVariability
                        ball.radius = (ball.initialRadius * (1.0f + effectiveSizeBehavior * (1.0f - lifeRatio))).coerceIn(5f, 500f)
                        
                        ball.mass = ball.radius * ball.radius // PI is constant, skip it for perf
                        
                        ball.x += ball.dx * (deltaTime * 60)
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
                }

                if (physicsEnabled) resolveCollisions(width, height)
            }

            private fun resolveCollisions(width: Int, height: Int) {
                grid.clear()
                val cols = (width / cellSize).toInt() + 1
                
                // Assign balls to grid cells
                for (ball in balls) {
                    val gx = (ball.x / cellSize).toInt()
                    val gy = (ball.y / cellSize).toInt()
                    val key = gx + gy * cols
                    grid.getOrPut(key) { mutableListOf() }.add(ball)
                }

                // Check collisions only in neighboring cells
                for (ball in balls) {
                    val gx = (ball.x / cellSize).toInt()
                    val gy = (ball.y / cellSize).toInt()
                    
                    for (ix in -1..1) {
                        for (iy in -1..1) {
                            val key = (gx + ix) + (gy + iy) * cols
                            grid[key]?.forEach { other ->
                                if (ball !== other) checkCollision(ball, other)
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
                    if (dist == 0f) return
                    val overlap = (minDist - dist)
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

            private fun createRandomBall(width: Int, height: Int): Ball {
                // Slightly smaller balls for high-density support
                val initialRadius = Random.nextFloat() * 40f + 10f
                // Apply a random variance to the lifespan (50% to 150% of base)
                val variance = Random.nextFloat() * 1.0f + 0.5f
                val lifespan = (variance * lifespanBase * 1000).toLong()
                val speed = Random.nextFloat() * baseSpeed + (baseSpeed / 2f)
                val angle = Random.nextFloat() * 2f * PI.toFloat()
                val sizeVariability = Random.nextFloat() * 0.8f + 0.6f // 60% to 140% variability
                val now = System.currentTimeMillis()
                
                val color = when (currentPalette) {
                    "Neon" -> intArrayOf("#FF00FF".toColorInt(), "#00FFFF".toColorInt(), "#00FF00".toColorInt(), "#FFFF00".toColorInt(), "#FF0000".toColorInt()).random()
                    "Ocean" -> intArrayOf("#0077be".toColorInt(), "#00a3cc".toColorInt(), "#00d9e8".toColorInt(), "#005f6b".toColorInt(), "#add8e6".toColorInt()).random()
                    "Fire" -> intArrayOf("#e25822".toColorInt(), "#f28c28".toColorInt(), "#ff4500".toColorInt(), "#ffd700".toColorInt(), "#800000".toColorInt()).random()
                    "Pastel" -> intArrayOf("#ffb7ce".toColorInt(), "#c5e0b4".toColorInt(), "#b4c6e7".toColorInt(), "#f8cbad".toColorInt(), "#d9d9d9".toColorInt()).random()
                    "Forest" -> intArrayOf("#228b22".toColorInt(), "#006400".toColorInt(), "#8b4513".toColorInt(), "#556b2f".toColorInt(), "#9acd32".toColorInt()).random()
                    else -> Color.argb(255, Random.nextInt(256), Random.nextInt(256), Random.nextInt(256))
                }

                return Ball(
                    x = Random.nextFloat() * (width - 2 * initialRadius) + initialRadius,
                    y = Random.nextFloat() * (height - 2 * initialRadius) + initialRadius,
                    dx = cos(angle.toDouble()).toFloat() * speed,
                    dy = sin(angle.toDouble()).toFloat() * speed,
                    radius = initialRadius,
                    initialRadius = initialRadius,
                    color = color,
                    startTime = now,
                    expiryTime = now + lifespan,
                    sizeVariability = sizeVariability,
                    alpha = 0f
                ).apply { mass = radius * radius }
            }
        }
    }

    data class Ball(
        var x: Float, var y: Float, var dx: Float, var dy: Float,
        var radius: Float, val initialRadius: Float, val color: Int,
        val startTime: Long, val expiryTime: Long, 
        val sizeVariability: Float, var alpha: Float = 1f
    ) {
        var mass: Float = 1.0f
    }
}
