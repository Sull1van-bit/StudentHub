package org.umn.application.studenthub.ui.academic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.umn.application.studenthub.data.local.StudentHubDatabase
import org.umn.application.studenthub.ui.components.CurriculumCourseDropdown
import org.umn.application.studenthub.ui.localization.AppLanguage
import org.umn.application.studenthub.ui.localization.LocalAppLanguage
import org.umn.application.studenthub.ui.localization.lString
import java.util.Locale

val LocalDrawerOpen = compositionLocalOf { false }

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
    return room.replace("Ruang ", "Room ")
}

@Composable
private fun rememberAcademicState(): AcademicState? {
    val context = LocalContext.current.applicationContext
    var state by remember {
        mutableStateOf<AcademicState?>(
            runCatching {
                AcademicState(StudentHubDatabase.get(context))
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
                    AcademicState(StudentHubDatabase.get(context))
                }
            }.onSuccess { state = it }.onFailure { failed = true }
        } else {
            withContext(Dispatchers.IO) {
                runCatching { state?.reloadFromDatabase() }
            }
        }
    }
    if (state == null) AcademicBody {
        if (failed) {
            Text(
                lString("Data akademik tidak dapat dibaca.", "Academic data could not be read."),
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = { attempt++ }) { Text(lString("Coba lagi", "Retry")) }
        } else {
            CircularProgressIndicator()
            BodyText(lString("Memuat data akademik…", "Loading academic data…"))
        }
    }
    return state
}

@Composable
private fun AcademicBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        content = content,
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
            if (onAction != null) {
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text(trailing) }
            } else {
                Text(trailing, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
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
private fun TaskRow(
    task: AcademicTask,
    checked: Boolean,
    onToggle: () -> Unit,
    onToggleReminder: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    compact: Boolean = false
) {
    val supporting = buildList {
        if (compact && task.deadline.isNotBlank()) add(task.deadline)
        if (compact) add(task.courseId)
        if (task.detail.isNotBlank()) add(task.detail)
    }.joinToString(" · ")

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task-${task.id}")
            .toggleable(checked, role = Role.Checkbox) { onToggle() },
        headlineContent = {
            Text(
                task.title,
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
        overlineContent = if (task.deadline.isNotEmpty() && !compact) {
            { Text(task.deadline) }
        } else null,
        supportingContent = if (supporting.isNotEmpty()) {
            { BodyText(supporting) }
        } else null,
        leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        trailingContent = if (onToggleReminder != null || onDelete != null) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onToggleReminder != null) {
                        IconButton(onClick = onToggleReminder) {
                            Icon(
                                imageVector = if (task.reminderEnabled) Icons.Default.NotificationsActive else Icons.Outlined.Notifications,
                                contentDescription = lString("Pengingat Tugas", "Task Reminder"),
                                tint = if (task.reminderEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = lString("Hapus Tugas", "Delete Task"),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        } else null,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

@Composable
private fun Progress(value: Float) = LinearProgressIndicator(progress = { value }, modifier = Modifier.fillMaxWidth().height(4.dp))

@Composable
private fun SummaryCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val modifier = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth()
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun CourseDetailScreen(
    courseId: String,
    onScanClick: () -> Unit = {},
    onEditNote: (noteId: String, courseId: String) -> Unit = { _, _ -> },
    onNotesClick: () -> Unit
) {
    val context = LocalContext.current
    val state = rememberAcademicState() ?: return
    val course = state.currentCourses().firstOrNull { it.id.equals(courseId, ignoreCase = true) }
    var tab by rememberSaveable(courseId) { mutableStateOf("Tugas") }
    var showAddTask by rememberSaveable { mutableStateOf(false) }
    var showEditGrade by rememberSaveable { mutableStateOf(false) }

    if (course == null) {
        AcademicBody {
            Headline(lString("Mata kuliah tidak ditemukan", "Course Not Found"))
            BodyText(lString("Data untuk $courseId tidak ditemukan.", "Data for $courseId was not found."))
        }
        return
    }

    val courseNotes = remember(course.id, tab) {
        runCatching {
            StudentHubDatabase.get(context).notes().filter {
                it.course.equals(course.id, ignoreCase = true) ||
                    it.course.equals(course.title, ignoreCase = true)
            }
        }.getOrDefault(emptyList())
    }

    val total = state.totalFor(course.id)
    val completed = state.completedFor(course.id)
    val progressRatio = if (total > 0) completed.toFloat() / total else 0f
    val progressPercent = if (total > 0) (completed * 100 / total) else 0
    val isEn = LocalAppLanguage.current == AppLanguage.ENGLISH
    val scheduleText = formatSchedule(course.schedule, isEn)
    val roomText = formatRoom(course.room, isEn)

    AcademicBody {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${course.id} • ${course.sks} " + lString("SKS", "credits"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Headline(course.title)
            if (course.lecturer.isNotBlank()) {
                BodyText(lString("Dosen: ${course.lecturer}", "Lecturer: ${course.lecturer}"))
            }
            val scheduleAndRoom = listOf(scheduleText, roomText).filter { it.isNotBlank() }.joinToString(" · ")
            if (scheduleAndRoom.isNotBlank()) {
                BodyText(scheduleAndRoom)
            }
            if (course.description.isNotBlank()) {
                Text(course.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SummaryCard {
            SectionTitle(lString("Progres tugas", "Assignment progress"), "$progressPercent%")
            Progress(progressRatio)
            if (total == 0) {
                BodyText(lString("Belum ada tugas untuk mata kuliah ini.", "No tasks added for this course yet."))
            } else {
                val remaining = total - completed
                val remainingText = when (remaining) {
                    0 -> lString(" Semua tugas selesai!", " All tasks completed!")
                    1 -> lString(" Tinggal satu lagi!", " Just one left!")
                    else -> ""
                }
                BodyText(lString("$completed dari $total tugas selesai.", "$completed of $total tasks completed.") + remainingText)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(lString("Tugas", "Tasks"), tab == "Tugas") { tab = "Tugas" }
            val isDrawerOpen = LocalDrawerOpen.current
            ChoiceChip(
                if (isDrawerOpen) lString("Catatan Kuliah", "Class Notes") else lString("Catatan", "Notes"),
                tab == "Catatan"
            ) {
                tab = "Catatan"
                onNotesClick()
            }
            ChoiceChip(lString("Nilai", "Grades"), tab == "Nilai") { tab = "Nilai" }
        }
        when (tab) {
            "Nilai" -> {
                val grade = state.grades[course.id]
                SummaryCard {
                    if (grade != null) {
                        SectionTitle(lString("Nilai tersimpan", "Saved grade"), "${grade.letter} · ${decimal(gradePoints.getValue(grade.letter))}")
                        Headline(lString("Skor ${grade.score}", "Score ${grade.score}"))
                        BodyText(lString("Nilai ini memengaruhi perhitungan IPS & IPK semester berjalan.", "This grade affects term and cumulative GPA calculations."))
                    } else {
                        SectionTitle(lString("Nilai mata kuliah", "Course grade"), lString("Belum dinilai", "Not graded"))
                        BodyText(lString("Belum ada nilai yang dimasukkan untuk mata kuliah ini.", "No grade has been entered for this course yet."))
                    }
                    Button(onClick = { showEditGrade = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (grade != null) lString("Ubah Nilai", "Edit Grade") else lString("Masukkan Nilai", "Enter Grade"))
                    }
                }
            }
            "Catatan" -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle(
                        lString("Catatan ${course.id}", "${course.id} Notes"),
                        lString("${courseNotes.size} catatan", "${courseNotes.size} notes")
                    )
                    if (courseNotes.isEmpty()) {
                        BodyText(lString("Belum ada catatan untuk mata kuliah ${course.title}.", "No notes linked to ${course.title} yet."))
                    } else {
                        courseNotes.forEach { note ->
                            SummaryCard(onClick = { onEditNote(note.id, course.id) }) {
                                Text(note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                if (note.body.isNotBlank()) {
                                    BodyText(note.body.lines().firstOrNull().orEmpty())
                                }
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onEditNote("", course.id) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(lString("Buat Catatan ${course.id}", "New ${course.id} Note"))
                        }
                        OutlinedButton(onClick = onScanClick, modifier = Modifier.weight(1f)) {
                            Text(lString("Pindai Catatan", "Scan Notes"))
                        }
                    }
                }
            }
            else -> {
                val courseTasks = state.tasks.filter { it.courseId == course.id }
                val active = courseTasks.filter { state.completion[it.id] != true }
                val done = courseTasks.filter { state.completion[it.id] == true }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle(lString("Tugas aktif", "Active tasks"), lString("${active.size} tugas", "${active.size} tasks"))
                    active.forEachIndexed { index, task ->
                        TaskRow(
                            task = task,
                            checked = false,
                            onToggle = { state.toggleTask(task.id) },
                            onToggleReminder = { state.toggleTaskReminder(task.id) },
                            onDelete = { state.deleteTask(task.id) }
                        )
                        if (task.reminderEnabled) {
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Text(
                                        lString("Pengingat aktif untuk tugas ini.", "Reminder active for this task."),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                        if (index < active.lastIndex) Divider()
                    }
                    if (active.isEmpty()) {
                        BodyText(lString("Tidak ada tugas aktif.", "No active tasks."))
                    }
                }
                if (done.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle(lString("Tugas selesai", "Completed tasks"), "${done.size}")
                        done.forEach { task ->
                            TaskRow(
                                task = task,
                                checked = true,
                                onToggle = { state.toggleTask(task.id) },
                                onDelete = { state.deleteTask(task.id) }
                            )
                            Divider()
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showAddTask = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(lString("Tambah tugas", "Add task"))
                    }
                    OutlinedButton(onClick = onScanClick, modifier = Modifier.weight(1f).testTag("course-scan-note")) {
                        Text(lString("Pindai Catatan", "Scan Notes"))
                    }
                }
            }
        }
    }

    if (showAddTask) {
        AddTaskDialog(
            onDismiss = { showAddTask = false },
            onSave = { title, detail, deadline, reminderEnabled, reminderTime ->
                val ok = state.addTask(course.id, title, detail, deadline, reminderEnabled, reminderTime)
                if (ok) showAddTask = false
                ok
            }
        )
    }

    if (showEditGrade) {
        GradeEditDialog(
            courseId = course.id,
            initialGrade = state.grades[course.id],
            onDismiss = { showEditGrade = false },
            onSave = { score, letter ->
                val ok = state.updateGrade(course.id, score, letter)
                if (ok) showEditGrade = false
                ok
            }
        )
    }
}

@Composable
private fun AddTaskDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, detail: String, deadline: String, reminderEnabled: Boolean, reminderTime: String) -> Boolean
) {
    var title by rememberSaveable { mutableStateOf("") }
    var detail by rememberSaveable { mutableStateOf("") }
    var deadline by rememberSaveable { mutableStateOf("") }
    var reminderEnabled by rememberSaveable { mutableStateOf(false) }
    var reminderTime by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(lString("Tambah Tugas", "Add Task")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; error = false },
                    label = { Text(lString("Nama tugas", "Task name")) },
                    isError = error && title.isBlank(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text(lString("Rincian tugas (opsional)", "Task details (optional)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                org.umn.application.studenthub.ui.components.DatePickerField(
                    value = deadline,
                    onValueChange = { deadline = it },
                    label = lString("Tenggat (opsional)", "Deadline (optional)")
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(lString("Aktifkan pengingat notifikasi", "Enable notification reminder"), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
                }
                if (reminderEnabled) {
                    org.umn.application.studenthub.ui.components.TimePickerField(
                        value = reminderTime,
                        onValueChange = { reminderTime = it; error = false },
                        label = lString("Jam pengingat (HH.mm)", "Reminder time (HH.mm)"),
                        placeholder = "08.00"
                    )
                }
                if (error) {
                    Text(
                        lString("Nama tugas wajib diisi (dan jam pengingat harus HH.mm jika diaktifkan).", "Task name is required (and reminder time must be HH.mm if enabled)."),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val timeToUse = if (reminderEnabled) StudentHubDatabase.normalizeTimeFormat(reminderTime) else "08.00"
                val timeOk = !reminderEnabled || Regex("(?:[01][0-9]|2[0-3])\\.[0-5][0-9]").matches(timeToUse)
                if (title.isNotBlank() && timeOk) {
                    if (!onSave(title, detail, deadline, reminderEnabled, timeToUse)) {
                        error = true
                    }
                } else {
                    error = true
                }
            }) { Text(lString("Simpan", "Save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(lString("Batal", "Cancel")) }
        }
    )
}

@Composable
private fun GradeEditDialog(
    courseId: String,
    initialGrade: AcademicGrade?,
    onDismiss: () -> Unit,
    onSave: (score: String, letter: String) -> Boolean
) {
    var score by rememberSaveable(courseId) { mutableStateOf(initialGrade?.score?.toString() ?: "") }
    var letter by rememberSaveable(courseId) { mutableStateOf(initialGrade?.letter ?: "A") }
    var error by rememberSaveable(courseId) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(lString("Edit nilai · $courseId", "Edit Grade · $courseId")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BodyText(lString("Masukkan skor 0-100 untuk memperbarui nilai huruf dan perhitungan IPK.", "Enter score 0-100 to update letter grade and GPA calculation."))
                OutlinedTextField(
                    value = score,
                    onValueChange = {
                        score = it
                        val s = it.toIntOrNull()
                        if (s != null && s in 0..100) {
                            error = false
                            letter = scoreToLetter(s)
                        } else {
                            error = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("grade-score"),
                    label = { Text(lString("Skor (0-100)", "Score (0-100)")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = error,
                    supportingText = if (error) {
                        { Text(lString("Skor harus berupa angka antara 0 dan 100.", "Score must be a number between 0 and 100.")) }
                    } else null
                )
                Text(lString("Nilai Huruf Terpilih:", "Selected Letter Grade:"), style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    gradePoints.keys.forEach { option -> ChoiceChip(option, letter == option) { letter = option } }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !error && score.toIntOrNull() in 0..100,
                onClick = {
                    val ok = runCatching { onSave(score, letter) }.getOrDefault(false)
                    if (!ok) error = true
                }
            ) { Text(lString("Simpan", "Save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(lString("Batal", "Cancel")) }
        }
    )
}

@Composable
fun GradesScreen() {
    val state = rememberAcademicState() ?: return
    val context = LocalContext.current
    val database = remember(context) { StudentHubDatabase.get(context) }
    val isEn = LocalAppLanguage.current == AppLanguage.ENGLISH
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var showGoal by rememberSaveable { mutableStateOf(false) }

    val dbProfile = remember(state.courses, state.grades) { runCatching { database.profile() }.getOrNull() }
    val studentName = dbProfile?.name?.takeIf { it.isNotBlank() } ?: lString("Mahasiswa", "Student")
    val program = dbProfile?.program?.takeIf { it.isNotBlank() } ?: lString("Program Studi", "Study Program")
    val semesterNum = dbProfile?.semester ?: 1
    val priorIpk = dbProfile?.priorIpk ?: 0.0
    val priorSks = dbProfile?.priorSks ?: 0

    val coursesToDisplay = state.currentCourses()
    val totalSks = coursesToDisplay.sumOf { it.sks }

    val journeyItems = remember(semesterNum, priorIpk, priorSks, state.semesterGpa, state.grades, isEn) {
        buildList {
            if (semesterNum > 1 && priorSks > 0 && priorIpk > 0.0) {
                add((if (isEn) "Term 1-${semesterNum - 1}" else "Sem 1-${semesterNum - 1}") to priorIpk)
            }
            if (state.grades.isNotEmpty()) {
                add((if (isEn) "Term $semesterNum" else "Sem $semesterNum") to state.semesterGpa)
            }
        }
    }

    AcademicBody {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Headline(if (isEn) "Grades & GPA" else "Nilai & IPK")
            BodyText("$studentName • $program")
            ChoiceChip(lString("Semester $semesterNum", "Semester $semesterNum"), true) {}
        }
        SummaryCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BodyText(lString("IPS sementara", "Term GPA"))
                    Headline(decimal(state.semesterGpa))
                    BodyText(lString("$totalSks SKS semester ini", "$totalSks credits this semester"))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BodyText(lString("IPK Kumulatif", "Cumulative GPA"))
                    Headline(decimal(state.cumulativeGpa))
                    BodyText(lString("Riwayat + semester ini", "History + this term"))
                }
            }
            BodyText(lString("IPS dan IPK dihitung otomatis dari mata kuliah semester berjalan.", "Term GPA and Cumulative GPA are calculated automatically from current semester courses."))
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(lString("Perjalanan IPS", "GPA Journey"), if (journeyItems.isNotEmpty()) "${journeyItems.size} periode" else null)
            if (journeyItems.isEmpty()) {
                BodyText(lString("Belum ada data nilai. Masukkan nilai mata kuliah untuk melihat grafik perjalanan IPS.", "No grade data yet. Enter course grades to see your GPA journey chart."))
            } else {
                Row(
                    Modifier.fillMaxWidth().height(130.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    journeyItems.forEachIndexed { index, (label, value) ->
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(decimal(value), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Box(
                                Modifier
                                    .width(48.dp)
                                    .height((value.coerceIn(0.2, 4.0) * 22).dp)
                                    .background(
                                        if (index == journeyItems.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                        RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                                    )
                            )
                            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val firstCourseCode = coursesToDisplay.firstOrNull()?.id
            SectionTitle(
                lString("Nilai semester $semesterNum", "Semester $semesterNum Grades"),
                if (firstCourseCode != null) lString("Edit nilai", "Edit Grade") else null,
                if (firstCourseCode != null) ({ editing = firstCourseCode }) else null
            )
            if (coursesToDisplay.isEmpty()) {
                BodyText(lString("Belum ada mata kuliah terdaftar. Tambahkan mata kuliah di menu Akademik atau Profil.", "No courses registered yet. Add courses in the Academics or Profile menu."))
            } else {
                Column {
                    coursesToDisplay.forEach { course ->
                        val grade = state.grades[course.id]
                        ListItem(
                            modifier = Modifier.clickable { editing = course.id },
                            overlineContent = {
                                val scoreLabel = if (grade != null) lString("Skor ${grade.score}", "Score ${grade.score}") else lString("Belum dinilai", "Not graded")
                                Text("${course.id} · ${course.sks} SKS · $scoreLabel")
                            },
                            headlineContent = { Text(course.title) },
                            supportingContent = { BodyText(lString("Ketuk untuk mengubah nilai", "Tap to edit grade")) },
                            trailingContent = {
                                if (grade != null) {
                                    Text("${grade.letter} · ${decimal(gradePoints.getValue(grade.letter))}")
                                } else {
                                    Text("-")
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                        Divider()
                    }
                }
            }
        }
        Button(onClick = { showGoal = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Star, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(lString("Atur target IPK (${decimal(state.goal)})", "Set GPA Target (${decimal(state.goal)})"))
        }
    }
    editing?.let { courseId ->
        GradeEditDialog(
            courseId = courseId,
            initialGrade = state.grades[courseId],
            onDismiss = { editing = null },
            onSave = { score, letter ->
                val ok = state.updateGrade(courseId, score, letter)
                if (ok) editing = null
                ok
            }
        )
    }
    if (showGoal) GoalDialog(state) { showGoal = false }
}

@Composable
fun LearningScreen() {
    val context = LocalContext.current
    val database = remember(context) { StudentHubDatabase.get(context) }
    val state = rememberAcademicState() ?: return
    val isEn = LocalAppLanguage.current == AppLanguage.ENGLISH
    var goalOpen by rememberSaveable { mutableStateOf(false) }
    var addPlanOpen by rememberSaveable { mutableStateOf(false) }
    var statusNotice by remember { mutableStateOf<String?>(null) }

    val plans = state.studyPlans
    val completedPlans = plans.count { it.completed }
    val totalPlans = plans.size
    val planProgress = if (totalPlans > 0) completedPlans.toFloat() / totalPlans else 0f
    val planPercent = if (totalPlans > 0) (completedPlans * 100 / totalPlans) else 0

    AcademicBody {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Headline(if (isEn) "Study Recommendations" else "Rekomendasi Belajar")
            BodyText(lString("Prioritas dari mata kuliah, nilai sementara, dan rencana belajarmu.", "Priorities based on your courses, interim grades, and study plans."))
            ChoiceChip(lString("Target IPK: ${decimal(state.goal)}", "GPA Target: ${decimal(state.goal)}"), true) { goalOpen = true }
        }
        statusNotice?.let { notice ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
        org.umn.application.studenthub.ui.notes.StudentProgressAndScheduleSection(
            database = database,
            refreshTrigger = plans.size + completedPlans,
            onFeedback = { msg ->
                statusNotice = msg
                runCatching { state.reloadFromDatabase() }
            }
        )
        SummaryCard {
            SectionTitle(lString("Prioritas hari ini", "Today's priority"), "$planPercent%")
            if (plans.isEmpty()) {
                Text(lString("Belum ada rencana belajar", "No study plans yet"), style = MaterialTheme.typography.titleLarge)
                BodyText(lString("Tambahkan rencana belajar harianmu untuk memantau progres belajar.", "Add your daily study plan to track study progress."))
            } else {
                val nextPlan = plans.firstOrNull { !it.completed } ?: plans.first()
                Text(nextPlan.title, style = MaterialTheme.typography.titleLarge)
                BodyText(
                    (if (nextPlan.courseId.isNotBlank()) "${nextPlan.courseId} · " else "") +
                        lString("$completedPlans dari $totalPlans rencana selesai", "$completedPlans of $totalPlans plans completed")
                )
                Progress(planProgress)
                if (nextPlan.detail.isNotBlank()) {
                    BodyText(nextPlan.detail)
                }
            }
            Button(onClick = { addPlanOpen = true }) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(lString("Tambah Rencana Belajar", "Add Study Plan"))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(lString("Berikutnya untukmu", "Next for you"))
            val courses = state.currentCourses()
            val activeTasks = state.tasks.filter { state.completion[it.id] != true }
            if (courses.isEmpty() && activeTasks.isEmpty()) {
                BodyText(lString("Belum ada rekomendasi. Tambahkan mata kuliah atau tugas terlebih dahulu.", "No recommendations yet. Add courses or tasks first."))
            } else {
                activeTasks.take(3).forEachIndexed { idx, task ->
                    RecommendationRow(
                        overline = "${task.courseId} · " + lString("Tugas aktif", "Active task"),
                        title = task.title,
                        supporting = task.deadline.ifBlank { task.detail.ifBlank { lString("Segera selesaikan tugas ini", "Complete this task soon") } }
                    )
                    if (idx < activeTasks.take(3).lastIndex || courses.isNotEmpty()) Divider()
                }
                if (activeTasks.isEmpty()) {
                    courses.take(3).forEachIndexed { idx, course ->
                        val grade = state.grades[course.id]
                        val gradeText = if (grade != null) lString("Skor saat ini ${grade.score} (${grade.letter})", "Current score ${grade.score} (${grade.letter})") else lString("Belum ada nilai", "No grade yet")
                        RecommendationRow(
                            overline = "${course.id} · ${course.sks} SKS",
                            title = lString("Ulas materi ${course.title}", "Review ${course.title}"),
                            supporting = gradeText
                        )
                        if (idx < courses.take(3).lastIndex) Divider()
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(lString("Rencana belajar hari ini", "Today's study plan"), lString("Tambah", "Add")) { addPlanOpen = true }
            if (plans.isEmpty()) {
                BodyText(lString("Belum ada daftar rencana belajar.", "No study plan items yet."))
            } else {
                plans.forEachIndexed { index, item ->
                    val planTask = AcademicTask(
                        id = item.id,
                        courseId = item.courseId,
                        title = item.title,
                        detail = listOf(item.courseId, item.detail).filter { it.isNotBlank() }.joinToString(" · "),
                        initiallyDone = item.completed
                    )
                    TaskRow(
                        task = planTask,
                        checked = item.completed,
                        onToggle = { state.toggleStudyPlan(item.id) },
                        onDelete = { state.deleteStudyPlan(item.id) }
                    )
                    if (index < plans.lastIndex) Divider()
                }
            }
        }
    }
    if (goalOpen) GoalDialog(state) { goalOpen = false }
    if (addPlanOpen) {
        AddStudyPlanDialog(
            courses = state.currentCourses().map { it.id },
            onDismiss = { addPlanOpen = false },
            onSave = { courseId, title, detail ->
                val ok = state.addStudyPlan(courseId, title, detail)
                if (ok) addPlanOpen = false
                ok
            }
        )
    }
}

@Composable
private fun AddStudyPlanDialog(
    courses: List<String>,
    onDismiss: () -> Unit,
    onSave: (courseId: String, title: String, detail: String) -> Boolean
) {
    var title by rememberSaveable { mutableStateOf("") }
    var courseId by rememberSaveable { mutableStateOf("") }
    var detail by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(lString("Tambah Rencana Belajar", "Add Study Plan")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; error = false },
                    label = { Text(lString("Topik / Kegiatan Belajar", "Study Topic / Activity")) },
                    isError = error && title.isBlank(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = courseId,
                    onValueChange = { courseId = it },
                    label = { Text(lString("Kode Mata Kuliah (opsional)", "Course Code (optional)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (courses.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        courses.forEach { code ->
                            FilterChip(
                                selected = courseId == code,
                                onClick = { courseId = code },
                                label = { Text(code) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text(lString("Waktu / Durasi (opsional)", "Time / Duration (optional)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error) {
                    Text(
                        lString("Topik rencana belajar wajib diisi.", "Study topic is required."),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!onSave(courseId, title, detail)) error = true
            }) { Text(lString("Simpan", "Save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(lString("Batal", "Cancel")) }
        }
    )
}

@Composable
private fun RecommendationRow(overline: String, title: String, supporting: String) {
    ListItem(
        overlineContent = { Text(overline) },
        headlineContent = { Text(title) },
        supportingContent = { BodyText(supporting) },
        leadingContent = { Icon(Icons.Default.School, null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
private fun GoalDialog(state: AcademicState, onDismiss: () -> Unit) {
    TextEditDialog(
        lString("Target belajar", "Study target"),
        lString("Target IPK (0-4)", "GPA target (0-4)"),
        if (state.goal > 0.0) decimal(state.goal) else "",
        onDismiss
    ) { value ->
        state.updateGoal(value).also { if (it) onDismiss() }
    }
}

private fun decimal(value: Double): String = String.format(Locale.forLanguageTag("id-ID"), "%.2f", value)

@Composable
private fun TextEditDialog(title: String, label: String, initial: String, onDismiss: () -> Unit, onSave: suspend (String) -> Boolean) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var value by rememberSaveable { mutableStateOf(initial) }
    var error by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it; error = false },
                label = { Text(label) },
                isError = error,
                supportingText = if (error) {
                    { Text(lString("Periksa kembali nilai yang dimasukkan.", "Please check the entered value.")) }
                } else null
            )
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                saving = true
                scope.launch {
                    error = !runCatching { onSave(value) }.getOrDefault(false)
                    saving = false
                }
            }) { Text(lString("Simpan", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(lString("Batal", "Cancel")) } }
    )
}

@Composable
fun AcademicScreen(onCourseClick: (String) -> Unit) {
    val state = rememberAcademicState() ?: return
    val context = LocalContext.current
    val database = remember(context) { StudentHubDatabase.get(context) }
    var allTasks by rememberSaveable { mutableStateOf(false) }
    var showAddCourseDialog by rememberSaveable { mutableStateOf(false) }

    val dbProfile = remember(state.courses) { runCatching { database.profile() }.getOrNull() }
    val studentName = dbProfile?.name?.takeIf { it.isNotBlank() } ?: lString("Mahasiswa", "Student")
    val program = dbProfile?.program?.takeIf { it.isNotBlank() } ?: lString("Program Studi", "Study Program")
    val semesterNum = dbProfile?.semester ?: 1

    val coursesToDisplay = state.currentCourses()
    val totalSks = coursesToDisplay.sumOf { it.sks }
    val courseCount = coursesToDisplay.size
    val activeTasksCount = state.activeTaskCount

    AcademicBody {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Headline("Semester $semesterNum")
            BodyText("$program • $studentName")
            Text(
                lString(
                    "$courseCount mata kuliah · $totalSks SKS · $activeTasksCount tugas aktif",
                    "$courseCount courses · $totalSks credits · $activeTasksCount active tasks"
                ),
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
            val activeTasksList = state.tasks.filter { it.courseId in currentCourseIds && state.completion[it.id] != true }
            val tasks = if (allTasks) activeTasksList else activeTasksList.take(3)
            tasks.forEachIndexed { index, task ->
                TaskRow(
                    task = task,
                    checked = state.completion[task.id] == true,
                    onToggle = { state.toggleTask(task.id) },
                    onToggleReminder = { state.toggleTaskReminder(task.id) },
                    onDelete = { state.deleteTask(task.id) },
                    compact = true
                )
                if (index < tasks.lastIndex) Divider()
            }
            if (tasks.isEmpty()) {
                BodyText(lString("Tidak ada tugas aktif saat ini.", "No active tasks at the moment."))
            }
        }
        if (!allTasks) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(lString("Mata kuliahmu", "Your courses"), lString("Tambah", "Add")) {
                    showAddCourseDialog = true
                }
                if (coursesToDisplay.isEmpty()) {
                    SummaryCard {
                        Text(lString("Belum ada mata kuliah", "No courses yet"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        BodyText(lString("Tambahkan mata kuliah dan jadwal kuliah semester ini.", "Add your courses and class schedule for this semester."))
                        Button(onClick = { showAddCourseDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(lString("Tambah Mata Kuliah", "Add Course"))
                        }
                    }
                } else {
                    coursesToDisplay.forEach { course ->
                        val completed = state.completedFor(course.id)
                        val total = state.totalFor(course.id)
                        val remaining = (total - completed).coerceAtLeast(0)
                        val sksLabel = lString("SKS", "credits")
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .testTag("course-${course.id}")
                                .clickable { onCourseClick(course.id) },
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ListItem(
                                headlineContent = { Text(course.title) },
                                overlineContent = { Text(course.id) },
                                supportingContent = {
                                    BodyText(
                                        "${course.sks} $sksLabel · " +
                                            if (remaining == 0) lString("Tidak ada tugas aktif", "No active tasks")
                                            else lString("$remaining tugas aktif", "$remaining active tasks")
                                    )
                                },
                                trailingContent = { Text("›") },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            )
                            Progress(if (total > 0) completed.toFloat() / total else 0f)
                            BodyText(lString("$completed dari $total tugas selesai", "$completed of $total tasks completed"))
                            Divider()
                        }
                    }
                    OutlinedButton(onClick = { showAddCourseDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(lString("Tambah Mata Kuliah", "Add Course"))
                    }
                }
            }
        }
    }

    if (showAddCourseDialog) {
        val today = remember { java.time.LocalDate.now() }
        val todayDayIndex = (today.dayOfWeek.value - 1).coerceIn(0, 6)
        var addCode by remember { mutableStateOf("") }
        var addTitle by remember { mutableStateOf("") }
        var addSks by remember { mutableStateOf("") }
        var addDay by remember { mutableIntStateOf(todayDayIndex) }
        var addTime by remember { mutableStateOf("") }
        var addEndTime by remember { mutableStateOf("") }
        var addRoom by remember { mutableStateOf("") }
        var addLecturer by remember { mutableStateOf("") }
        var addError by remember { mutableStateOf(false) }

        val activeSksInt = addSks.toIntOrNull()?.coerceIn(1, 12) ?: 2
        AlertDialog(
            onDismissRequest = { showAddCourseDialog = false },
            title = { Text(lString("Tambah Mata Kuliah", "Add Course")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CurriculumCourseDropdown(
                        currentSemester = semesterNum,
                        selectedTitle = addTitle,
                        onCourseSelected = { c ->
                            if (c != null) {
                                addCode = c.code
                                addTitle = c.title
                                addSks = c.sks.toString()
                                if (addTime.isNotBlank()) {
                                    val autoEnd = org.umn.application.studenthub.ui.components.defaultEndTimeFor(addTime, c.sks)
                                    if (autoEnd.isNotBlank()) addEndTime = autoEnd
                                }
                                addError = false
                            } else {
                                addCode = ""
                                addTitle = ""
                                addSks = ""
                            }
                        }
                    )
                    OutlinedTextField(addCode, { addCode = it; addError = false }, label = { Text(lString("Kode Mata Kuliah", "Course Code")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(addTitle, { addTitle = it; addError = false }, label = { Text(lString("Nama Mata Kuliah", "Course Name")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(
                        addSks,
                        { newVal ->
                            val cleanDigits = newVal.filter { ch -> ch.isDigit() }
                            addSks = cleanDigits
                            val parsedSks = cleanDigits.toIntOrNull()
                            if (parsedSks != null && parsedSks in 1..12 && addTime.isNotBlank()) {
                                val autoEnd = org.umn.application.studenthub.ui.components.defaultEndTimeFor(addTime, parsedSks)
                                if (autoEnd.isNotBlank()) addEndTime = autoEnd
                            }
                            addError = false
                        },
                        label = { Text(lString("Jumlah SKS (1-24)", "Credits / SKS (1-24)")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text(
                        lString(
                            "Jam Perkuliahan (Estimasi selesai: $activeSksInt SKS = $activeSksInt jam):",
                            "Class Hours (Auto-estimated end: $activeSksInt SKS = $activeSksInt hrs):"
                        ),
                        style = MaterialTheme.typography.labelMedium
                    )
                    org.umn.application.studenthub.ui.components.TimeRangePickerRow(
                        startTime = addTime,
                        endTime = addEndTime,
                        onStartTimeChange = { addTime = it; addError = false },
                        onEndTimeChange = { addEndTime = it; addError = false },
                        sks = activeSksInt
                    )
                    OutlinedTextField(addRoom, { addRoom = it }, label = { Text(lString("Ruangan (opsional)", "Room (optional)")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(addLecturer, { addLecturer = it }, label = { Text(lString("Nama Dosen (opsional)", "Lecturer Name (optional)")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text(lString("Hari Kuliah (jika ada jadwal):", "Class Day (if scheduled):"), style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            lString("Sen", "Mon"),
                            lString("Sel", "Tue"),
                            lString("Rab", "Wed"),
                            lString("Kam", "Thu"),
                            lString("Jum", "Fri"),
                            lString("Sab", "Sat"),
                            lString("Min", "Sun")
                        ).forEachIndexed { i, d ->
                            FilterChip(selected = addDay == i, onClick = { addDay = i }, label = { Text(d) })
                        }
                    }
                    if (addError) {
                        Text(
                            lString("Isi kode, nama mata kuliah, dan SKS (1-24) dengan benar.", "Please fill in course code, name, and credits (1-24) correctly."),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val sksNum = addSks.toIntOrNull()
                    val combinedTime = org.umn.application.studenthub.ui.components.combineTimeRange(addTime, addEndTime, sksNum ?: 2)
                    if (addCode.isNotBlank() && addTitle.isNotBlank() && sksNum != null && sksNum in 1..24) {
                        val ok = state.addCourse(
                            code = addCode,
                            title = addTitle,
                            sks = sksNum,
                            semester = semesterNum,
                            lecturer = addLecturer,
                            room = addRoom,
                            day = if (addTime.isNotBlank() || addEndTime.isNotBlank()) addDay else null,
                            time = combinedTime
                        )
                        if (ok) showAddCourseDialog = false else addError = true
                    } else {
                        addError = true
                    }
                }) { Text(lString("Simpan", "Save")) }
            },
            dismissButton = { TextButton(onClick = { showAddCourseDialog = false }) { Text(lString("Batal", "Cancel")) } }
        )
    }
}
