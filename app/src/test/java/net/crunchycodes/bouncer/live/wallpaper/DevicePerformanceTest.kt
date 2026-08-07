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
        val controller = RuntimePerformanceController(
            configuredBallCount = 100,
            deviceMaxBallCount = 100,
            initialRenderQuality = RenderQuality.Flat,
        )

        repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES * 3) {
            controller.recordFrame((frameBudgetNanos * 1.3f).toLong(), frameBudgetNanos)
        }

        val reducedBallCount = controller.snapshot().activeBallCount
        assertTrue(reducedBallCount < 100)

        var lowestBallCount = reducedBallCount
        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES * 12) {
            val currentBallCount = controller.recordFrame(
                frameBudgetNanos,
                frameBudgetNanos,
            ).activeBallCount
            lowestBallCount = minOf(lowestBallCount, currentBallCount)
        }
        assertTrue(lowestBallCount <= reducedBallCount)

        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES * 24) {
            controller.recordFrame(frameBudgetNanos, frameBudgetNanos)
        }

        assertTrue(controller.snapshot().activeBallCount > lowestBallCount)
        assertTrue(controller.snapshot().activeBallCount <= 100)
    }

    @Test
    fun runtimeControllerKeepsGlowAndReducesBallCount() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = RuntimePerformanceController(
            configuredBallCount = 100,
            deviceMaxBallCount = 100,
            initialRenderQuality = RenderQuality.Glow,
        )

        repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES * 3) {
            controller.recordFrame((frameBudgetNanos * 1.3f).toLong(), frameBudgetNanos)
        }

        assertEquals(RenderQuality.Glow, controller.snapshot().renderQuality)
        assertTrue(controller.snapshot().activeBallCount < 100)
    }

    @Test
    fun runtimeControllerReactsWithinTwoShortWindows() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = RuntimePerformanceController(100, 100, RenderQuality.Glow)

        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES * 2) {
            controller.recordFrame((frameBudgetNanos * 1.25f).toLong(), frameBudgetNanos)
        }

        assertTrue(controller.snapshot().activeBallCount < 100)
    }

    @Test
    fun isolatedLoadWindowDoesNotCauseDelayedOverThrottling() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = RuntimePerformanceController(100, 100, RenderQuality.Glow)

        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES) {
            controller.recordFrame((frameBudgetNanos * 1.3f).toLong(), frameBudgetNanos)
        }
        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES) {
            controller.recordFrame(frameBudgetNanos, frameBudgetNanos)
        }

        assertEquals(100, controller.snapshot().activeBallCount)
    }

    @Test
    fun veryHeavyLoadTemporarilySuspendsSolidBodyPhysics() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = performanceController(automaticPhysics = true)

        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES) {
            controller.recordFrame((frameBudgetNanos * 1.6f).toLong(), frameBudgetNanos)
        }

        assertFalse(controller.snapshot().solidBodyPhysicsAllowed)

        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES * 8) {
            controller.recordFrame(frameBudgetNanos, frameBudgetNanos)
        }

        assertTrue(controller.snapshot().solidBodyPhysicsAllowed)
    }

    @Test
    fun automaticPhysicsReductionCanBeDisabledByUser() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = RuntimePerformanceController(100, 100, RenderQuality.Glow)

        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES * 4) {
            controller.recordFrame((frameBudgetNanos * 1.6f).toLong(), frameBudgetNanos)
        }

        assertTrue(controller.snapshot().solidBodyPhysicsAllowed)

        controller.updateConfiguration(performanceConfig(automaticPhysics = true))
        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES) {
            controller.recordFrame((frameBudgetNanos * 1.6f).toLong(), frameBudgetNanos)
        }
        assertFalse(controller.snapshot().solidBodyPhysicsAllowed)

        controller.updateConfiguration(performanceConfig(automaticPhysics = false))
        assertTrue(controller.snapshot().solidBodyPhysicsAllowed)
    }

    @Test
    fun collisionPauseAlwaysReturnsForAProbe() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = performanceController(automaticPhysics = true)

        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES) {
            controller.recordFrame((frameBudgetNanos * 1.6f).toLong(), frameBudgetNanos)
        }
        assertFalse(controller.snapshot().solidBodyPhysicsAllowed)

        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES * 12) {
            controller.recordFrame((frameBudgetNanos * 1.05f).toLong(), frameBudgetNanos)
        }

        assertTrue(controller.snapshot().solidBodyPhysicsAllowed)
    }

    @Test
    fun failedCollisionProbeStartsCollisionEnabledCooldown() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = performanceController(automaticPhysics = true)

        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES) {
            controller.recordFrame((frameBudgetNanos * 1.6f).toLong(), frameBudgetNanos)
        }
        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES * 12) {
            controller.recordFrame((frameBudgetNanos * 1.05f).toLong(), frameBudgetNanos)
        }
        assertTrue(controller.snapshot().solidBodyPhysicsAllowed)

        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES * 4) {
            controller.recordFrame((frameBudgetNanos * 1.6f).toLong(), frameBudgetNanos)
        }
        assertTrue(controller.snapshot().solidBodyPhysicsAllowed)

        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES * 20) {
            controller.recordFrame((frameBudgetNanos * 1.6f).toLong(), frameBudgetNanos)
        }
        assertTrue(controller.snapshot().solidBodyPhysicsAllowed)
    }

    @Test
    fun adjustingSettingsImmediatelyClearsCollisionPauseAndHistory() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = performanceController(automaticPhysics = true)

        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES * 2) {
            controller.recordFrame((frameBudgetNanos * 1.6f).toLong(), frameBudgetNanos)
        }
        assertFalse(controller.snapshot().solidBodyPhysicsAllowed)
        assertTrue(controller.rollingPerformancePressure() > 0f)
        assertTrue(controller.snapshot().activeBallCount < 100)

        controller.updateConfiguration(
            performanceConfig(automaticPhysics = true),
            settingsReset = true,
        )

        assertTrue(controller.snapshot().solidBodyPhysicsAllowed)
        assertEquals(0f, controller.rollingPerformancePressure(), 0f)
        assertEquals(100, controller.snapshot().activeBallCount)

        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES * 4) {
            controller.recordFrame((frameBudgetNanos * 1.6f).toLong(), frameBudgetNanos)
        }
        assertTrue(controller.snapshot().solidBodyPhysicsAllowed)

        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES) {
            controller.recordFrame((frameBudgetNanos * 1.6f).toLong(), frameBudgetNanos)
        }
        assertFalse(controller.snapshot().solidBodyPhysicsAllowed)
    }

    @Test
    fun fixedModeHonoursConfiguredCountUnderLoad() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = performanceController(
            configuredBallCount = 60,
            adaptive = false,
            automaticPhysics = true,
        )

        repeat(DevicePerformance.RUNTIME_WINDOW_FRAMES * 20) {
            controller.recordFrame((frameBudgetNanos * 1.6f).toLong(), frameBudgetNanos)
        }
        controller.updateConfiguration(
            performanceConfig(
                configuredBallCount = 80,
                adaptive = false,
                automaticPhysics = true,
            ),
        )

        assertEquals(80, controller.snapshot().activeBallCount)
        assertEquals(RenderQuality.Glow, controller.snapshot().renderQuality)
        assertTrue(controller.snapshot().solidBodyPhysicsAllowed)
    }

    @Test
    fun runtimeControllerKeepsGlowAfterOverloadAndRecovery() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = RuntimePerformanceController(
            configuredBallCount = 100,
            deviceMaxBallCount = 100,
            initialRenderQuality = RenderQuality.Glow,
        )

        repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES * 3) {
            controller.recordFrame((frameBudgetNanos * 1.3f).toLong(), frameBudgetNanos)
        }
        assertEquals(RenderQuality.Glow, controller.snapshot().renderQuality)

        repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES * 24) {
            controller.recordFrame(frameBudgetNanos, frameBudgetNanos)
        }

        assertEquals(RenderQuality.Glow, controller.snapshot().renderQuality)
    }

    @Test
    fun explicitStyleChangeUpdatesRenderQuality() {
        val controller = RuntimePerformanceController(
            configuredBallCount = 100,
            deviceMaxBallCount = 100,
            initialRenderQuality = RenderQuality.Flat,
        )
        assertEquals(RenderQuality.Flat, controller.snapshot().renderQuality)

        controller.updateConfiguration(
            performanceConfig(preferredQuality = RenderQuality.Glow),
            settingsReset = true,
        )

        assertEquals(RenderQuality.Glow, controller.snapshot().renderQuality)
    }

    @Test
    fun rollingPressureScalesReductionSeverity() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val mildController = RuntimePerformanceController(100, 100, RenderQuality.Glow)
        val severeController = RuntimePerformanceController(100, 100, RenderQuality.Glow)

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

        assertTrue(severeController.snapshot().activeBallCount < mildController.snapshot().activeBallCount)
        assertTrue(mildController.rollingPerformancePressure() > 0f)
    }

    @Test
    fun autoStyleUsesFlatOnlyAfterSustainedPressureAndBallReduction() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = performanceController(automaticStyle = true)

        repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES * 8) {
            controller.recordFrame((frameBudgetNanos * 1.3f).toLong(), frameBudgetNanos)
        }

        assertEquals(RenderQuality.Flat, controller.snapshot().renderQuality)
        assertTrue(controller.snapshot().activeBallCount <= 50)
    }

    @Test
    fun explicitGlowNeverChangesStyleUnderSustainedPressure() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = RuntimePerformanceController(100, 100, RenderQuality.Glow)

        repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES * 24) {
            controller.recordFrame((frameBudgetNanos * 1.3f).toLong(), frameBudgetNanos)
        }

        assertEquals(RenderQuality.Glow, controller.snapshot().renderQuality)
        assertTrue(controller.snapshot().activeBallCount < 100)
    }

    @Test
    fun autoStyleRestoresGlowAfterLongStableRecovery() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = performanceController(automaticStyle = true)
        repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES * 8) {
            controller.recordFrame((frameBudgetNanos * 1.3f).toLong(), frameBudgetNanos)
        }
        assertEquals(RenderQuality.Flat, controller.snapshot().renderQuality)

        repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES * 90) {
            controller.recordFrame(frameBudgetNanos, frameBudgetNanos)
        }

        assertEquals(RenderQuality.Glow, controller.snapshot().renderQuality)
        assertTrue(controller.snapshot().activeBallCount >= 80)
    }

    @Test
    fun runtimeControllerDoesNotCollapseLargePopulationToOneBall() {
        val frameBudgetNanos = DevicePerformance.frameBudgetNanos(60f)
        val controller = RuntimePerformanceController(
            configuredBallCount = 100,
            deviceMaxBallCount = 100,
            initialRenderQuality = RenderQuality.Flat,
        )

        repeat(DevicePerformance.CALIBRATION_WINDOW_FRAMES * 100) {
            controller.recordFrame((frameBudgetNanos * 1.3f).toLong(), frameBudgetNanos)
        }

        assertTrue(controller.snapshot().activeBallCount >= 4)
        assertTrue(controller.snapshot().activeBallCount <= 12)
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

    @Test
    fun fixedStateIgnoresPerformanceWindows() {
        val config = performanceConfig(adaptive = false)
        val initial = RuntimePerformanceStateMachine.initialState(config)

        val updated = RuntimePerformanceStateMachine.transition(
            initial,
            RuntimePerformanceEvent.WindowMeasured(1f),
        )

        assertTrue(updated is PerformanceThrottleState.Fixed)
        assertEquals(100, updated.snapshot.activeBallCount)
        assertEquals(RuntimePerformancePhase.FIXED, updated.snapshot.phase)
        assertTrue(updated.snapshot.solidBodyPhysicsAllowed)
    }

    @Test
    fun settingsResetCreatesGraceAndRestoresPreferredOutput() {
        val config = performanceConfig(automaticPhysics = true)
        var state = RuntimePerformanceStateMachine.initialState(config)
        repeat(4) {
            state = RuntimePerformanceStateMachine.transition(
                state,
                RuntimePerformanceEvent.WindowMeasured(0.3f),
            )
        }
        assertTrue(state.snapshot.activeBallCount < 100)

        state = RuntimePerformanceStateMachine.transition(
            state,
            RuntimePerformanceEvent.ConfigurationChanged(config, settingsReset = true),
        )

        assertEquals(100, state.snapshot.activeBallCount)
        assertEquals(RenderQuality.Glow, state.snapshot.renderQuality)
        assertTrue(state.snapshot.solidBodyPhysicsAllowed)
        assertEquals(RuntimePerformancePhase.SETTINGS_GRACE, state.snapshot.phase)
    }

    @Test
    fun collisionStatesProgressFromPauseThroughProbeToCooldown() {
        val config = performanceConfig(automaticPhysics = true)
        var state = RuntimePerformanceStateMachine.initialState(config)

        state = RuntimePerformanceStateMachine.transition(
            state,
            RuntimePerformanceEvent.WindowMeasured(0.6f),
        )
        assertTrue((state as PerformanceThrottleState.Adaptive).physicsState is PhysicsThrottleState.Suspended)
        assertFalse(state.snapshot.solidBodyPhysicsAllowed)

        repeat(12) {
            state = RuntimePerformanceStateMachine.transition(
                state,
                RuntimePerformanceEvent.WindowMeasured(0.05f),
            )
        }
        assertTrue((state as PerformanceThrottleState.Adaptive).physicsState is PhysicsThrottleState.Probe)
        assertTrue(state.snapshot.solidBodyPhysicsAllowed)

        repeat(3) {
            state = RuntimePerformanceStateMachine.transition(
                state,
                RuntimePerformanceEvent.WindowMeasured(0.6f),
            )
        }
        assertTrue((state as PerformanceThrottleState.Adaptive).physicsState is PhysicsThrottleState.Cooldown)
        assertEquals(RuntimePerformancePhase.PHYSICS_COOLDOWN, state.snapshot.phase)
        assertTrue(state.snapshot.solidBodyPhysicsAllowed)
    }

    @Test
    fun conservativeRecoveryWaitsForFullHistoryAndDwell() {
        val config = performanceConfig()
        var state = RuntimePerformanceStateMachine.initialState(config)
        repeat(16) {
            state = RuntimePerformanceStateMachine.transition(
                state,
                RuntimePerformanceEvent.WindowMeasured(0.3f),
            )
        }
        assertEquals(12, state.snapshot.activeBallCount)

        repeat(11) {
            state = RuntimePerformanceStateMachine.transition(
                state,
                RuntimePerformanceEvent.WindowMeasured(0f),
            )
        }
        assertEquals(12, state.snapshot.activeBallCount)

        state = RuntimePerformanceStateMachine.transition(
            state,
            RuntimePerformanceEvent.WindowMeasured(0f),
        )
        assertEquals(15, state.snapshot.activeBallCount)
        assertEquals(RuntimePerformancePhase.RESTORING, state.snapshot.phase)
    }

    private fun performanceConfig(
        configuredBallCount: Int = 100,
        adaptive: Boolean = true,
        preferredQuality: RenderQuality = RenderQuality.Glow,
        automaticStyle: Boolean = false,
        automaticPhysics: Boolean = false,
    ): RuntimePerformanceConfig = RuntimePerformanceConfig(
        configuredBallCount = configuredBallCount,
        adaptivePerformanceEnabled = adaptive,
        preferredRenderQuality = preferredQuality,
        automaticStyleChanges = automaticStyle,
        automaticPhysicsReduction = automaticPhysics,
    )

    private fun performanceController(
        configuredBallCount: Int = 100,
        deviceMaxBallCount: Int = 100,
        adaptive: Boolean = true,
        preferredQuality: RenderQuality = RenderQuality.Glow,
        automaticStyle: Boolean = false,
        automaticPhysics: Boolean = false,
    ): RuntimePerformanceController = RuntimePerformanceController(
        deviceMaxBallCount = deviceMaxBallCount,
        initialConfig = performanceConfig(
            configuredBallCount = configuredBallCount,
            adaptive = adaptive,
            preferredQuality = preferredQuality,
            automaticStyle = automaticStyle,
            automaticPhysics = automaticPhysics,
        ),
    )
}
