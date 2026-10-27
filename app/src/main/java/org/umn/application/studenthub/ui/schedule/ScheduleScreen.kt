package org.umn.application.studenthub.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.umn.application.studenthub.R
import org.umn.application.studenthub.data.local.AgendaRow
import org.umn.application.studenthub.data.local.CourseRow
import org.umn.application.studenthub.data.local.ReminderItemRow
import org.umn.application.studenthub.data.local.StudentHubDatabase
import org.umn.application.studenthub.data.local.TaskRow
import org.umn.application.studenthub.ui.dashboard.AgendaEntry
import org.umn.application.studenthub.ui.dashboard.ReminderRow
import org.umn.application.studenthub.ui.dashboard.SectionHeading
import org.umn.application.studenthub.ui.dashboard.SupportingText
import org.umn.application.studenthub.ui.dashboard.computeClassTimeRange
import org.umn.application.studenthub.ui.dashboard.getCourseInfo
import org.umn.application.studenthub.ui.localization.AppLanguage
import org.umn.application.studenthub.ui.localization.LocalAppLanguage
import org.umn.application.studenthub.ui.localization.lString
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(onCourseClick: (String) -> Unit, onLearningClick: () -> Unit) {
    val today = remember { LocalDate.now() }
    val todayDayIndex = (today.dayOfWeek.value - 1).coerceIn(0, 6)
    var selectedDay by rememberSaveable { mutableIntStateOf(todayDayIndex) }
    // viewMode: 0 = Kalender (Monthly Calendar), 1 = Minggu (Week), 2 = Hari (Day)
    var viewMode by rememberSaveable { mutableIntStateOf(0) }
    var displayedYear by rememberSaveable { mutableIntStateOf(today.year) }
    var displayedMonthValue by rememberSaveable { mutableIntStateOf(today.monthValue) }
    var selectedDateEpochDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val selectedCalendarDate = remember(selectedDateEpochDay) { LocalDate.ofEpochDay(selectedDateEpochDay) }
    val displayedYearMonth = remember(displayedYear, displayedMonthValue) {
        YearMonth.of(displayedYear, displayedMonthValue.coerceIn(1, 12))
    }

    var adding by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var time by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("AGENDA") }
    var detail by rememberSaveable { mutableStateOf("") }
    var endTime by rememberSaveable { mutableStateOf("") }
    var enableReminder by rememberSaveable { mutableStateOf(true) }

    val context = LocalContext.current
    val database = remember(context) { StudentHubDatabase.get(context) }
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf(runCatching { (0..6).flatMap(database::agenda) }.getOrDefault(emptyList())) }
    var savedCourses by remember { mutableStateOf(runCatching { database.courses() }.getOrDefault(emptyList())) }
    var reminders by remember { mutableStateOf(runCatching { database.reminders() }.getOrDefault(emptyList())) }
    var savedTasks by remember { mutableStateOf(runCatching { database.tasks() }.getOrDefault(emptyList())) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var inspecting by remember { mutableStateOf<AgendaRow?>(null) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                version++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val isEn = LocalAppLanguage.current == AppLanguage.ENGLISH

    LaunchedEffect(version) {
        runCatching {
            withContext(Dispatchers.IO) {
                val courses = database.courses()
                val agendas = (0..6).flatMap(database::agenda).toMutableList()
                courses.forEach { course ->
                    val pair = org.umn.application.studenthub.ui.dashboard.extractCourseScheduleDayAndTime(course.schedule)
                    if (pair != null) {
                        val already = agendas.any {
                            it.day == pair.first && (
                                it.courseId.equals(course.code, true) ||
                                it.title.equals(course.title, true) ||
                                it.title.equals(course.code, true)
                            )
                        }
                        if (!already) {
                            val detailText = buildList {
                                add(course.code)
                                if (course.room.isNotBlank()) add(course.room)
                            }.joinToString(" • ")
                            val synth = AgendaRow(
                                id = "course-sched-${course.id}",
                                day = pair.first,
                                time = pair.second,
                                title = course.title,
                                kind = "KELAS",
                                detail = detailText,
                                courseId = course.code
                            )
                            agendas.add(synth)
                            runCatching {
                                database.addAgenda(synth.day, synth.time, synth.title, synth.kind, synth.detail, synth.courseId)
                            }
                        }
                    }
                }
                val rems = database.reminders()
                val tasks = database.tasks()
                listOf(agendas, courses, rems, tasks)
            }
        }.onSuccess { result ->
            @Suppress("UNCHECKED_CAST")
            entries = result[0] as List<AgendaRow>
            @Suppress("UNCHECKED_CAST")
            savedCourses = result[1] as List<CourseRow>
            @Suppress("UNCHECKED_CAST")
            reminders = result[2] as List<ReminderItemRow>
            @Suppress("UNCHECKED_CAST")
            savedTasks = result[3] as List<TaskRow>
            error = null
        }.onFailure {
            error = if (isEn) "Schedule could not be loaded." else "Gagal memuat jadwal agenda."
        }
        loading = false
    }

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

    val daysId = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
    val daysEn = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val fullDaysId = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
    val fullDaysEn = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    val days = if (isEn) daysEn else daysId
    val fullDays = if (isEn) fullDaysEn else fullDaysId

    val monDate = weekDates[0]
    val sunDate = weekDates[6]
    val weekRangeSubtitle = if (isEn) {
        "${getFullMonthName(monDate.monthValue, true)} ${monDate.dayOfMonth}–${sunDate.dayOfMonth}, ${sunDate.year}"
    } else {
        "${monDate.dayOfMonth}–${sunDate.dayOfMonth} ${getFullMonthName(sunDate.monthValue, false)} ${sunDate.year}"
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (isEn) "Schedule & Calendar" else "Jadwal & Kalender Kuliah", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
            SupportingText(
                if (viewMode == 0) {
                    "${getFullMonthName(displayedYearMonth.monthValue, isEn)} ${displayedYearMonth.year}"
                } else {
                    weekRangeSubtitle
                }
            )
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val modeLabels = listOf(
                lString("Kalender", "Calendar"),
                lString("Minggu", "Week"),
                lString("Hari", "Day")
            )
            modeLabels.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = viewMode == index,
                    onClick = { viewMode = index },
                    shape = SegmentedButtonDefaults.itemShape(index, modeLabels.size)
                ) {
                    Text(label)
                }
            }
        }

        if (viewMode == 0) {
            // Tampilan Kalender Bulanan Interaktif
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("schedule-calendar-card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val prev = displayedYearMonth.minusMonths(1)
                                displayedYear = prev.year
                                displayedMonthValue = prev.monthValue
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = lString("Bulan sebelumnya", "Previous month"))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${getFullMonthName(displayedYearMonth.monthValue, isEn)} ${displayedYearMonth.year}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (displayedYearMonth != YearMonth.from(today) || selectedCalendarDate != today) {
                                TextButton(
                                    onClick = {
                                        displayedYear = today.year
                                        displayedMonthValue = today.monthValue
                                        selectedDateEpochDay = today.toEpochDay()
                                        selectedDay = todayDayIndex
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text(lString("Ke Hari Ini", "Jump to Today"), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        IconButton(
                            onClick = {
                                val next = displayedYearMonth.plusMonths(1)
                                displayedYear = next.year
                                displayedMonthValue = next.monthValue
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = lString("Bulan berikutnya", "Next month"))
                        }
                    }

                    // Header Hari (Sen..Min)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        days.forEach { dLabel ->
                            Text(
                                text = dLabel,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val firstOfMonth = displayedYearMonth.atDay(1)
                    val leadingEmpty = (firstOfMonth.dayOfWeek.value - 1).coerceIn(0, 6)
                    val daysInMonth = displayedYearMonth.lengthOfMonth()
                    val totalCells = ((leadingEmpty + daysInMonth + 6) / 7) * 7
                    val rowsCount = totalCells / 7

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (rowIdx in 0 until rowsCount) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (colIdx in 0..6) {
                                    val cellIndex = rowIdx * 7 + colIdx
                                    val dayNumber = cellIndex - leadingEmpty + 1
                                    if (dayNumber !in 1..daysInMonth) {
                                        Spacer(modifier = Modifier.weight(1f).height(46.dp))
                                    } else {
                                        val cellDate = displayedYearMonth.atDay(dayNumber)
                                        val cellDayOfWeek = (cellDate.dayOfWeek.value - 1).coerceIn(0, 6)
                                        val isCellToday = cellDate == today
                                        val isCellSelected = cellDate == selectedCalendarDate && selectedDay == cellDayOfWeek

                                        val dayAgendas = entries.filter { it.day == cellDayOfWeek }
                                        val hasClass = dayAgendas.any { isClassEntry(it, savedCourses) }
                                        val hasStudyOrOther = dayAgendas.any { !isClassEntry(it, savedCourses) }
                                        val hasTaskDeadline = savedTasks.any {
                                            !it.completed && it.deadline.trim() == cellDate.toString()
                                        } || dayAgendas.any { isDeadlineEntry(it) }

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    when {
                                                        isCellSelected -> MaterialTheme.colorScheme.primary
                                                        isCellToday -> MaterialTheme.colorScheme.primaryContainer
                                                        else -> Color.Transparent
                                                    }
                                                )
                                                .border(
                                                    width = if (isCellToday && !isCellSelected) 1.dp else 0.dp,
                                                    color = if (isCellToday && !isCellSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .clickable {
                                                    selectedDateEpochDay = cellDate.toEpochDay()
                                                    selectedDay = cellDayOfWeek
                                                }
                                                .padding(vertical = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "$dayNumber",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (isCellSelected || isCellToday) FontWeight.Bold else FontWeight.Medium,
                                                color = when {
                                                    isCellSelected -> MaterialTheme.colorScheme.onPrimary
                                                    isCellToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (hasClass) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(5.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                if (isCellSelected) MaterialTheme.colorScheme.onPrimary
                                                                else MaterialTheme.colorScheme.primary
                                                            )
                                                    )
                                                }
                                                if (hasTaskDeadline) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(5.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                if (isCellSelected) MaterialTheme.colorScheme.errorContainer
                                                                else MaterialTheme.colorScheme.error
                                                            )
                                                    )
                                                }
                                                if (hasStudyOrOther) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(5.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                if (isCellSelected) MaterialTheme.colorScheme.secondaryContainer
                                                                else MaterialTheme.colorScheme.tertiary
                                                            )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CalendarLegendItem(color = MaterialTheme.colorScheme.primary, label = lString("Kelas", "Class"))
                        CalendarLegendItem(color = MaterialTheme.colorScheme.error, label = lString("Tenggat Tugas", "Task Deadline"))
                        CalendarLegendItem(color = MaterialTheme.colorScheme.tertiary, label = lString("Belajar / Agenda", "Study / Agenda"))
                    }
                }
            }
        } else if (viewMode == 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            .clickable {
                                selectedDay = index
                                selectedDateEpochDay = date.toEpochDay()
                            }
                            .height(92.dp)
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
                    ) {
                        Text(
                            day,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${date.dayOfMonth}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isToday) {
                                Text(
                                    lString("Hari ini", "Today"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (entries.any { it.day == index }) {
                                Icon(
                                    painterResource(R.drawable.hub_day_dot),
                                    null,
                                    Modifier.size(5.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            } else if (!isToday) {
                                Spacer(Modifier.size(5.dp))
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val selectedDate = if (viewMode == 0 && (selectedCalendarDate.dayOfWeek.value - 1).coerceIn(0, 6) == selectedDay) {
                selectedCalendarDate
            } else {
                weekDates[selectedDay]
            }
            val headingTitle = "${fullDays[selectedDay]}, ${selectedDate.dayOfMonth} ${getFullMonthName(selectedDate.monthValue, isEn)} ${selectedDate.year}"
            val headingAction = if (selectedDay != todayDayIndex || selectedDate != today) lString("Ke Hari Ini", "Today") else null
            SectionHeading(headingTitle, headingAction) {
                selectedDay = todayDayIndex
                selectedDateEpochDay = today.toEpochDay()
                displayedYear = today.year
                displayedMonthValue = today.monthValue
            }

            if (loading) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { version++ }) { Text(lString("Coba lagi", "Retry")) } }
            val dayEntries = entries.filter { it.day == selectedDay }.sortedBy { it.time }
            val dayClasses = dayEntries.filter { isClassEntry(it, savedCourses) }
            val dateDeadlines = savedTasks.filter { !it.completed && it.deadline.trim() == selectedDate.toString() }

            if (dateDeadlines.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            lString("Tenggat Tugas pada Tanggal Ini (${dateDeadlines.size})", "Task Deadlines on This Date (${dateDeadlines.size})"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        dateDeadlines.forEach { t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { if (t.courseId.isNotBlank()) onCourseClick(t.courseId) },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "• ${t.courseId.ifBlank { "Tugas" }}: ${t.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = t.deadline,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

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
                    val otherDaysWithClasses = entries.filter { isClassEntry(it, savedCourses) }
                    if (otherDaysWithClasses.isNotEmpty()) {
                        SupportingText(lString("Belum ada jadwal kuliah untuk hari ini.", "No scheduled classes for this day."))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                lString("Jadwal kelas hari lain minggu ini:", "Classes on other days this week:"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            otherDaysWithClasses.distinctBy { "${it.day}-${it.title}" }.sortedBy { it.day }.forEach { otherEntry ->
                                Card(
                                    onClick = { selectedDay = otherEntry.day },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                "${fullDays[otherEntry.day]} • ${computeClassTimeRange(otherEntry.time)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                otherEntry.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (otherEntry.detail.isNotBlank()) {
                                                SupportingText(otherEntry.detail)
                                            }
                                        }
                                        TextButton(onClick = { selectedDay = otherEntry.day }) {
                                            Text(lString("Buka", "Open"))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        SupportingText(lString("Belum ada agenda tersimpan untuk hari ini.", "No agenda scheduled for this day."))
                    }
                }
            }
            val nowTime = java.time.LocalTime.now()
            val nextClassToHighlight = if (selectedDay == todayDayIndex) {
                dayClasses.firstOrNull { org.umn.application.studenthub.ui.dashboard.isAgendaStillActiveOrUpcoming(it.time, nowTime) }
            } else {
                dayClasses.firstOrNull()
            }
            dayEntries.forEach { entry ->
                val isClass = isClassEntry(entry, savedCourses)
                val isStudy = !isClass && isStudyEntry(entry)
                val isDeadline = !isClass && isDeadlineEntry(entry)

                when {
                    isClass -> {
                        val (catCode, catRoom) = getCourseInfo(entry.title, savedCourses)
                        val matchingDbCourse = savedCourses.firstOrNull {
                            it.id == entry.courseId ||
                                it.code.equals(entry.title, ignoreCase = true) ||
                                it.title.equals(entry.title, ignoreCase = true) ||
                                (it.code.isNotBlank() && entry.title.contains(it.code, ignoreCase = true)) ||
                                (it.title.isNotBlank() && entry.title.contains(it.title, ignoreCase = true))
                        }
                        val code = entry.courseId.ifBlank { matchingDbCourse?.code ?: catCode }
                        val room = entry.detail.ifBlank { matchingDbCourse?.room?.takeIf { it.isNotBlank() } ?: catRoom.ifBlank { "Ruang Kuliah" } }
                        val timeRange = computeClassTimeRange(entry.time)
                        val detailParts = listOfNotNull(
                            code.takeIf { it.isNotBlank() },
                            room.takeIf { it.isNotBlank() },
                            timeRange
                        )
                        val isHighlightedNextClass = (entry == nextClassToHighlight)
                        val isPassedToday = selectedDay == todayDayIndex &&
                            !org.umn.application.studenthub.ui.dashboard.isAgendaStillActiveOrUpcoming(entry.time, nowTime)
                        val kindLabel = when {
                            isHighlightedNextClass -> lString("Kelas • Pengingat aktif", "Class • Reminder active")
                            isPassedToday -> lString("Kelas • Selesai", "Class • Finished")
                            else -> lString("Kelas", "Class")
                        }
                        AgendaEntry(
                            time = entry.time,
                            kind = kindLabel,
                            title = entry.title,
                            detail = detailParts.joinToString(" • "),
                            highlighted = isHighlightedNextClass
                        ) {
                            if (code.isNotBlank()) onCourseClick(code) else inspecting = entry
                        }
                    }
                    isStudy -> {
                        AgendaEntry(
                            time = entry.time,
                            kind = lString("Belajar", "Study"),
                            title = entry.title,
                            detail = entry.detail.ifBlank { lString("Sesi belajar mandiri", "Independent study session") },
                            onClick = onLearningClick
                        )
                    }
                    isDeadline -> {
                        val (code, _) = getCourseInfo(entry.title, savedCourses)
                        val resolvedCode = entry.courseId.ifBlank { code }
                        AgendaEntry(
                            time = entry.time,
                            kind = lString("Tenggat", "Deadline"),
                            title = entry.title,
                            detail = entry.detail.ifBlank {
                                if (resolvedCode.isNotBlank()) "$resolvedCode • " + lString("Batas pengumpulan", "Submission deadline")
                                else lString("Batas akhir pengumpulan", "Submission deadline")
                            }
                        ) {
                            if (resolvedCode.isNotBlank()) onCourseClick(resolvedCode) else inspecting = entry
                        }
                    }
                    entry.kind.equals("KEUANGAN", ignoreCase = true) || entry.title.contains("bayar", ignoreCase = true) -> {
                        AgendaEntry(entry.time, lString("Keuangan", "Finance"), entry.title, entry.detail.ifBlank { entry.title }) { inspecting = entry }
                    }
                    else -> {
                        AgendaEntry(entry.time, lString("Agenda", "Agenda"), entry.title, entry.detail.ifBlank { lString("Agenda pribadi", "Personal agenda") }) { inspecting = entry }
                    }
                }
            }
        }
        val activeReminderCount = reminders.count { it.enabled }
        val reminderStatusText = if (activeReminderCount > 0) {
            lString("$activeReminderCount pengingat aktif.", "$activeReminderCount active reminders.")
        } else {
            lString("Belum ada pengingat aktif.", "No active reminders.")
        }
        ReminderRow(R.drawable.hub_bell, reminderStatusText)
        Button(onClick = { adding = true }, enabled = !loading && error == null, modifier = Modifier.fillMaxWidth().testTag("add-agenda-btn")) {
            Text(lString("Tambah agenda", "Add agenda"))
        }
    }
    if (adding) AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth().padding(16.dp),
        onDismissRequest = { adding = false },
        title = { Text(lString("Tambah agenda", "Add agenda")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "AGENDA" to lString("Agenda", "Agenda"),
                        "KELAS" to lString("Kelas", "Class"),
                        "TUGAS" to lString("Tenggat", "Deadline"),
                        "BELAJAR" to lString("Belajar", "Study"),
                        "KEUANGAN" to lString("Keuangan", "Finance")
                    ).forEach { (k, label) ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(label) })
                    }
                }
                if (kind == "KELAS") {
                    val currentSem = remember { runCatching { database.profile().semester }.getOrDefault(5) }
                    org.umn.application.studenthub.ui.components.CurriculumCourseDropdown(
                        currentSemester = currentSem,
                        selectedTitle = title,
                        onCourseSelected = { c ->
                            if (c != null) {
                                title = "${c.code} ${c.title}"
                            }
                        }
                    )
                }
                OutlinedTextField(title, { title = it }, label = { Text(if (kind == "KELAS") lString("Nama / Kode Mata Kuliah", "Course Name / Code") else lString("Judul agenda", "Agenda title")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(lString("Waktu Agenda / Kelas (Mulai - Selesai):", "Agenda / Class Time (Start - End):"), style = MaterialTheme.typography.labelMedium)
                org.umn.application.studenthub.ui.components.TimeRangePickerRow(
                    startTime = time,
                    endTime = endTime,
                    onStartTimeChange = { time = it },
                    onEndTimeChange = { endTime = it }
                )
                OutlinedTextField(
                    detail,
                    { detail = it },
                    label = { Text(lString("Ruangan / Keterangan (opsional)", "Room / Detail (optional)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(lString("Aktifkan pengingat notifikasi", "Enable notification reminder"), style = MaterialTheme.typography.bodySmall)
                    Switch(checked = enableReminder, onCheckedChange = { enableReminder = it })
                }
            }
        },
        confirmButton = {
            val combinedTime = org.umn.application.studenthub.ui.components.combineTimeRange(time, endTime)
            val normalizedTime = StudentHubDatabase.normalizeTimeFormat(combinedTime)
            val timeValid = Regex("(?:[01][0-9]|2[0-3])\\.[0-5][0-9](?:–(?:[01][0-9]|2[0-3])\\.[0-5][0-9])?").matches(normalizedTime)
            TextButton(
                enabled = !saving && title.isNotBlank() && title.length <= 200 && timeValid,
                onClick = {
                    saving = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val rangeTime = computeClassTimeRange(normalizedTime)
                                val courseCode = if (kind == "KELAS") {
                                    val existingCourse = database.courses().firstOrNull {
                                        it.code.equals(title.trim(), true) || it.title.equals(title.trim(), true)
                                    }
                                    val code = existingCourse?.code ?: title.trim().take(10).uppercase()
                                    database.saveCourse(
                                        CourseRow(
                                            id = existingCourse?.id ?: code,
                                            code = code,
                                            title = title.trim(),
                                            sks = existingCourse?.sks ?: 3,
                                            semester = existingCourse?.semester ?: 1,
                                            room = detail.trim().ifBlank { existingCourse?.room.orEmpty() },
                                            schedule = "${fullDaysId[selectedDay]}, $rangeTime"
                                        )
                                    )
                                    code
                                } else ""

                                val savedAgenda = database.addAgenda(selectedDay, rangeTime, title, kind, detail, courseCode)
                                if (enableReminder) {
                                    val remCategory = when (kind) {
                                        "KELAS" -> "Kelas"
                                        "TUGAS" -> "Tugas"
                                        "BELAJAR" -> "Belajar"
                                        "KEUANGAN" -> "Keuangan"
                                        else -> "Umum"
                                    }
                                    database.addReminder(
                                        title = savedAgenda.title,
                                        detail = "${fullDaysId[selectedDay]} • ${savedAgenda.time}" + (if (detail.isNotBlank()) " • $detail" else ""),
                                        time = savedAgenda.time.substringBefore('–'),
                                        category = remCategory,
                                        enabled = true,
                                        sourceId = savedAgenda.id
                                    )
                                }
                            }
                        }
                            .onSuccess {
                                savedCourses = database.courses()
                                entries = (0..6).flatMap(database::agenda)
                                reminders = database.reminders()
                                version++
                                adding = false
                                title = ""
                                time = ""
                                endTime = ""
                                detail = ""
                                kind = "AGENDA"
                            }
                            .onFailure {
                                error = if (isEn) "Failed to save agenda. Please retry." else "Agenda gagal disimpan. Coba lagi."
                            }
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
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${fullDays[entry.day]} • " + lString("Pukul ${entry.time}", "At ${entry.time}"))
                    if (entry.detail.isNotBlank()) {
                        Text(entry.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspecting = null }) { Text(lString("Tutup", "Close")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        database.deleteAgenda(entry.id)
                        entries = (0..6).flatMap(database::agenda)
                        reminders = database.reminders()
                        version++
                        inspecting = null
                    }
                }) { Text(lString("Hapus", "Delete"), color = MaterialTheme.colorScheme.error) }
            }
        )
    }
}

private fun isClassEntry(entry: AgendaRow, savedCourses: List<CourseRow>): Boolean {
    if (entry.kind.equals("KELAS", ignoreCase = true)) return true
    if (entry.kind.equals("BELAJAR", ignoreCase = true) || entry.kind.equals("TUGAS", ignoreCase = true) || entry.kind.equals("KEUANGAN", ignoreCase = true)) return false
    if (entry.courseId.isNotBlank()) return true
    val (code, _) = getCourseInfo(entry.title, savedCourses)
    if (code.isNotBlank()) return true
    return savedCourses.any {
        it.code.equals(entry.title, ignoreCase = true) ||
            it.title.equals(entry.title, ignoreCase = true) ||
            (it.code.isNotBlank() && entry.title.contains(it.code, ignoreCase = true)) ||
            (it.title.isNotBlank() && entry.title.contains(it.title, ignoreCase = true))
    }
}

private fun isStudyEntry(entry: AgendaRow): Boolean {
    return entry.kind.equals("BELAJAR", ignoreCase = true) ||
        entry.title.contains("belajar", ignoreCase = true) ||
        entry.title.contains("latihan", ignoreCase = true) ||
        entry.title.contains("study", ignoreCase = true)
}

private fun isDeadlineEntry(entry: AgendaRow): Boolean {
    return entry.kind.equals("TUGAS", ignoreCase = true) ||
        entry.title.contains("tugas", ignoreCase = true) ||
        entry.title.contains("praktikum", ignoreCase = true) ||
        entry.title.contains("deadline", ignoreCase = true) ||
        entry.title.contains("tenggat", ignoreCase = true)
}

@Composable
private fun CalendarLegendItem(color: Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

