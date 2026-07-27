package net.crunchycodes.bouncer.live.wallpaper

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    var ballCount: Int
        get() = prefs.getInt(KEY_BALL_COUNT, 50)
        set(value) = prefs.edit { putInt(KEY_BALL_COUNT, value) }

    var ballSpeed: Float
        get() = prefs.getFloat(KEY_BALL_SPEED, 5f)
        set(value) = prefs.edit { putFloat(KEY_BALL_SPEED, value) }

    var palette: String
        get() = prefs.getString(KEY_PALETTE, "Random") ?: "Random"
        set(value) = prefs.edit { putString(KEY_PALETTE, value) }

    var physicsEnabled: Boolean
        get() = prefs.getBoolean(KEY_PHYSICS, true)
        set(value) = prefs.edit { putBoolean(KEY_PHYSICS, value) }

    var sizeBehavior: Float
        get() = prefs.getFloat(KEY_SIZE_BEHAVIOR, -0.5f)
        set(value) = prefs.edit { putFloat(KEY_SIZE_BEHAVIOR, value) }

    var lifespanBase: Float
        get() = prefs.getFloat(KEY_LIFESPAN, 15f)
        set(value) = prefs.edit { putFloat(KEY_LIFESPAN, value) }

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
