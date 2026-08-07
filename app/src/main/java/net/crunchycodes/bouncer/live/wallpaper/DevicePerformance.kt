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

internal enum class RuntimePerformancePhase(val id: String) {
    FIXED("fixed"),
    OBSERVING("observing"),
    REDUCING("reducing"),
    STABLE("stable"),
    RESTORING("restoring"),
    SETTINGS_GRACE("settings_grace"),
    PHYSICS_PAUSED("physics_paused"),
    TESTING_PHYSICS("testing_physics"),
    PHYSICS_COOLDOWN("physics_cooldown"),
    ;

    companion object {
        private val byId = entries.associateBy(RuntimePerformancePhase::id)

        fun fromStoredValue(value: String?): RuntimePerformancePhase =
            byId[value] ?: OBSERVING
    }
}

internal data class RuntimePerformanceConfig(
    val configuredBallCount: Int,
    val adaptivePerformanceEnabled: Boolean,
    val preferredRenderQuality: RenderQuality,
    val automaticStyleChanges: Boolean,
    val automaticPhysicsReduction: Boolean,
)

internal data class RuntimePerformanceSnapshot(
    val activeBallCount: Int,
    val renderQuality: RenderQuality,
    val solidBodyPhysicsAllowed: Boolean,
    val phase: RuntimePerformancePhase,
)

internal sealed interface LoadThrottleState {
    data object Observing : LoadThrottleState
    data object Throttling : LoadThrottleState
    data class Stable(val consecutiveWindows: Int) : LoadThrottleState
    data class Recovering(val windowsUntilNextStep: Int) : LoadThrottleState
}

internal sealed interface PhysicsThrottleState {
    data object Enabled : PhysicsThrottleState
    data class SettingsGrace(val remainingWindows: Int) : PhysicsThrottleState
    data class Suspended(
        val elapsedWindows: Int,
        val consecutiveLightWindows: Int,
    ) : PhysicsThrottleState

    data class Probe(
        val remainingWindows: Int,
        val totalPressure: Float,
        val peakPressure: Float,
    ) : PhysicsThrottleState

    data class Cooldown(val remainingWindows: Int) : PhysicsThrottleState
}

internal sealed interface PerformanceThrottleState {
    val config: RuntimePerformanceConfig
    val snapshot: RuntimePerformanceSnapshot

    data class Fixed(
        override val config: RuntimePerformanceConfig,
        override val snapshot: RuntimePerformanceSnapshot,
    ) : PerformanceThrottleState

    data class Adaptive(
        override val config: RuntimePerformanceConfig,
        val loadState: LoadThrottleState,
        val physicsState: PhysicsThrottleState,
        val pressureHistory: List<Float>,
        override val snapshot: RuntimePerformanceSnapshot,
    ) : PerformanceThrottleState
}

internal sealed interface RuntimePerformanceEvent {
    data class ConfigurationChanged(
        val config: RuntimePerformanceConfig,
        val settingsReset: Boolean,
    ) : RuntimePerformanceEvent

    data class WindowMeasured(val pressure: Float) : RuntimePerformanceEvent
}

internal object RuntimePerformanceStateMachine {
    private const val MIN_ADAPTIVE_BALL_COUNT = 4
    private const val MAX_ADAPTIVE_BALL_FLOOR = 12
    private const val PERFORMANCE_HISTORY_WINDOWS = 8
    private const val MIN_HISTORY_FOR_ADJUSTMENT = 2
    private const val RECOVERY_STABLE_WINDOWS = 6
    private const val RECENCY_WEIGHT_MULTIPLIER = 1.35f
    private const val RECENT_PRESSURE_FLOOR = 0.8f
    private const val IMMEDIATE_REACTION_THRESHOLD = 0.35f
    private const val STABLE_PRESSURE_THRESHOLD = 0.015f
    private const val STABLE_ROLLING_PRESSURE_THRESHOLD = 0.02f
    private const val MILD_PRESSURE_THRESHOLD = 0.04f
    private const val MODERATE_PRESSURE_THRESHOLD = 0.10f
    private const val SEVERE_PRESSURE_THRESHOLD = 0.22f
    private const val PHYSICS_IMMEDIATE_SUSPEND_THRESHOLD = 0.45f
    private const val PHYSICS_SUSPEND_PRESSURE_THRESHOLD = 0.24f
    private const val PHYSICS_RECOVERY_PRESSURE_THRESHOLD = 0.04f
    private const val PHYSICS_ROLLING_RECOVERY_THRESHOLD = 0.08f
    private const val PHYSICS_RECOVERY_WINDOW_COUNT = 4
    private const val PHYSICS_MAX_SUSPENDED_WINDOWS = 12
    private const val PHYSICS_SETTINGS_GRACE_WINDOWS = 4
    private const val PHYSICS_PROBE_WINDOWS = 3
    private const val PHYSICS_PAUSE_COOLDOWN_WINDOWS = 60
    private const val AUTO_FLAT_PRESSURE_THRESHOLD = 0.20f
    private const val AUTO_GLOW_PRESSURE_THRESHOLD = 0.01f
    private const val AUTO_FLAT_BALL_FRACTION = 0.5f
    private const val AUTO_GLOW_BALL_FRACTION = 0.8f

    fun initialState(
        config: RuntimePerformanceConfig,
        settingsReset: Boolean = false,
    ): PerformanceThrottleState {
        if (!config.adaptivePerformanceEnabled) {
            return PerformanceThrottleState.Fixed(
                config = config,
                snapshot = RuntimePerformanceSnapshot(
                    activeBallCount = config.configuredBallCount,
                    renderQuality = config.preferredRenderQuality,
                    solidBodyPhysicsAllowed = true,
                    phase = RuntimePerformancePhase.FIXED,
                ),
            )
        }

        val physicsState = if (config.automaticPhysicsReduction && settingsReset) {
            PhysicsThrottleState.SettingsGrace(PHYSICS_SETTINGS_GRACE_WINDOWS)
        } else {
            PhysicsThrottleState.Enabled
        }
        return adaptiveState(
            config = config,
            loadState = LoadThrottleState.Observing,
            physicsState = physicsState,
            pressureHistory = emptyList(),
            activeBallCount = config.configuredBallCount,
            renderQuality = config.preferredRenderQuality,
        )
    }

    fun transition(
        state: PerformanceThrottleState,
        event: RuntimePerformanceEvent,
    ): PerformanceThrottleState = when (event) {
        is RuntimePerformanceEvent.ConfigurationChanged -> {
            if (state.config == event.config && !event.settingsReset) {
                state
            } else {
                initialState(event.config, event.settingsReset)
            }
        }

        is RuntimePerformanceEvent.WindowMeasured -> when (state) {
            is PerformanceThrottleState.Fixed -> state
            is PerformanceThrottleState.Adaptive -> transitionWindow(
                state,
                event.pressure.coerceIn(0f, 1f),
            )
        }
    }

    fun rollingPressure(history: List<Float>): Float {
        if (history.isEmpty()) return 0f

        var weight = 1f
        var weightedPressure = 0f
        var totalWeight = 0f
        history.forEach { pressure ->
            weightedPressure += pressure * weight
            totalWeight += weight
            weight *= RECENCY_WEIGHT_MULTIPLIER
        }
        return weightedPressure / totalWeight
    }

    private fun transitionWindow(
        state: PerformanceThrottleState.Adaptive,
        latestPressure: Float,
    ): PerformanceThrottleState.Adaptive {
        val history = (state.pressureHistory + latestPressure)
            .takeLast(PERFORMANCE_HISTORY_WINDOWS)
        val rollingPressure = rollingPressure(history)
        val responsivePressure = max(rollingPressure, latestPressure * RECENT_PRESSURE_FLOOR)
        val loadTransition = transitionLoad(
            config = state.config,
            currentState = state.loadState,
            currentBallCount = state.snapshot.activeBallCount,
            latestPressure = latestPressure,
            rollingPressure = rollingPressure,
            responsivePressure = responsivePressure,
            historySize = history.size,
        )
        val renderQuality = transitionRenderQuality(
            config = state.config,
            currentQuality = state.snapshot.renderQuality,
            activeBallCount = loadTransition.activeBallCount,
            latestPressure = latestPressure,
            rollingPressure = rollingPressure,
            historySize = history.size,
        )
        val physicsState = transitionPhysics(
            config = state.config,
            currentState = state.physicsState,
            latestPressure = latestPressure,
            responsivePressure = responsivePressure,
            historySize = history.size,
        )

        return adaptiveState(
            config = state.config,
            loadState = loadTransition.state,
            physicsState = physicsState,
            pressureHistory = history,
            activeBallCount = loadTransition.activeBallCount,
            renderQuality = renderQuality,
        )
    }

    private fun transitionLoad(
        config: RuntimePerformanceConfig,
        currentState: LoadThrottleState,
        currentBallCount: Int,
        latestPressure: Float,
        rollingPressure: Float,
        responsivePressure: Float,
        historySize: Int,
    ): LoadTransition {
        val canReact = historySize >= MIN_HISTORY_FOR_ADJUSTMENT ||
            latestPressure >= IMMEDIATE_REACTION_THRESHOLD
        if (canReact && latestPressure >= MILD_PRESSURE_THRESHOLD) {
            val reductionFraction = when {
                responsivePressure >= SEVERE_PRESSURE_THRESHOLD -> 0.20f
                responsivePressure >= MODERATE_PRESSURE_THRESHOLD -> 0.12f
                else -> 0.06f
            }
            val reductionStep = max(2, (currentBallCount * reductionFraction).roundToInt())
            return LoadTransition(
                state = LoadThrottleState.Throttling,
                activeBallCount = max(
                    adaptiveMinimumBallCount(config.configuredBallCount),
                    currentBallCount - reductionStep,
                ),
            )
        }

        val fullyStable = historySize == PERFORMANCE_HISTORY_WINDOWS &&
            latestPressure <= STABLE_PRESSURE_THRESHOLD &&
            rollingPressure <= STABLE_ROLLING_PRESSURE_THRESHOLD
        if (!fullyStable) {
            return LoadTransition(
                state = LoadThrottleState.Observing,
                activeBallCount = currentBallCount,
            )
        }
        if (currentBallCount >= config.configuredBallCount) {
            return LoadTransition(
                state = LoadThrottleState.Stable(RECOVERY_STABLE_WINDOWS),
                activeBallCount = config.configuredBallCount,
            )
        }

        return when (currentState) {
            is LoadThrottleState.Recovering -> {
                if (currentState.windowsUntilNextStep > 1) {
                    LoadTransition(
                        state = currentState.copy(
                            windowsUntilNextStep = currentState.windowsUntilNextStep - 1,
                        ),
                        activeBallCount = currentBallCount,
                    )
                } else {
                    LoadTransition(
                        state = LoadThrottleState.Recovering(RECOVERY_STABLE_WINDOWS),
                        activeBallCount = min(
                            config.configuredBallCount,
                            currentBallCount + recoveryStep(config.configuredBallCount),
                        ),
                    )
                }
            }

            is LoadThrottleState.Stable -> {
                val stableWindows = currentState.consecutiveWindows + 1
                if (stableWindows >= RECOVERY_STABLE_WINDOWS) {
                    LoadTransition(
                        state = LoadThrottleState.Recovering(RECOVERY_STABLE_WINDOWS),
                        activeBallCount = min(
                            config.configuredBallCount,
                            currentBallCount + recoveryStep(config.configuredBallCount),
                        ),
                    )
                } else {
                    LoadTransition(
                        state = LoadThrottleState.Stable(stableWindows),
                        activeBallCount = currentBallCount,
                    )
                }
            }

            else -> LoadTransition(
                state = LoadThrottleState.Stable(1),
                activeBallCount = currentBallCount,
            )
        }
    }

    private fun transitionPhysics(
        config: RuntimePerformanceConfig,
        currentState: PhysicsThrottleState,
        latestPressure: Float,
        responsivePressure: Float,
        historySize: Int,
    ): PhysicsThrottleState {
        if (!config.automaticPhysicsReduction) return PhysicsThrottleState.Enabled

        return when (currentState) {
            PhysicsThrottleState.Enabled -> {
                val sustainedHeavyLoad =
                    historySize >= MIN_HISTORY_FOR_ADJUSTMENT &&
                        responsivePressure >= PHYSICS_SUSPEND_PRESSURE_THRESHOLD
                if (
                    latestPressure >= PHYSICS_IMMEDIATE_SUSPEND_THRESHOLD ||
                    sustainedHeavyLoad
                ) {
                    PhysicsThrottleState.Suspended(
                        elapsedWindows = 0,
                        consecutiveLightWindows = 0,
                    )
                } else {
                    PhysicsThrottleState.Enabled
                }
            }

            is PhysicsThrottleState.SettingsGrace -> {
                if (currentState.remainingWindows > 1) {
                    currentState.copy(remainingWindows = currentState.remainingWindows - 1)
                } else {
                    PhysicsThrottleState.Enabled
                }
            }

            is PhysicsThrottleState.Suspended -> {
                val elapsedWindows = currentState.elapsedWindows + 1
                val lightWindows = if (latestPressure <= PHYSICS_RECOVERY_PRESSURE_THRESHOLD) {
                    currentState.consecutiveLightWindows + 1
                } else {
                    0
                }
                val readyForProbe =
                    elapsedWindows >= PHYSICS_MAX_SUSPENDED_WINDOWS ||
                        (
                            lightWindows >= PHYSICS_RECOVERY_WINDOW_COUNT &&
                                responsivePressure <= PHYSICS_ROLLING_RECOVERY_THRESHOLD
                            )
                if (readyForProbe) {
                    PhysicsThrottleState.Probe(
                        remainingWindows = PHYSICS_PROBE_WINDOWS,
                        totalPressure = 0f,
                        peakPressure = 0f,
                    )
                } else {
                    currentState.copy(
                        elapsedWindows = elapsedWindows,
                        consecutiveLightWindows = lightWindows,
                    )
                }
            }

            is PhysicsThrottleState.Probe -> {
                val totalPressure = currentState.totalPressure + latestPressure
                val peakPressure = max(currentState.peakPressure, latestPressure)
                if (currentState.remainingWindows > 1) {
                    currentState.copy(
                        remainingWindows = currentState.remainingWindows - 1,
                        totalPressure = totalPressure,
                        peakPressure = peakPressure,
                    )
                } else {
                    val averagePressure = totalPressure / PHYSICS_PROBE_WINDOWS.toFloat()
                    val probeFailed =
                        peakPressure >= PHYSICS_IMMEDIATE_SUSPEND_THRESHOLD ||
                            averagePressure >= PHYSICS_SUSPEND_PRESSURE_THRESHOLD
                    if (probeFailed) {
                        PhysicsThrottleState.Cooldown(PHYSICS_PAUSE_COOLDOWN_WINDOWS)
                    } else {
                        PhysicsThrottleState.Enabled
                    }
                }
            }

            is PhysicsThrottleState.Cooldown -> {
                if (currentState.remainingWindows > 1) {
                    currentState.copy(remainingWindows = currentState.remainingWindows - 1)
                } else {
                    PhysicsThrottleState.Enabled
                }
            }
        }
    }

    private fun transitionRenderQuality(
        config: RuntimePerformanceConfig,
        currentQuality: RenderQuality,
        activeBallCount: Int,
        latestPressure: Float,
        rollingPressure: Float,
        historySize: Int,
    ): RenderQuality {
        if (
            !config.automaticStyleChanges ||
            config.preferredRenderQuality != RenderQuality.Glow ||
            historySize < PERFORMANCE_HISTORY_WINDOWS
        ) {
            return config.preferredRenderQuality
        }

        val flatSwitchBallCount = max(
            adaptiveMinimumBallCount(config.configuredBallCount),
            (config.configuredBallCount * AUTO_FLAT_BALL_FRACTION).roundToInt(),
        )
        if (
            currentQuality == RenderQuality.Glow &&
            latestPressure >= MODERATE_PRESSURE_THRESHOLD &&
            rollingPressure >= AUTO_FLAT_PRESSURE_THRESHOLD &&
            activeBallCount <= flatSwitchBallCount
        ) {
            return RenderQuality.Flat
        }
        if (
            currentQuality == RenderQuality.Flat &&
            rollingPressure <= AUTO_GLOW_PRESSURE_THRESHOLD &&
            activeBallCount >= (config.configuredBallCount * AUTO_GLOW_BALL_FRACTION).roundToInt()
        ) {
            return RenderQuality.Glow
        }
        return currentQuality
    }

    private fun adaptiveState(
        config: RuntimePerformanceConfig,
        loadState: LoadThrottleState,
        physicsState: PhysicsThrottleState,
        pressureHistory: List<Float>,
        activeBallCount: Int,
        renderQuality: RenderQuality,
    ): PerformanceThrottleState.Adaptive {
        val phase = when (physicsState) {
            is PhysicsThrottleState.SettingsGrace -> RuntimePerformancePhase.SETTINGS_GRACE
            is PhysicsThrottleState.Suspended -> RuntimePerformancePhase.PHYSICS_PAUSED
            is PhysicsThrottleState.Probe -> RuntimePerformancePhase.TESTING_PHYSICS
            is PhysicsThrottleState.Cooldown -> RuntimePerformancePhase.PHYSICS_COOLDOWN
            PhysicsThrottleState.Enabled -> when (loadState) {
                LoadThrottleState.Observing -> RuntimePerformancePhase.OBSERVING
                LoadThrottleState.Throttling -> RuntimePerformancePhase.REDUCING
                is LoadThrottleState.Stable -> RuntimePerformancePhase.STABLE
                is LoadThrottleState.Recovering -> RuntimePerformancePhase.RESTORING
            }
        }
        return PerformanceThrottleState.Adaptive(
            config = config,
            loadState = loadState,
            physicsState = physicsState,
            pressureHistory = pressureHistory,
            snapshot = RuntimePerformanceSnapshot(
                activeBallCount = activeBallCount,
                renderQuality = renderQuality,
                solidBodyPhysicsAllowed = physicsState !is PhysicsThrottleState.Suspended,
                phase = phase,
            ),
        )
    }

    private fun adaptiveMinimumBallCount(configuredBallCount: Int): Int =
        min(
            configuredBallCount,
            max(
                MIN_ADAPTIVE_BALL_COUNT,
                min(MAX_ADAPTIVE_BALL_FLOOR, (configuredBallCount * 0.15f).roundToInt()),
            ),
        )

    private fun recoveryStep(configuredBallCount: Int): Int =
        max(1, (configuredBallCount * 0.03f).roundToInt())

    private data class LoadTransition(
        val state: LoadThrottleState,
        val activeBallCount: Int,
    )
}

internal class RuntimePerformanceController(
    private val deviceMaxBallCount: Int,
    initialConfig: RuntimePerformanceConfig,
) {
    private var config = normalize(initialConfig)
    private var state: PerformanceThrottleState =
        RuntimePerformanceStateMachine.initialState(config)
    private var frameCounter = 0
    private var badFrames = 0
    private var totalFrameDurationNanos = 0.0

    constructor(
        configuredBallCount: Int,
        deviceMaxBallCount: Int,
        initialRenderQuality: RenderQuality = DevicePerformance.renderQuality(deviceMaxBallCount),
    ) : this(
        deviceMaxBallCount = deviceMaxBallCount,
        initialConfig = RuntimePerformanceConfig(
            configuredBallCount = configuredBallCount,
            adaptivePerformanceEnabled = true,
            preferredRenderQuality = initialRenderQuality,
            automaticStyleChanges = false,
            automaticPhysicsReduction = false,
        ),
    )

    fun snapshot(): RuntimePerformanceSnapshot = state.snapshot

    fun updateConfiguration(
        value: RuntimePerformanceConfig,
        settingsReset: Boolean = false,
    ) {
        val normalized = normalize(value)
        val configurationChanged = normalized != config
        state = RuntimePerformanceStateMachine.transition(
            state,
            RuntimePerformanceEvent.ConfigurationChanged(
                config = normalized,
                settingsReset = settingsReset,
            ),
        )
        config = normalized
        if (settingsReset || configurationChanged) resetFrameAccumulator()
    }

    fun recordFrame(frameDurationNanos: Long, frameBudgetNanos: Long): RuntimePerformanceSnapshot {
        if (state is PerformanceThrottleState.Fixed) return state.snapshot

        val badFrameThreshold = (frameBudgetNanos * 1.1f).roundToInt().toLong()
        frameCounter++
        totalFrameDurationNanos += frameDurationNanos.coerceAtLeast(0L).toDouble()
        if (frameDurationNanos > badFrameThreshold) badFrames++

        if (frameCounter >= DevicePerformance.RUNTIME_WINDOW_FRAMES) {
            val badRatio = badFrames.toFloat() / frameCounter.toFloat()
            val averageFrameDuration = totalFrameDurationNanos / frameCounter.toDouble()
            val averageOverrun =
                (averageFrameDuration / frameBudgetNanos.coerceAtLeast(1L) - 1.0)
                    .toFloat()
                    .coerceAtLeast(0f)
            val pressure = max(badRatio * BAD_FRAME_RATIO_WEIGHT, averageOverrun)
                .coerceIn(0f, 1f)
            state = RuntimePerformanceStateMachine.transition(
                state,
                RuntimePerformanceEvent.WindowMeasured(pressure),
            )
            resetFrameAccumulator()
        }
        return state.snapshot
    }

    internal fun rollingPerformancePressure(): Float =
        (state as? PerformanceThrottleState.Adaptive)
            ?.let { RuntimePerformanceStateMachine.rollingPressure(it.pressureHistory) }
            ?: 0f

    private fun normalize(value: RuntimePerformanceConfig): RuntimePerformanceConfig =
        value.copy(
            configuredBallCount = value.configuredBallCount.coerceIn(
                BouncerPhysics.MIN_BALL_COUNT,
                deviceMaxBallCount,
            ),
        )

    private fun resetFrameAccumulator() {
        frameCounter = 0
        badFrames = 0
        totalFrameDurationNanos = 0.0
    }

    private companion object {
        const val BAD_FRAME_RATIO_WEIGHT = 0.2f
    }
}
