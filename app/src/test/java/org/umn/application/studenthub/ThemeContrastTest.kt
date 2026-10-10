package org.umn.application.studenthub

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.umn.application.studenthub.ui.theme.StudentHubTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeContrastTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightTextRolesMeetContrast() = checkTheme(false)
    @Test fun darkTextRolesMeetContrast() = checkTheme(true)

    private fun checkTheme(dark: Boolean) {
        lateinit var colors: ColorScheme
        compose.setContent { StudentHubTheme(darkTheme = dark) {
            val scheme = MaterialTheme.colorScheme
            SideEffect { colors = scheme }
        } }
        compose.runOnIdle {
            with(colors) {
                listOf("surface" to (onSurface to surface), "body" to (onSurfaceVariant to surface),
                    "card" to (onSurface to surfaceContainerLow), "button" to (onPrimary to primary),
                    "tonal" to (onSecondaryContainer to secondaryContainer),
                    "summary" to (onPrimaryContainer to primaryContainer),
                    "summary label" to (primary to primaryContainer)).forEach { (name, pair) ->
                    val a = pair.first.luminance(); val b = pair.second.luminance()
                    val ratio = (maxOf(a,b) + 0.05f) / (minOf(a,b) + 0.05f)
                    assertTrue("$name contrast $ratio (dark=$dark)", ratio >= 4.5f)
                }
            }
        }
    }
}
