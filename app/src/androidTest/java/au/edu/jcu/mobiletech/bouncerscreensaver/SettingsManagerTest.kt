package au.edu.jcu.mobiletech.bouncerscreensaver

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsManagerTest {
    private lateinit var settingsManager: SettingsManager
    private val appContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        appContext.getSharedPreferences("bouncer_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        settingsManager = SettingsManager(appContext)
    }

    @After
    fun tearDown() {
        appContext.getSharedPreferences("bouncer_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun testBallCountPersistence() {
        val expectedValue = 123
        settingsManager.ballCount = expectedValue
        assertEquals(expectedValue, settingsManager.ballCount)
    }

    @Test
    fun testBallSpeedPersistence() {
        val expectedValue = 12.5f
        settingsManager.ballSpeed = expectedValue
        assertEquals(expectedValue, settingsManager.ballSpeed, 0.01f)
    }

    @Test
    fun testPalettePersistence() {
        val expectedValue = "Ocean"
        settingsManager.palette = expectedValue
        assertEquals(expectedValue, settingsManager.palette)
    }

    @Test
    fun testPhysicsEnabledPersistence() {
        settingsManager.physicsEnabled = false
        assertFalse(settingsManager.physicsEnabled)
        settingsManager.physicsEnabled = true
        assertTrue(settingsManager.physicsEnabled)
    }
}
