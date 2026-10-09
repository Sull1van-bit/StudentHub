package org.umn.application.studenthub.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.umn.application.studenthub.ui.screen.finance.FinanceScreen
import org.umn.application.studenthub.ui.screen.notes.NotesScreen
import org.umn.application.studenthub.ui.screen.schedule.ScheduleScreen
import org.umn.application.studenthub.ui.navigation.Screen
import org.umn.application.studenthub.ui.screen.home.HomeScreen
import org.umn.application.studenthub.ui.screen.nilai.NilaiScreen

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val navItems = Screen.bottomNavItems

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 8.dp
            ) {
                navItems.forEachIndexed { index, item ->
                    val isSelected = selectedTab == index
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                modifier = Modifier.size(if (isSelected) 28.dp else 24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> HomeScreen(
                    onNavigateToSchedule = { selectedTab = 1 },
                    onNavigateToFinance = { selectedTab = 2 },
                    onNavigateToNotes = { selectedTab = 3 },
                    onNavigateToNilai = { selectedTab = 4 }
                )
                1 -> ScheduleScreen()
                2 -> FinanceScreen()
                3 -> NotesScreen()
                4 -> NilaiScreen()
            }
        }
    }
}
