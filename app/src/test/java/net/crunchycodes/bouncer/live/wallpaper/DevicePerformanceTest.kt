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
        val controller = RuntimeBallCountController(configuredBallCount = 100, deviceMaxBallCount = 100)

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
    fun renderQualityUsesFlatModeOnLowCapDevices() {
        assertEquals(
            RenderQuality.Flat,
            DevicePerformance.renderQuality(
                deviceMaxBallCount = 48,
                activeBallCount = 48,
                configuredBallCount = 48,
            ),
        )
    }

    @Test
    fun renderQualityUsesFlatModeWhileRuntimeThrottled() {
        assertEquals(
            RenderQuality.Flat,
            DevicePerformance.renderQuality(
                deviceMaxBallCount = 120,
                activeBallCount = 72,
                configuredBallCount = 100,
            ),
        )
    }

    @Test
    fun renderQualityKeepsGlowWhenHeadroomIsHealthy() {
        assertEquals(
            RenderQuality.Glow,
            DevicePerformance.renderQuality(
                deviceMaxBallCount = 120,
                activeBallCount = 100,
                configuredBallCount = 100,
            ),
        )
    }
}
