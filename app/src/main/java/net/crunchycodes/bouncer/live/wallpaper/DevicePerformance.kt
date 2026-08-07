package net.crunchycodes.bouncer.live.wallpaper

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class FrameWindowMetrics(
    val averageFrameNanos: Long,
    val p95FrameNanos: Long,
    val droppedFrameRatio: Float,
    val stable: Boolean,
)

internal data class DeviceCalibrationResult(
    val refreshRateHz: Float,
    val frameBudgetNanos: Long,
    val deviceMaxBallCount: Int,
    val recommendedBallCount: Int,
    val deviceMaxBallSpeed: Float,
    val recommendedBallSpeed: Float,
    val usedFallback: Boolean,
)

internal enum class RenderQuality {
    Glow,
    Flat,
}

internal object DevicePerformance {
    const val FALLBACK_REFRESH_RATE_HZ = 60f
    const val MIN_SUPPORTED_REFRESH_RATE_HZ = 30f
    const val MAX_SUPPORTED_REFRESH_RATE_HZ = 144f
    const val FALLBACK_MAX_BALL_COUNT = 24
    const val CALIBRATION_START_BALL_COUNT = 12
    const val CALIBRATION_WARMUP_FRAMES = 12
    const val CALIBRATION_WINDOW_FRAMES = 72
    const val RUNTIME_WINDOW_FRAMES = 36
    const val MAX_BAD_WINDOW_STREAK = 2
    private const val GLOW_DEVICE_CAP_THRESHOLD = 64
    private const val STABLE_P95_MULTIPLIER = 1.1f
    private const val STABLE_DROP_RATIO = 0.05f
    private const val LOW_CAP_SPEED = 4.5f
    private const val MID_CAP_SPEED = 6f
    private const val HIGH_CAP_SPEED = 8f

    fun normalizeRefreshRateHz(value: Float): Float {
        if (!value.isFinite() || value <= 0f) return FALLBACK_REFRESH_RATE_HZ
        return value.coerceIn(MIN_SUPPORTED_REFRESH_RATE_HZ, MAX_SUPPORTED_REFRESH_RATE_HZ)
    }

    fun frameBudgetNanos(refreshRateHz: Float): Long =
        (1_000_000_000f / normalizeRefreshRateHz(refreshRateHz)).roundToInt().toLong()

    fun evaluateWindow(frameDurationsNanos: List<Long>, frameBudgetNanos: Long): FrameWindowMetrics {
        if (frameDurationsNanos.isEmpty()) {
            return FrameWindowMetrics(
                averageFrameNanos = frameBudgetNanos,
                p95FrameNanos = frameBudgetNanos,
                droppedFrameRatio = 0f,
                stable = true,
            )
        }

        val sorted = frameDurationsNanos.sorted()
        val average = frameDurationsNanos.average().roundToInt().toLong()
        val p95Index = ((sorted.lastIndex) * 0.95f).roundToInt().coerceIn(0, sorted.lastIndex)
        val p95 = sorted[p95Index]
        val droppedFrames = frameDurationsNanos.count {
            it > (frameBudgetNanos * STABLE_P95_MULTIPLIER).roundToInt().toLong()
        }
        val dropRatio = droppedFrames.toFloat() / frameDurationsNanos.size.toFloat()
        val stable = p95 <= (frameBudgetNanos * STABLE_P95_MULTIPLIER).roundToInt() &&
            dropRatio <= STABLE_DROP_RATIO

        return FrameWindowMetrics(
            averageFrameNanos = average,
            p95FrameNanos = p95,
            droppedFrameRatio = dropRatio,
            stable = stable,
        )
    }

    fun recommendedBallCount(deviceMaxBallCount: Int): Int =
        max(
            BouncerPhysics.MIN_BALL_COUNT,
            min(
                deviceMaxBallCount,
                (deviceMaxBallCount * 0.85f).roundToInt(),
            ),
        )

    fun fallbackMaxBallCount(): Int = FALLBACK_MAX_BALL_COUNT

    fun deviceMaxBallSpeed(deviceMaxBallCount: Int): Float = when {
        deviceMaxBallCount <= 24 -> LOW_CAP_SPEED
        deviceMaxBallCount <= 48 -> 5f
        deviceMaxBallCount <= 72 -> MID_CAP_SPEED
        deviceMaxBallCount <= 120 -> 7f
        else -> HIGH_CAP_SPEED
    }.coerceIn(BouncerPhysics.MIN_BALL_SPEED, BouncerPhysics.MAX_BALL_SPEED)

    fun recommendedBallSpeed(deviceMaxBallCount: Int): Float =
        (deviceMaxBallSpeed(deviceMaxBallCount) * 0.85f)
            .coerceIn(BouncerPhysics.MIN_BALL_SPEED, BouncerPhysics.MAX_BALL_SPEED)

    fun renderQuality(
        deviceMaxBallCount: Int,
        ballStyle: BallStyle = BallStyle.AUTO,
    ): RenderQuality = when (ballStyle) {
        BallStyle.GLOW -> RenderQuality.Glow
        BallStyle.FLAT -> RenderQuality.Flat
        BallStyle.AUTO -> if (deviceMaxBallCount <= GLOW_DEVICE_CAP_THRESHOLD) {
            RenderQuality.Flat
        } else {
            RenderQuality.Glow
        }
    }

    fun nextCalibrationBallCount(current: Int): Int = when {
        current < 60 -> current + 12
        current < 120 -> current + 24
        current < 200 -> current + 36
        current < BouncerPhysics.MAX_BALL_COUNT -> current + 48
        else -> BouncerPhysics.MAX_BALL_COUNT
    }.coerceAtMost(BouncerPhysics.MAX_BALL_COUNT)
}

internal class RuntimeBallCountController(
    configuredBallCount: Int,
    private val deviceMaxBallCount: Int,
    initialRenderQuality: RenderQuality = DevicePerformance.renderQuality(deviceMaxBallCount),
) {
    private var configuredBallCount = clamp(configuredBallCount)
    private var activeBallCount = this.configuredBallCount
    private var preferredRenderQuality = initialRenderQuality
    private var currentRenderQuality = initialRenderQuality
    private var adaptivePerformanceEnabled = true
    private var automaticStyleChanges = false
    private var automaticPhysicsReduction = false
    private var solidBodyPhysicsSuspended = false
    private var frameCounter = 0
    private var badFrames = 0
    private var totalFrameDurationNanos = 0.0
    private var consecutiveGoodWindows = 0
    private var consecutivePhysicsRecoveryWindows = 0
    private var physicsSuspendedWindowCount = 0
    private var physicsResumeGraceWindows = 0
    private var failedPhysicsProbeCount = 0
    private var physicsProbeInProgress = false
    private var consecutivePhysicsHealthyWindows = 0
    private var physicsPauseCooldownWindows = 0
    private val performancePressureHistory = ArrayDeque<Float>(PERFORMANCE_HISTORY_WINDOWS)

    fun activeBallCount(): Int = activeBallCount

    fun renderQuality(): RenderQuality = currentRenderQuality

    fun solidBodyPhysicsAllowed(): Boolean = !solidBodyPhysicsSuspended

    fun onSettingsAdjusted() {
        resetPerformanceHistory()
        activeBallCount = configuredBallCount
        currentRenderQuality = preferredRenderQuality
        solidBodyPhysicsSuspended = false
        physicsResumeGraceWindows = PHYSICS_SETTINGS_GRACE_WINDOWS
        failedPhysicsProbeCount = 0
        physicsProbeInProgress = false
        consecutivePhysicsHealthyWindows = 0
        physicsPauseCooldownWindows = 0
    }

    fun updateAdaptivePerformance(enabled: Boolean) {
        if (adaptivePerformanceEnabled == enabled) return
        adaptivePerformanceEnabled = enabled
        resetPerformanceHistory()
        if (!enabled) {
            activeBallCount = configuredBallCount
            currentRenderQuality = preferredRenderQuality
            solidBodyPhysicsSuspended = false
            failedPhysicsProbeCount = 0
            physicsProbeInProgress = false
            physicsPauseCooldownWindows = 0
        }
    }

    fun updateAutomaticPhysicsReduction(enabled: Boolean) {
        if (automaticPhysicsReduction == enabled) return
        automaticPhysicsReduction = enabled
        consecutivePhysicsRecoveryWindows = 0
        physicsSuspendedWindowCount = 0
        if (!enabled) {
            solidBodyPhysicsSuspended = false
            physicsResumeGraceWindows = 0
            failedPhysicsProbeCount = 0
            physicsProbeInProgress = false
            consecutivePhysicsHealthyWindows = 0
            physicsPauseCooldownWindows = 0
        }
    }

    fun updateConfiguredBallCount(value: Int) {
        configuredBallCount = clamp(value)
        activeBallCount = if (adaptivePerformanceEnabled) {
            min(activeBallCount, configuredBallCount)
        } else {
            configuredBallCount
        }
    }

    fun updatePreferredRenderQuality(
        value: RenderQuality,
        allowAutomaticStyleChanges: Boolean = false,
        force: Boolean = false,
    ) {
        if (
            value == preferredRenderQuality &&
            allowAutomaticStyleChanges == automaticStyleChanges &&
            !force
        ) {
            return
        }
        preferredRenderQuality = value
        currentRenderQuality = value
        automaticStyleChanges = allowAutomaticStyleChanges
        resetPerformanceHistory()
        solidBodyPhysicsSuspended = false
    }

    fun recordFrame(frameDurationNanos: Long, frameBudgetNanos: Long): Int {
        if (!adaptivePerformanceEnabled) return activeBallCount

        val badFrameThreshold = (frameBudgetNanos * 1.1f).roundToInt().toLong()
        frameCounter++
        totalFrameDurationNanos += frameDurationNanos.coerceAtLeast(0L).toDouble()
        if (frameDurationNanos > badFrameThreshold) {
            badFrames++
        }

        if (frameCounter < DevicePerformance.RUNTIME_WINDOW_FRAMES) {
            return activeBallCount
        }

        val badRatio = badFrames.toFloat() / frameCounter.toFloat()
        val averageFrameDuration = totalFrameDurationNanos / frameCounter.toDouble()
        val averageOverrun = (averageFrameDuration / frameBudgetNanos.coerceAtLeast(1L) - 1.0)
            .toFloat()
            .coerceAtLeast(0f)
        // A frame that only just misses the budget should not carry the same weight as a
        // genuinely expensive frame. Average overrun captures severity; the scaled ratio
        // still catches intermittent jank without turning a small timing miss into 100% load.
        val latestPressure = max(badRatio * BAD_FRAME_RATIO_WEIGHT, averageOverrun)
            .coerceIn(0f, 1f)
        addPerformancePressure(latestPressure)
        val rollingPressure = rollingPerformancePressure()
        val responsivePressure = max(rollingPressure, latestPressure * RECENT_PRESSURE_FLOOR)

        if (
            performancePressureHistory.size >= MIN_HISTORY_FOR_ADJUSTMENT ||
            latestPressure >= IMMEDIATE_REACTION_THRESHOLD
        ) {
            val reductionFraction = if (latestPressure >= MILD_PRESSURE_THRESHOLD) {
                reductionFraction(responsivePressure)
            } else {
                0f
            }
            if (reductionFraction > 0f) {
                activeBallCount = max(
                    adaptiveMinimumBallCount(),
                    activeBallCount - reductionStep(reductionFraction),
                )
                consecutiveGoodWindows = 0
            } else if (latestPressure <= STABLE_PRESSURE_THRESHOLD) {
                consecutiveGoodWindows++
                if (consecutiveGoodWindows >= RECOVERY_WINDOW_COUNT && activeBallCount < configuredBallCount) {
                    activeBallCount = min(configuredBallCount, activeBallCount + recoveryStep())
                    consecutiveGoodWindows = 0
                }
            } else {
                consecutiveGoodWindows = 0
            }

            updateAutomaticRenderQuality(latestPressure, responsivePressure)
        } else {
            consecutiveGoodWindows = 0
        }
        updateAutomaticPhysics(latestPressure, responsivePressure)

        frameCounter = 0
        badFrames = 0
        totalFrameDurationNanos = 0.0
        return activeBallCount
    }

    internal fun rollingPerformancePressure(): Float {
        if (performancePressureHistory.isEmpty()) return 0f

        var weight = 1f
        var weightedPressure = 0f
        var totalWeight = 0f
        performancePressureHistory.forEach { pressure ->
            weightedPressure += pressure * weight
            totalWeight += weight
            weight *= RECENCY_WEIGHT_MULTIPLIER
        }
        return weightedPressure / totalWeight
    }

    private fun addPerformancePressure(value: Float) {
        if (performancePressureHistory.size == PERFORMANCE_HISTORY_WINDOWS) {
            performancePressureHistory.removeFirst()
        }
        performancePressureHistory.addLast(value.coerceIn(0f, 1f))
    }

    private fun reductionFraction(rollingPressure: Float): Float = when {
        rollingPressure >= SEVERE_PRESSURE_THRESHOLD -> 0.20f
        rollingPressure >= MODERATE_PRESSURE_THRESHOLD -> 0.12f
        rollingPressure >= MILD_PRESSURE_THRESHOLD -> 0.06f
        else -> 0f
    }

    private fun reductionStep(fraction: Float): Int =
        max(2, (activeBallCount * fraction).roundToInt())

    private fun recoveryStep(): Int = max(1, (configuredBallCount * 0.05f).roundToInt())

    private fun updateAutomaticPhysics(latestPressure: Float, responsivePressure: Float) {
        if (!automaticPhysicsReduction) return
        if (physicsPauseCooldownWindows > 0) {
            physicsPauseCooldownWindows--
            solidBodyPhysicsSuspended = false
            if (physicsPauseCooldownWindows == 0) {
                resetPerformanceHistory()
            }
            return
        }
        if (physicsResumeGraceWindows > 0) {
            physicsResumeGraceWindows--
            solidBodyPhysicsSuspended = false
            return
        }

        if (!solidBodyPhysicsSuspended) {
            if (latestPressure <= PHYSICS_RECOVERY_PRESSURE_THRESHOLD) {
                consecutivePhysicsHealthyWindows++
                if (consecutivePhysicsHealthyWindows >= PHYSICS_PROBE_SUCCESS_WINDOWS) {
                    failedPhysicsProbeCount = 0
                    physicsProbeInProgress = false
                    consecutivePhysicsHealthyWindows = 0
                }
            } else {
                consecutivePhysicsHealthyWindows = 0
            }

            val sustainedHeavyLoad =
                performancePressureHistory.size >= MIN_HISTORY_FOR_ADJUSTMENT &&
                    responsivePressure >= PHYSICS_SUSPEND_PRESSURE_THRESHOLD
            if (latestPressure >= PHYSICS_IMMEDIATE_SUSPEND_THRESHOLD || sustainedHeavyLoad) {
                if (physicsProbeInProgress) {
                    failedPhysicsProbeCount++
                    if (failedPhysicsProbeCount >= PHYSICS_FAILED_PROBES_BEFORE_COOLDOWN) {
                        // If repeated probes remain overloaded, pausing collisions is not
                        // fixing the bottleneck. Keep physics enabled while ball/style
                        // adaptation does the useful work, then reassess much later.
                        solidBodyPhysicsSuspended = false
                        physicsProbeInProgress = false
                        failedPhysicsProbeCount = 0
                        physicsPauseCooldownWindows = PHYSICS_PAUSE_COOLDOWN_WINDOWS
                        consecutivePhysicsHealthyWindows = 0
                        return
                    }
                }
                solidBodyPhysicsSuspended = true
                physicsProbeInProgress = false
                consecutivePhysicsRecoveryWindows = 0
                consecutivePhysicsHealthyWindows = 0
                physicsSuspendedWindowCount = 0
            }
            return
        }

        physicsSuspendedWindowCount++
        val maximumSuspendedWindows = PHYSICS_MAX_SUSPENDED_WINDOWS shl failedPhysicsProbeCount

        if (latestPressure <= PHYSICS_RECOVERY_PRESSURE_THRESHOLD) {
            consecutivePhysicsRecoveryWindows++
            if (
                physicsSuspendedWindowCount >= maximumSuspendedWindows ||
                (
                    consecutivePhysicsRecoveryWindows >= PHYSICS_RECOVERY_WINDOW_COUNT &&
                        responsivePressure <= PHYSICS_ROLLING_RECOVERY_THRESHOLD
                    )
            ) {
                startPhysicsProbe()
            }
        } else {
            consecutivePhysicsRecoveryWindows = 0
            if (physicsSuspendedWindowCount >= maximumSuspendedWindows) {
                startPhysicsProbe()
            }
        }
    }

    private fun startPhysicsProbe() {
        // Discard measurements gathered with collisions disabled. The fresh history and
        // grace windows let the controller judge the cost of collisions themselves.
        resetPerformanceHistory()
        solidBodyPhysicsSuspended = false
        physicsProbeInProgress = true
        physicsResumeGraceWindows = PHYSICS_PROBE_GRACE_WINDOWS
    }

    private fun resetPerformanceHistory() {
        frameCounter = 0
        badFrames = 0
        totalFrameDurationNanos = 0.0
        consecutiveGoodWindows = 0
        consecutivePhysicsRecoveryWindows = 0
        consecutivePhysicsHealthyWindows = 0
        physicsSuspendedWindowCount = 0
        performancePressureHistory.clear()
    }

    private fun adaptiveMinimumBallCount(): Int =
        min(
            configuredBallCount,
            max(
                MIN_ADAPTIVE_BALL_COUNT,
                min(MAX_ADAPTIVE_BALL_FLOOR, (configuredBallCount * 0.15f).roundToInt()),
            ),
        )

    private fun updateAutomaticRenderQuality(latestPressure: Float, rollingPressure: Float) {
        if (
            !automaticStyleChanges ||
            preferredRenderQuality != RenderQuality.Glow ||
            performancePressureHistory.size < PERFORMANCE_HISTORY_WINDOWS
        ) {
            return
        }

        val flatSwitchBallCount = max(
            adaptiveMinimumBallCount(),
            (configuredBallCount * AUTO_FLAT_BALL_FRACTION).roundToInt(),
        )
        if (
            currentRenderQuality == RenderQuality.Glow &&
            latestPressure >= MODERATE_PRESSURE_THRESHOLD &&
            rollingPressure >= AUTO_FLAT_PRESSURE_THRESHOLD &&
            activeBallCount <= flatSwitchBallCount
        ) {
            currentRenderQuality = RenderQuality.Flat
            return
        }

        if (
            currentRenderQuality == RenderQuality.Flat &&
            rollingPressure <= AUTO_GLOW_PRESSURE_THRESHOLD &&
            activeBallCount >= (configuredBallCount * AUTO_GLOW_BALL_FRACTION).roundToInt()
        ) {
            currentRenderQuality = RenderQuality.Glow
        }
    }

    private fun clamp(value: Int): Int =
        value.coerceIn(BouncerPhysics.MIN_BALL_COUNT, deviceMaxBallCount)

    private companion object {
        const val MIN_ADAPTIVE_BALL_COUNT = 4
        const val MAX_ADAPTIVE_BALL_FLOOR = 12
        const val PERFORMANCE_HISTORY_WINDOWS = 8
        const val MIN_HISTORY_FOR_ADJUSTMENT = 2
        const val RECOVERY_WINDOW_COUNT = 2
        const val RECENCY_WEIGHT_MULTIPLIER = 1.35f
        const val RECENT_PRESSURE_FLOOR = 0.8f
        const val BAD_FRAME_RATIO_WEIGHT = 0.2f
        const val IMMEDIATE_REACTION_THRESHOLD = 0.35f
        const val STABLE_PRESSURE_THRESHOLD = 0.015f
        const val MILD_PRESSURE_THRESHOLD = 0.04f
        const val MODERATE_PRESSURE_THRESHOLD = 0.10f
        const val SEVERE_PRESSURE_THRESHOLD = 0.22f
        const val PHYSICS_IMMEDIATE_SUSPEND_THRESHOLD = 0.45f
        const val PHYSICS_SUSPEND_PRESSURE_THRESHOLD = 0.24f
        const val PHYSICS_RECOVERY_PRESSURE_THRESHOLD = 0.04f
        const val PHYSICS_ROLLING_RECOVERY_THRESHOLD = 0.08f
        const val PHYSICS_RECOVERY_WINDOW_COUNT = 4
        const val PHYSICS_MAX_SUSPENDED_WINDOWS = 12
        const val PHYSICS_SETTINGS_GRACE_WINDOWS = 4
        const val PHYSICS_PROBE_GRACE_WINDOWS = 3
        const val PHYSICS_PROBE_SUCCESS_WINDOWS = 6
        const val PHYSICS_FAILED_PROBES_BEFORE_COOLDOWN = 1
        const val PHYSICS_PAUSE_COOLDOWN_WINDOWS = 60
        const val AUTO_FLAT_PRESSURE_THRESHOLD = 0.20f
        const val AUTO_GLOW_PRESSURE_THRESHOLD = 0.01f
        const val AUTO_FLAT_BALL_FRACTION = 0.5f
        const val AUTO_GLOW_BALL_FRACTION = 0.8f
    }
}
