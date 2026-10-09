package org.umn.application.studenthub.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AdsClick
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.umn.application.studenthub.ui.screen.home.components.AgendaItemCard
import org.umn.application.studenthub.ui.screen.home.components.NextClassHeroCard
import org.umn.application.studenthub.ui.screen.home.components.QuickAccessCard
import org.umn.application.studenthub.ui.theme.StudentHubTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSchedule: () -> Unit = {},
    onNavigateToFinance: () -> Unit = {},
    onNavigateToNotes: () -> Unit = {},
    onNavigateToNilai: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val accentColor = Color(0xFF5E5CE6)
    val secondaryTextColor = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF6E6B7B)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { /* TODO: Open drawer / menu */ }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                title = {
                    Text(
                        text = "StudentHub",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    IconButton(onClick = { /* TODO: Notification action */ }) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Notifikasi",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Greeting Section
            item {
                Column {
                    Text(
                        text = "Senin, 5 Oktober 2026 • Semester 5",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Selamat Siang, Budi.",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                }
            }

            // Featured Hero Card: KELAS BERIKUTNYA
            item {
                NextClassHeroCard(onClick = onNavigateToSchedule)
            }

            // Section 1: Sisa agenda hari ini
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sisa agenda hari ini",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        TextButton(onClick = onNavigateToSchedule) {
                            Text(
                                text = "Lihat jadwal",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor
                            )
                        }
                    }

                    AgendaItemCard(
                        time = "13.00",
                        badge = "KELAS",
                        title = "Rekayasa Perangkat Lunak",
                        subtitle = "IF550 • D.204 • hingga 14.40"
                    )

                    AgendaItemCard(
                        time = "17.00",
                        badge = "TENGGAT TUGAS",
                        title = "Praktikum 3: Navigasi",
                        subtitle = "IF570 • Belum dikumpulkan"
                    )
                }
            }

            // Section 2: Pengingatmu
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pengingatmu",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        TextButton(onClick = { /* TODO */ }) {
                            Text(
                                text = "Atur",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor
                            )
                        }
                    }

                    ReminderRowBadge(
                        badgeText = "4",
                        text = "Pengingat aktif hari ini"
                    )
                    ReminderRowIcon(
                        icon = Icons.Outlined.CalendarToday,
                        text = "Kelas IF570 dimulai pukul 09.00"
                    )
                    ReminderRowIcon(
                        icon = Icons.AutoMirrored.Outlined.Assignment,
                        text = "Praktikum 3 jatuh tempo pukul 17.00"
                    )
                    ReminderRowIcon(
                        icon = Icons.Outlined.TrackChanges,
                        text = "Target belajar navigasi sudah 80%"
                    )
                    ReminderRowIcon(
                        icon = Icons.Outlined.CreditCard,
                        text = "Bayar kos Rp800.000 sebelum 10 Okt"
                    )
                }
            }

            // Section 3: Ruang mahasiswa
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Ruang mahasiswa",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickAccessCard(
                            icon = Icons.Outlined.EditNote,
                            label = "Catatan",
                            onClick = onNavigateToNotes,
                            modifier = Modifier.weight(1f)
                        )
                        QuickAccessCard(
                            icon = Icons.Outlined.AccountBalanceWallet,
                            label = "Keuangan",
                            onClick = onNavigateToFinance,
                            modifier = Modifier.weight(1f)
                        )
                        QuickAccessCard(
                            icon = Icons.Outlined.BarChart,
                            label = "Nilai",
                            onClick = onNavigateToNilai,
                            modifier = Modifier.weight(1f)
                        )
                        QuickAccessCard(
                            icon = Icons.Outlined.AdsClick,
                            label = "Belajar",
                            onClick = { /* TODO */ },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderRowBadge(
    badgeText: String,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(Color(0xFFC0392B), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun ReminderRowIcon(
    icon: ImageVector,
    text: String
) {
    val iconColor = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF6E6B7B)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreviewLight() {
    StudentHubTheme(darkTheme = false) {
        HomeScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreviewDark() {
    StudentHubTheme(darkTheme = true) {
        HomeScreen()
    }
}
