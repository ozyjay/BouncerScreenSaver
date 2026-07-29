package net.crunchycodes.bouncer.live.wallpaper

import android.graphics.Color
import androidx.annotation.StringRes
import androidx.core.graphics.toColorInt

enum class ColorPalette(
    val id: String,
    @StringRes val labelRes: Int,
    val colors: IntArray?,
) {
    RANDOM("random", R.string.palette_random, null),
    NEON(
        "neon",
        R.string.palette_neon,
        intArrayOf(
            "#FF00FF".toColorInt(),
            "#00FFFF".toColorInt(),
            "#00FF00".toColorInt(),
            "#FFFF00".toColorInt(),
            "#FF0000".toColorInt(),
        ),
    ),
    OCEAN(
        "ocean",
        R.string.palette_ocean,
        intArrayOf(
            "#0077be".toColorInt(),
            "#00a3cc".toColorInt(),
            "#00d9e8".toColorInt(),
            "#005f6b".toColorInt(),
            "#add8e6".toColorInt(),
        ),
    ),
    FIRE(
        "fire",
        R.string.palette_fire,
        intArrayOf(
            "#e25822".toColorInt(),
            "#f28c28".toColorInt(),
            "#ff4500".toColorInt(),
            "#ffd700".toColorInt(),
            "#800000".toColorInt(),
        ),
    ),
    PASTEL(
        "pastel",
        R.string.palette_pastel,
        intArrayOf(
            "#ffb7ce".toColorInt(),
            "#c5e0b4".toColorInt(),
            "#b4c6e7".toColorInt(),
            "#f8cbad".toColorInt(),
            "#d9d9d9".toColorInt(),
        ),
    ),
    FOREST(
        "forest",
        R.string.palette_forest,
        intArrayOf(
            "#228b22".toColorInt(),
            "#006400".toColorInt(),
            "#8b4513".toColorInt(),
            "#556b2f".toColorInt(),
            "#9acd32".toColorInt(),
        ),
    );

    internal fun randomColor(randomSource: RandomSource): Int {
        val paletteColors = colors ?: return Color.argb(
            255,
            randomSource.nextInt(256),
            randomSource.nextInt(256),
            randomSource.nextInt(256),
        )
        return paletteColors[randomSource.nextInt(paletteColors.size)]
    }

    companion object {
        private val byId = entries.associateBy(ColorPalette::id)

        fun fromStoredValue(value: String?): ColorPalette = when (value?.trim()) {
            null, "", "Random", "random" -> RANDOM
            "Neon", "neon" -> NEON
            "Ocean", "ocean" -> OCEAN
            "Fire", "fire" -> FIRE
            "Pastel", "pastel" -> PASTEL
            "Forest", "forest" -> FOREST
            else -> byId[value.lowercase()] ?: RANDOM
        }
    }
}
