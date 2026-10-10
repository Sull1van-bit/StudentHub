package org.umn.application.studenthub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.core.view.WindowCompat

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-notnight-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun themeSelectionChangesPaletteAndSurvivesRecreation() {
        compose.onNodeWithTag("nav-profile").performClick()
        compose.onNodeWithText("Tampilan").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Gelap").performScrollTo().performClick()
        compose.onNodeWithTag("theme-dark").assertIsSelected()
        compose.onNodeWithTag("studenthub-root").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Tema gelap"))
        compose.runOnIdle {
            assertTrue(!WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).isAppearanceLightStatusBars)
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("theme-dark").assertIsSelected()
        compose.onNodeWithTag("studenthub-root").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Tema gelap"))
        compose.onNodeWithText("Terang").performScrollTo().performClick()
        compose.onNodeWithTag("theme-light").assertIsSelected()
        compose.onNodeWithTag("studenthub-root").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Tema terang"))
        compose.onNodeWithText("Ikuti perangkat").performScrollTo().performClick()
        compose.onNodeWithTag("theme-system").assertIsSelected()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("theme-system").assertIsSelected()
        compose.runOnIdle {
            assertEquals("system", compose.activity.getSharedPreferences("studenthub_settings", 0).getString("theme", null))
        }
    }
}
