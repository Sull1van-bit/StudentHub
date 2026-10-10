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

@Composable
private fun rememberAcademicState(): AcademicState? {
    val context = LocalContext.current.applicationContext
    var state by remember { mutableStateOf<AcademicState?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(context, attempt) {
        failed = false
        withContext(Dispatchers.IO) { runCatching {
            val prefs = context.getSharedPreferences("academic", Context.MODE_PRIVATE)
            AcademicState(org.umn.application.studenthub.data.local.StudentHubDatabase.get(context),
                { prefs.getString(it, null) }, { key, value -> prefs.edit().putString(key, value).apply() })
        } }.onSuccess { state = it }.onFailure { failed = true }
    }
    if (state == null) AcademicBody {
        if (failed) {
            Text("Data akademik tidak dapat dibaca. Data lama tidak diubah.", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { attempt++ }) { Text("Coba lagi") }
        } else { CircularProgressIndicator(); BodyText("Memuat data akademik…") }
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
    ListItem(
        modifier = Modifier.fillMaxWidth().testTag("task-${task.id}").toggleable(checked, role = Role.Checkbox) { onToggle() },
        headlineContent = { Text(task.title) },
        overlineContent = if (task.deadline.isNotEmpty() && !compact) { { Text(task.deadline) } } else null,
        supportingContent = { BodyText(if (compact && task.id == "navigation") "Hari ini, 17.00 · IF570" else task.detail) },
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
fun CourseDetailScreen(courseId: String, onNotesClick: () -> Unit) {
    val state = rememberAcademicState() ?: return
    val course = academicCourses.firstOrNull { it.id == courseId }
    var tab by rememberSaveable(courseId) { mutableStateOf("Tugas") }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    if (course == null) {
        AcademicBody { Headline("Mata kuliah tidak ditemukan"); BodyText("Data untuk $courseId belum tersedia.") }
        return
    }
    val completed = state.completedFor(course.id)
    AcademicBody {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${course.id} • 3 SKS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Headline(course.title)
            if (course.id == "IF570") BodyText("Dosen: Budi Santoso, M.Kom.")
        }
        SummaryCard {
            SectionTitle("Progres tugas", "${completed * 100 / state.totalFor(course.id)}%")
            Progress(completed.toFloat() / state.totalFor(course.id))
            BodyText("$completed dari ${state.totalFor(course.id)} tugas selesai." + if (course.id == "IF570" && completed == 8) " Tinggal dua lagi!" else "")
            if (course.id == "IF570") BodyText("Senin, 09.00–10.40 · Ruang D.302")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Tugas", tab == "Tugas") { tab = "Tugas" }
            ChoiceChip("Catatan", false, onNotesClick)
            ChoiceChip("Nilai", tab == "Nilai") { tab = "Nilai" }
        }
        if (tab == "Nilai") {
            val grade = state.grades.getValue(course.id)
            SummaryCard {
                SectionTitle("Nilai sementara", "${grade.letter} · ${decimal(gradePoints.getValue(grade.letter))}")
                Headline("Skor ${grade.score}")
                BodyText("Nilai ini disimpan secara lokal, bukan transkrip resmi.")
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Tugas aktif", "${state.totalFor(course.id) - completed} tugas")
                val active = state.tasks.filter { it.courseId == course.id && !it.initiallyDone }
                active.forEachIndexed { index, task ->
                    TaskRow(task, state.completion[task.id] == true, { state.toggleTask(task.id) })
                    if (task.id == "navigation") Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(8.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            BodyText("Pengingat aktif • 1 jam sebelum tenggat")
                            Text("Preferensi contoh; notifikasi belum dijadwalkan.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (index < active.lastIndex) Divider()
                }
                if (active.isEmpty()) BodyText(if (state.totalFor(course.id) == completed) "Tidak ada tugas aktif" else "Detail tugas belum tersedia pada data lokal.")
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Terakhir selesai", "Lihat $completed") { showHistory = true }
                state.tasks.filter { it.courseId == course.id && it.initiallyDone }.forEach { task ->
                    TaskRow(task, state.completion[task.id] == true, { state.toggleTask(task.id) })
                    Divider()
                }
            }
            Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.School, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Tambah tugas")
            }
        }
    }
    if (showAdd) TextEditDialog("Tambah tugas", "Nama tugas", "", { showAdd = false }) { name ->
        state.addTask(courseId, name).also { if (it) showAdd = false }
    }
    if (showHistory) AlertDialog(onDismissRequest = { showHistory = false }, title = { Text("Tugas selesai") }, text = {
        BodyText("$completed dari ${state.totalFor(course.id)} tugas selesai. Hanya rincian tugas yang tersedia secara lokal ditampilkan di halaman ini.")
    }, confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Tutup") } })
}

@Composable
fun GradesScreen() {
    val state = rememberAcademicState() ?: return
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var showGoal by rememberSaveable { mutableStateOf(false) }
    var semesterInfo by rememberSaveable { mutableStateOf(false) }
    AcademicBody {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Headline("Setiap kemajuan berarti.")
            BodyText("Nadia Putri • Informatika")
            ChoiceChip("Semester 5 · 2026", true) { semesterInfo = true }
        }
        SummaryCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BodyText("IPS sementara"); Headline(decimal(state.semesterGpa)); BodyText("12 SKS semester ini")
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BodyText("IPK"); Headline("3,56"); BodyText("60 SKS telah lulus")
                }
            }
            BodyText("IPS memakai nilai sementara. IPK memakai nilai final semester 1–4.")
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Perjalanan IPS", "4 semester")
            Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
                listOf(3.40, 3.50, 3.60, 3.75).forEachIndexed { index, value ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(decimal(value), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Box(Modifier.width(42.dp).height((value * 22).dp).background(
                            if (index == 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)))
                        Text("Sem ${index + 1}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Nilai semester 5", "Edit nilai") { editing = "IF570" }
            Column {
                academicCourses.forEach { course ->
                    val grade = state.grades.getValue(course.id)
                    ListItem(modifier = Modifier.clickable { editing = course.id },
                        overlineContent = { Text("${course.id} · 3 SKS · Skor ${grade.score}") },
                        headlineContent = { Text(course.title) }, supportingContent = { BodyText("Nilai sementara") },
                        trailingContent = { Text("${grade.letter} · ${decimal(gradePoints.getValue(grade.letter))}") },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
                    Divider()
                }
            }
        }
        Button(onClick = { showGoal = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Star, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Lihat target belajar")
        }
    }
    editing?.let { courseId ->
        key(courseId) {
            val grade = state.grades.getValue(courseId)
            var score by rememberSaveable { mutableStateOf(grade.score.toString()) }
            var letter by rememberSaveable { mutableStateOf(grade.letter) }
            var error by rememberSaveable { mutableStateOf(false) }
            var saveError by remember { mutableStateOf<String?>(null) }
            var saving by remember { mutableStateOf(false) }
            AlertDialog(onDismissRequest = { editing = null }, title = { Text("Edit nilai · $courseId") }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BodyText("Catatan lokal. Pilih nilai huruf sesuai penilaian dosen.")
                    OutlinedTextField(score, { score = it; error = false }, Modifier.testTag("grade-score"), label = { Text("Skor (0–100)") }, isError = error)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        gradePoints.keys.forEach { option -> ChoiceChip(option, letter == option) { letter = option } }
                    }
                    if (error) Text("Skor harus antara 0 dan 100.", color = MaterialTheme.colorScheme.error)
                    saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }, confirmButton = { TextButton(enabled = !saving, onClick = {
                saving = true
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { state.updateGrade(courseId, score, letter) } }
                        .onSuccess { if (it) editing = null else error = true }
                        .onFailure { saveError = "Nilai gagal disimpan. Coba lagi." }
                    saving = false
                }
            }) { Text("Simpan") } }, dismissButton = { TextButton(onClick = { editing = null }) { Text("Batal") } })
        }
    }
    if (showGoal) GoalDialog(state) { showGoal = false }
    if (semesterInfo) AlertDialog(onDismissRequest = { semesterInfo = false }, title = { Text("Semester 5 · 2026") },
        text = { BodyText("Nilai sementara semester ini dapat diedit. Riwayat semester 1–4 ditampilkan pada Perjalanan IPS.") },
        confirmButton = { TextButton(onClick = { semesterInfo = false }) { Text("Tutup") } })
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
            Headline("Fokus kecil, hasil besar.")
            BodyText("Prioritas dari mata kuliah, nilai sementara, dan tenggatmu.")
            ChoiceChip("Target IPK: ${decimal(state.goal)}", true) { goalOpen = true }
        }
        SummaryCard {
            SectionTitle("PRIORITAS HARI INI", if (backStackDone) "100%" else "80%")
            Text("Tuntaskan latihan navigasi", style = MaterialTheme.typography.titleLarge)
            BodyText("IF570 · ${if (backStackDone) 5 else 4} dari 5 latihan selesai")
            Progress(if (backStackDone) 1f else 0.8f)
            BodyText("Praktikum 3 jatuh tempo hari ini, 17.00. Selesaikan 1 latihan back stack sebelum mengumpulkan.")
            Button(onClick = { sessionOpen = true }) { Text(if (backStackDone) "Tinjau target" else "Lanjutkan target") }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Berikutnya untukmu")
            RecommendationRow("IF510 · PERKUAT PEMAHAMAN", "Latihan optimasi query", "Nilai ${state.grades.getValue("IF510").score} · target 80 · 45 menit")
            BodyText("Kerjakan 5 soal indeks dan baca ulang catatan B-tree. Kuis dijadwalkan Kamis, 8 Okt.")
            Divider()
            RecommendationRow("IF550 · SIAPKAN TENGGAT", "Ulas pola arsitektur", "Nilai ${state.grades.getValue("IF550").score} · 30 menit · Selasa, 6 Okt")
            BodyText("Bandingkan MVC dan MVVM sebelum tugas diagram arsitektur, Rabu, 7 Okt.")
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Rencana belajar hari ini", "Ubah") { planOpen = true }
            TaskRow(AcademicTask("lifecycle", "IF570", "Ulangi Activity lifecycle", "20 menit · " + if (lifecycleDone) "Selesai pukul 08.00" else "Belum selesai"), lifecycleDone, {
                lifecycleDone = !lifecycleDone
                prefs.edit().putBoolean("learning.lifecycle", lifecycleDone).apply()
            })
            Divider()
            TaskRow(AcademicTask("back-stack", "IF570", "Latihan back stack", plan), backStackDone, {
                backStackDone = !backStackDone
                prefs.edit().putBoolean("learning.back-stack", backStackDone).apply()
            })
        }
        BodyText("Saran ini adalah panduan. Sesuaikan target dan waktu belajar dengan kebutuhanmu.")
    }
    if (goalOpen) GoalDialog(state) { goalOpen = false }
    if (planOpen) TextEditDialog("Rencana belajar hari ini", "Waktu / rencana latihan", plan, { planOpen = false }) {
        if (it.isBlank()) false else {
            plan = it.trim(); prefs.edit().putString("learning.plan", plan).apply(); planOpen = false; true
        }
    }
    if (sessionOpen) AlertDialog(onDismissRequest = { sessionOpen = false }, title = { Text("Latihan back stack") },
        text = { BodyText("Selesaikan 1 latihan back stack sebelum mengumpulkan. Tandai selesai setelah kamu mengerjakannya; status ini hanya disimpan di perangkat.") },
        confirmButton = { TextButton(onClick = {
            backStackDone = true; prefs.edit().putBoolean("learning.back-stack", true).apply(); sessionOpen = false
        }) { Text("Tandai selesai") } }, dismissButton = { TextButton(onClick = { sessionOpen = false }) { Text("Kembali") } })
}

@Composable
private fun RecommendationRow(overline: String, title: String, supporting: String) {
    ListItem(overlineContent = { Text(overline) }, headlineContent = { Text(title) }, supportingContent = { BodyText(supporting) },
        leadingContent = { Icon(Icons.Default.School, null) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
}

@Composable
private fun GoalDialog(state: AcademicState, onDismiss: () -> Unit) {
    TextEditDialog("Target belajar", "Target IPK (0–4)", decimal(state.goal), onDismiss) { value ->
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
            supportingText = if (error) { { Text("Periksa kembali nilai yang dimasukkan.") } } else null)
    }, confirmButton = { TextButton(enabled = !saving, onClick = {
        saving = true
        scope.launch { error = !runCatching { onSave(value) }.getOrDefault(false); saving = false }
    }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

/** Scrollable content only. Navigation and system bars belong to the host Scaffold. */
@Composable
fun AcademicScreen(onCourseClick: (String) -> Unit) {
    val state = rememberAcademicState() ?: return
    var allTasks by rememberSaveable { mutableStateOf(false) }
    AcademicBody {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Headline("Semester 5")
            BodyText("Informatika • Nadia Putri")
            Text("4 mata kuliah · 12 SKS · ${state.activeTaskCount} tugas aktif", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Mata kuliah", !allTasks) { allTasks = false }
            ChoiceChip("Semua tugas", allTasks) { allTasks = true }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Perlu diselesaikan", "${state.activeTaskCount} tugas")
            val tasks = if (allTasks) state.tasks.filterNot { it.initiallyDone } else academicTasks.take(2)
            tasks.forEachIndexed { index, task ->
                TaskRow(task, state.completion[task.id] == true, { state.toggleTask(task.id) }, compact = true)
                if (index < tasks.lastIndex) Divider()
            }
            if (allTasks) BodyText("IF510 memiliki 1 tugas aktif. Detail tugas belum tersedia pada data lokal.")
        }
        if (!allTasks) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Mata kuliahmu")
            academicCourses.forEach { course ->
                val completed = state.completedFor(course.id)
                val remaining = state.totalFor(course.id) - completed
                Column(Modifier.fillMaxWidth().testTag("course-${course.id}").clickable { onCourseClick(course.id) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ListItem(
                        headlineContent = { Text(course.title) },
                        overlineContent = { Text(course.id) },
                        supportingContent = { BodyText("3 SKS · " + if (remaining == 0) "Tidak ada tugas aktif" else "$remaining tugas aktif") },
                        trailingContent = { Text("›") },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                    Progress(completed.toFloat() / state.totalFor(course.id))
                    BodyText("$completed dari ${state.totalFor(course.id)} tugas selesai")
                    Divider()
                }
            }
        }
    }
}
