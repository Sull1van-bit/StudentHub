package org.umn.application.studenthub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.umn.application.studenthub.ui.MainScreen
import org.umn.application.studenthub.ui.theme.StudentHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StudentHubTheme {
                MainScreen()
            }
        }
    }
}
