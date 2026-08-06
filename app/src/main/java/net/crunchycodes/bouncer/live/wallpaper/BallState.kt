package net.crunchycodes.bouncer.live.wallpaper

import android.graphics.ColorFilter

internal class BallState(
    var x: Float,
    var y: Float,
    var dx: Float,
    var dy: Float,
    var radius: Float,
    var initialRadius: Float,
    var color: Int,
    var startTime: Long,
    var expiryTime: Long,
    var sizeVariability: Float,
    var alpha: Float = 1f,
    var id: Long = 0L,
) {
    // Mass and cached tint are derived at runtime because radius and draw state change over time.
    var mass: Float = 1f
    var colorFilter: ColorFilter? = null
    var retiring: Boolean = false
    var retireStartTime: Long = 0L
    var retireDurationMillis: Long = 0L

    fun prepareForReuse(
        x: Float,
        y: Float,
        dx: Float,
        dy: Float,
        radius: Float,
        initialRadius: Float,
        color: Int,
        startTime: Long,
        expiryTime: Long,
        sizeVariability: Float,
        id: Long,
    ) {
        this.x = x
        this.y = y
        this.dx = dx
        this.dy = dy
        this.radius = radius
        this.initialRadius = initialRadius
        this.color = color
        this.startTime = startTime
        this.expiryTime = expiryTime
        this.sizeVariability = sizeVariability
        this.alpha = 1f
        this.id = id
        mass = radius * radius
        colorFilter = null
        retiring = false
        retireStartTime = 0L
        retireDurationMillis = 0L
    }

    fun shiftTimeline(pausedDurationMillis: Long) {
        if (pausedDurationMillis <= 0L) return
        startTime += pausedDurationMillis
        expiryTime += pausedDurationMillis
        if (retiring) {
            retireStartTime += pausedDurationMillis
        }
    }
}

internal interface RandomSource {
    fun nextFloat(): Float
    fun nextInt(until: Int): Int
}
