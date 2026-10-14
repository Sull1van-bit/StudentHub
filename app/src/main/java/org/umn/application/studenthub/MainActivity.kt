package org.umn.application.studenthub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import org.umn.application.studenthub.ui.localization.AppLanguage
import org.umn.application.studenthub.ui.localization.LanguagePreferences
import org.umn.application.studenthub.ui.localization.LocalAppLanguage
import org.umn.application.studenthub.ui.navigation.StudentHubApp
import org.umn.application.studenthub.ui.theme.StudentHubTheme
import org.umn.application.studenthub.ui.theme.ThemeChoice
import org.umn.application.studenthub.ui.theme.ThemePreferences

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var theme by remember { mutableStateOf(ThemePreferences.read(this)) }
            var language by remember { mutableStateOf(LanguagePreferences.read(this)) }
            val dark = when (theme) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
            }
            CompositionLocalProvider(LocalAppLanguage provides language) {
                StudentHubTheme(darkTheme = dark) {
                    StudentHubApp(
                        themeChoice = theme,
                        darkTheme = dark,
                        onThemeChange = {
                            ThemePreferences.write(this, it)
                            theme = it
                        },
                        language = language,
                        onLanguageChange = {
                            LanguagePreferences.write(this, it)
                            language = it
                        }
                    )
                }
            }
        }
    }
}
