package net.crunchycodes.bouncer.live.wallpaper

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class SettingsActivityTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<SettingsActivity>()

    @Test
    fun settingsRemainVisibleAfterLeavingAndResumingApp() {
        composeTestRule.activityRule.scenario.onActivity { activity ->
            InstrumentationRegistry.getInstrumentation().callActivityOnUserLeaving(activity)
        }
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        composeTestRule.onNodeWithText("Bouncer Settings").assertExists()
    }
}
