package net.crunchycodes.bouncer.live.wallpaper

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
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

    @Test
    fun appearanceControlsAreDisplayed() {
        composeTestRule.onNodeWithText("Ball style").assertExists()
        composeTestRule.onNodeWithText("Auto").assertExists()
        composeTestRule.onNodeWithText("Glow").assertExists()
        composeTestRule.onNodeWithText("Flat").assertExists()
        composeTestRule.onNodeWithText("Brightness:", substring = true).assertExists()
        composeTestRule.onNodeWithText("Transparency:", substring = true).assertExists()
    }

    @Test
    fun collapsedSectionCanBeExpanded() {
        composeTestRule.onNodeWithText("Motion & behaviour").performClick()

        composeTestRule.onNodeWithText("Base speed:", substring = true).assertExists()
        composeTestRule.onNodeWithText("Average lifespan:", substring = true).assertExists()
    }

    @Test
    fun performanceSectionShowsCurrentStateMachinePhase() {
        composeTestRule.onNodeWithText("Performance & calibration").performClick()

        composeTestRule.onNodeWithText("Adaptive state:", substring = true).assertExists()
    }

    @Test
    fun performanceTelemetryUpdatesWhileSettingsIsVisible() {
        composeTestRule.onNodeWithText("Performance & calibration").performClick()

        composeTestRule.activityRule.scenario.onActivity { activity ->
            SettingsManager(activity).persistRuntimePerformanceState(
                ballCount = 7,
                renderQuality = RenderQuality.Flat,
                physicsSuspended = true,
                phase = RuntimePerformancePhase.REDUCING,
            )
        }

        composeTestRule.onNodeWithText("Adaptive output: 7 balls · Flat · collisions paused")
            .assertExists()
        composeTestRule.onNodeWithText("Adaptive state: Reducing balls").assertExists()
    }

    @Test
    fun doneKeepsSettingsActivityInTask() {
        val activity = composeTestRule.activity

        composeTestRule.onNodeWithText("Done").performClick()

        assertFalse(activity.isFinishing)
    }
}
