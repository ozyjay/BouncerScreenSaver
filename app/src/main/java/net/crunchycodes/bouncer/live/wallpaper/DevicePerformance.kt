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
    val usedFallback: Boolean,
)

internal object DevicePerformance {
    const val FALLBACK_REFRESH_RATE_HZ = 60f
    const val MIN_SUPPORTED_REFRESH_RATE_HZ = 30f
    const val MAX_SUPPORTED_REFRESH_RATE_HZ = 144f
    const val FALLBACK_MAX_BALL_COUNT = 24
    const val CALIBRATION_START_BALL_COUNT = 12
    const val CALIBRATION_WARMUP_FRAMES = 12
    const val CALIBRATION_WINDOW_FRAMES = 72
    const val MAX_BAD_WINDOW_STREAK = 2
    private const val STABLE_P95_MULTIPLIER = 1.1f
    private const val STABLE_DROP_RATIO = 0.05f

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
) {
    private var configuredBallCount = clamp(configuredBallCount)
    private var activeBallCount = this.configuredBallCount
    private var frameCounter = 0
    private var badFrames = 0
    private var consecutiveBadWindows = 0
    private var consecutiveGoodWindows = 0

    fun activeBallCount(): Int = activeBallCount

    fun updateConfiguredBallCount(value: Int) {
        configuredBallCount = clamp(value)
        activeBallCount = min(activeBallCount, configuredBallCount)
    }

    fun recordFrame(frameDurationNanos: Long, frameBudgetNanos: Long): Int {
        val badFrameThreshold = (frameBudgetNanos * 1.1f).roundToInt().toLong()
        frameCounter++
        if (frameDurationNanos > badFrameThreshold) {
            badFrames++
        }

        if (frameCounter < DevicePerformance.CALIBRATION_WINDOW_FRAMES) {
            return activeBallCount
        }

        val badRatio = badFrames.toFloat() / frameCounter.toFloat()
        if (badRatio > 0.08f) {
            consecutiveBadWindows++
            consecutiveGoodWindows = 0
            if (consecutiveBadWindows >= 2 && activeBallCount > BouncerPhysics.MIN_BALL_COUNT) {
                activeBallCount = max(BouncerPhysics.MIN_BALL_COUNT, activeBallCount - reductionStep())
                consecutiveBadWindows = 0
            }
        } else {
            consecutiveGoodWindows++
            consecutiveBadWindows = 0
            if (consecutiveGoodWindows >= 6 && activeBallCount < configuredBallCount) {
                activeBallCount = min(configuredBallCount, activeBallCount + recoveryStep())
                consecutiveGoodWindows = 0
            }
        }

        frameCounter = 0
        badFrames = 0
        return activeBallCount
    }

    private fun reductionStep(): Int = max(2, (activeBallCount * 0.1f).roundToInt())

    private fun recoveryStep(): Int = max(1, (configuredBallCount * 0.05f).roundToInt())

    private fun clamp(value: Int): Int =
        value.coerceIn(BouncerPhysics.MIN_BALL_COUNT, deviceMaxBallCount)
}
