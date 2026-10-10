package org.umn.application.studenthub.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Home : Screen("home", "Home", Icons.Rounded.Home)
    data object Schedule : Screen("schedule", "Jadwal", Icons.Rounded.CalendarMonth)
    data object Profile : Screen("profile", "Profil", Icons.Rounded.Person)
    data object More : Screen("more", "Lainnya", Icons.Rounded.MoreHoriz)
    data object Finance : Screen("finance", "Keuangan", Icons.Rounded.AccountBalanceWallet)
    data object Notes : Screen("notes", "Catatan", Icons.Rounded.Book)

    companion object {
        val bottomNavItems = listOf(Home, Schedule, Profile)
    }
}
