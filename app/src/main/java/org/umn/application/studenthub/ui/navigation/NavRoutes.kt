package org.umn.application.studenthub.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Home : Screen("home", "Home", Icons.Rounded.Home)
    object Schedule : Screen("schedule", "Jadwal", Icons.Rounded.CalendarMonth)
    object Finance : Screen("finance", "Keuangan", Icons.Rounded.AccountBalanceWallet)
    object Notes : Screen("notes", "Catatan", Icons.Rounded.Book)

    companion object {
        val bottomNavItems = listOf(Home, Schedule, Finance, Notes)
    }
}
