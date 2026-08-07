package net.crunchycodes.bouncer.live.wallpaper

import androidx.annotation.StringRes

internal enum class BallStyle(
    val id: String,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
) {
    AUTO("auto", R.string.ball_style_auto, R.string.ball_style_auto_description),
    GLOW("glow", R.string.ball_style_glow, R.string.ball_style_glow_description),
    FLAT("flat", R.string.ball_style_flat, R.string.ball_style_flat_description),
    ;

    companion object {
        private val byId = entries.associateBy(BallStyle::id)

        fun fromStoredValue(value: String?): BallStyle =
            byId[value?.trim()?.lowercase()] ?: AUTO
    }
}
