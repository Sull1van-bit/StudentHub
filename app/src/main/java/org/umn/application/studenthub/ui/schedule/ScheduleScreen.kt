package org.umn.application.studenthub.ui.schedule

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.umn.application.studenthub.data.local.AgendaRow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.umn.application.studenthub.R
import org.umn.application.studenthub.ui.dashboard.AgendaEntry
import org.umn.application.studenthub.ui.dashboard.ReminderRow
import org.umn.application.studenthub.ui.dashboard.SectionHeading
import org.umn.application.studenthub.ui.dashboard.SupportingText
import org.umn.application.studenthub.data.local.StudentHubDatabase
import org.umn.application.studenthub.data.local.CourseRow
import org.umn.application.studenthub.ui.dashboard.getCourseInfo
import org.umn.application.studenthub.ui.dashboard.computeClassTimeRange

import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import org.umn.application.studenthub.ui.localization.lString
import org.umn.application.studenthub.ui.localization.LocalAppLanguage
import org.umn.application.studenthub.ui.localization.AppLanguage
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(onCourseClick: (String) -> Unit, onLearningClick: () -> Unit) {
    val today = remember { LocalDate.now() }
    val todayDayIndex = (today.dayOfWeek.value - 1).coerceIn(0, 6)
    var selectedDay by rememberSaveable { mutableIntStateOf(0) }
    var daily by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var time by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val database = remember(context) { StudentHubDatabase.get(context) }
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf<List<AgendaRow>>(emptyList()) }
    var savedCourses by remember { mutableStateOf<List<CourseRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var inspecting by remember { mutableStateOf<AgendaRow?>(null) }

    val isEn = LocalAppLanguage.current == AppLanguage.ENGLISH
    val monday = remember(today) { today.minusDays(todayDayIndex.toLong()) }
    val weekDates = remember(monday) { (0..6).map { monday.plusDays(it.toLong()) } }

    fun getFullMonthName(m: Int, en: Boolean): String = when (m) {
        1 -> if (en) "January" else "Januari"
        2 -> if (en) "February" else "Februari"
        3 -> if (en) "March" else "Maret"
        4 -> if (en) "April" else "April"
        5 -> if (en) "May" else "Mei"
        6 -> if (en) "June" else "Juni"
        7 -> if (en) "July" else "Juli"
        8 -> if (en) "August" else "Agustus"
        9 -> if (en) "September" else "September"
        10 -> if (en) "October" else "Oktober"
        11 -> if (en) "November" else "November"
        else -> if (en) "December" else "Desember"
    }

    LaunchedEffect(version) {
        loading = true
        withContext(Dispatchers.IO) {
            runCatching {
                val agendas = (0..6).flatMap(database::agenda)
                val courses = database.courses()
                Pair(agendas, courses)
            }
        }.onSuccess { (agendas, courses) ->
            entries = agendas
            savedCourses = courses
            error = null
        }.onFailure {
            error = if (isEn) "Agenda could not be loaded." else "Agenda tidak dapat dibaca. Data lama tidak diubah."
        }
        loading = false
    }

    val daysId = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
    val daysEn = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val fullDaysId = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
    val fullDaysEn = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    val days = if (isEn) daysEn else daysId
    val fullDays = if (isEn) fullDaysEn else fullDaysId

    val monDate = weekDates[0]
    val sunDate = weekDates[6]
    val weekRangeSubtitle = if (isEn) {
        "${getFullMonthName(monDate.monthValue, true)} ${monDate.dayOfMonth}–${sunDate.dayOfMonth}, ${sunDate.year} • Academic Week 6"
    } else {
        "${monDate.dayOfMonth}–${sunDate.dayOfMonth} ${getFullMonthName(sunDate.monthValue, false)} ${sunDate.year} • Minggu kuliah 6"
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (isEn) "Class Schedule" else "Minggu yang terarah.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
            SupportingText(weekRangeSubtitle)
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(lString("Minggu", "Week"), lString("Hari", "Day")).forEachIndexed { index, label ->
                SegmentedButton(selected = daily == (index == 1), onClick = { daily = index == 1 }, shape = SegmentedButtonDefaults.itemShape(index, 2)) {
                    Text(label)
                }
            }
        }
        if (!daily) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.forEachIndexed { index, day ->
                val date = weekDates[index]
                val isToday = (index == todayDayIndex)
                val isSelected = (selectedDay == index)
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .background(
                            when {
                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                isToday -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.surface
                            }
                        )
                        .border(
                            width = if (isToday && !isSelected) 1.5.dp else 0.dp,
                            color = if (isToday && !isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { selectedDay = index }.height(92.dp).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
                ) {
                    Text(
                        day,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${date.dayOfMonth}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (isToday) {
                        Text(
                            lString("Hari ini", "Today"),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            painterResource(R.drawable.hub_day_dot),
                            null,
                            Modifier.size(4.dp),
                            tint = if (entries.any { it.day == index }) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val selectedDate = weekDates[selectedDay]
            val headingTitle = "${fullDays[selectedDay]}, ${selectedDate.dayOfMonth} ${getFullMonthName(selectedDate.monthValue, isEn)}"
            val headingAction = if (selectedDay != todayDayIndex) lString("Ke Hari Ini", "Today") else null
            SectionHeading(headingTitle, headingAction) { selectedDay = todayDayIndex }

            if (loading) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { version++ }) { Text(lString("Coba lagi", "Retry")) } }
            val rawDayEntries = entries.filter { it.day == selectedDay }.sortedBy { it.time }
            val hasCustomEntries = rawDayEntries.any { !it.id.startsWith("demo-") && !it.id.startsWith("legacy-") }
            val dayEntries = if (hasCustomEntries) {
                rawDayEntries.filter { !it.id.startsWith("demo-") && !it.id.startsWith("legacy-") && !it.title.contains("Rekayasa Perangkat Lunak", ignoreCase = true) }
            } else {
                rawDayEntries
            }
            val dayClasses = dayEntries.filter { isClassEntry(it, savedCourses) }

            if (!loading && error == null) {
                if (dayEntries.isNotEmpty()) {
                    val classCount = dayClasses.size
                    val deadlineCount = dayEntries.count { isDeadlineEntry(it) }
                    val studyCount = dayEntries.count { isStudyEntry(it) }
                    val otherCount = dayEntries.size - classCount - deadlineCount - studyCount

                    val parts = mutableListOf<String>()
                    if (classCount > 0) parts.add(lString("$classCount kelas", if (classCount == 1) "$classCount class" else "$classCount classes"))
                    if (deadlineCount > 0) parts.add(lString("$deadlineCount tenggat", if (deadlineCount == 1) "$deadlineCount deadline" else "$deadlineCount deadlines"))
                    if (studyCount > 0) parts.add(lString("$studyCount sesi belajar", if (studyCount == 1) "$studyCount study session" else "$studyCount study sessions"))
                    if (otherCount > 0 && parts.isEmpty()) parts.add(lString("$otherCount agenda", if (otherCount == 1) "$otherCount agenda" else "$otherCount agendas"))

                    val summaryText = if (parts.isNotEmpty()) parts.joinToString(" • ") else lString("${dayEntries.size} agenda terjadwal", "${dayEntries.size} scheduled items")
                    SupportingText(summaryText)
                } else {
                    SupportingText(lString("Belum ada agenda tersimpan untuk hari ini.", "No agenda scheduled for this day."))
                }
            }
            dayEntries.forEach { entry ->
                val isClass = isClassEntry(entry, savedCourses)
                val isStudy = !isClass && isStudyEntry(entry)
                val isDeadline = !isClass && isDeadlineEntry(entry)

                when {
                    isClass -> {
                        val (catCode, catRoom) = getCourseInfo(entry.title)
                        val matchingDbCourse = savedCourses.firstOrNull {
                            it.code.equals(entry.title, ignoreCase = true) ||
                            it.title.equals(entry.title, ignoreCase = true) ||
                            entry.title.contains(it.code, ignoreCase = true) ||
                            entry.title.contains(it.title, ignoreCase = true)
                        }
                        val code = matchingDbCourse?.code ?: catCode.ifBlank { "IF570" }
                        val room = if (catRoom.isNotBlank() && catRoom != "Ruang Kuliah") catRoom else "Ruang Kuliah"
                        val timeRange = computeClassTimeRange(entry.time)
                        val detail = "$code • $room • $timeRange"
                        val isFirstClassOfDay = (entry == dayClasses.firstOrNull())
                        val kind = if (isFirstClassOfDay) lString("KELAS • PENGINGAT 15 MENIT", "CLASS • 15 MIN REMINDER") else lString("KELAS", "CLASS")
                        AgendaEntry(
                            time = entry.time,
                            kind = kind,
                            title = entry.title,
                            detail = detail,
                            highlighted = isFirstClassOfDay
                        ) { onCourseClick(code) }
                    }
                    isStudy -> {
                        AgendaEntry(
                            time = entry.time,
                            kind = lString("BELAJAR", "STUDY"),
                            title = entry.title,
                            detail = if (entry.id == "demo-study-if550") "Ulas pola MVC/MVVM" else lString("Target belajar mandiri • 30 menit", "Independent study session • 30 mins"),
                            onClick = onLearningClick
                        )
                    }
                    isDeadline -> {
                        val (code, _) = getCourseInfo(entry.title)
                        AgendaEntry(
                            time = entry.time,
                            kind = lString("TENGGAT", "DEADLINE"),
                            title = entry.title,
                            detail = if (code.isNotBlank()) "$code • " + lString("Unggah laporan & kode", "Submit report & code") else lString("Batas akhir pengumpulan", "Submission deadline")
                        ) {
                            if (code.isNotBlank()) onCourseClick(code) else { inspecting = entry }
                        }
                    }
                    entry.id.startsWith("demo-lab") || entry.title.contains("lab", ignoreCase = true) -> {
                        val (code, _) = getCourseInfo(entry.title)
                        AgendaEntry(entry.time, lString("PRAKTIKUM", "LAB"), entry.title, if (code.isNotBlank()) "$code • Lab" else entry.title) {
                            if (code.isNotBlank()) onCourseClick(code) else { inspecting = entry }
                        }
                    }
                    entry.id.startsWith("demo-quiz") || entry.title.contains("kuis", ignoreCase = true) -> {
                        val (code, _) = getCourseInfo(entry.title)
                        AgendaEntry(entry.time, lString("KUIS", "QUIZ"), entry.title, if (code.isNotBlank()) "$code • Evaluasi kuis" else entry.title) {
                            if (code.isNotBlank()) onCourseClick(code) else { inspecting = entry }
                        }
                    }
                    entry.id.startsWith("demo-project") || entry.title.contains("proyek", ignoreCase = true) -> {
                        val (code, _) = getCourseInfo(entry.title)
                        AgendaEntry(entry.time, lString("ASISTENSI", "ASSISTANCE"), entry.title, if (code.isNotBlank()) "$code • Proyek semester" else entry.title) {
                            if (code.isNotBlank()) onCourseClick(code) else { inspecting = entry }
                        }
                    }
                    entry.id == "demo-kos-payment" || entry.title.contains("bayar", ignoreCase = true) -> {
                        AgendaEntry(entry.time, lString("KEUANGAN", "FINANCE"), entry.title, entry.title) { inspecting = entry }
                    }
                    else -> {
                        AgendaEntry(entry.time, lString("AGENDA", "AGENDA"), entry.title, lString("Agenda pribadi", "Personal agenda")) { inspecting = entry }
                    }
                }
            }
        }
        ReminderRow(R.drawable.hub_bell, lString("Pengingat kelas aktif 15 menit sebelumnya.", "Class reminders active 15 minutes before."))
        Button(onClick = { adding = true }, enabled = !loading && error == null, modifier = Modifier.fillMaxWidth().testTag("add-agenda-btn")) {
            Text(lString("Tambah agenda", "Add agenda"))
        }
    }
    if (adding) AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(16.dp),
        onDismissRequest = { adding = false },
        title = { Text(lString("Tambah agenda", "Add agenda")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(lString("Judul agenda", "Agenda title")) }, singleLine = true)
                OutlinedTextField(time, { time = it }, label = { Text(lString("Waktu (HH.mm)", "Time (HH.mm)")) }, singleLine = true,
                    supportingText = { Text(lString("Contoh: 18.30", "Example: 18.30")) })
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && title.isNotBlank() && title.length <= 200 && Regex("(?:[01][0-9]|2[0-3])\\.[0-5][0-9]").matches(time),
                onClick = {
                    saving = true
                    scope.launch {
                        runCatching { database.addAgenda(selectedDay, time, title) }
                            .onSuccess { version++; adding = false; title = ""; time = "" }
                            .onFailure { error = if (isEn) "Failed to save agenda. Please retry." else "Agenda gagal disimpan. Coba lagi." }
                        saving = false
                    }
                }
            ) { Text(lString("Simpan", "Save")) }
        },
        dismissButton = { TextButton(onClick = { adding = false }) { Text(lString("Batal", "Cancel")) } }
    )
    inspecting?.let { entry ->
        AlertDialog(
            onDismissRequest = { inspecting = null },
            title = { Text(entry.title) },
            text = { Text("${fullDays[entry.day]} • " + lString("Pukul ${entry.time}", "At ${entry.time}")) },
            confirmButton = {
                TextButton(onClick = { inspecting = null }) { Text(lString("Tutup", "Close")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { database.deleteAgenda(entry.id) }
                        version++
                        inspecting = null
                    }
                }) { Text(lString("Hapus", "Delete"), color = MaterialTheme.colorScheme.error) }
            }
        )
    }
}

private fun isClassEntry(entry: AgendaRow, savedCourses: List<CourseRow>): Boolean {
    if (entry.id.startsWith("demo-class")) return true
    if (entry.id.startsWith("demo-study") || entry.id.startsWith("demo-deadline") || entry.id.startsWith("demo-lab") || entry.id.startsWith("demo-quiz") || entry.id.startsWith("demo-project") || entry.id.startsWith("demo-kos")) return false
    val (code, _) = getCourseInfo(entry.title)
    if (code.isNotBlank()) return true
    return savedCourses.any {
        it.code.equals(entry.title, ignoreCase = true) ||
        it.title.equals(entry.title, ignoreCase = true) ||
        entry.title.contains(it.code, ignoreCase = true) ||
        entry.title.contains(it.title, ignoreCase = true)
    }
}

private fun isStudyEntry(entry: AgendaRow): Boolean {
    return entry.id.startsWith("demo-study") || entry.title.contains("belajar", ignoreCase = true) || entry.title.contains("latihan", ignoreCase = true) || entry.title.contains("study", ignoreCase = true)
}

private fun isDeadlineEntry(entry: AgendaRow): Boolean {
    return entry.id.startsWith("demo-deadline") || entry.title.contains("tugas", ignoreCase = true) || entry.title.contains("praktikum", ignoreCase = true) || entry.title.contains("deadline", ignoreCase = true) || entry.title.contains("tenggat", ignoreCase = true)
}
