package org.umn.application.studenthub.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.umn.application.studenthub.ui.components.CurriculumCourseDropdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.umn.application.studenthub.data.local.CourseRow
import org.umn.application.studenthub.data.local.ProfileRow
import org.umn.application.studenthub.data.local.ReminderScheduler
import org.umn.application.studenthub.data.local.StudentHubDatabase
import org.umn.application.studenthub.ui.localization.lString
import java.util.UUID

@Composable
fun ProfileScreen(
    themeContent: @Composable () -> Unit,
    languageContent: @Composable () -> Unit = {},
    onProfileSaved: () -> Unit = {},
    onResetOnboarding: () -> Unit = {}
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<ProfileRow?>(null) }
    var courses by remember { mutableStateOf<List<CourseRow>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var program by rememberSaveable { mutableStateOf("") }
    var semester by rememberSaveable { mutableStateOf("") }
    var courseCode by rememberSaveable { mutableStateOf("") }
    var courseTitle by rememberSaveable { mutableStateOf("") }
    var courseCredits by rememberSaveable { mutableStateOf("") }
    var courseSemester by rememberSaveable { mutableStateOf("") }
    val todayDayIndex = (java.time.LocalDate.now().dayOfWeek.value - 1).coerceIn(0, 6)
    var courseDay by rememberSaveable { mutableIntStateOf(todayDayIndex) }
    var courseStartTime by rememberSaveable { mutableStateOf("") }
    var courseEndTime by rememberSaveable { mutableStateOf("") }
    var courseRoom by rememberSaveable { mutableStateOf("") }

    fun load() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val db = StudentHubDatabase.get(context)
                    db.profile() to db.courses()
                }
            }.onSuccess { (p, c) ->
                profile = p
                courses = c
                name = p.name
                program = p.program
                semester = if (p.name.isBlank() && p.program.isBlank()) "" else p.semester.toString()
            }.onFailure {
                error = "Data profil tidak dapat dibaca."
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    if (profile == null || courses == null) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(error ?: lString("Memuat profil…", "Loading profile…"))
        }
        return
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(lString("Profil Mahasiswa", "Student Profile"), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text(lString("Nama Lengkap", "Full Name")) }, singleLine = true)
        OutlinedTextField(value = program, onValueChange = { program = it }, modifier = Modifier.fillMaxWidth(), label = { Text(lString("Program Studi", "Study Program")) }, singleLine = true)
        val semInt = semester.toIntOrNull()
        val semError = semester.isNotBlank() && (semInt == null || semInt !in 1..14)
        OutlinedTextField(
            value = semester,
            onValueChange = { semester = it.filter { ch -> ch.isDigit() } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(lString("Semester (1-14)", "Semester (1-14)")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = semError,
            supportingText = {
                if (semError) {
                    Text(lString("Semester harus berupa angka antara 1 dan 14", "Semester must be a number between 1 and 14"))
                }
            },
            singleLine = true
        )
        Button(
            enabled = name.isNotBlank() && program.isNotBlank() && semInt != null && semInt in 1..14,
            onClick = {
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val current = profile ?: ProfileRow("", "", 1)
                            StudentHubDatabase.get(context).saveProfile(
                                ProfileRow(
                                    name = name,
                                    program = program,
                                    semester = semester.toInt(),
                                    priorIpk = current.priorIpk,
                                    priorSks = current.priorSks
                                )
                            )
                        }
                    }.onSuccess {
                        error = null
                        onProfileSaved()
                    }.onFailure {
                        error = "Profil belum tersimpan. Periksa isian."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(lString("Simpan profil", "Save profile"))
        }
        OutlinedButton(
            onClick = {
                context.getSharedPreferences("studenthub_prefs", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("has_completed_onboarding", false)
                    .apply()
                context.getSharedPreferences("academic", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("onboarding_completed", false)
                    .apply()
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val db = StudentHubDatabase.get(context)
                        db.reminders().forEach { ReminderScheduler.cancelReminder(context, it.id) }
                        db.resetAllData()
                    }
                    onResetOnboarding()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(lString("Reset Semua Data & Mulai Ulang Setup", "Reset All Data & Restart Setup"))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        languageContent()

        themeContent()

        Text(lString("Daftar Mata Kuliah", "Courses"), style = MaterialTheme.typography.titleLarge)
        val activeSem = semester.toIntOrNull() ?: 5
        CurriculumCourseDropdown(
            currentSemester = activeSem,
            selectedTitle = courseTitle,
            onCourseSelected = { c ->
                if (c != null) {
                    courseCode = c.code
                    courseTitle = c.title
                    courseCredits = c.sks.toString()
                    courseSemester = c.semester.toString()
                } else {
                    courseCode = ""
                    courseTitle = ""
                    courseCredits = ""
                    courseSemester = activeSem.toString()
                }
            }
        )
        OutlinedTextField(courseCode, { courseCode = it }, Modifier.fillMaxWidth(), label = { Text(lString("Kode mata kuliah", "Course code")) }, singleLine = true)
        OutlinedTextField(courseTitle, { courseTitle = it }, Modifier.fillMaxWidth(), label = { Text(lString("Nama mata kuliah", "Course title")) }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                courseCredits,
                { courseCredits = it.filter { ch -> ch.isDigit() } },
                Modifier.weight(1f),
                label = { Text(lString("SKS", "Credits")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            OutlinedTextField(
                courseSemester,
                { courseSemester = it.filter { ch -> ch.isDigit() } },
                Modifier.weight(1f),
                label = { Text(lString("Semester", "Semester")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
        Text(lString("Jadwal Kuliah (Opsional, jam mulai & selesai):", "Class Schedule (Optional, start & end time):"), style = MaterialTheme.typography.labelMedium)
        org.umn.application.studenthub.ui.components.TimeRangePickerRow(
            startTime = courseStartTime,
            endTime = courseEndTime,
            onStartTimeChange = { courseStartTime = it },
            onEndTimeChange = { courseEndTime = it }
        )
        OutlinedTextField(courseRoom, { courseRoom = it }, Modifier.fillMaxWidth(), label = { Text(lString("Ruangan (opsional)", "Room (optional)")) }, singleLine = true)
        Text(lString("Hari Kuliah (jika ada jadwal):", "Class Day (if scheduled):"), style = MaterialTheme.typography.labelMedium)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                lString("Sen", "Mon"),
                lString("Sel", "Tue"),
                lString("Rab", "Wed"),
                lString("Kam", "Thu"),
                lString("Jum", "Fri"),
                lString("Sab", "Sat"),
                lString("Min", "Sun")
            ).forEachIndexed { idx, label ->
                androidx.compose.material3.FilterChip(
                    selected = courseDay == idx,
                    onClick = { courseDay = idx },
                    label = { Text(label) }
                )
            }
        }
        Button(
            enabled = courseCode.isNotBlank() && courseTitle.isNotBlank() && courseCredits.toIntOrNull() != null && (courseSemester.ifBlank { semester }).toIntOrNull() != null,
            onClick = {
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val db = StudentHubDatabase.get(context)
                            val daysId = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
                            val cleanCode = courseCode.trim().uppercase()
                            val cleanTitle = courseTitle.trim()
                            val combinedTime = org.umn.application.studenthub.ui.components.combineTimeRange(courseStartTime, courseEndTime)
                            val normalizedTime = if (combinedTime.isNotBlank()) StudentHubDatabase.normalizeTimeFormat(combinedTime) else ""
                            val hasValidTime = Regex("(?:[01][0-9]|2[0-3])\\.[0-5][0-9](?:–(?:[01][0-9]|2[0-3])\\.[0-5][0-9])?").matches(normalizedTime)
                            val rangeLabel = if (hasValidTime) org.umn.application.studenthub.ui.dashboard.computeClassTimeRange(normalizedTime) else ""
                            val scheduleLabel = if (hasValidTime) "${daysId[courseDay]}, $rangeLabel" else ""

                            db.saveCourse(
                                CourseRow(
                                    id = cleanCode,
                                    code = cleanCode,
                                    title = cleanTitle,
                                    sks = courseCredits.trim().toInt(),
                                    semester = (courseSemester.ifBlank { semester }).trim().toInt(),
                                    grade = null,
                                    room = courseRoom.trim(),
                                    schedule = scheduleLabel
                                )
                            )
                            if (hasValidTime) {
                                val detailText = buildList {
                                    add(cleanCode)
                                    if (courseRoom.isNotBlank()) add(courseRoom.trim())
                                }.joinToString(" • ")
                                db.addAgenda(courseDay, rangeLabel.ifBlank { normalizedTime }, cleanTitle, "KELAS", detailText, cleanCode)
                                db.addReminder(
                                    title = "Kelas $cleanTitle",
                                    detail = "${daysId[courseDay]}, $rangeLabel" + if (courseRoom.isNotBlank()) " • ${courseRoom.trim()}" else "",
                                    time = normalizedTime.substringBefore('–'),
                                    category = "Kelas",
                                    enabled = true,
                                    sourceId = "course:$cleanCode"
                                )
                            }
                        }
                    }.onSuccess {
                        courseCode = ""
                        courseTitle = ""
                        courseCredits = ""
                        courseSemester = ""
                        courseStartTime = ""
                        courseEndTime = ""
                        courseRoom = ""
                        error = null
                        load()
                    }.onFailure {
                        error = "Mata kuliah belum tersimpan. Periksa isian."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(lString("Tambah mata kuliah", "Add course")) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            courses!!.groupBy { it.semester }.forEach { (term, rows) ->
                Text("Semester $term", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                rows.forEach { course ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(course.title)
                            val sub = buildList {
                                add("${course.code} · ${course.sks} SKS")
                                if (course.schedule.isNotBlank()) add(course.schedule)
                                if (course.room.isNotBlank()) add(course.room)
                            }.joinToString(" • ")
                            Text(sub, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(course.grade?.let { "${it.letter} · ${it.score}" } ?: lString("Belum ada nilai", "No grade yet"))
                        IconButton(
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        StudentHubDatabase.get(context).deleteCourse(course.id)
                                    }
                                    load()
                                }
                            }
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = lString("Hapus mata kuliah", "Delete course"), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    HorizontalDivider()
                }
            }
            if (courses!!.isEmpty()) Text(lString("Belum ada mata kuliah.", "No courses registered yet."))
        }
    }
}
