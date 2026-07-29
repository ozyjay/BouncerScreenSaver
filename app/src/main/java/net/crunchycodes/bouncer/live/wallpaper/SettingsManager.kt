package net.crunchycodes.bouncer.live.wallpaper

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "bouncer_prefs"
        const val KEY_BALL_COUNT = "ball_count"
        const val KEY_BALL_SPEED = "ball_speed"
        const val KEY_PALETTE = "palette"
        const val KEY_PHYSICS = "physics_enabled"
        const val KEY_SIZE_BEHAVIOR = "size_behavior"
        const val KEY_LIFESPAN = "lifespan_base"
        const val KEY_DESTROY_ON_TOUCH = "destroy_on_touch"
    }

    // Clamp again at the storage boundary so invalid persisted values cannot destabilize
    // the renderer even if they came from an older build or external tooling.
    var ballCount: Int
        get() = BouncerPhysics.clampBallCount(
            prefs.getInt(KEY_BALL_COUNT, BouncerPhysics.DEFAULT_BALL_COUNT),
        )
        set(value) = prefs.edit { putInt(KEY_BALL_COUNT, BouncerPhysics.clampBallCount(value)) }

    var ballSpeed: Float
        get() = BouncerPhysics.clampBallSpeed(
            prefs.getFloat(KEY_BALL_SPEED, BouncerPhysics.DEFAULT_BALL_SPEED),
        )
        set(value) = prefs.edit { putFloat(KEY_BALL_SPEED, BouncerPhysics.clampBallSpeed(value)) }

    var palette: ColorPalette
        get() = ColorPalette.fromStoredValue(prefs.getString(KEY_PALETTE, ColorPalette.RANDOM.id))
        set(value) = prefs.edit { putString(KEY_PALETTE, value.id) }

    var physicsEnabled: Boolean
        get() = prefs.getBoolean(KEY_PHYSICS, true)
        set(value) = prefs.edit { putBoolean(KEY_PHYSICS, value) }

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

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
