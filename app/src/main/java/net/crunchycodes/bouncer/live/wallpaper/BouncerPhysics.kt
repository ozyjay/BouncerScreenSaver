package net.crunchycodes.bouncer.live.wallpaper

import kotlin.math.max
import kotlin.math.min

internal object BouncerPhysics {
    const val MIN_COLLISION_CELL_SIZE = 100f

    fun radiusForSurface(
        initialRadius: Float,
        sizeBehavior: Float,
        sizeVariability: Float,
        lifeRatio: Float,
        width: Int,
        height: Int,
    ): Float {
        val maxRadius = min(width, height).coerceAtLeast(0) / 2f
        if (maxRadius <= 0f) return 0f

        val effectiveSizeBehavior = sizeBehavior * sizeVariability
        val rawRadius = initialRadius * (
            1f + effectiveSizeBehavior * (1f - lifeRatio.coerceIn(0f, 1f))
        )
        return rawRadius.coerceIn(min(5f, maxRadius), maxRadius)
    }

    fun collisionCellSize(maxRadius: Float): Float =
        max(MIN_COLLISION_CELL_SIZE, maxRadius.coerceAtLeast(0f) * 2f)

    fun ballsToSpawn(currentCount: Int, targetCount: Int): Int =
        (targetCount.coerceIn(1, 1_000) - currentCount).coerceAtLeast(0)

    fun areApproaching(
        v1x: Float,
        v1y: Float,
        v2x: Float,
        v2y: Float,
        normalX: Float,
        normalY: Float,
    ): Boolean {
        val relativeNormalVelocity =
            (v1x - v2x) * normalX + (v1y - v2y) * normalY
        return relativeNormalVelocity > 0f
    }
}
