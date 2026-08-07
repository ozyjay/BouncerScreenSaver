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

        repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES * 3) {
            controller.recordFrame((frameBudgetNanos * 1.3f).toLong(), frameBudgetNanos)
        }

        val reducedBallCount = controller.activeBallCount()
        assertTrue(reducedBallCount < 100)

        var lowestBallCount = reducedBallCount
        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES * 12) {
            val currentBallCount = controller.recordFrame(frameBudgetNanos, frameBudgetNanos)
            lowestBallCount = minOf(lowestBallCount, currentBallCount)
        }
        assertTrue(lowestBallCount <= reducedBallCount)

        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES * 24) {
            controller.recordFrame(frameBudgetNanos, frameBudgetNanos)
        }

        assertTrue(controller.activeBallCount() > lowestBallCount)
        assertTrue(controller.activeBallCount() <= 100)
    }

    @Test
    fun runtimeControllerKeepsGlowAndReducesBallCount() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = RuntimeBallCountController(
            configuredBallCount = 100,
            deviceMaxBallCount = 100,
            initialRenderQuality = RenderQuality.Glow,
        )

        repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES * 3) {
            controller.recordFrame((frameBudgetNanos * 1.3f).toLong(), frameBudgetNanos)
        }

        assertEquals(RenderQuality.Glow, controller.renderQuality())
        assertTrue(controller.activeBallCount() < 100)
    }

    @Test
    fun runtimeControllerReactsWithinTwoShortWindows() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = RuntimeBallCountController(100, 100, RenderQuality.Glow)

        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES * 2) {
            controller.recordFrame((frameBudgetNanos * 1.25f).toLong(), frameBudgetNanos)
        }

        assertTrue(controller.activeBallCount() < 100)
    }

    @Test
    fun isolatedLoadWindowDoesNotCauseDelayedOverThrottling() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = RuntimeBallCountController(100, 100, RenderQuality.Glow)

        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES) {
            controller.recordFrame((frameBudgetNanos * 1.3f).toLong(), frameBudgetNanos)
        }
        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES) {
            controller.recordFrame(frameBudgetNanos, frameBudgetNanos)
        }

        assertEquals(100, controller.activeBallCount())
    }

    @Test
    fun veryHeavyLoadTemporarilySuspendsSolidBodyPhysics() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = RuntimeBallCountController(100, 100, RenderQuality.Glow)
        controller.updateAutomaticPhysicsReduction(true)

        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES) {
            controller.recordFrame((frameBudgetNanos * 1.6f).toLong(), frameBudgetNanos)
        }

        assertFalse(controller.solidBodyPhysicsAllowed())

        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES * 8) {
            controller.recordFrame(frameBudgetNanos, frameBudgetNanos)
        }

        assertTrue(controller.solidBodyPhysicsAllowed())
    }

    @Test
    fun automaticPhysicsReductionCanBeDisabledByUser() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = RuntimeBallCountController(100, 100, RenderQuality.Glow)

        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES * 4) {
            controller.recordFrame((frameBudgetNanos * 1.6f).toLong(), frameBudgetNanos)
        }

        assertTrue(controller.solidBodyPhysicsAllowed())

        controller.updateAutomaticPhysicsReduction(true)
        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES) {
            controller.recordFrame((frameBudgetNanos * 1.6f).toLong(), frameBudgetNanos)
        }
        assertFalse(controller.solidBodyPhysicsAllowed())

        controller.updateAutomaticPhysicsReduction(false)
        assertTrue(controller.solidBodyPhysicsAllowed())
    }

    @Test
    fun runtimeControllerKeepsGlowAfterOverloadAndRecovery() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = RuntimeBallCountController(
            configuredBallCount = 100,
            deviceMaxBallCount = 100,
            initialRenderQuality = RenderQuality.Glow,
        )

        repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES * 3) {
            controller.recordFrame((frameBudgetNanos * 1.3f).toLong(), frameBudgetNanos)
        }
        assertEquals(RenderQuality.Glow, controller.renderQuality())

        repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES * 24) {
            controller.recordFrame(frameBudgetNanos, frameBudgetNanos)
        }

        assertEquals(RenderQuality.Glow, controller.renderQuality())
    }

    @Test
    fun explicitStyleChangeUpdatesRenderQuality() {
        val controller = RuntimeBallCountController(
            configuredBallCount = 100,
            deviceMaxBallCount = 100,
            initialRenderQuality = RenderQuality.Flat,
        )
        assertEquals(RenderQuality.Flat, controller.renderQuality())

        controller.updatePreferredRenderQuality(RenderQuality.Glow, force = true)

        assertEquals(RenderQuality.Glow, controller.renderQuality())
    }

    @Test
    fun rollingPressureScalesReductionSeverity() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val mildController = RuntimeBallCountController(100, 100, RenderQuality.Glow)
        val severeController = RuntimeBallCountController(100, 100, RenderQuality.Glow)

        repeat(3) {
            repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES) { frame ->
                val mildDuration = if (frame < 5) {
                    (frameBudgetNanos * 1.2f).toLong()
                } else {
                    frameBudgetNanos
                }
                mildController.recordFrame(mildDuration, frameBudgetNanos)
                severeController.recordFrame((frameBudgetNanos * 1.3f).toLong(), frameBudgetNanos)
            }
        }

        assertTrue(severeController.activeBallCount() < mildController.activeBallCount())
        assertTrue(mildController.rollingPerformancePressure() > 0f)
    }

    @Test
    fun autoStyleUsesFlatOnlyAfterSustainedPressureAndBallReduction() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = RuntimeBallCountController(100, 100, RenderQuality.Glow)
        controller.updatePreferredRenderQuality(
            value = RenderQuality.Glow,
            allowAutomaticStyleChanges = true,
            force = true,
        )

        repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES * 8) {
            controller.recordFrame((frameBudgetNanos * 1.3f).toLong(), frameBudgetNanos)
        }

        assertEquals(RenderQuality.Flat, controller.renderQuality())
        assertTrue(controller.activeBallCount() <= 50)
    }

    @Test
    fun explicitGlowNeverChangesStyleUnderSustainedPressure() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = RuntimeBallCountController(100, 100, RenderQuality.Glow)

        repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES * 24) {
            controller.recordFrame((frameBudgetNanos * 1.3f).toLong(), frameBudgetNanos)
        }

        assertEquals(RenderQuality.Glow, controller.renderQuality())
        assertTrue(controller.activeBallCount() < 100)
    }

    @Test
    fun autoStyleRestoresGlowAfterLongStableRecovery() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = RuntimeBallCountController(100, 100, RenderQuality.Glow)
        controller.updatePreferredRenderQuality(
            value = RenderQuality.Glow,
            allowAutomaticStyleChanges = true,
            force = true,
        )
        repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES * 8) {
            controller.recordFrame((frameBudgetNanos * 1.3f).toLong(), frameBudgetNanos)
        }
        assertEquals(RenderQuality.Flat, controller.renderQuality())

        repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES * 60) {
            controller.recordFrame(frameBudgetNanos, frameBudgetNanos)
        }

        assertEquals(RenderQuality.Glow, controller.renderQuality())
        assertTrue(controller.activeBallCount() >= 80)
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
    fun ballStyleOverridesAutomaticInitialQuality() {
        assertEquals(RenderQuality.Glow, DevicePerformance.renderQuality(24, BallStyle.GLOW))
        assertEquals(RenderQuality.Flat, DevicePerformance.renderQuality(120, BallStyle.FLAT))
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
