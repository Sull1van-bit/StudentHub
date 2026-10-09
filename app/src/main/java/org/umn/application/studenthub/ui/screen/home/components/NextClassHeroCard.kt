package org.umn.application.studenthub.ui.screen.home.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.umn.application.studenthub.ui.theme.StudentHubTheme

@Composable
fun NextClassHeroCard(
    onClick: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) Color(0xFF2E2E5D) else Color(0xFFC7C7FE)
    val textColor = if (isDark) Color(0xFFECECFF) else Color(0xFF1B1B36)
    val subtitleColor = if (isDark) Color(0xFFB0B0E6) else Color(0xFF4A4B7C)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "KELAS BERIKUTNYA",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = subtitleColor
                )
                Text(
                    text = "09.00–10.40",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = subtitleColor
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Pemrograman Aplikasi Mobile",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "IF570 • Ruang D.302 • 45 menit lagi",
                style = MaterialTheme.typography.bodyMedium,
                color = subtitleColor
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NextClassHeroCardPreview() {
    StudentHubTheme {
        NextClassHeroCard()
    }
}
