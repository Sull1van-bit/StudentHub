package org.umn.application.studenthub.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.umn.application.studenthub.ui.theme.ThemeChoice

@Composable
fun ThemeSettings(themeChoice: ThemeChoice, onThemeChange: (ThemeChoice) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tampilan", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            listOf(ThemeChoice.LIGHT to "Terang", ThemeChoice.DARK to "Gelap", ThemeChoice.SYSTEM to "Ikuti perangkat").forEach { (choice, label) ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("theme-${choice.storageValue}")
                        .selectable(selected = themeChoice == choice, role = Role.RadioButton, onClick = { onThemeChange(choice) }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    RadioButton(selected = themeChoice == choice, onClick = null)
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
