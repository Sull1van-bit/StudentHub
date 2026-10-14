package org.umn.application.studenthub.ui.academic

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.umn.application.studenthub.ui.localization.lString
import org.umn.application.studenthub.data.local.StudentHubDatabase
import org.umn.application.studenthub.ui.localization.LocalAppLanguage
import org.umn.application.studenthub.ui.localization.AppLanguage

val LocalDrawerOpen = compositionLocalOf { false }

fun getCourseEnglishTitle(id: String, defaultTitle: String): String = when (id) {
    "IF570" -> "Mobile Application Programming"
    "EM105" -> "Technopreneurship"
    "UM321" -> "English 3"
    "IF590" -> "Information Technology Research"
    "IF542" -> "Deep Learning"
    "IF571" -> "Cybersecurity"
    "IF581" -> "Game Development"
    "IF510" -> "Advanced Database"
    "IF530" -> "Human-Computer Interaction"
    "IF550" -> "Software Engineering"
    else -> defaultTitle
}

fun getCourseIndonesianTitle(id: String, defaultTitle: String): String = when (id) {
    "IF570" -> "Pemrograman Aplikasi Mobile"
    "EM105" -> "Technopreneurship"
    "UM321" -> "Bahasa Inggris 3"
    "IF590" -> "Riset Teknologi Informasi"
    "IF542" -> "Deep Learning"
    "IF571" -> "Keamanan Siber"
    "IF581" -> "Pengembangan Game"
    "IF510" -> "Basis Data Lanjut"
    "IF530" -> "Interaksi Manusia & Komputer"
    "IF550" -> "Rekayasa Perangkat Lunak"
    else -> defaultTitle
}

fun formatSchedule(schedule: String, isEnglish: Boolean): String {
    if (!isEnglish) return schedule
    return schedule
        .replace("Senin", "Monday")
        .replace("Selasa", "Tuesday")
        .replace("Rabu", "Wednesday")
        .replace("Kamis", "Thursday")
        .replace("Jumat", "Friday")
        .replace("Sabtu", "Saturday")
        .replace("Minggu", "Sunday")
        .replace(".", ":")
}

fun formatRoom(room: String, isEnglish: Boolean): String {
    if (!isEnglish) return room
    return room
        .replace("Ruang ", "Room ")
        .replace("Lab AI", "AI Lab")
        .replace("Lab Cyber", "Cyber Lab")
        .replace("Lab Game", "Game Lab")
        .replace("Lab IMK", "HCI Lab")
}

@Composable
private fun rememberAcademicState(): AcademicState? {
    val context = LocalContext.current.applicationContext
    var state by remember {
        mutableStateOf<AcademicState?>(
            runCatching {
                val prefs = context.getSharedPreferences("academic", Context.MODE_PRIVATE)
                val appPrefs = context.getSharedPreferences("studenthub_prefs", Context.MODE_PRIVATE)
                val isOnboarded = prefs.getBoolean("onboarding_completed", false) || appPrefs.getBoolean("has_completed_onboarding", false)
                AcademicState(
                    org.umn.application.studenthub.data.local.StudentHubDatabase.get(context),
                    { key ->
                        if (key == "onboarding_completed") {
                            isOnboarded.toString()
                        } else {
                            runCatching { prefs.all[key]?.toString() }.getOrNull()
                        }
                    },
                    { key, value -> prefs.edit().putString(key, value).apply() }
                )
            }.getOrNull()
        )
    }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(context, attempt) {
        if (state == null) {
            failed = false
            withContext(Dispatchers.IO) {
                runCatching {
                    val prefs = context.getSharedPreferences("academic", Context.MODE_PRIVATE)
                    val appPrefs = context.getSharedPreferences("studenthub_prefs", Context.MODE_PRIVATE)
                    val isOnboarded = prefs.getBoolean("onboarding_completed", false) || appPrefs.getBoolean("has_completed_onboarding", false)
                    AcademicState(
                        org.umn.application.studenthub.data.local.StudentHubDatabase.get(context),
                        { key ->
                            if (key == "onboarding_completed") {
                                isOnboarded.toString()
                            } else {
                                runCatching { prefs.all[key]?.toString() }.getOrNull()
                            }
                        },
                        { key, value -> prefs.edit().putString(key, value).apply() }
                    )
                }
            }.onSuccess { state = it }.onFailure { failed = true }
        }
    }
    if (state == null) AcademicBody {
        if (failed) {
            Text(lString("Data akademik tidak dapat dibaca. Data lama tidak diubah.", "Academic data could not be read. Existing data not modified."), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { attempt++ }) { Text(lString("Coba lagi", "Retry")) }
        } else { CircularProgressIndicator(); BodyText(lString("Memuat data akademik…", "Loading academic data…")) }
    }
    return state
}

@Composable
private fun AcademicBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp), content = content,
    )
}

@Composable
private fun BodyText(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Headline(text: String) = Text(text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)

@Composable
private fun SectionTitle(title: String, trailing: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (trailing != null) {
            if (onAction != null) TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) { Text(trailing) }
            else Text(trailing, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, leadingIcon = if (selected) {
        { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
    } else null)
}

@Composable
private fun TaskRow(task: AcademicTask, checked: Boolean, onToggle: () -> Unit, compact: Boolean = false) {
    val taskTitle = when (task.id) {
        "navigation" -> lString("Praktikum 3: Navigasi", "Practicum 3: Navigation")
        "architecture" -> lString("Diagram arsitektur", "Architecture diagram")
        "mini-project" -> lString("Proyek mini: Daftar tugas", "Mini project: Task list")
        "layout" -> lString("Praktikum 2: Layout", "Practicum 2: Layout")
        "components" -> lString("Praktikum 1: Komponen UI", "Practicum 1: UI Components")
        "research-prop" -> lString("Proposal Topik Riset", "Research Topic Proposal")
        "deep-learning-lab" -> lString("Lab 1: Pengenalan PyTorch", "Lab 1: PyTorch Introduction")
        else -> task.title
    }
    val deadlineText = when (task.id) {
        "navigation" -> lString("HARI INI • 17.00", "TODAY • 17:00")
        "architecture" -> lString("RABU, 7 OKT • IF550", "WEDNESDAY, OCT 7 • IF550")
        "mini-project" -> lString("JUMAT, 9 OKT • 23.59", "FRIDAY, OCT 9 • 23:59")
        "research-prop" -> lString("RABU, 14 OKT • 23.59", "WEDNESDAY, OCT 14 • 23:59")
        "deep-learning-lab" -> lString("KAMIS, 15 OKT • 17.00", "THURSDAY, OCT 15 • 17:00")
        else -> task.deadline
    }
    val detailText = when {
        compact && task.id == "navigation" -> lString("Hari ini, 17.00 · IF570", "Today, 17:00 · IF570")
        compact && task.id == "architecture" -> lString("Rabu, 7 Okt · IF550", "Wednesday, Oct 7 · IF550")
        compact && task.id == "mini-project" -> lString("Jumat, 9 Okt · IF570", "Friday, Oct 9 · IF570")
        compact && task.id == "research-prop" -> lString("Rabu, 14 Okt · IF590", "Wednesday, Oct 14 · IF590")
        compact && task.id == "deep-learning-lab" -> lString("Kamis, 15 Okt · IF542", "Thursday, Oct 15 · IF542")
        task.id == "navigation" -> lString("Laporan PDF & kode proyek Android", "PDF report & Android project code")
        task.id == "mini-project" -> lString("Implementasi penyimpanan lokal", "Local storage implementation")
        task.id == "layout" -> lString("Dikumpulkan 28 Sep • Nilai 88", "Submitted Sep 28 • Grade 88")
        task.id == "components" -> lString("Dikumpulkan 21 Sep • Nilai 85", "Submitted Sep 21 • Grade 85")
        else -> task.detail
    }
    ListItem(
        modifier = Modifier.fillMaxWidth().testTag("task-${task.id}").toggleable(checked, role = Role.Checkbox) { onToggle() },
        headlineContent = {
            Text(
                taskTitle,
                style = if (checked) {
                    MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    MaterialTheme.typography.bodyLarge
                }
            )
        },
        overlineContent = if (deadlineText.isNotEmpty() && !compact) { { Text(deadlineText) } } else null,
        supportingContent = { BodyText(detailText) },
        leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

@Composable
private fun Progress(value: Float) = LinearProgressIndicator(progress = { value }, modifier = Modifier.fillMaxWidth().height(4.dp))

@Composable
private fun SummaryCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun CourseDetailScreen(courseId: String, onScanClick: () -> Unit = {}, onNotesClick: () -> Unit) {
    val state = rememberAcademicState() ?: return
    val course = state.currentCourses().firstOrNull { it.id == courseId } ?: academicCourses.firstOrNull { it.id == courseId } ?: semester5Courses.firstOrNull { it.id == courseId }
    var tab by rememberSaveable(courseId) { mutableStateOf("Tugas") }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    if (course == null) {
        AcademicBody { Headline(lString("Mata kuliah tidak ditemukan", "Course Not Found")); BodyText(lString("Data untuk $courseId belum tersedia.", "Data for $courseId is not available.")) }
        return
    }
    val total = runCatching { state.totalFor(course.id) }.getOrDefault(course.total).coerceAtLeast(1)
    val completed = runCatching { state.completedFor(course.id) }.getOrDefault(course.completed).coerceAtMost(total)
    val isEn = LocalAppLanguage.current == AppLanguage.ENGLISH
    val courseTitle = if (isEn) getCourseEnglishTitle(course.id, course.title) else getCourseIndonesianTitle(course.id, course.title)
    val lecturerName = if (course.lecturer == "Dosen Pengampu") lString("Dosen Pengampu", "Course Lecturer") else course.lecturer
    val scheduleText = formatSchedule(course.schedule, isEn)
    val roomText = formatRoom(course.room, isEn)
    AcademicBody {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${course.id} • ${course.sks} " + lString("SKS", "credits"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Headline(courseTitle)
            BodyText(lString("Dosen: $lecturerName", "Lecturer: $lecturerName"))
            BodyText("$scheduleText · $roomText")
            if (course.description.isNotBlank()) {
                Text(course.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SummaryCard {
            SectionTitle(lString("Progres tugas", "Assignment progress"), "${completed * 100 / total}%")
            Progress(completed.toFloat() / total)
            val remaining = total - completed
            val remainingText = when {
                remaining == 0 -> lString(" Semua tugas selesai!", " All tasks completed!")
                remaining == 1 -> lString(" Tinggal satu lagi!", " Just one left!")
                remaining == 2 -> lString(" Tinggal dua lagi!", " Just two left!")
                else -> ""
            }
            BodyText(lString("$completed dari $total tugas selesai.", "$completed of $total tasks completed.") + remainingText)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(lString("Tugas", "Tasks"), tab == "Tugas") { tab = "Tugas" }
            val isDrawerOpen = LocalDrawerOpen.current
            ChoiceChip(if (isDrawerOpen) lString("Catatan Kuliah", "Class Notes") else lString("Catatan", "Notes"), false, onNotesClick)
            ChoiceChip(lString("Nilai", "Grades"), tab == "Nilai") { tab = "Nilai" }
        }
        if (tab == "Nilai") {
            val grade = state.grades[course.id] ?: AcademicGrade(85, "A")
            SummaryCard {
                SectionTitle(lString("Nilai sementara", "Interim grade"), "${grade.letter} · ${decimal(gradePoints.getValue(grade.letter))}")
                Headline(lString("Skor ${grade.score}", "Score ${grade.score}"))
                BodyText(lString("Nilai ini disimpan secara lokal dan memengaruhi perhitungan IPS.", "This grade is saved locally and affects term GPA calculations."))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(lString("Tugas aktif", "Active tasks"), lString("${state.totalFor(course.id) - completed} tugas", "${state.totalFor(course.id) - completed} tasks"))
                val active = state.tasks.filter { it.courseId == course.id && !it.initiallyDone }
                active.forEachIndexed { index, task ->
                    TaskRow(task, state.completion[task.id] == true, { state.toggleTask(task.id) })
                    if (task.id == "navigation") Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(8.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            BodyText(lString("Pengingat aktif • 1 jam sebelum tenggat", "Active reminder • 1 hour before deadline"))
                            Text(lString("Preferensi contoh; notifikasi belum dijadwalkan.", "Sample preference; notifications not yet scheduled."), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (index < active.lastIndex) Divider()
                }
                if (active.isEmpty()) BodyText(if (state.totalFor(course.id) == completed) lString("Tidak ada tugas aktif", "No active tasks") else lString("Detail tugas belum tersedia pada data lokal.", "Task details not available in local data."))
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(lString("Terakhir selesai", "Recently completed"), lString("Lihat $completed", "View $completed")) { showHistory = true }
                state.tasks.filter { it.courseId == course.id && it.initiallyDone }.forEach { task ->
                    TaskRow(task, state.completion[task.id] == true, { state.toggleTask(task.id) })
                    Divider()
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showAdd = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.School, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(lString("Tambah tugas", "Add task"))
                }
                OutlinedButton(onClick = onScanClick, modifier = Modifier.weight(1f).testTag("course-scan-note")) {
                    Text(lString("Pindai Catatan", "Scan Notes"))
                }
            }
        }
    }
    if (showAdd) TextEditDialog(lString("Tambah tugas", "Add task"), lString("Nama tugas", "Task name"), "", { showAdd = false }) { name ->
        state.addTask(courseId, name).also { if (it) showAdd = false }
    }
    if (showHistory) AlertDialog(onDismissRequest = { showHistory = false }, title = { Text(lString("Tugas selesai", "Completed tasks")) }, text = {
        BodyText(lString("$completed dari ${state.totalFor(course.id)} tugas selesai. Hanya rincian tugas yang tersedia secara lokal ditampilkan di halaman ini.", "$completed of ${state.totalFor(course.id)} tasks completed. Only tasks available locally are shown here."))
    }, confirmButton = { TextButton(onClick = { showHistory = false }) { Text(lString("Tutup", "Close")) } })
}

@Composable
fun GradesScreen() {
    val state = rememberAcademicState() ?: return
    val context = LocalContext.current
    val database = remember(context) { StudentHubDatabase.get(context) }
    val isEn = LocalAppLanguage.current == AppLanguage.ENGLISH
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var showGoal by rememberSaveable { mutableStateOf(false) }
    var semesterInfo by rememberSaveable { mutableStateOf(false) }

    val prefs = remember(context) { context.getSharedPreferences("academic", Context.MODE_PRIVATE) }
    val appPrefs = remember(context) { context.getSharedPreferences("studenthub_prefs", Context.MODE_PRIVATE) }
    val isOnboarded = prefs.getBoolean("onboarding_completed", false) || appPrefs.getBoolean("has_completed_onboarding", false)

    val dbProfile = remember { runCatching { database.profile() }.getOrNull() }
    val studentName = dbProfile?.name?.takeIf { it.isNotBlank() } ?: "Nadia Putri"
    val program = dbProfile?.program?.takeIf { it.isNotBlank() } ?: lString("Informatika", "Computer Science")
    val semesterNum = dbProfile?.semester ?: 5

    val coursesToDisplay = if (isOnboarded) semester5Courses else academicCourses
    val totalSks = coursesToDisplay.sumOf { it.sks }

    AcademicBody {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Headline(if (isEn) "Grades & GPA" else "Setiap kemajuan berarti.")
            BodyText("$studentName • $program")
            ChoiceChip(lString("Semester $semesterNum · 2026", "Semester $semesterNum · 2026"), true) { semesterInfo = true }
        }
        SummaryCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BodyText(lString("IPS sementara", "Term GPA")); Headline(decimal(state.semesterGpa))
                    BodyText(if (isOnboarded) lString("$totalSks SKS semester ini", "$totalSks credits this semester") else lString("12 SKS semester ini", "12 credits this semester"))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BodyText(lString("IPK Kumulatif", "Cumulative GPA")); Headline(decimal(state.cumulativeGpa)); BodyText(lString("Riwayat + semester ini", "History + this term"))
                }
            }
            BodyText(lString("IPS memakai nilai sementara. IPK dihitung dinamis dari riwayat semester lalu dan nilai berjalan.", "Term GPA uses interim grades. Cumulative GPA is computed dynamically from prior history and current grades."))
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(lString("Perjalanan IPS", "GPA Journey"), lString("4 semester", "4 semesters"))
            Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
                listOf(3.40, 3.50, 3.60, 3.75).forEachIndexed { index, value ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(decimal(value), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Box(Modifier.width(42.dp).height((value * 22).dp).background(
                            if (index == 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)))
                        Text(lString("Sem ${index + 1}", "Term ${index + 1}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(lString("Nilai semester $semesterNum", "Semester $semesterNum Grades"), lString("Edit nilai", "Edit Grade")) { editing = "IF570" }
            Column {
                coursesToDisplay.forEach { course ->
                    val grade = state.grades[course.id] ?: AcademicGrade(85, "A")
                    val courseTitle = if (isEn) getCourseEnglishTitle(course.id, course.title) else getCourseIndonesianTitle(course.id, course.title)
                    ListItem(modifier = Modifier.clickable { editing = course.id },
                        overlineContent = { Text("${course.id} · ${course.sks} SKS · " + lString("Skor ${grade.score}", "Score ${grade.score}")) },
                        headlineContent = { Text(courseTitle) }, supportingContent = { BodyText(lString("Nilai sementara", "Interim grade")) },
                        trailingContent = { Text("${grade.letter} · ${decimal(gradePoints.getValue(grade.letter))}") },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
                    Divider()
                }
            }
        }
        Button(onClick = { showGoal = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Star, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(lString("Lihat target belajar", "View Study Target"))
        }
    }
    editing?.let { courseId ->
        key(courseId) {
            val grade = state.grades[courseId] ?: AcademicGrade(85, "A")
            var score by rememberSaveable { mutableStateOf(grade.score.toString()) }
            var letter by rememberSaveable { mutableStateOf(grade.letter) }
            var error by rememberSaveable { mutableStateOf(false) }
            var saveError by remember { mutableStateOf<String?>(null) }
            var saving by remember { mutableStateOf(false) }
            AlertDialog(onDismissRequest = { editing = null }, title = { Text(lString("Edit nilai · $courseId", "Edit Grade · $courseId")) }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BodyText(lString("Catatan lokal. Masukkan skor 0–100 untuk mengupdate nilai huruf dan IPK.", "Local record. Enter score 0–100 to update letter grade and GPA."))
                    OutlinedTextField(
                        value = score,
                        onValueChange = {
                            score = it
                            val s = it.toIntOrNull()
                            if (s != null && s in 0..100) {
                                error = false
                                letter = when {
                                    s >= 85 -> "A"
                                    s >= 80 -> "A-"
                                    s >= 75 -> "B+"
                                    s >= 70 -> "B"
                                    s >= 65 -> "B-"
                                    s >= 60 -> "C+"
                                    s >= 55 -> "C"
                                    s >= 40 -> "D"
                                    else -> "E"
                                }
                            } else {
                                error = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("grade-score"),
                        label = { Text(lString("Skor (0–100)", "Score (0–100)")) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        isError = error,
                        supportingText = if (error) { { Text(lString("Skor harus berupa angka antara 0 dan 100.", "Score must be a number between 0 and 100.")) } } else null
                    )
                    Text(lString("Nilai Huruf Terpilih:", "Selected Letter Grade:"), style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        gradePoints.keys.forEach { option -> ChoiceChip(option, letter == option) { letter = option } }
                    }
                    saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }, confirmButton = { TextButton(enabled = !saving && !error && score.toIntOrNull() in 0..100, onClick = {
                saving = true
                val ok = runCatching { state.updateGrade(courseId, score, letter) }.getOrDefault(false)
                if (ok) editing = null else error = true
                saving = false
            }) { Text(lString("Simpan", "Save")) } }, dismissButton = { TextButton(onClick = { editing = null }) { Text(lString("Batal", "Cancel")) } })
        }
    }
    if (showGoal) GoalDialog(state) { showGoal = false }
    if (semesterInfo) AlertDialog(onDismissRequest = { semesterInfo = false }, title = { Text("Semester 5 · 2026") },
        text = { BodyText(lString("Nilai sementara semester ini dapat diedit. Riwayat semester 1–4 ditampilkan pada Perjalanan IPS.", "Interim grades for this semester can be edited. Semesters 1–4 history is shown on the GPA Journey.")) },
        confirmButton = { TextButton(onClick = { semesterInfo = false }) { Text(lString("Tutup", "Close")) } })
}

@Composable
fun LearningScreen() {
    val state = rememberAcademicState() ?: return
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("academic", Context.MODE_PRIVATE) }
    var lifecycleDone by remember { mutableStateOf(prefs.getBoolean("learning.lifecycle", true)) }
    var backStackDone by remember { mutableStateOf(prefs.getBoolean("learning.back-stack", false)) }
    var plan by remember { mutableStateOf(prefs.getString("learning.plan", "15.30–16.00 · IF570").orEmpty()) }
    var goalOpen by rememberSaveable { mutableStateOf(false) }
    var planOpen by rememberSaveable { mutableStateOf(false) }
    var sessionOpen by rememberSaveable { mutableStateOf(false) }
    AcademicBody {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Headline(if (org.umn.application.studenthub.ui.localization.LocalAppLanguage.current == org.umn.application.studenthub.ui.localization.AppLanguage.ENGLISH) "Study Recommendations" else "Fokus kecil, hasil besar.")
            BodyText(lString("Prioritas dari mata kuliah, nilai sementara, dan tenggatmu.", "Priorities from your courses, interim grades, and deadlines."))
            ChoiceChip(lString("Target IPK: ${decimal(state.goal)}", "GPA Target: ${decimal(state.goal)}"), true) { goalOpen = true }
        }
        SummaryCard {
            SectionTitle(lString("PRIORITAS HARI INI", "TODAY'S PRIORITY"), if (backStackDone) "100%" else "80%")
            Text(lString("Tuntaskan latihan navigasi", "Complete navigation exercises"), style = MaterialTheme.typography.titleLarge)
            BodyText("IF570 · " + lString("${if (backStackDone) 5 else 4} dari 5 latihan selesai", "${if (backStackDone) 5 else 4} of 5 exercises completed"))
            Progress(if (backStackDone) 1f else 0.8f)
            BodyText(lString("Praktikum 3 jatuh tempo hari ini, 17.00. Selesaikan 1 latihan back stack sebelum mengumpulkan.", "Practicum 3 due today at 17.00. Complete 1 back stack exercise before submitting."))
            Button(onClick = { sessionOpen = true }) { Text(if (backStackDone) lString("Tinjau target", "Review target") else lString("Lanjutkan target", "Continue target")) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(lString("Berikutnya untukmu", "Next for you"))
            val appPrefs = remember(context) { context.getSharedPreferences("studenthub_prefs", Context.MODE_PRIVATE) }
            val isOnboarded = prefs.getBoolean("onboarding_completed", false) || appPrefs.getBoolean("has_completed_onboarding", false)
            if (isOnboarded) {
                RecommendationRow("IF590 · " + lString("PERSIAPAN RISET", "RESEARCH PREPARATION"), lString("Kajian literatur metodologi", "Methodology literature review"), lString("Nilai ${state.grades["IF590"]?.score ?: 85} · target 90 · 45 menit", "Score ${state.grades["IF590"]?.score ?: 85} · target 90 · 45 mins"))
                BodyText(lString("Kumpulkan 3 paper terkait topik skripsi sebelum asistensi Rabu, 14 Okt.", "Collect 3 relevant papers for thesis topic before assistance Wednesday, Oct 14."))
                Divider()
                RecommendationRow("IF542 · " + lString("SIAPKAN PRAKTIKUM", "PREPARE LAB"), lString("Eksperimen PyTorch tensor", "PyTorch tensor experiment"), lString("Nilai ${state.grades["IF542"]?.score ?: 85} · 30 menit · Kamis, 15 Okt", "Score ${state.grades["IF542"]?.score ?: 85} · 30 mins · Thursday, Oct 15"))
                BodyText(lString("Ulas konsep backpropagation dan implementasi autograd sebelum sesi lab.", "Review backpropagation concepts and autograd implementation before lab session."))
            } else {
                RecommendationRow("IF510 · " + lString("PERKUAT PEMAHAMAN", "DEEPEN UNDERSTANDING"), lString("Latihan optimasi query", "Query optimization practice"), lString("Nilai ${state.grades["IF510"]?.score ?: 74} · target 80 · 45 menit", "Score ${state.grades["IF510"]?.score ?: 74} · target 80 · 45 mins"))
                BodyText(lString("Kerjakan 5 soal indeks dan baca ulang catatan B-tree. Kuis dijadwalkan Kamis, 8 Okt.", "Solve 5 index exercises and re-read B-tree notes. Quiz scheduled Thursday, Oct 8."))
                Divider()
                RecommendationRow("IF550 · " + lString("SIAPKAN TENGGAT", "PREPARE DEADLINE"), lString("Ulas pola arsitektur", "Review architecture patterns"), lString("Nilai ${state.grades["IF550"]?.score ?: 82} · 30 menit · Selasa, 6 Okt", "Score ${state.grades["IF550"]?.score ?: 82} · 30 mins · Tuesday, Oct 6"))
                BodyText(lString("Bandingkan MVC dan MVVM sebelum tugas diagram arsitektur, Rabu, 7 Okt.", "Compare MVC and MVVM before architecture diagram assignment, Wednesday, Oct 7."))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(lString("Rencana belajar hari ini", "Today's study plan"), lString("Ubah", "Edit")) { planOpen = true }
            TaskRow(AcademicTask("lifecycle", "IF570", lString("Ulangi Activity lifecycle", "Review Activity lifecycle"), "20 menit · " + if (lifecycleDone) lString("Selesai pukul 08.00", "Completed at 08.00") else lString("Belum selesai", "Not completed")), lifecycleDone, {
                lifecycleDone = !lifecycleDone
                prefs.edit().putBoolean("learning.lifecycle", lifecycleDone).apply()
            })
            Divider()
            TaskRow(AcademicTask("back-stack", "IF570", lString("Latihan back stack", "Back stack exercise"), plan), backStackDone, {
                backStackDone = !backStackDone
                prefs.edit().putBoolean("learning.back-stack", backStackDone).apply()
            })
        }
        BodyText(lString("Saran ini adalah panduan. Sesuaikan target dan waktu belajar dengan kebutuhanmu.", "These suggestions are a guide. Adjust study targets and schedule according to your needs."))
    }
    if (goalOpen) GoalDialog(state) { goalOpen = false }
    if (planOpen) TextEditDialog(lString("Rencana belajar hari ini", "Today's study plan"), lString("Waktu / rencana latihan", "Time / exercise plan"), plan, { planOpen = false }) {
        if (it.isBlank()) false else {
            plan = it.trim(); prefs.edit().putString("learning.plan", plan).apply(); planOpen = false; true
        }
    }
    if (sessionOpen) AlertDialog(onDismissRequest = { sessionOpen = false }, title = { Text(lString("Latihan back stack", "Back stack practice")) },
        text = { BodyText(lString("Selesaikan 1 latihan back stack sebelum mengumpulkan. Tandai selesai setelah kamu mengerjakannya; status ini hanya disimpan di perangkat.", "Complete 1 back stack exercise before submitting. Mark done after you finish; this status is saved only on device.")) },
        confirmButton = { TextButton(onClick = {
            backStackDone = true; prefs.edit().putBoolean("learning.back-stack", true).apply(); sessionOpen = false
        }) { Text(lString("Tandai selesai", "Mark completed")) } }, dismissButton = { TextButton(onClick = { sessionOpen = false }) { Text(lString("Kembali", "Back")) } })
}

@Composable
private fun RecommendationRow(overline: String, title: String, supporting: String) {
    ListItem(overlineContent = { Text(overline) }, headlineContent = { Text(title) }, supportingContent = { BodyText(supporting) },
        leadingContent = { Icon(Icons.Default.School, null) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
}

@Composable
private fun GoalDialog(state: AcademicState, onDismiss: () -> Unit) {
    TextEditDialog(lString("Target belajar", "Study target"), lString("Target IPK (0–4)", "GPA target (0–4)"), decimal(state.goal), onDismiss) { value ->
        withContext(Dispatchers.IO) { state.updateGoal(value) }.also { if (it) onDismiss() }
    }
}

private fun decimal(value: Double): String = String.format(Locale.forLanguageTag("id-ID"), "%.2f", value)

@Composable
private fun TextEditDialog(title: String, label: String, initial: String, onDismiss: () -> Unit, onSave: suspend (String) -> Boolean) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var value by rememberSaveable { mutableStateOf(initial) }
    var error by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        OutlinedTextField(value, { value = it; error = false }, label = { Text(label) }, isError = error,
            supportingText = if (error) { { Text(lString("Periksa kembali nilai yang dimasukkan.", "Please check the entered value.")) } } else null)
    }, confirmButton = { TextButton(enabled = !saving, onClick = {
        saving = true
        scope.launch { error = !runCatching { onSave(value) }.getOrDefault(false); saving = false }
    }) { Text(lString("Simpan", "Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(lString("Batal", "Cancel")) } })
}

/** Scrollable content only. Navigation and system bars belong to the host Scaffold. */
@Composable
fun AcademicScreen(onCourseClick: (String) -> Unit) {
    val state = rememberAcademicState() ?: return
    val context = LocalContext.current
    val database = remember(context) { StudentHubDatabase.get(context) }
    val isEn = LocalAppLanguage.current == AppLanguage.ENGLISH
    var allTasks by rememberSaveable { mutableStateOf(false) }

    val prefs = remember(context) { context.getSharedPreferences("academic", Context.MODE_PRIVATE) }
    val appPrefs = remember(context) { context.getSharedPreferences("studenthub_prefs", Context.MODE_PRIVATE) }
    val isOnboarded = prefs.getBoolean("onboarding_completed", false) || appPrefs.getBoolean("has_completed_onboarding", false)

    val dbProfile = remember { runCatching { database.profile() }.getOrNull() }
    val studentName = dbProfile?.name?.takeIf { it.isNotBlank() } ?: "Nadia Putri"
    val program = dbProfile?.program?.takeIf { it.isNotBlank() } ?: lString("Informatika", "Computer Science")
    val semesterNum = dbProfile?.semester ?: 5

    val coursesToDisplay = if (isOnboarded) semester5Courses else academicCourses
    val totalSks = coursesToDisplay.sumOf { it.sks }
    val courseCount = coursesToDisplay.size
    val activeTasksCount = state.activeTaskCount

    AcademicBody {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Headline("Semester $semesterNum")
            BodyText("$program • $studentName")
            Text(
                if (isOnboarded) {
                    lString(
                        "$courseCount mata kuliah · $totalSks SKS · $activeTasksCount tugas aktif",
                        "$courseCount courses · $totalSks credits · $activeTasksCount active tasks"
                    )
                } else {
                    lString(
                        "4 mata kuliah · 12 SKS · $activeTasksCount tugas aktif",
                        "4 courses · 12 credits · $activeTasksCount active tasks"
                    )
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(lString("Mata kuliah", "Courses"), !allTasks) { allTasks = false }
            ChoiceChip(lString("Semua tugas", "All tasks"), allTasks) { allTasks = true }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(lString("Perlu diselesaikan", "To complete"), lString("$activeTasksCount tugas", "$activeTasksCount tasks"))
            val currentCourseIds = coursesToDisplay.map { it.id }.toSet()
            val activeTasksList = state.tasks.filter { it.courseId in currentCourseIds && !it.initiallyDone }
            val tasks = if (allTasks) activeTasksList else activeTasksList.take(2)
            tasks.forEachIndexed { index, task ->
                TaskRow(task, state.completion[task.id] == true, { state.toggleTask(task.id) }, compact = true)
                if (index < tasks.lastIndex) Divider()
            }
            if (tasks.isEmpty()) {
                BodyText(lString("Semua tugas telah diselesaikan!", "All tasks have been completed!"))
            } else if (allTasks && !isOnboarded) {
                BodyText(lString("IF510 memiliki 1 tugas aktif. Detail tugas belum tersedia pada data lokal.", "IF510 has 1 active task. Task details not available in local data."))
            }
        }
        if (!allTasks) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(lString("Mata kuliahmu", "Your courses"))
            coursesToDisplay.forEach { course ->
                val completed = state.completedFor(course.id)
                val total = state.totalFor(course.id)
                val remaining = total - completed
                val courseTitle = if (isEn) getCourseEnglishTitle(course.id, course.title) else getCourseIndonesianTitle(course.id, course.title)
                val sksLabel = lString("SKS", "credits")
                Column(Modifier.fillMaxWidth().testTag("course-${course.id}").clickable { onCourseClick(course.id) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ListItem(
                        headlineContent = { Text(courseTitle) },
                        overlineContent = { Text(course.id) },
                        supportingContent = { BodyText("${course.sks} $sksLabel · " + if (remaining == 0) lString("Tidak ada tugas aktif", "No active tasks") else lString("$remaining tugas aktif", "$remaining active tasks")) },
                        trailingContent = { Text("›") },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                    Progress(if (total > 0) completed.toFloat() / total else 0f)
                    BodyText(lString("$completed dari $total tugas selesai", "$completed of $total tasks completed"))
                    Divider()
                }
            }
        }
    }
}
