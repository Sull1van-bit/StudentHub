package org.umn.application.studenthub.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.umn.application.studenthub.ui.localization.AppLanguage
import org.umn.application.studenthub.ui.localization.lString

@Composable
fun LanguageSettings(currentLanguage: AppLanguage, onLanguageChange: (AppLanguage) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(lString("Bahasa Aplikasi", "App Language"), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppLanguage.values().forEach { lang ->
                FilterChip(
                    selected = currentLanguage == lang,
                    onClick = { onLanguageChange(lang) },
                    label = { Text(if (lang == AppLanguage.INDONESIAN) "🇮🇩 Indonesia" else "🇬🇧 English") },
                    leadingIcon = if (currentLanguage == lang) { { Icon(Icons.Default.Check, null) } } else null,
                    modifier = Modifier.weight(1f).testTag("lang-${lang.code}")
                )
            }
        }
    }
}
