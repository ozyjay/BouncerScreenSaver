package net.crunchycodes.bouncer.live.wallpaper

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsManagerTest {
    // Instrumentation is used here because SharedPreferences persistence is part of the contract.
    private lateinit var settingsManager: SettingsManager
    private val appContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        appContext.getSharedPreferences("bouncer_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putInt(SettingsManager.KEY_DEVICE_MAX_BALL_COUNT, BouncerPhysics.MAX_BALL_COUNT)
            .commit()
        settingsManager = SettingsManager(appContext)
    }

    @After
    fun tearDown() {
        appContext.getSharedPreferences("bouncer_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun persistsAndClampsBallCount() {
        settingsManager.ballCount = 5_000
        assertEquals(BouncerPhysics.MAX_BALL_COUNT, settingsManager.ballCount)
    }

    @Test
    fun persistsAndClampsBallSpeed() {
        settingsManager.ballSpeed = -5f
        assertEquals(BouncerPhysics.MIN_BALL_SPEED, settingsManager.ballSpeed, 0f)
    }

    @Test
    fun persistsAndClampsSizeBehavior() {
        settingsManager.sizeBehavior = 10f
        assertEquals(BouncerPhysics.MAX_SIZE_BEHAVIOR, settingsManager.sizeBehavior, 0f)
    }

    @Test
    fun persistsAndClampsLifespan() {
        settingsManager.lifespanBase = 500f
        assertEquals(BouncerPhysics.MAX_LIFESPAN_SECONDS, settingsManager.lifespanBase, 0f)
    }

    @Test
    fun palettePersistsAsStableIdentifier() {
        settingsManager.palette = ColorPalette.OCEAN
        assertEquals(ColorPalette.OCEAN, settingsManager.palette)
    }

    @Test
    fun legacyPaletteValuesRemainReadable() {
        appContext.getSharedPreferences("bouncer_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(SettingsManager.KEY_PALETTE, "Ocean")
            .commit()

        assertEquals(ColorPalette.OCEAN, settingsManager.palette)
    }

    @Test
    fun appearanceLevelsPersistAndClamp() {
        settingsManager.brightness = 5f
        settingsManager.transparency = -1f

        assertEquals(BallAppearance.MAX_BRIGHTNESS, settingsManager.brightness, 0f)
        assertEquals(BallAppearance.MIN_TRANSPARENCY, settingsManager.transparency, 0f)
    }

    @Test
    fun ballStylePersistsAsStableIdentifier() {
        settingsManager.ballStyle = BallStyle.FLAT

        assertEquals(BallStyle.FLAT, settingsManager.ballStyle)
    }

    @Test
    fun performanceModePersistsAsStableIdentifier() {
        settingsManager.performanceMode = PerformanceMode.FIXED

        assertEquals(PerformanceMode.FIXED, settingsManager.performanceMode)
    }

    @Test
    fun runtimePerformanceStatePersistsForSettingsSummary() {
        settingsManager.persistRuntimePerformanceState(
            ballCount = 42,
            renderQuality = RenderQuality.Flat,
            physicsSuspended = true,
        )

        assertEquals(42, settingsManager.runtimeBallCount)
        assertEquals(RenderQuality.Flat, settingsManager.runtimeRenderQuality)
        assertTrue(settingsManager.runtimePhysicsSuspended)
    }

    @Test
    fun unknownBallStyleFallsBackToAuto() {
        appContext.getSharedPreferences("bouncer_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(SettingsManager.KEY_BALL_STYLE, "unknown")
            .commit()

        assertEquals(BallStyle.AUTO, settingsManager.ballStyle)
    }

    @Test
    fun booleansPersist() {
        settingsManager.physicsEnabled = false
        settingsManager.autoDisablePhysicsOnHeavyLoad = false
        settingsManager.destroyOnTouch = true

        assertFalse(settingsManager.physicsEnabled)
        assertFalse(settingsManager.autoDisablePhysicsOnHeavyLoad)
        assertTrue(settingsManager.destroyOnTouch)
    }
}
