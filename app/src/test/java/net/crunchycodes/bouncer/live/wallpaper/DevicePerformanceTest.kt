package net.crunchycodes.bouncer.live.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePerformanceTest {
    @Test
    fun evaluateWindowTreatsStableFramesAsStable() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val samples = List(72) { frameBudgetNanos }

        val metrics = DevicePerformance.evaluateWindow(samples, frameBudgetNanos)

        assertTrue(metrics.stable)
        assertEquals(frameBudgetNanos, metrics.p95FrameNanos)
        assertEquals(0f, metrics.droppedFrameRatio, 0.0001f)
    }

    @Test
    fun evaluateWindowFlagsDroppedFrames() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val samples = List(60) { frameBudgetNanos } + List(12) { (frameBudgetNanos * 1.4f).toLong() }

        val metrics = DevicePerformance.evaluateWindow(samples, frameBudgetNanos)

        assertFalse(metrics.stable)
        assertTrue(metrics.droppedFrameRatio > 0.05f)
    }

    @Test
    fun runtimeControllerReducesAndRecoversBallCount() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = RuntimeBallCountController(
            configuredBallCount = 100,
            deviceMaxBallCount = 100,
            initialRenderQuality = RenderQuality.Flat,
        )

        repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES * 2) {
            controller.recordFrame((frameBudgetNanos * 1.3f).toLong(), frameBudgetNanos)
        }

        val reducedBallCount = controller.activeBallCount()
        assertTrue(reducedBallCount < 100)

        repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES * 6) {
            controller.recordFrame(frameBudgetNanos, frameBudgetNanos)
        }

        assertTrue(controller.activeBallCount() > reducedBallCount)
        assertTrue(controller.activeBallCount() <= 100)
    }

    @Test
    fun runtimeControllerDisablesGlowBeforeRemovingBalls() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = RuntimeBallCountController(
            configuredBallCount = 100,
            deviceMaxBallCount = 100,
            initialRenderQuality = RenderQuality.Glow,
        )

        repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES * 2) {
            controller.recordFrame((frameBudgetNanos * 1.3f).toLong(), frameBudgetNanos)
        }

        assertEquals(RenderQuality.Flat, controller.renderQuality())
        assertEquals(100, controller.activeBallCount())

        repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES * 2) {
            controller.recordFrame((frameBudgetNanos * 1.3f).toLong(), frameBudgetNanos)
        }

        assertTrue(controller.activeBallCount() < 100)
    }

    @Test
    fun runtimeControllerKeepsGlowDisabledAfterOverload() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = RuntimeBallCountController(
            configuredBallCount = 100,
            deviceMaxBallCount = 100,
            initialRenderQuality = RenderQuality.Glow,
        )

        repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES * 2) {
            controller.recordFrame((frameBudgetNanos * 1.3f).toLong(), frameBudgetNanos)
        }
        assertEquals(RenderQuality.Flat, controller.renderQuality())

        repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES * 24) {
            controller.recordFrame(frameBudgetNanos, frameBudgetNanos)
        }

        assertEquals(RenderQuality.Flat, controller.renderQuality())
    }

    @Test
    fun runtimeControllerDoesNotCollapseLargePopulationToOneBall() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = RuntimeBallCountController(
            configuredBallCount = 100,
            deviceMaxBallCount = 100,
            initialRenderQuality = RenderQuality.Flat,
        )

        repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES * 100) {
            controller.recordFrame((frameBudgetNanos * 1.3f).toLong(), frameBudgetNanos)
        }

        assertTrue(controller.activeBallCount() >= 4)
    }

    @Test
    fun renderQualityUsesFlatModeOnLowCapDevices() {
        assertEquals(
            RenderQuality.Flat,
            DevicePerformance.renderQuality(deviceMaxBallCount = 48),
        )
    }

    @Test
    fun renderQualityKeepsGlowWhileRuntimeThrottledOnCapableDevices() {
        assertEquals(
            RenderQuality.Glow,
            DevicePerformance.renderQuality(deviceMaxBallCount = 120),
        )
    }

    @Test
    fun renderQualityKeepsGlowWhenHeadroomIsHealthy() {
        assertEquals(
            RenderQuality.Glow,
            DevicePerformance.renderQuality(deviceMaxBallCount = 120),
        )
    }

    @Test
    fun deviceMaxBallSpeedGetsMoreConservativeOnLowerCapDevices() {
        assertTrue(DevicePerformance.deviceMaxBallSpeed(24) < DevicePerformance.deviceMaxBallSpeed(120))
        assertTrue(DevicePerformance.deviceMaxBallSpeed(120) <= BouncerPhysics.MAX_BALL_SPEED)
    }

    @Test
    fun recommendedBallSpeedStaysWithinDeviceCap() {
        val deviceCap = DevicePerformance.deviceMaxBallSpeed(48)
        val recommended = DevicePerformance.recommendedBallSpeed(48)

        assertTrue(recommended <= deviceCap)
        assertTrue(recommended >= BouncerPhysics.MIN_BALL_SPEED)
    }
}
