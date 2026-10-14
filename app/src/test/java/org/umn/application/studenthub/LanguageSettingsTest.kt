package org.umn.application.studenthub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.umn.application.studenthub.ui.localization.AppLanguage
import org.umn.application.studenthub.ui.localization.LanguagePreferences

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-notnight-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LanguageSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun switchingLanguageUpdatesPreferencesAndUi() {
        compose.onNodeWithTag("nav-profile").performClick()
        compose.onNodeWithText("Bahasa Aplikasi").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("lang-en").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(AppLanguage.ENGLISH, LanguagePreferences.read(compose.activity))
        }
        compose.onNodeWithText("App Language").assertExists()
        compose.onNodeWithText("Appearance").assertExists()
        compose.onNodeWithText("Student Profile").assertExists()
        compose.onNodeWithTag("lang-id").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(AppLanguage.INDONESIAN, LanguagePreferences.read(compose.activity))
        }
        compose.onNodeWithText("Bahasa Aplikasi").assertExists()
    }
}
