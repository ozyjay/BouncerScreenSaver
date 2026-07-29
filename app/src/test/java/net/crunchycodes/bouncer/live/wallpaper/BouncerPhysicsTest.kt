package net.crunchycodes.bouncer.live.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BouncerPhysicsTest {
    // These tests pin the small math helpers that keep the render loop stable at high counts.
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
    fun populationDeficitIsFilledInBatchesAndConverges() {
        var current = 0
        var frames = 0

        while (current < BouncerPhysics.MAX_BALL_COUNT) {
            current += BouncerPhysics.ballsToSpawn(current, BouncerPhysics.MAX_BALL_COUNT)
            frames++
        }

        assertEquals(24, BouncerPhysics.ballsToSpawn(0, BouncerPhysics.MAX_BALL_COUNT))
        assertEquals(BouncerPhysics.MAX_BALL_COUNT, current)
        assertTrue(frames > 1)
    }

    @Test
    fun reducingPopulationDoesNotSpawnExtraBalls() {
        assertEquals(0, BouncerPhysics.ballsToSpawn(500, 200))
    }

    @Test
    fun collisionImpulseOnlyAppliesWhileBallsApproach() {
        assertTrue(BouncerPhysics.areApproaching(1f, 0f, -1f, 0f, 1f, 0f))
        assertFalse(BouncerPhysics.areApproaching(-1f, 0f, 1f, 0f, 1f, 0f))
    }

    @Test
    fun touchDistanceCheckUsesSquaredDistance() {
        assertTrue(BouncerPhysics.isTouchWithinDestroyRadius(10f, 10f, 10f, 25f, 10f))
        assertFalse(BouncerPhysics.isTouchWithinDestroyRadius(10f, 10f, 10f, 40f, 10f))
    }

    @Test
    fun alphaProgressionIsTimeBased() {
        assertEquals(0f, BouncerPhysics.alphaForTime(0L), 0f)
        assertEquals(0.5f, BouncerPhysics.alphaForTime(125L), 0.0001f)
        assertEquals(1f, BouncerPhysics.alphaForTime(250L), 0f)
        assertEquals(1f, BouncerPhysics.alphaForTime(500L), 0f)
    }

    @Test
    fun collisionSeparationKeepsBallsInsideBounds() {
        val first = ball(x = 10f, y = 10f, dx = 3f, dy = 0f, radius = 10f)
        val second = ball(x = 15f, y = 10f, dx = -3f, dy = 0f, radius = 10f)

        BouncerPhysics.separateAndResolveCollision(first, second, width = 30, height = 30, allowStopped = false)

        assertTrue(first.x in first.radius..(30f - first.radius))
        assertTrue(second.x in second.radius..(30f - second.radius))
    }

    @Test
    fun repeatedCollisionsStayFinite() {
        val first = ball(x = 40f, y = 50f, dx = 4f, dy = 1f, radius = 12f)
        val second = ball(x = 55f, y = 50f, dx = -4f, dy = -1f, radius = 12f)

        repeat(100) {
            BouncerPhysics.separateAndResolveCollision(first, second, width = 200, height = 200, allowStopped = false)
        }

        assertTrue(first.x.isFinite())
        assertTrue(first.y.isFinite())
        assertTrue(first.dx.isFinite())
        assertTrue(second.dx.isFinite())
    }

    @Test
    fun collisionResolutionPreservesNonZeroSpeed() {
        val first = ball(x = 40f, y = 50f, dx = 0f, dy = 0f, radius = 12f)
        val second = ball(x = 45f, y = 50f, dx = 0f, dy = 0f, radius = 12f)

        BouncerPhysics.separateAndResolveCollision(first, second, width = 200, height = 200, allowStopped = false)

        assertTrue(BouncerPhysics.speedSquared(first.dx, first.dy) > 0f)
        assertTrue(BouncerPhysics.speedSquared(second.dx, second.dy) > 0f)
    }

    @Test
    fun invalidValuesRecoverToFiniteDefaults() {
        val ball = ball(
            x = Float.NaN,
            y = Float.POSITIVE_INFINITY,
            dx = Float.NaN,
            dy = Float.NEGATIVE_INFINITY,
            radius = Float.NaN,
        )
        ball.mass = Float.NaN

        BouncerPhysics.ensureFiniteBall(ball, width = 100, height = 100, allowStopped = false)

        assertTrue(ball.x.isFinite())
        assertTrue(ball.y.isFinite())
        assertTrue(ball.dx.isFinite())
        assertTrue(ball.dy.isFinite())
        assertTrue(ball.radius.isFinite())
        assertTrue(ball.mass.isFinite())
    }

    private fun ball(
        x: Float,
        y: Float,
        dx: Float,
        dy: Float,
        radius: Float,
    ): BallState = BallState(
        x = x,
        y = y,
        dx = dx,
        dy = dy,
        radius = radius,
        initialRadius = radius.coerceAtLeast(10f),
        color = 0,
        startTime = 0L,
        expiryTime = 10_000L,
        sizeVariability = 1f,
        id = 1L,
    ).apply {
        mass = radius * radius
    }
}
