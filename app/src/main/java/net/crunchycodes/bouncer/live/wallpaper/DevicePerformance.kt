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
    private var automaticStyleChanges = false
    private var frameCounter = 0
    private var badFrames = 0
    private var totalFrameDurationNanos = 0.0
    private var consecutiveGoodWindows = 0
    private val performancePressureHistory = ArrayDeque<Float>(PERFORMANCE_HISTORY_WINDOWS)

    fun activeBallCount(): Int = activeBallCount

    fun renderQuality(): RenderQuality = currentRenderQuality

    fun updateConfiguredBallCount(value: Int) {
        configuredBallCount = clamp(value)
        activeBallCount = min(activeBallCount, configuredBallCount)
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
        frameCounter = 0
        badFrames = 0
        totalFrameDurationNanos = 0.0
        consecutiveGoodWindows = 0
        performancePressureHistory.clear()
    }

    fun recordFrame(frameDurationNanos: Long, frameBudgetNanos: Long): Int {
        val badFrameThreshold = (frameBudgetNanos * 1.1f).roundToInt().toLong()
        frameCounter++
        totalFrameDurationNanos += frameDurationNanos.coerceAtLeast(0L).toDouble()
        if (frameDurationNanos > badFrameThreshold) {
            badFrames++
        }

        if (frameCounter < DevicePerformance.CALIBRATION_WINDOW_FRAMES) {
            return activeBallCount
        }

        val badRatio = badFrames.toFloat() / frameCounter.toFloat()
        val averageFrameDuration = totalFrameDurationNanos / frameCounter.toDouble()
        val averageOverrun = (averageFrameDuration / frameBudgetNanos.coerceAtLeast(1L) - 1.0)
            .toFloat()
            .coerceAtLeast(0f)
        addPerformancePressure(max(badRatio, averageOverrun))

        if (performancePressureHistory.size >= MIN_HISTORY_FOR_ADJUSTMENT) {
            val rollingPressure = rollingPerformancePressure()
            val reductionFraction = reductionFraction(rollingPressure)
            if (reductionFraction > 0f) {
                activeBallCount = max(
                    adaptiveMinimumBallCount(),
                    activeBallCount - reductionStep(reductionFraction),
                )
                consecutiveGoodWindows = 0
            } else if (rollingPressure <= STABLE_PRESSURE_THRESHOLD) {
                consecutiveGoodWindows++
                if (consecutiveGoodWindows >= RECOVERY_WINDOW_COUNT && activeBallCount < configuredBallCount) {
                    activeBallCount = min(configuredBallCount, activeBallCount + recoveryStep())
                    consecutiveGoodWindows = 0
                }
            } else {
                consecutiveGoodWindows = 0
            }

            updateAutomaticRenderQuality(rollingPressure)
        } else {
            consecutiveGoodWindows++
        }

        frameCounter = 0
        badFrames = 0
        totalFrameDurationNanos = 0.0
        return activeBallCount
    }

    internal fun rollingPerformancePressure(): Float =
        if (performancePressureHistory.isEmpty()) {
            0f
        } else {
            performancePressureHistory.average().toFloat()
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

    private fun adaptiveMinimumBallCount(): Int =
        min(configuredBallCount, max(MIN_ADAPTIVE_BALL_COUNT, (configuredBallCount * 0.15f).roundToInt()))

    private fun updateAutomaticRenderQuality(rollingPressure: Float) {
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
        const val PERFORMANCE_HISTORY_WINDOWS = 8
        const val MIN_HISTORY_FOR_ADJUSTMENT = 3
        const val RECOVERY_WINDOW_COUNT = 3
        const val STABLE_PRESSURE_THRESHOLD = 0.015f
        const val MILD_PRESSURE_THRESHOLD = 0.05f
        const val MODERATE_PRESSURE_THRESHOLD = 0.12f
        const val SEVERE_PRESSURE_THRESHOLD = 0.25f
        const val AUTO_FLAT_PRESSURE_THRESHOLD = 0.20f
        const val AUTO_GLOW_PRESSURE_THRESHOLD = 0.01f
        const val AUTO_FLAT_BALL_FRACTION = 0.5f
        const val AUTO_GLOW_BALL_FRACTION = 0.8f
    }
}
