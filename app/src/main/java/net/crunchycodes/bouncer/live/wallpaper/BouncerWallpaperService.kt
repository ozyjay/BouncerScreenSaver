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
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Display
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.core.graphics.createBitmap
import java.util.Arrays
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class BouncerWallpaperService : WallpaperService() {
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Wallpaper service created")
    }

    override fun onDestroy() {
        Log.d(TAG, "Wallpaper service destroyed")
        super.onDestroy()
    }

    override fun onCreateEngine(): Engine = BouncerEngine()

    inner class BouncerEngine : Engine() {
        // Engine callbacks can arrive from different lifecycle edges while the render
        // thread is starting or stopping, so thread ownership changes stay behind one lock.
        private val lifecycleLock = Any()
        private val lifecycleController = RenderLifecycleController()
        private val simulationState = SimulationState()
        private val settings = SettingsManager(this@BouncerWallpaperService)
        private val pendingPausedDurationMillis = AtomicLong(0L)
        private var hiddenAtElapsedRealtimeMillis: Long? = null

        @Volatile
        private var targetBallCount = BouncerPhysics.DEFAULT_BALL_COUNT

        @Volatile
        private var baseSpeed = BouncerPhysics.DEFAULT_BALL_SPEED

        @Volatile
        private var currentPalette = ColorPalette.RANDOM

        @Volatile
        private var brightness = BallAppearance.DEFAULT_BRIGHTNESS

        @Volatile
        private var transparency = BallAppearance.DEFAULT_TRANSPARENCY

        @Volatile
        private var currentBallStyle = BallStyle.AUTO

        @Volatile
        private var performanceMode = PerformanceMode.ADAPTIVE

        private val appearanceRevision = AtomicLong(0L)
        private val performanceSettingsRevision = AtomicLong(0L)

        @Volatile
        private var physicsEnabled = true

        @Volatile
        private var autoDisablePhysicsOnHeavyLoad = true

        @Volatile
        private var sizeBehavior = BouncerPhysics.DEFAULT_SIZE_BEHAVIOR

        @Volatile
        private var lifespanBase = BouncerPhysics.DEFAULT_LIFESPAN_SECONDS

        @Volatile
        private var destroyOnTouch = false

        private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            applySettings(key)
        }

        @Volatile
        private var renderThread: RenderThread? = null

        private var runtimeBallController: RuntimeBallCountController? = null
        private var runtimeControllerDeviceCap = 0
        private var simulationBaseSpeed = Float.NaN
        @Volatile
        private var runtimeControllerSettingsRevision = -1L

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            applySettings(null)
            simulationState.prepareCapacity(settings.effectiveMaxBallCount())
            // Settings live for the full engine lifetime so renderer restarts do not churn listeners.
            settings.registerListener(prefListener)
            setTouchEventsEnabled(true)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            Log.d(TAG, "Visibility changed: visible=$visible")
            recordSimulationVisibility(visible)
            updateRenderingState(
                reason = "visibility=$visible",
                actionProvider = { lifecycleController.onVisibilityChanged(visible) },
            )
        }

        private fun recordSimulationVisibility(visible: Boolean) {
            val now = SystemClock.elapsedRealtime()
            synchronized(lifecycleLock) {
                if (!visible) {
                    if (hiddenAtElapsedRealtimeMillis == null) {
                        hiddenAtElapsedRealtimeMillis = now
                    }
                    return
                }

                val hiddenAt = hiddenAtElapsedRealtimeMillis ?: return
                pendingPausedDurationMillis.addAndGet((now - hiddenAt).coerceAtLeast(0L))
                hiddenAtElapsedRealtimeMillis = null
            }
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                handleTouch(event.x, event.y)
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

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            Log.d(TAG, "Surface changed: width=$width height=$height format=$format")
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
            settings.unregisterListener(prefListener)
            synchronized(lifecycleLock) {
                if (renderThread == null) {
                    simulationState.clear()
                    runtimeBallController = null
                }
            }
            super.onDestroy()
        }

        private fun updateRenderingState(
            reason: String,
            actionProvider: RenderLifecycleController.() -> RenderAction,
        ) {
            val joinCandidate = synchronized(lifecycleLock) {
                // A previous stop may have timed out, so always reconcile dead threads before
                // asking the controller whether a new start is required.
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
                // Use a bounded join so framework lifecycle callbacks never block forever on
                // a thread that is slow to leave lockCanvas() or sleep().
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
            if (lifecycleController.currentState().destroyed) {
                simulationState.clear()
                runtimeBallController = null
            }
            val restartThreadId = exitResult.restartThreadId ?: return
            Log.d(TAG, "Restarting render thread automatically with id=$restartThreadId")
            startRenderThreadLocked(restartThreadId, "autoRestart:$reason")
        }

        private fun handleTouch(x: Float, y: Float) {
            if (destroyOnTouch) {
                simulationState.lastTouch.set(PointF(x, y))
            }
        }

        private fun applySettings(changedKey: String?) {
            var recolorBalls = false
            if (changedKey == null || changedKey == SettingsManager.KEY_BALL_COUNT) {
                targetBallCount = settings.ballCount
            }
            if (changedKey == null || changedKey == SettingsManager.KEY_BALL_SPEED) {
                baseSpeed = settings.ballSpeed
            }
            if (changedKey == null || changedKey == SettingsManager.KEY_PALETTE) {
                currentPalette = settings.palette
                recolorBalls = true
            }
            if (changedKey == null || changedKey == SettingsManager.KEY_BRIGHTNESS) {
                brightness = settings.brightness
                recolorBalls = true
            }
            if (changedKey == null || changedKey == SettingsManager.KEY_TRANSPARENCY) {
                transparency = settings.transparency
            }
            if (changedKey == null || changedKey == SettingsManager.KEY_BALL_STYLE) {
                currentBallStyle = settings.ballStyle
            }
            if (changedKey == null || changedKey == SettingsManager.KEY_PERFORMANCE_MODE) {
                performanceMode = settings.performanceMode
            }
            if (changedKey == null || changedKey == SettingsManager.KEY_PHYSICS) {
                physicsEnabled = settings.physicsEnabled
            }
            if (changedKey == null || changedKey == SettingsManager.KEY_AUTO_DISABLE_PHYSICS) {
                autoDisablePhysicsOnHeavyLoad = settings.autoDisablePhysicsOnHeavyLoad
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
            if (recolorBalls) {
                appearanceRevision.incrementAndGet()
            }
            if (SettingsManager.isUserSettingKey(changedKey)) {
                performanceSettingsRevision.incrementAndGet()
            }
        }

        private inner class RenderThread(
            val threadId: Int,
            private val surfaceHolder: SurfaceHolder,
        ) : Thread("BouncerRender-$threadId") {
            private val stopRequested = AtomicBoolean(false)
            private val randomSource = object : RandomSource {
                override fun nextFloat(): Float = Random.nextFloat()
                override fun nextInt(until: Int): Int = Random.nextInt(until)
            }

            private val glowBitmap: Bitmap = createBitmap(200, 200)
            private val glowPaint = Paint(Paint.FILTER_BITMAP_FLAG)
            private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val glowDestination = RectF()
            private var collisionHeads = IntArray(0)
            private var collisionNext = IntArray(0)
            private var activeBallIndices = IntArray(0)
            private var appliedAppearanceRevision = -1L
            private var reportedRuntimeBallCount = -1
            private var reportedRenderQuality: RenderQuality? = null
            private var reportedPhysicsSuspended: Boolean? = null

            init {
                // Build the glow sprite once per render thread and reuse it for every ball.
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
                var lastFrameNanos = SystemClock.elapsedRealtimeNanos()
                val targetRefreshRateHz = resolveTargetRefreshRateHz()
                val targetFrameIntervalNanos = DevicePerformance.frameBudgetNanos(targetRefreshRateHz)
                val deviceMaxBallCount = settings.effectiveMaxBallCount()
                simulationState.prepareCapacity(deviceMaxBallCount)
                ensureCollisionCapacity(deviceMaxBallCount * 4, deviceMaxBallCount)
                val runtimeBallController = obtainRuntimeBallController(deviceMaxBallCount)
                var appliedBallStyle = currentBallStyle
                try {
                    while (!stopRequested.get() && !isInterrupted) {
                        val frameStartNanos = SystemClock.elapsedRealtimeNanos()
                        val deltaTime = ((frameStartNanos - lastFrameNanos) / NANOS_PER_SECOND)
                            .toFloat()
                            .coerceIn(0f, MAX_DELTA_SECONDS)
                        lastFrameNanos = frameStartNanos
                        runtimeBallController.updateConfiguredBallCount(targetBallCount)
                        val requestedBallStyle = currentBallStyle
                        val adaptivePerformance = performanceMode == PerformanceMode.ADAPTIVE
                        val requestedSettingsRevision = performanceSettingsRevision.get()
                        if (requestedSettingsRevision != runtimeControllerSettingsRevision) {
                            runtimeBallController.onSettingsAdjusted()
                            simulationState.clear()
                            runtimeControllerSettingsRevision = requestedSettingsRevision
                        }
                        runtimeBallController.updateAdaptivePerformance(adaptivePerformance)
                        runtimeBallController.updatePreferredRenderQuality(
                            value = DevicePerformance.renderQuality(deviceMaxBallCount, requestedBallStyle),
                            allowAutomaticStyleChanges =
                                adaptivePerformance && requestedBallStyle == BallStyle.AUTO,
                            force = requestedBallStyle != appliedBallStyle,
                        )
                        runtimeBallController.updateAutomaticPhysicsReduction(
                            adaptivePerformance && physicsEnabled && autoDisablePhysicsOnHeavyLoad,
                        )
                        appliedBallStyle = requestedBallStyle
                        applyUpdatedBallSpeed()
                        refreshBallAppearanceIfNeeded()

                        var canvas: Canvas? = null
                        try {
                            canvas = surfaceHolder.lockCanvas()
                            if (canvas != null) {
                                // Physics and drawing stay on the same thread so ball state
                                // is never read mid-update by another renderer.
                                updateState(
                                    width = canvas.width,
                                    height = canvas.height,
                                    deltaTime = deltaTime,
                                    desiredBallCount = runtimeBallController.activeBallCount(),
                                    solidBodyPhysicsAllowed = runtimeBallController.solidBodyPhysicsAllowed(),
                                )
                                drawFrame(
                                    canvas = canvas,
                                    quality = runtimeBallController.renderQuality(),
                                )
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

                        val frameDurationNanos = SystemClock.elapsedRealtimeNanos() - frameStartNanos
                        runtimeBallController.recordFrame(frameDurationNanos, targetFrameIntervalNanos)
                        reportRuntimePerformanceIfChanged(runtimeBallController)
                        val remainingFrameNanos = targetFrameIntervalNanos - frameDurationNanos
                        if (remainingFrameNanos > 0L) {
                            sleep(
                                remainingFrameNanos / NANOS_PER_MILLISECOND,
                                (remainingFrameNanos % NANOS_PER_MILLISECOND).toInt(),
                            )
                        }
                    }
                } catch (_: InterruptedException) {
                    interrupt()
                } finally {
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

            private fun drawFrame(canvas: Canvas, quality: RenderQuality) {
                canvas.drawColor(Color.BLACK)
                val opacity = BallAppearance.opacityForTransparency(transparency)
                for (ball in simulationState.balls) {
                    val alpha = (ball.alpha * opacity * 255).toInt().coerceIn(0, 255)
                    if (alpha <= 0) continue
                    if (quality == RenderQuality.Glow) {
                        glowPaint.colorFilter = ball.colorFilter ?: PorterDuffColorFilter(
                            ball.color,
                            PorterDuff.Mode.SRC_IN,
                        ).also { ball.colorFilter = it }
                        glowPaint.alpha = alpha

                        glowDestination.set(
                            ball.x - ball.radius * 1.5f,
                            ball.y - ball.radius * 1.5f,
                            ball.x + ball.radius * 1.5f,
                            ball.y + ball.radius * 1.5f,
                        )
                        canvas.drawBitmap(glowBitmap, null, glowDestination, glowPaint)
                    } else {
                        ballPaint.color = ball.color
                        ballPaint.alpha = alpha
                        canvas.drawCircle(ball.x, ball.y, ball.radius, ballPaint)
                    }
                }
            }

            private fun refreshBallAppearanceIfNeeded() {
                val requestedRevision = appearanceRevision.get()
                if (requestedRevision == appliedAppearanceRevision) return

                for (ball in simulationState.balls) {
                    ball.color = BallAppearance.adjustBrightness(
                        currentPalette.randomColor(randomSource),
                        brightness,
                    )
                    ball.colorFilter = null
                }
                appliedAppearanceRevision = requestedRevision
            }

            private fun applyUpdatedBallSpeed() {
                val requestedBaseSpeed = baseSpeed
                if (!simulationBaseSpeed.isFinite()) {
                    simulationBaseSpeed = requestedBaseSpeed
                    return
                }
                if (requestedBaseSpeed == simulationBaseSpeed) return

                val scale = BouncerPhysics.speedChangeScale(simulationBaseSpeed, requestedBaseSpeed)
                for (ball in simulationState.balls) {
                    ball.dx *= scale
                    ball.dy *= scale
                }
                simulationBaseSpeed = requestedBaseSpeed
            }

            private fun updateState(
                width: Int,
                height: Int,
                deltaTime: Float,
                desiredBallCount: Int,
                solidBodyPhysicsAllowed: Boolean,
            ) {
                if (width <= 0 || height <= 0) return

                simulationState.shiftTimestamps(pendingPausedDurationMillis.getAndSet(0L))
                val currentTime = SystemClock.elapsedRealtime()
                val touch = if (destroyOnTouch) simulationState.lastTouch.getAndSet(null) else null
                val balls = simulationState.balls
                val excessBallCount = simulationState.activeBallCount - desiredBallCount
                val deltaScale = deltaTime * 60f
                val radiusLimit = BouncerPhysics.maxRadiusForPopulation(
                    width = width,
                    height = height,
                    populationCount = max(desiredBallCount, simulationState.activeBallCount),
                )

                if (excessBallCount > 0) {
                    markBallsForRetirement(excessBallCount, currentTime)
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
                        simulationState.recycleBall(ball)
                        iterator.remove()
                        continue
                    }

                    if (ball.retiring && currentTime - ball.retireStartTime >= ball.retireDurationMillis) {
                        simulationState.recycleBall(ball)
                        iterator.remove()
                        continue
                    }

                    val remainingLife = (ball.expiryTime - currentTime).toFloat()
                    val totalLife = (ball.expiryTime - ball.startTime).toFloat().coerceAtLeast(1f)
                    val lifeRatio = (remainingLife / totalLife).coerceIn(0f, 1f)
                    val fadeInAlpha = BouncerPhysics.alphaForTime(currentTime - ball.startTime)
                    val retireAlpha = if (ball.retiring) {
                        val elapsedRetireTime = (currentTime - ball.retireStartTime).coerceAtLeast(0L)
                        val progress = (elapsedRetireTime.toFloat() / ball.retireDurationMillis.coerceAtLeast(1L))
                            .coerceIn(0f, 1f)
                        1f - progress
                    } else {
                        1f
                    }
                    ball.alpha = fadeInAlpha * retireAlpha
                    ball.radius = BouncerPhysics.radiusForSurface(
                        initialRadius = ball.initialRadius,
                        sizeBehavior = sizeBehavior,
                        sizeVariability = ball.sizeVariability,
                        lifeRatio = lifeRatio,
                        width = width,
                        height = height,
                    ).coerceAtMost(radiusLimit) *
                        if (ball.retiring) max(ball.alpha, MIN_RETIRE_SCALE) else 1f
                    ball.mass = ball.radius * ball.radius
                    ball.x += ball.dx * deltaScale
                    ball.y += ball.dy * deltaScale

                    if (ball.x + ball.radius > width || ball.x - ball.radius < 0f) {
                        ball.dx = -ball.dx
                    }
                    if (ball.y + ball.radius > height || ball.y - ball.radius < 0f) {
                        ball.dy = -ball.dy
                    }

                    BouncerPhysics.keepInsideBounds(ball, width, height)
                    BouncerPhysics.ensureFiniteBall(ball, width, height, allowStopped = false)
                }

                repeat(BouncerPhysics.ballsToSpawn(simulationState.activeBallCount, desiredBallCount)) {
                    simulationState.addBall(createRandomBall(width, height, currentTime))
                }

                if (
                    physicsEnabled &&
                    solidBodyPhysicsAllowed &&
                    simulationState.activeBallCount > 1
                ) {
                    resolveCollisions(width, height)
                }
            }

            private fun markBallsForRetirement(excessBallCount: Int, now: Long) {
                var remaining = excessBallCount.coerceAtLeast(0)
                if (remaining == 0) return

                val balls = simulationState.balls
                for (index in balls.lastIndex downTo 0) {
                    val ball = balls[index]
                    if (ball.retiring) continue

                    simulationState.markBallRetiring(ball, now)
                    remaining--
                    if (remaining == 0) return
                }
            }

            private fun resolveCollisions(width: Int, height: Int) {
                // Partition the surface into bins so each ball only checks nearby neighbors
                // instead of naively iterating the full population.
                val balls = simulationState.balls
                var maxRadius = 0f
                var activeCount = 0
                for (ball in balls) {
                    if (ball.retiring) continue
                    if (ball.radius > maxRadius) {
                        maxRadius = ball.radius
                    }
                    activeCount++
                }
                if (activeCount < 2) return

                val cellSize = BouncerPhysics.collisionCellSize(maxRadius)
                val cols = (width / cellSize).toInt() + 1
                val rows = (height / cellSize).toInt() + 1
                val cellCount = cols * rows
                ensureCollisionCapacity(cellCount, balls.size)
                Arrays.fill(collisionHeads, 0, cellCount, NO_COLLISION_INDEX)

                var activeIndexCount = 0
                for (ballIndex in balls.indices) {
                    if (balls[ballIndex].retiring) continue
                    activeBallIndices[activeIndexCount++] = ballIndex
                }

                for (activeIndex in 0 until activeIndexCount) {
                    val ballIndex = activeBallIndices[activeIndex]
                    val ball = balls[ballIndex]
                    val gridX = (ball.x / cellSize).toInt().coerceIn(0, cols - 1)
                    val gridY = (ball.y / cellSize).toInt().coerceIn(0, rows - 1)
                    val key = gridX + gridY * cols
                    collisionNext[ballIndex] = collisionHeads[key]
                    collisionHeads[key] = ballIndex
                }

                for (activeIndex in 0 until activeIndexCount) {
                    val ballIndex = activeBallIndices[activeIndex]
                    val ball = balls[ballIndex]
                    val gridX = (ball.x / cellSize).toInt().coerceIn(0, cols - 1)
                    val gridY = (ball.y / cellSize).toInt().coerceIn(0, rows - 1)

                    for (offsetX in -1..1) {
                        for (offsetY in -1..1) {
                            val neighborX = gridX + offsetX
                            val neighborY = gridY + offsetY
                            if (neighborX !in 0 until cols || neighborY !in 0 until rows) continue
                            val key = neighborX + neighborY * cols
                            var otherIndex = collisionHeads[key]
                            while (otherIndex != NO_COLLISION_INDEX) {
                                if (ballIndex < otherIndex) {
                                    val other = balls[otherIndex]
                                    BouncerPhysics.separateAndResolveCollision(
                                        first = ball,
                                        second = other,
                                        width = width,
                                        height = height,
                                        allowStopped = false,
                                    )
                                }
                                otherIndex = collisionNext[otherIndex]
                            }
                        }
                    }
                }
            }

            private fun ensureCollisionCapacity(cellCount: Int, ballCapacity: Int) {
                if (collisionHeads.size < cellCount) {
                    collisionHeads = IntArray(cellCount)
                }
                if (collisionNext.size < ballCapacity) {
                    collisionNext = IntArray(ballCapacity)
                }
                if (activeBallIndices.size < ballCapacity) {
                    activeBallIndices = IntArray(ballCapacity)
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
                val color = BallAppearance.adjustBrightness(
                    currentPalette.randomColor(randomSource),
                    brightness,
                )
                val ball = simulationState.obtainBall()
                ball.prepareForReuse(
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
                    id = simulationState.nextBallId++,
                )
                ball.alpha = 0f
                BouncerPhysics.ensureFiniteBall(ball, width, height, allowStopped = false)
                return ball
            }

            private fun resolveTargetRefreshRateHz(): Float {
                val calibrationRefreshRate = settings.calibrationRefreshRateHz
                val displayManager = getSystemService(DisplayManager::class.java)
                val displayRefreshRate = displayManager
                    ?.getDisplay(Display.DEFAULT_DISPLAY)
                    ?.refreshRate
                return DevicePerformance.normalizeRefreshRateHz(displayRefreshRate ?: calibrationRefreshRate)
            }

            private fun reportRuntimePerformanceIfChanged(controller: RuntimeBallCountController) {
                val ballCount = controller.activeBallCount()
                val quality = controller.renderQuality()
                val physicsSuspended = !controller.solidBodyPhysicsAllowed()
                if (
                    ballCount == reportedRuntimeBallCount &&
                    quality == reportedRenderQuality &&
                    physicsSuspended == reportedPhysicsSuspended
                ) {
                    return
                }

                reportedRuntimeBallCount = ballCount
                reportedRenderQuality = quality
                reportedPhysicsSuspended = physicsSuspended
                settings.persistRuntimePerformanceState(ballCount, quality, physicsSuspended)
            }
        }

        private fun obtainRuntimeBallController(deviceMaxBallCount: Int): RuntimeBallCountController {
            val existing = runtimeBallController
            if (existing != null && runtimeControllerDeviceCap == deviceMaxBallCount) {
                return existing
            }

            return RuntimeBallCountController(
                configuredBallCount = targetBallCount,
                deviceMaxBallCount = deviceMaxBallCount,
                initialRenderQuality = DevicePerformance.renderQuality(
                    deviceMaxBallCount,
                    currentBallStyle,
                ),
            ).also {
                runtimeBallController = it
                runtimeControllerDeviceCap = deviceMaxBallCount
                runtimeControllerSettingsRevision = -1L
            }
        }

        private inner class SimulationState {
            val balls = ArrayList<BallState>(BouncerPhysics.DEFAULT_BALL_COUNT)
            val lastTouch = AtomicReference<PointF?>(null)
            private val recycledBalls = ArrayDeque<BallState>()
            var activeBallCount = 0
                private set
            var nextBallId = 1L

            fun clear() {
                balls.forEach(::recycleBall)
                balls.clear()
                lastTouch.set(null)
                activeBallCount = 0
                nextBallId = 1L
            }

            fun addBall(ball: BallState) {
                balls.add(ball)
                if (!ball.retiring) {
                    activeBallCount++
                }
            }

            fun prepareCapacity(capacity: Int) {
                val clampedCapacity = capacity.coerceIn(BouncerPhysics.MIN_BALL_COUNT, MAX_RECYCLED_BALLS)
                balls.ensureCapacity(clampedCapacity)
                while (recycledBalls.size < clampedCapacity) {
                    recycledBalls.addLast(createPooledBall())
                }
            }

            fun shiftTimestamps(pausedDurationMillis: Long) {
                if (pausedDurationMillis <= 0L) return
                for (ball in balls) {
                    ball.shiftTimeline(pausedDurationMillis)
                }
            }

            fun markBallRetiring(ball: BallState, now: Long) {
                if (ball.retiring) return
                ball.retiring = true
                ball.retireStartTime = now
                ball.retireDurationMillis = RETIRE_DURATION_MILLIS
                if (activeBallCount > 0) {
                    activeBallCount--
                }
            }

            fun obtainBall(): BallState = recycledBalls.removeFirstOrNull() ?: BallState(
                x = 0f,
                y = 0f,
                dx = 0f,
                dy = 0f,
                radius = BouncerPhysics.MIN_RADIUS,
                initialRadius = BouncerPhysics.MIN_RADIUS,
                color = Color.WHITE,
                startTime = 0L,
                expiryTime = 0L,
                sizeVariability = 1f,
            )

            fun recycleBall(ball: BallState) {
                if (!ball.retiring && activeBallCount > 0) {
                    activeBallCount--
                }
                if (recycledBalls.size >= MAX_RECYCLED_BALLS) return
                ball.colorFilter = null
                ball.retiring = false
                recycledBalls.addLast(ball)
            }

            private fun createPooledBall(): BallState = BallState(
                x = 0f,
                y = 0f,
                dx = 0f,
                dy = 0f,
                radius = BouncerPhysics.MIN_RADIUS,
                initialRadius = BouncerPhysics.MIN_RADIUS,
                color = Color.WHITE,
                startTime = 0L,
                expiryTime = 0L,
                sizeVariability = 1f,
            )
        }
    }

    private companion object {
        const val TAG = "BouncerWallpaper"
        const val RENDER_THREAD_STOP_TIMEOUT_MS = 500L
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val MAX_DELTA_SECONDS = 0.05f
        const val RETIRE_DURATION_MILLIS = 220L
        const val MIN_RETIRE_SCALE = 0.35f
        const val MAX_RECYCLED_BALLS = 320
        const val NO_COLLISION_INDEX = -1
    }
}
