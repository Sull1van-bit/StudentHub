package org.umn.application.studenthub.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.umn.application.studenthub.ui.dashboard.TodayScreen
import org.umn.application.studenthub.ui.schedule.ScheduleScreen
import org.umn.application.studenthub.ui.academic.AcademicScreen
import org.umn.application.studenthub.ui.academic.CourseDetailScreen
import org.umn.application.studenthub.ui.academic.GradesScreen
import org.umn.application.studenthub.ui.academic.LearningScreen
import org.umn.application.studenthub.ui.notes.NotesScreen
import org.umn.application.studenthub.ui.notes.NoteEditorScreen
import org.umn.application.studenthub.ui.finance.FinanceScreen
import org.umn.application.studenthub.ui.profile.ProfileScreen
import org.umn.application.studenthub.ui.theme.ThemeChoice
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentHubApp(themeChoice: ThemeChoice, darkTheme: Boolean, onThemeChange: (ThemeChoice) -> Unit) {
    var route by rememberSaveable { mutableStateOf("home") }
    var reminders by rememberSaveable { mutableStateOf(false) }
    var courseId by rememberSaveable { mutableStateOf("IF570") }
    var noteId by rememberSaveable { mutableStateOf("") }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val primary = Screen.bottomNavItems
    val goBack: () -> Unit = {
        route = when (route) {
            "course" -> "academic"
            "editor" -> "notes"
            "notes", "finance", "grades", "learning", "academic", "profile" -> "home"
            else -> "home"
        }
    }
    BackHandler(route != "home" && drawerState.isClosed, onBack = goBack)
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("StudentHub", Modifier.padding(horizontal = 28.dp, vertical = 24.dp), style = MaterialTheme.typography.titleLarge)
                listOf("Akademik" to "academic", "Catatan" to "notes", "Keuangan" to "finance", "Nilai semester" to "grades", "Belajar" to "learning").forEach { (label, destination) ->
                    NavigationDrawerItem(
                        label = { Text(label) },
                        selected = route == destination,
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag("drawer-$destination"),
                        onClick = { route = destination; scope.launch { drawerState.close() } }
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                NavigationDrawerItem(label = { Text("Profil & tampilan") }, selected = route == "profile",
                    onClick = { route = "profile"; scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag("drawer-profile"))
            }
        }
    ) {
    Scaffold(
        modifier = Modifier.testTag("studenthub-root").semantics { stateDescription = if (darkTheme) "Tema gelap" else "Tema terang" },
        topBar = {
            TopAppBar(
                title = { Text(if (route == "home") "StudentHub" else primary.firstOrNull { it.route == route }?.title ?: when (route) {
                    "notes" -> "Catatan"
                    "finance" -> "Keuangan"
                    "grades" -> "Pelacak akademik"
                    "learning" -> "Rekomendasi belajar"
                    "course" -> "Detail mata kuliah"
                    "editor" -> "Editor catatan"
                    "academic" -> "Akademik"
                    "profile" -> "Profil"
                    else -> "StudentHub"
                }) },
                navigationIcon = {
                    if (route == "course" || route == "editor") IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    } else IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, "Buka menu")
                    }
                },
                actions = {
                    IconButton(onClick = { reminders = true }) { Icon(Icons.Outlined.Notifications, "Pengingat") }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                primary.forEach { screen ->
                    NavigationBarItem(
                        modifier = Modifier.testTag("nav-${screen.route}"),
                        selected = route == screen.route,
                        onClick = { route = screen.route },
                        icon = { Icon(screen.icon, null) },
                        label = { Text(screen.title) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (route) {
                "home" -> TodayScreen(onNavigate = { route = it }, onReminders = { reminders = true })
                "schedule" -> ScheduleScreen(onCourseClick = { courseId = it; route = "course" }, onLearningClick = { route = "learning" })
                "academic" -> AcademicScreen { courseId = it; route = "course" }
                "course" -> CourseDetailScreen(courseId) { route = "notes" }
                "notes" -> NotesScreen { noteId = it; route = "editor" }
                "editor" -> NoteEditorScreen(noteId) { route = "notes" }
                "finance" -> FinanceScreen()
                "grades" -> GradesScreen()
                "learning" -> LearningScreen()
                "profile" -> ProfileScreen(themeContent = { ThemeSettings(themeChoice, onThemeChange) })
                else -> ThemeSettings(themeChoice, onThemeChange)
            }
        }
    }
    }
    if (reminders) AlertDialog(
        onDismissRequest = { reminders = false },
        title = { Text("Pengingatmu") },
        text = { Text("Kelas IF570 • 09.00\nPraktikum 3 • 17.00\nTarget belajar navigasi • 80%\nBayar kos • sebelum 10 Okt\n\nData contoh dari desain. Notifikasi sistem belum diaktifkan.") },
        confirmButton = { TextButton(onClick = { reminders = false }) { Text("Tutup") } }
    )
}
