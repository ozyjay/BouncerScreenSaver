package net.crunchycodes.bouncer.live.wallpaper

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.math.min

internal class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "bouncer_prefs"
        const val KEY_BALL_COUNT = "ball_count"
        const val KEY_BALL_SPEED = "ball_speed"
        const val KEY_PALETTE = "palette"
        const val KEY_BRIGHTNESS = "brightness"
        const val KEY_TRANSPARENCY = "transparency"
        const val KEY_BALL_STYLE = "ball_style"
        const val KEY_PERFORMANCE_MODE = "performance_mode"
        const val KEY_PHYSICS = "physics_enabled"
        const val KEY_AUTO_DISABLE_PHYSICS = "auto_disable_physics_on_heavy_load"
        private const val KEY_RUNTIME_BALL_COUNT = "runtime_ball_count"
        private const val KEY_RUNTIME_RENDER_QUALITY = "runtime_render_quality"
        private const val KEY_RUNTIME_PHYSICS_SUSPENDED = "runtime_physics_suspended"
        const val KEY_SIZE_BEHAVIOR = "size_behavior"
        const val KEY_LIFESPAN = "lifespan_base"
        const val KEY_DESTROY_ON_TOUCH = "destroy_on_touch"
        const val KEY_HAS_COMPLETED_CALIBRATION = "has_completed_calibration"
        const val KEY_CALIBRATION_REFRESH_RATE = "calibration_refresh_rate"
        const val KEY_DEVICE_MAX_BALL_COUNT = "device_max_ball_count"
        const val KEY_RECOMMENDED_BALL_COUNT = "recommended_ball_count"
        const val KEY_DEVICE_MAX_BALL_SPEED = "device_max_ball_speed"
        const val KEY_RECOMMENDED_BALL_SPEED = "recommended_ball_speed"
    }

    // Clamp again at the storage boundary so invalid persisted values cannot destabilize
    // the renderer even if they came from an older build or external tooling.
    var ballCount: Int
        get() = clampBallCountForDevice(
            prefs.getInt(KEY_BALL_COUNT, defaultConfiguredBallCount()),
        )
        set(value) = prefs.edit {
            putInt(KEY_BALL_COUNT, clampBallCountForDevice(value))
        }

    var ballSpeed: Float
        get() = clampBallSpeedForDevice(
            prefs.getFloat(KEY_BALL_SPEED, BouncerPhysics.DEFAULT_BALL_SPEED),
        )
        set(value) = prefs.edit { putFloat(KEY_BALL_SPEED, clampBallSpeedForDevice(value)) }

    var palette: ColorPalette
        get() = ColorPalette.fromStoredValue(prefs.getString(KEY_PALETTE, ColorPalette.RANDOM.id))
        set(value) = prefs.edit { putString(KEY_PALETTE, value.id) }

    var brightness: Float
        get() = BallAppearance.clampBrightness(
            prefs.getFloat(KEY_BRIGHTNESS, BallAppearance.DEFAULT_BRIGHTNESS),
        )
        set(value) = prefs.edit {
            putFloat(KEY_BRIGHTNESS, BallAppearance.clampBrightness(value))
        }

    var transparency: Float
        get() = BallAppearance.clampTransparency(
            prefs.getFloat(KEY_TRANSPARENCY, BallAppearance.DEFAULT_TRANSPARENCY),
        )
        set(value) = prefs.edit {
            putFloat(KEY_TRANSPARENCY, BallAppearance.clampTransparency(value))
        }

    var ballStyle: BallStyle
        get() = BallStyle.fromStoredValue(prefs.getString(KEY_BALL_STYLE, BallStyle.AUTO.id))
        set(value) = prefs.edit { putString(KEY_BALL_STYLE, value.id) }

    var performanceMode: PerformanceMode
        get() = PerformanceMode.fromStoredValue(
            prefs.getString(KEY_PERFORMANCE_MODE, PerformanceMode.ADAPTIVE.id),
        )
        set(value) = prefs.edit { putString(KEY_PERFORMANCE_MODE, value.id) }

    var physicsEnabled: Boolean
        get() = prefs.getBoolean(KEY_PHYSICS, true)
        set(value) = prefs.edit { putBoolean(KEY_PHYSICS, value) }

    var autoDisablePhysicsOnHeavyLoad: Boolean
        get() = prefs.getBoolean(KEY_AUTO_DISABLE_PHYSICS, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_DISABLE_PHYSICS, value) }

    val runtimeBallCount: Int
        get() = prefs.getInt(KEY_RUNTIME_BALL_COUNT, ballCount)
            .coerceIn(BouncerPhysics.MIN_BALL_COUNT, effectiveMaxBallCount())

    val runtimeRenderQuality: RenderQuality
        get() = prefs.getString(KEY_RUNTIME_RENDER_QUALITY, null)
            ?.let { stored -> RenderQuality.entries.firstOrNull { it.name == stored } }
            ?: DevicePerformance.renderQuality(effectiveMaxBallCount(), ballStyle)

    val runtimePhysicsSuspended: Boolean
        get() = prefs.getBoolean(KEY_RUNTIME_PHYSICS_SUSPENDED, false)

    fun persistRuntimePerformanceState(
        ballCount: Int,
        renderQuality: RenderQuality,
        physicsSuspended: Boolean,
    ) {
        prefs.edit {
            putInt(
                KEY_RUNTIME_BALL_COUNT,
                ballCount.coerceIn(BouncerPhysics.MIN_BALL_COUNT, effectiveMaxBallCount()),
            )
            putString(KEY_RUNTIME_RENDER_QUALITY, renderQuality.name)
            putBoolean(KEY_RUNTIME_PHYSICS_SUSPENDED, physicsSuspended)
        }
    }

    var sizeBehavior: Float
        get() = BouncerPhysics.clampSizeBehavior(
            prefs.getFloat(KEY_SIZE_BEHAVIOR, BouncerPhysics.DEFAULT_SIZE_BEHAVIOR),
        )
        set(value) = prefs.edit {
            putFloat(KEY_SIZE_BEHAVIOR, BouncerPhysics.clampSizeBehavior(value))
        }

    var lifespanBase: Float
        get() = BouncerPhysics.clampLifespanSeconds(
            prefs.getFloat(KEY_LIFESPAN, BouncerPhysics.DEFAULT_LIFESPAN_SECONDS),
        )
        set(value) = prefs.edit {
            putFloat(KEY_LIFESPAN, BouncerPhysics.clampLifespanSeconds(value))
        }

    var destroyOnTouch: Boolean
        get() = prefs.getBoolean(KEY_DESTROY_ON_TOUCH, false)
        set(value) = prefs.edit { putBoolean(KEY_DESTROY_ON_TOUCH, value) }

    var hasCompletedCalibration: Boolean
        get() = prefs.getBoolean(KEY_HAS_COMPLETED_CALIBRATION, false)
        set(value) = prefs.edit { putBoolean(KEY_HAS_COMPLETED_CALIBRATION, value) }

    var calibrationRefreshRateHz: Float
        get() = DevicePerformance.normalizeRefreshRateHz(
            prefs.getFloat(KEY_CALIBRATION_REFRESH_RATE, DevicePerformance.FALLBACK_REFRESH_RATE_HZ),
        )
        set(value) = prefs.edit {
            putFloat(KEY_CALIBRATION_REFRESH_RATE, DevicePerformance.normalizeRefreshRateHz(value))
        }

    var recommendedBallCount: Int
        get() = clampBallCountForDevice(
            prefs.getInt(
                KEY_RECOMMENDED_BALL_COUNT,
                DevicePerformance.recommendedBallCount(deviceBallCountCeiling()),
            ),
        )
        set(value) = prefs.edit {
            putInt(KEY_RECOMMENDED_BALL_COUNT, clampBallCountForDevice(value))
        }

    var deviceMaxBallSpeed: Float
        get() = prefs.getFloat(
            KEY_DEVICE_MAX_BALL_SPEED,
            DevicePerformance.deviceMaxBallSpeed(deviceBallCountCeiling()),
        ).coerceIn(BouncerPhysics.MIN_BALL_SPEED, BouncerPhysics.MAX_BALL_SPEED)
        set(value) = prefs.edit {
            putFloat(
                KEY_DEVICE_MAX_BALL_SPEED,
                value.coerceIn(BouncerPhysics.MIN_BALL_SPEED, BouncerPhysics.MAX_BALL_SPEED),
            )
        }

    fun effectiveMaxBallCount(): Int = deviceBallCountCeiling()
    fun effectiveMaxBallSpeed(): Float = deviceMaxBallSpeed

    fun persistCalibrationResult(result: DeviceCalibrationResult) {
        val deviceCap = result.deviceMaxBallCount
            .coerceIn(BouncerPhysics.MIN_BALL_COUNT, BouncerPhysics.MAX_BALL_COUNT)
        val recommended = result.recommendedBallCount
            .coerceIn(BouncerPhysics.MIN_BALL_COUNT, deviceCap)
        val deviceSpeedCap = result.deviceMaxBallSpeed
            .coerceIn(BouncerPhysics.MIN_BALL_SPEED, BouncerPhysics.MAX_BALL_SPEED)
        val recommendedSpeed = result.recommendedBallSpeed
            .coerceIn(BouncerPhysics.MIN_BALL_SPEED, deviceSpeedCap)
        val existingBallCount = prefs.getInt(KEY_BALL_COUNT, recommended)
        val existingBallSpeed = prefs.getFloat(KEY_BALL_SPEED, recommendedSpeed)
        prefs.edit {
            putBoolean(KEY_HAS_COMPLETED_CALIBRATION, true)
            putFloat(KEY_CALIBRATION_REFRESH_RATE, DevicePerformance.normalizeRefreshRateHz(result.refreshRateHz))
            putInt(KEY_DEVICE_MAX_BALL_COUNT, deviceCap)
            putInt(KEY_RECOMMENDED_BALL_COUNT, recommended)
            putFloat(KEY_DEVICE_MAX_BALL_SPEED, deviceSpeedCap)
            putFloat(KEY_RECOMMENDED_BALL_SPEED, recommendedSpeed)
            putInt(KEY_BALL_COUNT, existingBallCount.coerceIn(BouncerPhysics.MIN_BALL_COUNT, deviceCap))
            putFloat(KEY_BALL_SPEED, existingBallSpeed.coerceIn(BouncerPhysics.MIN_BALL_SPEED, deviceSpeedCap))
        }
    }

    fun resetCalibration() {
        prefs.edit {
            putBoolean(KEY_HAS_COMPLETED_CALIBRATION, false)
            remove(KEY_CALIBRATION_REFRESH_RATE)
            putInt(KEY_DEVICE_MAX_BALL_COUNT, DevicePerformance.fallbackMaxBallCount())
            putInt(
                KEY_RECOMMENDED_BALL_COUNT,
                DevicePerformance.recommendedBallCount(DevicePerformance.fallbackMaxBallCount()),
            )
            putFloat(
                KEY_DEVICE_MAX_BALL_SPEED,
                DevicePerformance.deviceMaxBallSpeed(DevicePerformance.fallbackMaxBallCount()),
            )
            putFloat(
                KEY_RECOMMENDED_BALL_SPEED,
                DevicePerformance.recommendedBallSpeed(DevicePerformance.fallbackMaxBallCount()),
            )
            putInt(
                KEY_BALL_COUNT,
                min(
                    clampBallCountForDevice(ballCount),
                    DevicePerformance.recommendedBallCount(DevicePerformance.fallbackMaxBallCount()),
                ),
            )
            putFloat(
                KEY_BALL_SPEED,
                clampBallSpeedForDevice(ballSpeed).coerceAtMost(
                    DevicePerformance.recommendedBallSpeed(DevicePerformance.fallbackMaxBallCount()),
                ),
            )
        }
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun deviceBallCountCeiling(): Int {
        val persistedCap = prefs.getInt(KEY_DEVICE_MAX_BALL_COUNT, DevicePerformance.fallbackMaxBallCount())
        return persistedCap.coerceIn(BouncerPhysics.MIN_BALL_COUNT, BouncerPhysics.MAX_BALL_COUNT)
    }

    private fun defaultConfiguredBallCount(): Int =
        if (hasCompletedCalibration) recommendedBallCount else DevicePerformance.fallbackMaxBallCount()

    private fun clampBallCountForDevice(value: Int): Int =
        value.coerceIn(BouncerPhysics.MIN_BALL_COUNT, deviceBallCountCeiling())

    private fun clampBallSpeedForDevice(value: Float): Float =
        value.coerceIn(BouncerPhysics.MIN_BALL_SPEED, deviceMaxBallSpeed)
}
