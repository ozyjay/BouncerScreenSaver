package net.crunchycodes.bouncer.live.wallpaper

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal object BouncerPhysics {
    const val MIN_BALL_COUNT = 1
    const val MAX_BALL_COUNT = 300
    const val MAX_SPAWN_BATCH = 24
    const val MIN_COLLISION_CELL_SIZE = 100f
    const val MIN_RADIUS = 5f
    const val DEFAULT_BALL_COUNT = 50
    const val DEFAULT_BALL_SPEED = 5f
    const val DEFAULT_SIZE_BEHAVIOR = -0.5f
    const val DEFAULT_LIFESPAN_SECONDS = 15f
    const val MIN_BALL_SPEED = 1f
    const val MAX_BALL_SPEED = 10f
    const val MAX_SPEED_MULTIPLIER = 1.5f
    const val MIN_SIZE_BEHAVIOR = -2f
    const val MAX_SIZE_BEHAVIOR = 2f
    const val MIN_LIFESPAN_SECONDS = 2f
    const val MAX_LIFESPAN_SECONDS = 300f
    const val TOUCH_DESTROY_RADIUS_MULTIPLIER = 2f
    const val FADE_IN_DURATION_MILLIS = 250L
    const val DEFAULT_NON_ZERO_SPEED = 0.1f

    fun clampBallCount(value: Int): Int = value.coerceIn(MIN_BALL_COUNT, MAX_BALL_COUNT)

    fun clampSizeBehavior(value: Float): Float =
        value.coerceIn(MIN_SIZE_BEHAVIOR, MAX_SIZE_BEHAVIOR)

    fun clampLifespanSeconds(value: Float): Float =
        value.coerceIn(MIN_LIFESPAN_SECONDS, MAX_LIFESPAN_SECONDS)

    fun speedChangeScale(previousBaseSpeed: Float, newBaseSpeed: Float): Float {
        val previous = if (previousBaseSpeed.isFinite() && previousBaseSpeed > 0f) {
            previousBaseSpeed
        } else {
            DEFAULT_BALL_SPEED
        }
        val updated = if (newBaseSpeed.isFinite() && newBaseSpeed > 0f) {
            newBaseSpeed
        } else {
            DEFAULT_BALL_SPEED
        }
        return updated / previous
    }

    fun maximumSpeedForBase(baseSpeed: Float): Float {
        val safeBaseSpeed = if (baseSpeed.isFinite() && baseSpeed > 0f) {
            baseSpeed
        } else {
            DEFAULT_BALL_SPEED
        }
        return safeBaseSpeed * MAX_SPEED_MULTIPLIER
    }

    fun limitBallSpeed(ball: BallState, maximumSpeed: Float) {
        val safeMaximumSpeed = if (maximumSpeed.isFinite() && maximumSpeed > 0f) {
            maximumSpeed
        } else {
            maximumSpeedForBase(DEFAULT_BALL_SPEED)
        }
        val currentSpeedSquared = speedSquared(ball.dx, ball.dy)
        val maximumSpeedSquared = safeMaximumSpeed * safeMaximumSpeed
        if (!currentSpeedSquared.isFinite() || currentSpeedSquared <= maximumSpeedSquared) return

        val scale = safeMaximumSpeed / sqrt(currentSpeedSquared)
        ball.dx *= scale
        ball.dy *= scale
    }

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

        val effectiveSizeBehavior = clampSizeBehavior(sizeBehavior) * sizeVariability
        val rawRadius = initialRadius * (
            1f + effectiveSizeBehavior * (1f - lifeRatio.coerceIn(0f, 1f))
        )
        return rawRadius.coerceIn(min(MIN_RADIUS, maxRadius), maxRadius)
    }

    fun collisionCellSize(maxRadius: Float): Float =
        max(MIN_COLLISION_CELL_SIZE, maxRadius.coerceAtLeast(0f) * 2f)

    fun maxRadiusForPopulation(width: Int, height: Int, populationCount: Int): Float {
        val surfaceLimit = min(width, height).coerceAtLeast(0) / 2f
        if (surfaceLimit <= 0f) return 0f

        val population = populationCount.coerceAtLeast(MIN_BALL_COUNT)
        val areaPerBall = width.coerceAtLeast(0).toFloat() * height.coerceAtLeast(0).toFloat() / population
        val densityLimit = sqrt(areaPerBall.coerceAtLeast(0f)) * MAX_RADIUS_TO_SPACING_RATIO
        return densityLimit.coerceIn(min(MIN_RADIUS, surfaceLimit), surfaceLimit)
    }

    fun ballsToSpawn(currentCount: Int, targetCount: Int, maxBatch: Int = MAX_SPAWN_BATCH): Int {
        // Spawn in batches so jumping from a small count to the configured max does not create a single
        // expensive frame full of allocations and collision work.
        val deficit = clampBallCount(targetCount) - currentCount.coerceAtLeast(0)
        return deficit.coerceAtLeast(0).coerceAtMost(maxBatch.coerceAtLeast(1))
    }

    fun areApproaching(
        v1x: Float,
        v1y: Float,
        v2x: Float,
        v2y: Float,
        normalX: Float,
        normalY: Float,
    ): Boolean {
        val relativeNormalVelocity = (v1x - v2x) * normalX + (v1y - v2y) * normalY
        return relativeNormalVelocity > 0f
    }

    fun isTouchWithinDestroyRadius(ballX: Float, ballY: Float, ballRadius: Float, touchX: Float, touchY: Float): Boolean {
        val dx = ballX - touchX
        val dy = ballY - touchY
        val destroyRadius = ballRadius * TOUCH_DESTROY_RADIUS_MULTIPLIER
        return dx * dx + dy * dy < destroyRadius * destroyRadius
    }

    fun alphaForTime(ageMillis: Long, fadeInDurationMillis: Long = FADE_IN_DURATION_MILLIS): Float {
        if (fadeInDurationMillis <= 0L) return 1f
        return (ageMillis.toFloat() / fadeInDurationMillis).coerceIn(0f, 1f)
    }

    fun keepInsideBounds(ball: BallState, width: Int, height: Int) {
        ball.x = ball.x.coerceIn(ball.radius, width.toFloat() - ball.radius)
        ball.y = ball.y.coerceIn(ball.radius, height.toFloat() - ball.radius)
    }

    fun ensureFiniteBall(ball: BallState, width: Int, height: Int, allowStopped: Boolean) {
        // Corrupted physics values are rare but catastrophic when they happen. Repair them
        // in place so one bad collision or preference value does not poison later frames.
        val fallbackRadius = if (ball.initialRadius.isFinite() && ball.initialRadius > 0f) {
            ball.initialRadius.coerceAtLeast(MIN_RADIUS)
        } else {
            MIN_RADIUS
        }
        if (!ball.radius.isFinite() || ball.radius <= 0f) {
            ball.radius = fallbackRadius
        }
        if (!ball.mass.isFinite() || ball.mass <= 0f) {
            ball.mass = ball.radius * ball.radius
        }
        if (!ball.x.isFinite()) {
            ball.x = width / 2f
        }
        if (!ball.y.isFinite()) {
            ball.y = height / 2f
        }
        if (!ball.dx.isFinite()) {
            ball.dx = DEFAULT_NON_ZERO_SPEED
        }
        if (!ball.dy.isFinite()) {
            ball.dy = DEFAULT_NON_ZERO_SPEED
        }
        keepInsideBounds(ball, width, height)
        if (!allowStopped && speedSquared(ball.dx, ball.dy) < DEFAULT_NON_ZERO_SPEED * DEFAULT_NON_ZERO_SPEED) {
            ball.dx = if (abs(ball.dx) >= DEFAULT_NON_ZERO_SPEED) ball.dx else DEFAULT_NON_ZERO_SPEED
            ball.dy = if (abs(ball.dy) >= DEFAULT_NON_ZERO_SPEED) ball.dy else DEFAULT_NON_ZERO_SPEED
        }
    }

    fun separateAndResolveCollision(
        first: BallState,
        second: BallState,
        width: Int,
        height: Int,
        allowStopped: Boolean,
        maximumSpeed: Float,
    ) {
        val deltaX = second.x - first.x
        val deltaY = second.y - first.y
        val minDistance = first.radius + second.radius
        val distanceSquared = deltaX * deltaX + deltaY * deltaY
        if (distanceSquared >= minDistance * minDistance) {
            return
        }

        val distance = if (distanceSquared > 0f) sqrt(distanceSquared) else 0f
        val normalX = if (distance > 0f) deltaX / distance else 1f
        val normalY = if (distance > 0f) deltaY / distance else 0f
        val overlap = (minDistance - distance).coerceAtLeast(0f)
        val safeFirstMass = first.mass.coerceAtLeast(1f)
        val safeSecondMass = second.mass.coerceAtLeast(1f)
        val totalMass = safeFirstMass + safeSecondMass

        // Separate before applying impulse so overlapping balls do not remain embedded in
        // each other or get pushed outside the visible surface.
        first.x -= normalX * overlap * (safeSecondMass / totalMass)
        first.y -= normalY * overlap * (safeSecondMass / totalMass)
        second.x += normalX * overlap * (safeFirstMass / totalMass)
        second.y += normalY * overlap * (safeFirstMass / totalMass)

        keepInsideBounds(first, width, height)
        keepInsideBounds(second, width, height)

        val firstNormalVelocity = first.dx * normalX + first.dy * normalY
        val secondNormalVelocity = second.dx * normalX + second.dy * normalY
        if (!areApproaching(first.dx, first.dy, second.dx, second.dy, normalX, normalY)) {
            ensureFiniteBall(first, width, height, allowStopped)
            ensureFiniteBall(second, width, height, allowStopped)
            limitBallSpeed(first, maximumSpeed)
            limitBallSpeed(second, maximumSpeed)
            return
        }

        val firstNormalVelocityAfter =
            (firstNormalVelocity * (safeFirstMass - safeSecondMass) + 2 * safeSecondMass * secondNormalVelocity) /
                totalMass
        val secondNormalVelocityAfter =
            (secondNormalVelocity * (safeSecondMass - safeFirstMass) + 2 * safeFirstMass * firstNormalVelocity) /
                totalMass

        first.dx += (firstNormalVelocityAfter - firstNormalVelocity) * normalX
        first.dy += (firstNormalVelocityAfter - firstNormalVelocity) * normalY
        second.dx += (secondNormalVelocityAfter - secondNormalVelocity) * normalX
        second.dy += (secondNormalVelocityAfter - secondNormalVelocity) * normalY

        ensureFiniteBall(first, width, height, allowStopped)
        ensureFiniteBall(second, width, height, allowStopped)
        limitBallSpeed(first, maximumSpeed)
        limitBallSpeed(second, maximumSpeed)
    }

    fun speedSquared(dx: Float, dy: Float): Float = dx * dx + dy * dy

    private const val MAX_RADIUS_TO_SPACING_RATIO = 0.55f
}
