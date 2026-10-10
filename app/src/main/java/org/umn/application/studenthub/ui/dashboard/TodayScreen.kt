package org.umn.application.studenthub.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.umn.application.studenthub.R

@Composable
fun TodayScreen(onNavigate: (String) -> Unit, onReminders: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SupportingText("Senin, 5 Oktober 2026 • Semester 5")
            Text("Selamat pagi, Nadia.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
            SupportingText("Dua kelas, satu langkah lebih dekat.")
        }
        Card(
            onClick = { onNavigate("schedule") },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("KELAS BERIKUTNYA", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("09.00–10.40", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Text("Pemrograman Aplikasi Mobile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
                Text("IF570 • Ruang D.302 • 45 menit lagi", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading("Sisa agenda hari ini", "Lihat jadwal") { onNavigate("schedule") }
            AgendaEntry("13.00", "KELAS", "Rekayasa Perangkat Lunak", "IF550 • D.204 • hingga 14.40") { onNavigate("academic") }
            AgendaEntry("17.00", "TENGGAT TUGAS", "Praktikum 3: Navigasi", "IF570 • Belum dikumpulkan") { onNavigate("academic") }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading("Pengingatmu", "Atur", onReminders)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge { Text("4") }
                SupportingText("Pengingat aktif hari ini")
            }
            ReminderRow(R.drawable.hub_calendar, "Kelas IF570 dimulai pukul 09.00")
            ReminderRow(R.drawable.hub_clipboard, "Praktikum 3 jatuh tempo pukul 17.00")
            ReminderRow(R.drawable.hub_target_small, "Target belajar navigasi sudah 80%")
            ReminderRow(R.drawable.hub_wallet_small, "Bayar kos Rp800.000 sebelum 10 Okt")
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading("Ruang mahasiswa")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("Catatan", "notes", R.drawable.hub_notebook),
                    Triple("Keuangan", "finance", R.drawable.hub_wallet),
                    Triple("Nilai", "grades", R.drawable.hub_chart),
                    Triple("Belajar", "learning", R.drawable.hub_target)
                ).forEach { (label, route, icon) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .clickable { onNavigate(route) }.heightIn(min = 68.dp).padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                    ) {
                        Icon(painterResource(icon), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun SupportingText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun SectionHeading(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (action != null) TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(action)
        }
    }
}

@Composable
fun ReminderRow(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(icon), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        SupportingText(text)
    }
}

@Composable
fun AgendaEntry(time: String, kind: String, title: String, detail: String, highlighted: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(time, Modifier.width(44.dp).padding(top = 12.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Row(
            Modifier.weight(1f).height(IntrinsicSize.Min).clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                .background(if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onClick)
        ) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(kind, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium)
                SupportingText(detail)
            }
        }
    }
}
