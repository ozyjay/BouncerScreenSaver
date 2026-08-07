package net.crunchycodes.bouncer.live.wallpaper

import androidx.annotation.StringRes

internal enum class PerformanceMode(
    val id: String,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
) {
    ADAPTIVE("adaptive", R.string.performance_mode_adaptive, R.string.performance_mode_adaptive_description),
    FIXED("fixed", R.string.performance_mode_fixed, R.string.performance_mode_fixed_description),
    ;

    companion object {
        private val byId = entries.associateBy(PerformanceMode::id)

        fun fromStoredValue(value: String?): PerformanceMode = byId[value] ?: ADAPTIVE
    }
}
