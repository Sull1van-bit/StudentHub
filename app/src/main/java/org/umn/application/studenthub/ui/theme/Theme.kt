package org.umn.application.studenthub.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFC5C0FF),
    onPrimary = Color(0xFF2D2856),
    primaryContainer = Color(0xFF45416E),
    onPrimaryContainer = Color(0xFFE5DFFF),
    secondary = Color(0xFFCAC2DC),
    onSecondary = Color(0xFF322D41),
    secondaryContainer = Color(0xFF494358),
    onSecondaryContainer = Color(0xFFE7DFF8),
    tertiary = Color(0xFFD3BDCD),
    onTertiary = Color(0xFF392B36),
    background = Color(0xFF141318),
    surface = Color(0xFF141318),
    surfaceContainerLowest = Color(0xFF0F0E13),
    surfaceContainerLow = Color(0xFF1C1B20),
    surfaceContainer = Color(0xFF222027),
    surfaceContainerHigh = Color(0xFF2C2A31),
    surfaceContainerHighest = Color(0xFF37343C),
    surfaceVariant = Color(0xFF48444F),
    onBackground = Color(0xFFE5E1EA),
    onSurface = Color(0xFFE5E1EA),
    onSurfaceVariant = Color(0xFFCBC4D2),
    outline = Color(0xFF948D9B),
    outlineVariant = Color(0xFF48444F),
    inverseSurface = Color(0xFFE5E1EA),
    inverseOnSurface = Color(0xFF302E35),
    inversePrimary = Color(0xFF484676)
)

private val LightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF484676),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFC1BDFE),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF19164B),
    secondary = androidx.compose.ui.graphics.Color(0xFF5F5C71),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFE4DFF9),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF474459),
    background = androidx.compose.ui.graphics.Color(0xFFFCF8FF),
    surface = androidx.compose.ui.graphics.Color(0xFFFCF8FF),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFF6F2FA),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFF0ECF4),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE5E1EC),
    outline = androidx.compose.ui.graphics.Color(0xFF787680),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFC8C5D0),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onBackground = androidx.compose.ui.graphics.Color(0xFF1C1B21),
    onSurface = androidx.compose.ui.graphics.Color(0xFF1C1B21),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF47464F)
)

@Composable
fun StudentHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                if (Build.VERSION.SDK_INT < 35) {
                    window.statusBarColor = colorScheme.background.toArgb()
                    window.navigationBarColor = colorScheme.surfaceContainer.toArgb()
                }
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}