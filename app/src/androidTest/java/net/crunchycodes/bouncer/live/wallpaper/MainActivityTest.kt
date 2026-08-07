package net.crunchycodes.bouncer.live.wallpaper

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

class MainActivityTest {
    private val launchPreferencesRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                context.getSharedPreferences("bouncer_prefs", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .putBoolean(SettingsManager.KEY_HAS_COMPLETED_CALIBRATION, true)
                    .commit()
                base.evaluate()
            }
        }
    }

    // Freeze the Compose test clock so the always-animating dashboard background does not keep
    // the test environment perpetually busy.
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(launchPreferencesRule)
        .around(composeTestRule)

    @Before
    fun freezeAnimations() {
        composeTestRule.mainClock.autoAdvance = false
    }

    @Test
    fun dashboardDisplay() {
        composeTestRule.onNodeWithText("BOUNCER").assertExists()
        composeTestRule.onNodeWithText("Customise").assertExists()
    }

    @Test
    fun customizeButtonClickNavigatesToSettings() {
        composeTestRule.onNodeWithText("Customise").performClick()

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Bouncer Settings").assertExists()
    }
}
