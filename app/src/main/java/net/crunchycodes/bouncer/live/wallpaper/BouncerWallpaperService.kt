package net.crunchycodes.bouncer.live.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class BouncerWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = BouncerEngine()

    inner class BouncerEngine : Engine() {
        private val lifecycleLock = Any()
        private val lifecycleController = RenderLifecycleController()

        @Volatile
        private var renderThread: RenderThread? = null

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            Log.d(TAG, "Visibility changed: visible=$visible")
            updateRenderingState(
                reason = "visibility=$visible",
                actionProvider = { lifecycleController.onVisibilityChanged(visible) },
            )
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                renderThread?.handleTouch(event.x, event.y)
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            Log.d(TAG, "Surface created")
            updateRenderingState(
                reason = "surfaceCreated",
                actionProvider = { lifecycleController.onSurfaceChanged(true) },
            )
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            Log.d(TAG, "Surface destroyed")
            updateRenderingState(
                reason = "surfaceDestroyed",
                actionProvider = { lifecycleController.onSurfaceChanged(false) },
            )
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            Log.d(TAG, "Engine destroyed")
            updateRenderingState(
                reason = "engineDestroyed",
                actionProvider = { lifecycleController.onDestroyed() },
            )
            super.onDestroy()
        }

        private fun updateRenderingState(
            reason: String,
            actionProvider: RenderLifecycleController.() -> RenderAction,
        ) {
            val joinCandidate = synchronized(lifecycleLock) {
                reconcileDeadRenderThreadLocked("pre-update:$reason")
                handleRenderActionLocked(lifecycleController.actionProvider(), reason)
            }
            joinRenderThread(joinCandidate, reason)
        }

        private fun handleRenderActionLocked(action: RenderAction, reason: String): RenderThread? {
            return when (action) {
                RenderAction.None -> null
                is RenderAction.Start -> {
                    startRenderThreadLocked(action.threadId, reason)
                    null
                }
                is RenderAction.Stop -> {
                    val thread = renderThread
                    if (thread != null && thread.threadId == action.threadId) {
                        thread.requestStop(reason)
                        thread
                    } else {
                        null
                    }
                }
            }
        }

        private fun startRenderThreadLocked(threadId: Int, reason: String) {
            val existingThread = renderThread
            if (existingThread != null && existingThread.isAlive) {
                Log.d(
                    TAG,
                    "Skipped render-thread start for id=$threadId because thread ${existingThread.threadId} is still alive",
                )
                return
            }

            val newThread = RenderThread(threadId, surfaceHolder)
            renderThread = newThread
            Log.d(TAG, "Creating render thread id=$threadId reason=$reason")
            newThread.start()
        }

        private fun joinRenderThread(thread: RenderThread?, reason: String) {
            if (thread == null) return

            try {
                thread.join(RENDER_THREAD_STOP_TIMEOUT_MS)
                if (thread.isAlive) {
                    Log.w(
                        TAG,
                        "Render thread ${thread.threadId} join timed out after ${RENDER_THREAD_STOP_TIMEOUT_MS}ms reason=$reason",
                    )
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        private fun reconcileDeadRenderThreadLocked(reason: String) {
            val thread = renderThread ?: return
            if (thread.isAlive) return

            Log.d(TAG, "Reconciling dead render thread id=${thread.threadId} reason=$reason")
            finishRenderThreadLocked(thread.threadId, "reconcile:$reason")
        }

        private fun finishRenderThreadLocked(threadId: Int, reason: String) {
            if (renderThread?.threadId == threadId) {
                renderThread = null
            }

            val exitResult = lifecycleController.onThreadExited(threadId)
            if (exitResult.ignored) {
                Log.d(TAG, "Ignoring stale render thread exit id=$threadId reason=$reason")
                return
            }

            Log.d(TAG, "Render thread exited id=$threadId reason=$reason")
            val restartThreadId = exitResult.restartThreadId ?: return
            Log.d(TAG, "Restarting render thread automatically with id=$restartThreadId")
            startRenderThreadLocked(restartThreadId, "autoRestart:$reason")
        }

        private inner class RenderThread(
            val threadId: Int,
            private val surfaceHolder: SurfaceHolder,
        ) : Thread("BouncerRender-$threadId") {
            private val stopRequested = AtomicBoolean(false)
            private val balls = mutableListOf<BallState>()
            private val randomSource = object : RandomSource {
                override fun nextFloat(): Float = Random.nextFloat()
                override fun nextInt(until: Int): Int = Random.nextInt(until)
            }

            @Volatile
            private var targetBallCount = BouncerPhysics.DEFAULT_BALL_COUNT

            @Volatile
            private var baseSpeed = BouncerPhysics.DEFAULT_BALL_SPEED

            @Volatile
            private var currentPalette = ColorPalette.RANDOM

            @Volatile
            private var physicsEnabled = true

            @Volatile
            private var sizeBehavior = BouncerPhysics.DEFAULT_SIZE_BEHAVIOR

            @Volatile
            private var lifespanBase = BouncerPhysics.DEFAULT_LIFESPAN_SECONDS

            @Volatile
            private var destroyOnTouch = false

            private val lastTouch = AtomicReference<PointF?>(null)
            private var nextBallId = 1L

            private val glowBitmap: Bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            private val glowPaint = Paint(Paint.FILTER_BITMAP_FLAG)
            private val glowDestination = RectF()
            private val grid = mutableMapOf<Int, MutableList<BallState>>()
            private val settings = SettingsManager(this@BouncerWallpaperService)
            private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                applySettings(key)
            }

            init {
                applySettings(null)
                val canvas = Canvas(glowBitmap)
                val paint = Paint().apply {
                    isAntiAlias = true
                    shader = RadialGradient(
                        100f,
                        100f,
                        100f,
                        intArrayOf(Color.WHITE, Color.TRANSPARENT),
                        null,
                        Shader.TileMode.CLAMP,
                    )
                }
                canvas.drawCircle(100f, 100f, 100f, paint)
            }

            override fun run() {
                settings.registerListener(prefListener)
                var lastFrameNanos = SystemClock.elapsedRealtimeNanos()
                try {
                    while (!stopRequested.get() && !isInterrupted) {
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
                            if (!stopRequested.get()) {
                                Log.e(TAG, "Unable to render wallpaper frame", error)
                            }
                        } finally {
                            if (canvas != null) {
                                try {
                                    surfaceHolder.unlockCanvasAndPost(canvas)
                                } catch (error: Exception) {
                                    if (!stopRequested.get()) {
                                        Log.e(TAG, "Unable to post wallpaper frame", error)
                                    }
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
                    interrupt()
                } finally {
                    settings.unregisterListener(prefListener)
                    if (!glowBitmap.isRecycled) {
                        glowBitmap.recycle()
                    }
                    synchronized(lifecycleLock) {
                        finishRenderThreadLocked(threadId, "threadFinally")
                    }
                }
            }

            fun requestStop(reason: String) {
                if (stopRequested.compareAndSet(false, true)) {
                    Log.d(TAG, "Requesting render thread stop id=$threadId reason=$reason")
                    interrupt()
                }
            }

            fun handleTouch(x: Float, y: Float) {
                if (destroyOnTouch) {
                    lastTouch.set(PointF(x, y))
                }
            }

            private fun applySettings(changedKey: String?) {
                if (changedKey == null || changedKey == SettingsManager.KEY_BALL_COUNT) {
                    targetBallCount = settings.ballCount
                }
                if (changedKey == null || changedKey == SettingsManager.KEY_BALL_SPEED) {
                    baseSpeed = settings.ballSpeed
                }
                if (changedKey == null || changedKey == SettingsManager.KEY_PALETTE) {
                    currentPalette = settings.palette
                }
                if (changedKey == null || changedKey == SettingsManager.KEY_PHYSICS) {
                    physicsEnabled = settings.physicsEnabled
                }
                if (changedKey == null || changedKey == SettingsManager.KEY_SIZE_BEHAVIOR) {
                    sizeBehavior = settings.sizeBehavior
                }
                if (changedKey == null || changedKey == SettingsManager.KEY_LIFESPAN) {
                    lifespanBase = settings.lifespanBase
                }
                if (changedKey == null || changedKey == SettingsManager.KEY_DESTROY_ON_TOUCH) {
                    destroyOnTouch = settings.destroyOnTouch
                }
            }

            private fun drawFrame(canvas: Canvas) {
                canvas.drawColor(Color.BLACK)
                for (ball in balls) {
                    glowPaint.colorFilter = ball.colorFilter ?: PorterDuffColorFilter(
                        ball.color,
                        PorterDuff.Mode.SRC_IN,
                    ).also { ball.colorFilter = it }
                    glowPaint.alpha = (ball.alpha * 255).toInt().coerceIn(0, 255)

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
                val touch = lastTouch.getAndSet(null)
                val desiredBallCount = targetBallCount

                while (balls.size > desiredBallCount) {
                    balls.removeAt(balls.lastIndex)
                }

                val iterator = balls.iterator()
                while (iterator.hasNext()) {
                    val ball = iterator.next()
                    BouncerPhysics.ensureFiniteBall(ball, width, height, allowStopped = false)

                    val isTouched = touch != null && BouncerPhysics.isTouchWithinDestroyRadius(
                        ballX = ball.x,
                        ballY = ball.y,
                        ballRadius = ball.radius,
                        touchX = touch.x,
                        touchY = touch.y,
                    )

                    if (currentTime > ball.expiryTime || isTouched) {
                        iterator.remove()
                        continue
                    }

                    val remainingLife = (ball.expiryTime - currentTime).toFloat()
                    val totalLife = (ball.expiryTime - ball.startTime).toFloat().coerceAtLeast(1f)
                    val lifeRatio = (remainingLife / totalLife).coerceIn(0f, 1f)
                    ball.alpha = BouncerPhysics.alphaForTime(currentTime - ball.startTime)
                    ball.radius = BouncerPhysics.radiusForSurface(
                        initialRadius = ball.initialRadius,
                        sizeBehavior = sizeBehavior,
                        sizeVariability = ball.sizeVariability,
                        lifeRatio = lifeRatio,
                        width = width,
                        height = height,
                    )
                    ball.mass = ball.radius * ball.radius
                    ball.x += ball.dx * (deltaTime * 60f)
                    ball.y += ball.dy * (deltaTime * 60f)

                    if (ball.x + ball.radius > width || ball.x - ball.radius < 0f) {
                        ball.dx = -ball.dx
                    }
                    if (ball.y + ball.radius > height || ball.y - ball.radius < 0f) {
                        ball.dy = -ball.dy
                    }

                    BouncerPhysics.keepInsideBounds(ball, width, height)
                    BouncerPhysics.ensureFiniteBall(ball, width, height, allowStopped = false)
                }

                repeat(BouncerPhysics.ballsToSpawn(balls.size, desiredBallCount)) {
                    balls.add(createRandomBall(width, height, currentTime))
                }

                if (physicsEnabled) {
                    resolveCollisions(width, height)
                }
            }

            private fun resolveCollisions(width: Int, height: Int) {
                grid.values.forEach(MutableList<BallState>::clear)
                val cellSize = BouncerPhysics.collisionCellSize(balls.maxOfOrNull(BallState::radius) ?: 0f)
                val cols = (width / cellSize).toInt() + 1
                val rows = (height / cellSize).toInt() + 1

                for (ball in balls) {
                    val gridX = (ball.x / cellSize).toInt().coerceIn(0, cols - 1)
                    val gridY = (ball.y / cellSize).toInt().coerceIn(0, rows - 1)
                    val key = gridX + gridY * cols
                    grid.getOrPut(key) { mutableListOf() }.add(ball)
                }

                for (ball in balls) {
                    val gridX = (ball.x / cellSize).toInt().coerceIn(0, cols - 1)
                    val gridY = (ball.y / cellSize).toInt().coerceIn(0, rows - 1)

                    for (offsetX in -1..1) {
                        for (offsetY in -1..1) {
                            val neighborX = gridX + offsetX
                            val neighborY = gridY + offsetY
                            if (neighborX !in 0 until cols || neighborY !in 0 until rows) continue
                            val key = neighborX + neighborY * cols
                            grid[key]?.forEach { other ->
                                if (ball.id < other.id) {
                                    BouncerPhysics.separateAndResolveCollision(
                                        first = ball,
                                        second = other,
                                        width = width,
                                        height = height,
                                        allowStopped = false,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            private fun createRandomBall(width: Int, height: Int, now: Long): BallState {
                val initialRadius = (randomSource.nextFloat() * 40f + 10f)
                    .coerceAtMost(min(width, height) / 2f)
                val variance = randomSource.nextFloat() + 0.5f
                val lifespan = (variance * lifespanBase * 1_000f).toLong()
                val speed = randomSource.nextFloat() * baseSpeed + (baseSpeed / 2f)
                val angle = randomSource.nextFloat() * 2f * PI.toFloat()
                val sizeVariability = randomSource.nextFloat() * 0.8f + 0.6f
                val color = currentPalette.randomColor(randomSource)

                return BallState(
                    x = randomSource.nextFloat() * (width - 2f * initialRadius) + initialRadius,
                    y = randomSource.nextFloat() * (height - 2f * initialRadius) + initialRadius,
                    dx = cos(angle) * speed,
                    dy = sin(angle) * speed,
                    radius = initialRadius,
                    initialRadius = initialRadius,
                    color = color,
                    startTime = now,
                    expiryTime = now + lifespan,
                    sizeVariability = sizeVariability,
                    alpha = 0f,
                    id = nextBallId++,
                ).apply {
                    mass = radius * radius
                    BouncerPhysics.ensureFiniteBall(this, width, height, allowStopped = false)
                }
            }
        }
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
