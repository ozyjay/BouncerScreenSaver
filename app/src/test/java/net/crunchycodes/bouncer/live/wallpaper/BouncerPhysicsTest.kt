package net.crunchycodes.bouncer.live.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BouncerPhysicsTest {
    @Test
    fun growingRadiusIsLimitedBySurface() {
        val radius = BouncerPhysics.radiusForSurface(
            initialRadius = 50f,
            sizeBehavior = 2f,
            sizeVariability = 1.4f,
            lifeRatio = 0f,
            width = 320,
            height = 480,
        )

        assertEquals(160f, radius, 0f)
    }

    @Test
    fun shrinkingRadiusRetainsMinimumWhenSurfaceAllowsIt() {
        val radius = BouncerPhysics.radiusForSurface(
            initialRadius = 40f,
            sizeBehavior = -2f,
            sizeVariability = 1.4f,
            lifeRatio = 0f,
            width = 1_080,
            height = 2_400,
        )

        assertEquals(5f, radius, 0f)
    }

    @Test
    fun collisionCellCoversLargestPossibleDiameter() {
        assertEquals(100f, BouncerPhysics.collisionCellSize(30f), 0f)
        assertEquals(380f, BouncerPhysics.collisionCellSize(190f), 0f)
    }

    @Test
    fun populationDeficitIsFilledExactlyAndCapped() {
        assertEquals(1_000, BouncerPhysics.ballsToSpawn(0, 2_000))
        assertEquals(100, BouncerPhysics.ballsToSpawn(900, 1_000))
        assertEquals(0, BouncerPhysics.ballsToSpawn(1_000, 1_000))
    }

    @Test
    fun collisionImpulseOnlyAppliesWhileBallsApproach() {
        assertTrue(BouncerPhysics.areApproaching(1f, 0f, -1f, 0f, 1f, 0f))
        assertFalse(BouncerPhysics.areApproaching(-1f, 0f, 1f, 0f, 1f, 0f))
    }
}
