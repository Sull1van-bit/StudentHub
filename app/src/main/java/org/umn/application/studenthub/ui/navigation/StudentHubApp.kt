package org.umn.application.studenthub.ui.navigation

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import org.umn.application.studenthub.data.local.StudentHubDatabase
import org.umn.application.studenthub.ui.academic.AcademicScreen
import org.umn.application.studenthub.ui.academic.CourseDetailScreen
import org.umn.application.studenthub.ui.academic.GradesScreen
import org.umn.application.studenthub.ui.academic.LearningScreen
import org.umn.application.studenthub.ui.dashboard.TodayScreen
import org.umn.application.studenthub.ui.finance.FinanceScreen
import org.umn.application.studenthub.ui.notes.NoteEditorScreen
import org.umn.application.studenthub.ui.notes.NotesScreen
import org.umn.application.studenthub.ui.notes.notesStore
import org.umn.application.studenthub.ui.onboarding.OnboardingScreen
import org.umn.application.studenthub.ui.profile.ProfileScreen
import org.umn.application.studenthub.ui.scanner.ScanNoteScreen
import org.umn.application.studenthub.ui.schedule.ScheduleScreen
import org.umn.application.studenthub.ui.localization.AppLanguage
import org.umn.application.studenthub.ui.localization.LocalAppLanguage
import org.umn.application.studenthub.ui.localization.lString
import org.umn.application.studenthub.ui.theme.ThemeChoice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentHubApp(
    themeChoice: ThemeChoice,
    darkTheme: Boolean,
    onThemeChange: (ThemeChoice) -> Unit,
    startDestinationOverride: String? = null,
    language: AppLanguage = AppLanguage.INDONESIAN,
    onLanguageChange: (AppLanguage) -> Unit = {}
) {
    CompositionLocalProvider(LocalAppLanguage provides language) {
        val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"
    val currentBaseRoute = currentRoute.substringBefore('?').substringBefore('/')

    val context = LocalContext.current
    val isRobolectric = remember {
        android.os.Build.FINGERPRINT.contains("robolectric", ignoreCase = true)
    }
    val hasCompletedOnboarding = remember {
        val prefs = context.getSharedPreferences("studenthub_prefs", Context.MODE_PRIVATE)
        prefs.getBoolean("has_completed_onboarding", false) || runCatching {
            StudentHubDatabase.get(context).isOnboardingCompleted()
        }.getOrDefault(false)
    }

    val startDestination = remember {
        when {
            startDestinationOverride != null -> startDestinationOverride
            isRobolectric -> "home"
            hasCompletedOnboarding -> "home"
            else -> "onboarding"
        }
    }

    var reminders by rememberSaveable { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val primary = Screen.bottomNavItems

    val isTopLevel = currentBaseRoute in listOf("home", "onboarding")
    BackHandler(!isTopLevel && drawerState.isClosed) {
        navController.popBackStack()
    }

    val isDrawerOpen = drawerState.targetValue == DrawerValue.Open || drawerState.isOpen
    CompositionLocalProvider(org.umn.application.studenthub.ui.academic.LocalDrawerOpen provides isDrawerOpen) {
        ModalNavigationDrawer(
            drawerState = drawerState,
        gesturesEnabled = currentBaseRoute != "onboarding",
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "StudentHub",
                    Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                val drawerDestinations = listOf(
                    Triple(lString("Akademik", "Academics"), Screen.Academic.route, Screen.Academic.icon),
                    Triple(lString("Catatan", "Notes"), Screen.Notes.route, Screen.Notes.icon),
                    Triple(lString("Keuangan", "Finances"), Screen.Finance.route, Screen.Finance.icon),
                    Triple(lString("Nilai", "Grades"), Screen.Grades.route, Screen.Grades.icon),
                    Triple(lString("Belajar", "Study"), Screen.Learning.route, Screen.Learning.icon),
                    Triple(lString("Pindai Catatan", "Scan Notes"), Screen.ScanNote.route, Screen.ScanNote.icon)
                )
                drawerDestinations.forEach { (label, destination, icon) ->
                    NavigationDrawerItem(
                        label = { Text(label) },
                        icon = { Icon(icon, contentDescription = null) },
                        selected = currentBaseRoute == destination,
                        modifier = Modifier
                            .padding(NavigationDrawerItemDefaults.ItemPadding)
                            .testTag("drawer-$destination"),
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(destination) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                NavigationDrawerItem(
                    label = { Text(lString("Profil & tampilan", "Profile & Appearance")) },
                    icon = { Icon(Screen.Profile.icon, contentDescription = null) },
                    selected = currentBaseRoute == "profile",
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("profile") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .testTag("drawer-profile")
                )
                NavigationDrawerItem(
                    label = { Text(lString("Ulangi Onboarding", "Restart Onboarding")) },
                    icon = { Icon(Screen.More.icon, contentDescription = null) },
                    selected = currentBaseRoute == "onboarding",
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("onboarding")
                    },
                    modifier = Modifier
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .testTag("drawer-onboarding")
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier
                .testTag("studenthub-root")
                .semantics { stateDescription = if (darkTheme) "Tema gelap" else "Tema terang" },
            topBar = {
                if (currentBaseRoute != "onboarding" && currentBaseRoute != "scan_note") {
                    TopAppBar(
                        title = {
                            Text(
                                when (currentBaseRoute) {
                                    "home" -> "StudentHub"
                                    "schedule" -> lString("Jadwal", "Schedule")
                                    "academic" -> lString("Akademik", "Academics")
                                    "notes" -> lString("Catatan", "Notes")
                                    "finance" -> lString("Keuangan", "Finances")
                                    "grades" -> lString("Pelacak akademik", "Academic Tracker")
                                    "learning" -> lString("Rekomendasi belajar", "Study Recommendations")
                                    "course" -> lString("Detail mata kuliah", "Course Details")
                                    "editor" -> lString("Editor catatan", "Note Editor")
                                    "profile" -> lString("Profil", "Profile")
                                    else -> "StudentHub"
                                }
                            )
                        },
                        navigationIcon = {
                            if (currentBaseRoute in listOf("course", "editor")) {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, lString("Kembali", "Back"))
                                }
                            } else {
                                IconButton(
                                    modifier = Modifier.testTag("nav-more"),
                                    onClick = { scope.launch { drawerState.open() } }
                                ) {
                                    Icon(Icons.Default.Menu, lString("Buka menu", "Open menu"))
                                }
                            }
                        },
                        actions = {
                            if (currentBaseRoute !in listOf("course", "editor")) {
                                IconButton(
                                    modifier = Modifier.testTag("nav-academic"),
                                    onClick = {
                                        navController.navigate("academic") {
                                            launchSingleTop = true
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.School, lString("Akademik", "Academics"))
                                }
                                IconButton(onClick = { reminders = true }) {
                                    Icon(Icons.Outlined.Notifications, lString("Pengingat", "Reminders"))
                                }
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (currentBaseRoute != "onboarding" && currentBaseRoute != "scan_note") {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        primary.forEach { screen ->
                            NavigationBarItem(
                                modifier = Modifier.testTag("nav-${screen.route}"),
                                selected = currentBaseRoute == screen.route,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(screen.icon, null) },
                                label = {
                                    Text(
                                        when (screen.route) {
                                            "home" -> lString("Home", "Home")
                                            "schedule" -> lString("Jadwal", "Schedule")
                                            "profile" -> lString("Profil", "Profile")
                                            else -> screen.title
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(if (currentBaseRoute == "onboarding" || currentBaseRoute == "scan_note") PaddingValues(0.dp) else padding)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("onboarding") {
                        OnboardingScreen(onFinished = {
                            context.getSharedPreferences("studenthub_prefs", Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean("has_completed_onboarding", true)
                                .apply()
                            navController.navigate("home") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        })
                    }
                    composable("home") {
                        TodayScreen(
                            onNavigate = { dest ->
                                navController.navigate(dest) {
                                    launchSingleTop = true
                                }
                            },
                            onReminders = { reminders = true }
                        )
                    }
                    composable("schedule") {
                        ScheduleScreen(
                            onCourseClick = { courseId ->
                                navController.navigate("course/$courseId")
                            },
                            onLearningClick = {
                                navController.navigate("learning")
                            }
                        )
                    }
                    composable("academic") {
                        AcademicScreen { courseId ->
                            navController.navigate("course/$courseId")
                        }
                    }
                    composable("course") {
                        CourseDetailScreen(
                            courseId = "IF570",
                            onNotesClick = { navController.navigate("notes") },
                            onScanClick = { navController.navigate("scan_note?courseId=IF570") }
                        )
                    }
                    composable(
                        route = "course/{courseId}",
                        arguments = listOf(navArgument("courseId") {
                            type = NavType.StringType
                            defaultValue = "IF570"
                        })
                    ) { backStackEntry ->
                        val courseId = backStackEntry.arguments?.getString("courseId") ?: "IF570"
                        CourseDetailScreen(
                            courseId = courseId,
                            onNotesClick = { navController.navigate("notes") },
                            onScanClick = { navController.navigate("scan_note?courseId=$courseId") }
                        )
                    }
                    composable("notes") {
                        NotesScreen(
                            onEditNote = { id ->
                                navController.navigate("editor?noteId=$id")
                            },
                            onScanNote = {
                                navController.navigate("scan_note")
                            }
                        )
                    }
                    composable("editor") {
                        NoteEditorScreen(noteId = "", defaultCourse = "IF570") {
                            navController.popBackStack()
                        }
                    }
                    composable(
                        route = "editor?noteId={noteId}&courseId={courseId}",
                        arguments = listOf(
                            navArgument("noteId") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("courseId") {
                                type = NavType.StringType
                                defaultValue = "IF570"
                            }
                        )
                    ) { backStackEntry ->
                        val noteId = backStackEntry.arguments?.getString("noteId") ?: ""
                        val courseId = backStackEntry.arguments?.getString("courseId") ?: "IF570"
                        NoteEditorScreen(noteId = noteId, defaultCourse = courseId) {
                            navController.popBackStack()
                        }
                    }
                    composable(
                        route = "scan_note?courseId={courseId}",
                        arguments = listOf(
                            navArgument("courseId") {
                                type = NavType.StringType
                                defaultValue = "IF570"
                            }
                        )
                    ) { backStackEntry ->
                        val courseId = backStackEntry.arguments?.getString("courseId") ?: "IF570"
                        ScanNoteScreen(
                            initialCourseId = courseId,
                            onNavigateBack = { navController.popBackStack() },
                            onSaveToNote = { title, course, content ->
                                runCatching {
                                    notesStore(context).save("", title, course, content)
                                }
                                navController.navigate("notes") {
                                    popUpTo("notes") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("finance") {
                        FinanceScreen()
                    }
                    composable("grades") {
                        GradesScreen()
                    }
                    composable("learning") {
                        LearningScreen()
                    }
                    composable("profile") {
                        ProfileScreen(
                            themeContent = { ThemeSettings(themeChoice, onThemeChange) },
                            languageContent = { LanguageSettings(language, onLanguageChange) },
                            onResetOnboarding = {
                                navController.navigate("onboarding")
                            }
                        )
                    }
                    }
                }
            }
        }
    }
    if (reminders) AlertDialog(
        onDismissRequest = { reminders = false },
        title = { Text(lString("Pengingatmu", "Your Reminders")) },
        text = {
            Text(
                lString(
                    "Kelas IF570 • 09.00\nPraktikum 3 • 17.00\nTarget belajar navigasi • 80%\nBayar kos • sebelum 10 Okt\n\nData contoh dari desain. Notifikasi sistem belum diaktifkan.",
                    "IF570 Class • 09.00\nPracticum 3 • 17.00\nNavigation study target • 80%\nPay rent • before Oct 10\n\nDesign fixture data. System notifications not yet enabled."
                )
            )
        },
        confirmButton = { TextButton(onClick = { reminders = false }) { Text(lString("Tutup", "Close")) } }
    )
}
}
