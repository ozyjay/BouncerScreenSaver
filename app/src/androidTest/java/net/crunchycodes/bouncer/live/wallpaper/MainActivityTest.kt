package net.crunchycodes.bouncer.live.wallpaper

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun clearPrefs() {
        composeTestRule.mainClock.autoAdvance = false
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("bouncer_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun dashboardDisplay() {
        composeTestRule.onNodeWithText("BOUNCER").assertExists()
        composeTestRule.onNodeWithText("Set Wallpaper").assertExists()
        composeTestRule.onNodeWithText("Customize").assertExists()
    }

    @Test
    fun customizeButtonClickNavigatesToSettings() {
        composeTestRule.onNodeWithText("Customize").performClick()

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Bouncer Settings").assertExists()
    }
}
