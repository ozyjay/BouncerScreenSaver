package net.crunchycodes.bouncer.live.wallpaper

import android.graphics.ColorFilter

internal data class BallState(
    var x: Float,
    var y: Float,
    var dx: Float,
    var dy: Float,
    var radius: Float,
    val initialRadius: Float,
    val color: Int,
    val startTime: Long,
    val expiryTime: Long,
    val sizeVariability: Float,
    var alpha: Float = 1f,
    val id: Long = 0L,
) {
    var mass: Float = 1f
    var colorFilter: ColorFilter? = null
}

internal interface RandomSource {
    fun nextFloat(): Float
    fun nextInt(until: Int): Int
}
