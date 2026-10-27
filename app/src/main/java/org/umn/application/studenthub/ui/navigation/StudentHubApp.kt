package org.umn.application.studenthub.ui.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.umn.application.studenthub.data.local.ReminderItemRow
import org.umn.application.studenthub.data.local.ReminderScheduler
import org.umn.application.studenthub.data.local.StudentHubDatabase
import org.umn.application.studenthub.ui.academic.AcademicScreen
import org.umn.application.studenthub.ui.academic.CourseDetailScreen
import org.umn.application.studenthub.ui.academic.GradesScreen
import org.umn.application.studenthub.ui.academic.LearningScreen
import org.umn.application.studenthub.ui.dashboard.TodayScreen
import org.umn.application.studenthub.ui.finance.FinanceScreen
import org.umn.application.studenthub.ui.localization.AppLanguage
import org.umn.application.studenthub.ui.localization.LocalAppLanguage
import org.umn.application.studenthub.ui.localization.lString
import org.umn.application.studenthub.ui.notes.NoteEditorScreen
import org.umn.application.studenthub.ui.notes.NotesScreen
import org.umn.application.studenthub.ui.notes.notesStore
import org.umn.application.studenthub.ui.onboarding.OnboardingScreen
import org.umn.application.studenthub.ui.profile.ProfileScreen
import org.umn.application.studenthub.ui.scanner.ScanNoteScreen
import org.umn.application.studenthub.ui.schedule.ScheduleScreen
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
            Build.FINGERPRINT.contains("robolectric", ignoreCase = true)
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
                    ModalDrawerSheet(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = 310.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                "StudentHub",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                lString("Teman kuliah harianmu", "Your daily campus companion"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            lString("Kuliah", "Academics"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                        )
                        val academicDrawerItems = listOf(
                            Triple(
                                lString("Mata Kuliah & Tugas", "Courses & Tasks"),
                                lString("Daftar kelas, SKS, dan tenggat tugas", "Classes, credits, and task deadlines"),
                                Screen.Academic
                            ),
                            Triple(
                                lString("Nilai & Hitung IPK", "Grades & GPA"),
                                lString("IPS semester ini, IPK, dan target nilai", "Term GPA, cumulative GPA, and target"),
                                Screen.Grades
                            ),
                            Triple(
                                lString("Target & Kesiapan Belajar", "Study Readiness"),
                                lString("Cek kesiapan ujian dan saran jam belajar", "Exam readiness and suggested study times"),
                                Screen.Learning
                            )
                        )
                        academicDrawerItems.forEach { (label, subtitle, screen) ->
                            NavigationDrawerItem(
                                label = {
                                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                icon = { Icon(screen.icon, contentDescription = null) },
                                selected = currentBaseRoute == screen.route,
                                modifier = Modifier
                                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                                    .testTag("drawer-${screen.route}"),
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }

                        HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

                        Text(
                            lString("Catatan", "Notes"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                        )
                        val noteDrawerItems = listOf(
                            Triple(
                                lString("Catatan Kuliah", "Class Notes"),
                                lString("Tulis materi, masukkan PDF, atau latihan kuis", "Write notes, import PDFs, or practice quizzes"),
                                Screen.Notes
                            ),
                            Triple(
                                lString("Foto Papan Tulis & Catatan", "Photo Whiteboard & Notes"),
                                lString("Foto papan tulis kelas, buku tulis, atau modul", "Photograph whiteboards, notebooks, or handouts"),
                                Screen.ScanNote
                            )
                        )
                        noteDrawerItems.forEach { (label, subtitle, screen) ->
                            NavigationDrawerItem(
                                label = {
                                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                icon = { Icon(screen.icon, contentDescription = null) },
                                selected = currentBaseRoute == screen.route,
                                modifier = Modifier
                                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                                    .testTag("drawer-${screen.route}"),
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }

                        HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

                        Text(
                            lString("Lainnya", "More"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                        )
                        NavigationDrawerItem(
                            label = {
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(lString("Uang Saku & Pengeluaran", "Allowance & Expenses"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                    Text(lString("Catat pemasukan, pengeluaran, dan sisa saldo", "Track income, spending, and balance"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            icon = { Icon(Screen.Finance.icon, contentDescription = null) },
                            selected = currentBaseRoute == Screen.Finance.route,
                            modifier = Modifier
                                .padding(NavigationDrawerItemDefaults.ItemPadding)
                                .testTag("drawer-${Screen.Finance.route}"),
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(Screen.Finance.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                        NavigationDrawerItem(
                            label = {
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(lString("Profil & Tampilan", "Profile & Appearance"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                    Text(lString("Data diri, semester, tema gelap, dan bahasa", "Profile info, semester, dark mode, and language"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
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
                            label = { Text(lString("Mulai Ulang dari Awal (Reset)", "Start Over (Reset Data)")) },
                            icon = { Icon(Screen.More.icon, contentDescription = null) },
                            selected = currentBaseRoute == "onboarding",
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                    context.getSharedPreferences("studenthub_prefs", Context.MODE_PRIVATE)
                                        .edit()
                                        .putBoolean("has_completed_onboarding", false)
                                        .apply()
                                    context.getSharedPreferences("academic", Context.MODE_PRIVATE)
                                        .edit()
                                        .putBoolean("onboarding_completed", false)
                                        .apply()
                                    withContext(Dispatchers.IO) {
                                        val db = StudentHubDatabase.get(context)
                                        db.reminders().forEach { ReminderScheduler.cancelReminder(context, it.id) }
                                        db.resetAllData()
                                    }
                                    navController.navigate("onboarding")
                                }
                            },
                            modifier = Modifier
                                .padding(NavigationDrawerItemDefaults.ItemPadding)
                                .testTag("drawer-onboarding")
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }
            ) {
                Scaffold(
                    modifier = Modifier
                        .testTag("studenthub-root")
                        .semantics { stateDescription = if (darkTheme) "Tema gelap" else "Tema terang" },
                    topBar = {
                        if (currentBaseRoute != "onboarding") {
                            TopAppBar(
                                title = {
                                    Text(
                                        when (currentBaseRoute) {
                                            "home" -> "StudentHub"
                                            "schedule" -> lString("Jadwal & Kalender", "Schedule & Calendar")
                                            "academic" -> lString("Mata Kuliah & Tugas", "Courses & Tasks")
                                            "notes" -> lString("Catatan Kuliah", "Class Notes")
                                            "scan_note" -> lString("Foto Papan Tulis & Catatan", "Photo Whiteboard & Notes")
                                            "finance" -> lString("Uang Saku & Pengeluaran", "Allowance & Expenses")
                                            "grades" -> lString("Nilai & Hitung IPK", "Grades & GPA")
                                            "learning" -> lString("Target & Kesiapan Belajar", "Study Readiness")
                                            "course" -> lString("Detail Mata Kuliah", "Course Details")
                                            "editor" -> lString("Tulis Catatan", "Edit Note")
                                            "profile" -> lString("Profil & Tampilan", "Profile & Appearance")
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
                                            Icon(Icons.Default.School, lString("Mata Kuliah & Tugas", "Courses & Tasks"))
                                        }
                                        IconButton(
                                            modifier = Modifier.testTag("nav-reminders"),
                                            onClick = { reminders = true }
                                        ) {
                                            Icon(Icons.Outlined.Notifications, lString("Pengingat", "Reminders"))
                                        }
                                    }
                                }
                            )
                        }
                    },
                    bottomBar = {
                        if (currentBaseRoute != "onboarding") {
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
                                                    "home" -> lString("Beranda", "Home")
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
                            .padding(if (currentBaseRoute == "onboarding") PaddingValues(0.dp) else padding)
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = startDestination,
                            modifier = Modifier.fillMaxSize(),
                            enterTransition = {
                                slideInHorizontally(
                                    initialOffsetX = { it },
                                    animationSpec = tween(300)
                                )
                            },
                            exitTransition = {
                                slideOutHorizontally(
                                    targetOffsetX = { -it },
                                    animationSpec = tween(300)
                                )
                            },
                            popEnterTransition = {
                                slideInHorizontally(
                                    initialOffsetX = { -it },
                                    animationSpec = tween(300)
                                )
                            },
                            popExitTransition = {
                                slideOutHorizontally(
                                    targetOffsetX = { it },
                                    animationSpec = tween(300)
                                )
                            }
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
                                    courseId = "",
                                    onNotesClick = {},
                                    onEditNote = { noteId, cId ->
                                        navController.navigate("editor?noteId=$noteId&courseId=$cId")
                                    },
                                    onScanClick = { navController.navigate("scan_note") }
                                )
                            }
                            composable(
                                route = "course/{courseId}",
                                arguments = listOf(navArgument("courseId") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                })
                            ) { backStackEntry ->
                                val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                                CourseDetailScreen(
                                    courseId = courseId,
                                    onNotesClick = {},
                                    onEditNote = { noteId, cId ->
                                        navController.navigate("editor?noteId=$noteId&courseId=$cId")
                                    },
                                    onScanClick = { navController.navigate("scan_note?courseId=$courseId") }
                                )
                            }
                            composable("notes") {
                                NotesScreen(
                                    initialCourseFilter = "",
                                    onScanNote = { cId ->
                                        navController.navigate("scan_note?courseId=$cId")
                                    },
                                    onEditNoteWithCourse = { id, cId ->
                                        navController.navigate("editor?noteId=$id&courseId=$cId")
                                    }
                                )
                            }
                            composable("editor") {
                                NoteEditorScreen(noteId = "", defaultCourse = "") {
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
                                        defaultValue = ""
                                    }
                                )
                            ) { backStackEntry ->
                                val noteId = backStackEntry.arguments?.getString("noteId") ?: ""
                                val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                                NoteEditorScreen(noteId = noteId, defaultCourse = courseId) {
                                    navController.popBackStack()
                                }
                            }
                            composable(
                                route = "scan_note?courseId={courseId}",
                                arguments = listOf(
                                    navArgument("courseId") {
                                        type = NavType.StringType
                                        defaultValue = ""
                                    }
                                )
                            ) { backStackEntry ->
                                val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
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
        if (reminders) {
            RemindersDialog(onDismiss = { reminders = false })
        }
    }
}

@Composable
fun RemindersDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val database = remember(context) { StudentHubDatabase.get(context) }
    val scope = rememberCoroutineScope()
    val isEn = LocalAppLanguage.current == AppLanguage.ENGLISH

    var version by remember { mutableIntStateOf(0) }
    var reminderList by remember {
        mutableStateOf(runCatching { database.reminders() }.getOrDefault(emptyList()))
    }
    var newTitle by rememberSaveable { mutableStateOf("") }
    var newTime by rememberSaveable { mutableStateOf("") }
    var newCategory by rememberSaveable { mutableStateOf("Kelas") }
    var newDetail by rememberSaveable { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        statusMessage = if (granted) {
            if (isEn) "System notification permission enabled." else "Izin notifikasi sistem diaktifkan."
        } else {
            if (isEn) "Notification permission denied; reminders remain saved in the app." else "Izin notifikasi ditolak; pengingat tetap tersimpan di aplikasi."
        }
    }

    LaunchedEffect(version) {
        withContext(Dispatchers.IO) {
            runCatching { database.reminders() }
        }.onSuccess {
            reminderList = it
        }
    }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth().padding(16.dp),
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(lString("Pengingat & Notifikasi", "Reminders & Notifications"))
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    lString(
                        "Atur pengingat kelas, tugas, belajar, dan batas pembayaranmu.",
                        "Manage your class, task, study, and payment deadline reminders."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(lString("Tambah Pengingat Baru", "Add New Reminder"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it },
                            label = { Text(lString("Judul pengingat", "Reminder title")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("reminder-title-input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            org.umn.application.studenthub.ui.components.TimePickerField(
                                value = newTime,
                                onValueChange = { newTime = it },
                                label = lString("Waktu (HH.mm)", "Time (HH.mm)"),
                                placeholder = "09.00",
                                testTag = "reminder-time-input",
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = newDetail,
                                onValueChange = { newDetail = it },
                                label = { Text(lString("Detail", "Detail")) },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("reminder-detail-input")
                            )
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Kelas", "Tugas", "Belajar", "Keuangan", "Umum").forEach { cat ->
                                FilterChip(
                                    selected = newCategory == cat,
                                    onClick = { newCategory = cat },
                                    label = { Text(cat) }
                                )
                            }
                        }
                        Button(
                            enabled = newTitle.isNotBlank(),
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    runCatching { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                                }
                                scope.launch {
                                    runCatching {
                                        database.addReminder(
                                            title = newTitle.trim(),
                                            detail = newDetail.trim(),
                                            time = newTime.trim().ifBlank { "09.00" },
                                            category = newCategory,
                                            enabled = true
                                        )
                                    }.onSuccess {
                                        newTitle = ""
                                        newTime = ""
                                        newDetail = ""
                                        reminderList = database.reminders()
                                        statusMessage = if (isEn) "Reminder saved & scheduled." else "Pengingat disimpan & dijadwalkan."
                                        version++
                                    }.onFailure {
                                        statusMessage = it.message ?: (if (isEn) "Failed to save reminder." else "Gagal menyimpan pengingat.")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("save-reminder-btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(lString("Simpan Pengingat", "Save Reminder"))
                        }
                    }
                }

                statusMessage?.let { msg ->
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }

                HorizontalDivider()

                Text(
                    lString("Daftar Pengingat (${reminderList.size})", "Reminders List (${reminderList.size})"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                if (reminderList.isEmpty()) {
                    Text(
                        lString("Belum ada pengingat tersimpan.", "No reminders saved yet."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    reminderList.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                val sub = listOfNotNull(
                                    item.category.takeIf { it.isNotBlank() },
                                    item.time.takeIf { it.isNotBlank() },
                                    item.detail.takeIf { it.isNotBlank() }
                                ).joinToString(" • ")
                                if (sub.isNotBlank()) {
                                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = item.enabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        database.toggleReminder(item.id, enabled)
                                        reminderList = database.reminders()
                                        version++
                                    }
                                },
                                modifier = Modifier.testTag("toggle-reminder-${item.id}")
                            )
                            IconButton(
                                onClick = {
                                    ReminderScheduler.postNotification(
                                        context = context,
                                        notificationId = item.id.hashCode(),
                                        title = "[${item.category}] ${item.title}",
                                        detail = item.detail.ifBlank { item.time }
                                    )
                                    statusMessage = if (isEn) "Notification '${item.title}' sent." else "Notifikasi '${item.title}' dikirim."
                                },
                                modifier = Modifier.testTag("trigger-reminder-${item.id}")
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = lString("Uji Notifikasi", "Test Notification"), tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        database.deleteReminder(item.id)
                                        reminderList = database.reminders()
                                        version++
                                    }
                                },
                                modifier = Modifier.testTag("delete-reminder-${item.id}")
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = lString("Hapus", "Delete"), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(lString("Tutup", "Close"))
            }
        }
    )
}
