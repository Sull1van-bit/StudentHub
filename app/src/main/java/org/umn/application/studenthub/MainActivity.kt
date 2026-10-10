package org.umn.application.studenthub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import org.umn.application.studenthub.ui.theme.ThemeChoice
import org.umn.application.studenthub.ui.theme.ThemePreferences
import org.umn.application.studenthub.ui.navigation.StudentHubApp
import org.umn.application.studenthub.ui.theme.StudentHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var theme by remember { mutableStateOf(ThemePreferences.read(this)) }
            val dark = when (theme) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
            }
            StudentHubTheme(darkTheme = dark) {
                StudentHubApp(themeChoice = theme, darkTheme = dark, onThemeChange = {
                    ThemePreferences.write(this, it)
                    theme = it
                })
            }
        }
    }
}
