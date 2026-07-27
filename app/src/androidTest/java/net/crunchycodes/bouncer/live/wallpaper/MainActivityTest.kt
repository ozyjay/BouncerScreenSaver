package net.crunchycodes.bouncer.live.wallpaper

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testDashboardDisplay() {
        // Check if the main title is displayed
        composeTestRule.onNodeWithText("BOUNCER").assertExists()
        
        // Check if the "SET WALLPAPER" button is displayed
        composeTestRule.onNodeWithText("SET WALLPAPER").assertExists()
        
        // Check if the "CUSTOMIZE" button is displayed
        composeTestRule.onNodeWithText("CUSTOMIZE").assertExists()
    }

    @Test
    fun testCustomizeButtonClick() {
        // Clicking "CUSTOMIZE" should not crash (it opens SettingsActivity)
        composeTestRule.onNodeWithText("CUSTOMIZE").performClick()
        
        // After click, we should be on the Settings screen
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Wallpaper Settings").assertExists()
    }
}
