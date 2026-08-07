package net.crunchycodes.bouncer.live.wallpaper

import kotlin.math.roundToInt

internal object BallAppearance {
    const val MIN_BRIGHTNESS = 0.25f
    const val MAX_BRIGHTNESS = 1.5f
    const val DEFAULT_BRIGHTNESS = 1f
    const val MIN_TRANSPARENCY = 0f
    const val MAX_TRANSPARENCY = 1f
    const val DEFAULT_TRANSPARENCY = 0f

    fun clampBrightness(value: Float): Float =
        value.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS)

    fun clampTransparency(value: Float): Float =
        value.coerceIn(MIN_TRANSPARENCY, MAX_TRANSPARENCY)

    fun adjustBrightness(color: Int, brightness: Float): Int {
        val multiplier = clampBrightness(brightness)
        val alpha = color ushr 24 and 0xff
        val red = ((color ushr 16 and 0xff) * multiplier).roundToInt().coerceIn(0, 255)
        val green = ((color ushr 8 and 0xff) * multiplier).roundToInt().coerceIn(0, 255)
        val blue = ((color and 0xff) * multiplier).roundToInt().coerceIn(0, 255)
        return alpha shl 24 or (red shl 16) or (green shl 8) or blue
    }

    fun opacityForTransparency(transparency: Float): Float =
        1f - clampTransparency(transparency)
}
