package org.umn.application.studenthub.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.umn.application.studenthub.R
import org.umn.application.studenthub.data.local.AgendaRow
import org.umn.application.studenthub.data.local.CourseRow
import org.umn.application.studenthub.data.local.ReminderItemRow
import org.umn.application.studenthub.data.local.StudentHubDatabase
import org.umn.application.studenthub.ui.localization.lString
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

fun getCourseInfo(title: String, courses: List<CourseRow> = emptyList()): Pair<String, String> {
    if (title.isBlank()) return Pair("", "")
    val match = courses.firstOrNull {
        it.code.equals(title, ignoreCase = true) ||
            it.title.equals(title, ignoreCase = true) ||
            (it.code.isNotBlank() && title.contains(it.code, ignoreCase = true)) ||
            (it.title.isNotBlank() && title.contains(it.title, ignoreCase = true))
    }
    return if (match != null) {
        Pair(match.code, match.room.ifBlank { "Ruang Kuliah" })
    } else {
        Pair("", "")
    }
}

fun extractCourseScheduleDayAndTime(schedule: String): Pair<Int, String>? {
    val sched = schedule.trim()
    if (sched.isBlank()) return null
    val daysMap = mapOf(
        "senin" to 0, "sen" to 0, "mon" to 0, "monday" to 0,
        "selasa" to 1, "sel" to 1, "tue" to 1, "tuesday" to 1,
        "rabu" to 2, "rab" to 2, "wed" to 2, "wednesday" to 2,
        "kamis" to 3, "kam" to 3, "thu" to 3, "thursday" to 3,
        "jumat" to 4, "jum" to 4, "fri" to 4, "friday" to 4,
        "sabtu" to 5, "sab" to 5, "sat" to 5, "saturday" to 5,
        "minggu" to 6, "min" to 6, "sun" to 6, "sunday" to 6
    )
    val lower = sched.lowercase()
    val matchedDay = daysMap.entries.firstOrNull { lower.contains(it.key) }?.value ?: return null
    val rawTime = sched.substringAfter(',').trim().ifBlank {
        sched.split(' ').drop(1).joinToString(" ").trim()
    }
    val normalizedTime = StudentHubDatabase.normalizeTimeFormat(rawTime)
    if (!Regex("(?:[01][0-9]|2[0-3])\\.[0-5][0-9](?:–(?:[01][0-9]|2[0-3])\\.[0-5][0-9])?").matches(normalizedTime)) {
        return null
    }
    return matchedDay to computeClassTimeRange(normalizedTime)
}

fun computeClassTimeRange(timeStr: String): String {
    val start = parseTime(timeStr)
    val explicitEnd = parseEndTime(timeStr)
    if (start != null && explicitEnd != null) {
        return String.format("%02d.%02d–%02d.%02d", start.hour, start.minute, explicitEnd.hour, explicitEnd.minute)
    }
    if (start != null) {
        val endMinutes = (start.hour * 60 + start.minute + 100) % (24 * 60)
        val endH = endMinutes / 60
        val endM = endMinutes % 60
        return String.format("%02d.%02d–%02d.%02d", start.hour, start.minute, endH, endM)
    }
    return "$timeStr–selesai"
}

internal fun parseSingleTimeToken(token: String): LocalTime? {
    val clean = token.trim().replace(':', '.')
    val parts = clean.split('.')
    if (parts.size == 2) {
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h in 0..23 && m in 0..59) return LocalTime.of(h, m)
    }
    return null
}

internal fun parseTime(timeStr: String): LocalTime? {
    val startToken = timeStr.substringBefore('–').substringBefore('-').trim()
    return parseSingleTimeToken(startToken)
}

internal fun parseEndTime(timeStr: String): LocalTime? {
    val hasRange = timeStr.contains('–') || timeStr.contains('-')
    if (!hasRange) return null
    val endToken = timeStr.substringAfter('–', "").ifBlank { timeStr.substringAfter('-', "") }.trim()
    return parseSingleTimeToken(endToken)
}

private fun agendaMinuteBounds(timeStr: String): Pair<Int, Int>? {
    val start = parseTime(timeStr) ?: return null
    val startMins = start.hour * 60 + start.minute
    val explicitEnd = parseEndTime(timeStr)
    val endMins = if (explicitEnd != null) {
        val rawEnd = explicitEnd.hour * 60 + explicitEnd.minute
        if (rawEnd > startMins) rawEnd else rawEnd + 24 * 60
    } else {
        startMins + 100
    }
    return startMins to endMins
}

internal fun isAgendaStillActiveOrUpcoming(timeStr: String, now: LocalTime): Boolean {
    val (_, endMins) = agendaMinuteBounds(timeStr) ?: return false
    val nowMins = now.hour * 60 + now.minute
    return nowMins < endMins
}

internal fun isAgendaInProgress(timeStr: String, now: LocalTime): Boolean {
    val (startMins, endMins) = agendaMinuteBounds(timeStr) ?: return false
    val nowMins = now.hour * 60 + now.minute
    return nowMins in startMins until endMins
}

internal fun isAgendaUpcoming(timeStr: String, now: LocalTime): Boolean {
    val (startMins, _) = agendaMinuteBounds(timeStr) ?: return false
    val nowMins = now.hour * 60 + now.minute
    return nowMins < startMins
}

@Composable
fun TodayScreen(onNavigate: (String) -> Unit, onReminders: () -> Unit = {}) {
    val context = LocalContext.current
    val db = remember(context) { StudentHubDatabase.get(context) }

    var dbProfile by remember {
        mutableStateOf(runCatching { db.profile() }.getOrNull())
    }
    val today = remember { LocalDate.now() }
    val todayDayIndex = (today.dayOfWeek.value - 1).coerceIn(0, 6)

    var allAgendasList by remember {
        mutableStateOf(runCatching { db.agenda(todayDayIndex) }.getOrDefault(emptyList()))
    }
    var savedCourses by remember {
        mutableStateOf(runCatching { db.courses() }.getOrDefault(emptyList()))
    }
    var activeReminders by remember {
        mutableStateOf(runCatching { db.reminders().filter { it.enabled } }.getOrDefault(emptyList()))
    }

    var refreshVersion by remember { mutableIntStateOf(0) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshVersion++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(refreshVersion) {
        withContext(Dispatchers.IO) {
            runCatching {
                val p = db.profile()
                val courses = db.courses()
                val agendas = db.agenda(todayDayIndex).toMutableList()
                courses.forEach { course ->
                    val pair = extractCourseScheduleDayAndTime(course.schedule)
                    if (pair != null && pair.first == todayDayIndex) {
                        val already = agendas.any {
                            it.courseId.equals(course.code, true) ||
                                it.title.equals(course.title, true) ||
                                it.title.equals(course.code, true)
                        }
                        if (!already) {
                            val detail = buildList {
                                add(course.code)
                                if (course.room.isNotBlank()) add(course.room)
                            }.joinToString(" • ")
                            val synth = AgendaRow(
                                id = "course-today-${course.id}",
                                day = todayDayIndex,
                                time = pair.second,
                                title = course.title,
                                kind = "KELAS",
                                detail = detail,
                                courseId = course.code
                            )
                            agendas.add(synth)
                            runCatching {
                                db.addAgenda(synth.day, synth.time, synth.title, synth.kind, synth.detail, synth.courseId)
                            }
                        }
                    }
                }
                val rems = db.reminders().filter { it.enabled }
                listOf(p, agendas, courses, rems)
            }
        }.onSuccess { result ->
            @Suppress("UNCHECKED_CAST")
            dbProfile = result[0] as? org.umn.application.studenthub.data.local.ProfileRow
            @Suppress("UNCHECKED_CAST")
            allAgendasList = result[1] as? List<AgendaRow> ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            savedCourses = result[2] as? List<CourseRow> ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            activeReminders = result[3] as? List<ReminderItemRow> ?: emptyList()
        }
    }

    val dbName = dbProfile?.name?.trim().orEmpty()
    val profileSemester = dbProfile?.semester?.takeIf { it > 0 }

    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = LocalTime.now()
        }
    }

    val dayOfWeekId = when (today.dayOfWeek) {
        DayOfWeek.MONDAY -> "Senin"
        DayOfWeek.TUESDAY -> "Selasa"
        DayOfWeek.WEDNESDAY -> "Rabu"
        DayOfWeek.THURSDAY -> "Kamis"
        DayOfWeek.FRIDAY -> "Jumat"
        DayOfWeek.SATURDAY -> "Sabtu"
        DayOfWeek.SUNDAY -> "Minggu"
    }
    val dayOfWeekEn = when (today.dayOfWeek) {
        DayOfWeek.MONDAY -> "Monday"
        DayOfWeek.TUESDAY -> "Tuesday"
        DayOfWeek.WEDNESDAY -> "Wednesday"
        DayOfWeek.THURSDAY -> "Thursday"
        DayOfWeek.FRIDAY -> "Friday"
        DayOfWeek.SATURDAY -> "Saturday"
        DayOfWeek.SUNDAY -> "Sunday"
    }

    val activeAgendas = remember(allAgendasList) {
        allAgendasList.sortedBy { it.time }
    }

    val classInProgress = remember(activeAgendas, currentTime) {
        activeAgendas.firstOrNull { agenda -> isAgendaInProgress(agenda.time, currentTime) }
    }

    val upcomingClass = remember(activeAgendas, currentTime) {
        activeAgendas.firstOrNull { agenda -> isAgendaUpcoming(agenda.time, currentTime) }
    }

    val featuredClass = classInProgress ?: upcomingClass
    val allTodayClassesFinished = activeAgendas.isNotEmpty() && featuredClass == null

    val featuredTimeRange = when {
        featuredClass != null -> computeClassTimeRange(featuredClass.time)
        allTodayClassesFinished -> lString("Selesai", "Done")
        else -> "--:--"
    }

    val (featuredCourseCode, featuredRoom) = remember(featuredClass, savedCourses) {
        if (featuredClass == null) {
            Pair("", "")
        } else {
            val (c, r) = getCourseInfo(featuredClass.title, savedCourses)
            val code = featuredClass.courseId.ifBlank { c }
            val room = featuredClass.detail.ifBlank { r }
            Pair(code, room)
        }
    }

    val upcomingRemindersToday = remember(activeReminders, currentTime, dayOfWeekId) {
        val allWeekdays = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
        activeReminders.filter { rem ->
            val mentionsOtherDay = allWeekdays.any { dayName ->
                !dayName.equals(dayOfWeekId, ignoreCase = true) && rem.detail.contains(dayName, ignoreCase = true)
            }
            if (mentionsOtherDay) return@filter false
            if (rem.category.equals("Kelas", ignoreCase = true) && !isAgendaStillActiveOrUpcoming(rem.time, currentTime)) {
                return@filter false
            }
            isAgendaUpcoming(rem.time, currentTime)
        }
    }

    val (alertTitle, alertText, countdownBadge) = remember(
        currentTime,
        classInProgress,
        upcomingClass,
        allTodayClassesFinished,
        savedCourses,
        upcomingRemindersToday
    ) {
        when {
            classInProgress != null -> {
                val end = parseEndTime(classInProgress.time)
                    ?: parseTime(classInProgress.time)?.plusMinutes(100)
                    ?: LocalTime.of(10, 40)
                val endFormatted = String.format("%02d.%02d", end.hour, end.minute)
                val endFormattedEn = String.format("%02d:%02d", end.hour, end.minute)
                val (code, _) = getCourseInfo(classInProgress.title, savedCourses)
                val name = classInProgress.courseId.ifBlank { code.ifBlank { classInProgress.title } }
                Triple(
                    Pair("Kelas Berlangsung", "Class In Progress"),
                    Pair(
                        "Kelas $name sedang berlangsung (hingga $endFormatted)",
                        "Class $name is in progress (until $endFormattedEn)"
                    ),
                    Pair("sedang berlangsung", "in progress")
                )
            }
            upcomingClass != null -> {
                val start = parseTime(upcomingClass.time) ?: LocalTime.of(9, 0)
                val durationMinutes = java.time.Duration.between(currentTime, start).toMinutes().coerceAtLeast(1)
                val hours = durationMinutes / 60
                val minutes = durationMinutes % 60
                val timeTextId = if (hours > 0) "$hours jam $minutes menit lagi" else "$minutes menit lagi"
                val timeTextEn = if (hours > 0) "in $hours hrs $minutes mins" else "in $minutes mins"
                val startFormatted = String.format("%02d.%02d", start.hour, start.minute)
                val startFormattedEn = String.format("%02d:%02d", start.hour, start.minute)
                val (code, _) = getCourseInfo(upcomingClass.title, savedCourses)
                val name = upcomingClass.courseId.ifBlank { code.ifBlank { upcomingClass.title } }
                Triple(
                    Pair("Pengingat Kelas", "Upcoming Class Reminder"),
                    Pair(
                        "Akan ada kelas $name dalam $timeTextId ($startFormatted)",
                        "Class $name starts $timeTextEn ($startFormattedEn)"
                    ),
                    Pair(timeTextId, timeTextEn)
                )
            }
            allTodayClassesFinished -> {
                Triple(
                    Pair("Tidak Ada Kelas Lagi Hari Ini", "No More Classes Today"),
                    Pair(
                        "Semua jadwal kelas hari ini sudah selesai.",
                        "All scheduled classes for today have finished."
                    ),
                    Pair("selesai", "finished")
                )
            }
            upcomingRemindersToday.isNotEmpty() -> {
                val nextRem = upcomingRemindersToday.first()
                Triple(
                    Pair("Pengingat Hari Ini (${upcomingRemindersToday.size})", "Today's Reminders (${upcomingRemindersToday.size})"),
                    Pair(
                        "${nextRem.title} • ${nextRem.time.ifBlank { nextRem.category }}",
                        "${nextRem.title} • ${nextRem.time.ifBlank { nextRem.category }}"
                    ),
                    Pair("pengingat aktif", "active reminder")
                )
            }
            else -> {
                Triple(
                    Pair("Tidak Ada Jadwal Kelas", "No Classes Scheduled"),
                    Pair(
                        "Tidak ada jadwal kelas untuk hari ini. Ketuk untuk kelola pengingat.",
                        "No scheduled classes for today. Tap to manage reminders."
                    ),
                    Pair("bebas", "free")
                )
            }
        }
    }

    val monthNameId = when (today.monthValue) {
        1 -> "Januari"; 2 -> "Februari"; 3 -> "Maret"; 4 -> "April"
        5 -> "Mei"; 6 -> "Juni"; 7 -> "Juli"; 8 -> "Agustus"
        9 -> "September"; 10 -> "Oktober"; 11 -> "November"; else -> "Desember"
    }
    val monthNameEn = when (today.monthValue) {
        1 -> "January"; 2 -> "February"; 3 -> "March"; 4 -> "April"
        5 -> "May"; 6 -> "June"; 7 -> "July"; 8 -> "August"
        9 -> "September"; 10 -> "October"; 11 -> "November"; else -> "December"
    }

    val dateStr = lString(
        "$dayOfWeekId, ${today.dayOfMonth} $monthNameId ${today.year}",
        "$dayOfWeekEn, $monthNameEn ${today.dayOfMonth}, ${today.year}"
    )
    val timeFormatted = String.format("%02d.%02d", currentTime.hour, currentTime.minute)
    val timeFormattedEn = String.format("%02d:%02d", currentTime.hour, currentTime.minute)
    val timeStr = lString("$timeFormatted WIB", timeFormattedEn)

    val remainingUpcomingCount = remember(activeAgendas, currentTime) {
        activeAgendas.count { isAgendaStillActiveOrUpcoming(it.time, currentTime) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val semText = if (profileSemester != null) {
                    "$dateStr • " + lString("Semester $profileSemester", "Semester $profileSemester")
                } else {
                    dateStr
                }
                SupportingText(semText)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        timeStr,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val (greetingId, greetingEn) = when (currentTime.hour) {
                in 4..10 -> "Selamat pagi" to "Good morning"
                in 11..14 -> "Selamat siang" to "Good afternoon"
                in 15..17 -> "Selamat sore" to "Good afternoon"
                else -> "Selamat malam" to "Good evening"
            }
            val greeting = if (dbName.isNotBlank()) {
                lString("$greetingId, $dbName.", "$greetingEn, $dbName.")
            } else {
                lString("$greetingId.", "$greetingEn.")
            }
            Text(greeting, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
            SupportingText(
                when {
                    remainingUpcomingCount > 0 -> lString(
                        "Kamu punya $remainingUpcomingCount jadwal tersisa hari ini.",
                        "You have $remainingUpcomingCount remaining scheduled items today."
                    )
                    allTodayClassesFinished -> lString(
                        "Tidak ada kelas lagi hari ini (${activeAgendas.size} jadwal telah selesai).",
                        "No more classes today (${activeAgendas.size} scheduled items finished)."
                    )
                    else -> lString("Tidak ada jadwal kelas hari ini.", "No classes scheduled today.")
                }
            )
        }

        Card(
            onClick = onReminders,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painterResource(R.drawable.hub_bell),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(22.dp)
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = lString(alertTitle.first, alertTitle.second),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = lString(alertText.first, alertText.second),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        Card(
            onClick = { onNavigate("schedule") },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(lString("Kelas berikutnya", "Next class"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(featuredTimeRange, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    text = when {
                        featuredClass != null -> featuredClass.title
                        allTodayClassesFinished -> lString("Tidak ada kelas lagi hari ini", "No more classes today")
                        else -> lString("Tidak ada kelas hari ini", "No classes today")
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = when {
                        featuredClass != null -> {
                            val parts = buildList {
                                if (featuredCourseCode.isNotBlank()) add(featuredCourseCode)
                                if (featuredRoom.isNotBlank()) add(lString(featuredRoom, featuredRoom.replace("Ruang", "Room")))
                                add(lString(countdownBadge.first, countdownBadge.second))
                            }
                            parts.joinToString(" • ")
                        }
                        allTodayClassesFinished -> lString(
                            "Semua kelas hari ini telah selesai.",
                            "All classes for today have finished."
                        )
                        else -> lString("Tidak ada jadwal kelas hari ini.", "No classes scheduled for today.")
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        val remainingAgendas = remember(activeAgendas, featuredClass, currentTime) {
            activeAgendas.filter { agenda ->
                agenda.id != featuredClass?.id && isAgendaStillActiveOrUpcoming(agenda.time, currentTime)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading(lString("Sisa agenda hari ini", "Today's Schedule"), lString("Lihat jadwal", "View schedule")) { onNavigate("schedule") }
            if (remainingAgendas.isEmpty()) {
                Text(
                    if (allTodayClassesFinished) {
                        lString("Semua agenda hari ini sudah selesai.", "All agendas for today have finished.")
                    } else {
                        lString("Tidak ada agenda perkuliahan lain untuk hari ini.", "No other scheduled classes for today.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                remainingAgendas.forEach { agenda ->
                    val (code, room) = getCourseInfo(agenda.title, savedCourses)
                    val resolvedCode = agenda.courseId.ifBlank { code }
                    val resolvedRoom = agenda.detail.ifBlank { room }
                    val isDeadline = agenda.kind.equals("TUGAS", ignoreCase = true) ||
                        agenda.title.contains("tugas", ignoreCase = true) ||
                        agenda.title.contains("praktikum", ignoreCase = true) ||
                        agenda.title.contains("deadline", ignoreCase = true)
                    val isStudy = agenda.kind.equals("BELAJAR", ignoreCase = true) ||
                        agenda.title.contains("belajar", ignoreCase = true) ||
                        agenda.title.contains("latihan", ignoreCase = true)
                    val kind = when {
                        isDeadline -> lString("Tenggat tugas", "Deadline")
                        isStudy -> lString("Belajar", "Study")
                        else -> lString("Kelas", "Class")
                    }
                    val detailParts = listOfNotNull(
                        resolvedCode.takeIf { it.isNotBlank() },
                        resolvedRoom.takeIf { it.isNotBlank() }
                    )
                    val detail = if (detailParts.isNotEmpty()) detailParts.joinToString(" • ") else agenda.title
                    AgendaEntry(
                        time = agenda.time,
                        kind = kind,
                        title = agenda.title,
                        detail = detail
                    ) { onNavigate("schedule") }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading(lString("Ruang mahasiswa", "Quick access"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(lString("Catatan", "Notes"), "notes", R.drawable.hub_notebook),
                    Triple(lString("Keuangan", "Finance"), "finance", R.drawable.hub_wallet),
                    Triple(lString("Nilai", "Grades"), "grades", R.drawable.hub_chart),
                    Triple(lString("Belajar", "Study"), "learning", R.drawable.hub_target)
                ).forEach { (label, route, icon) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .clickable { onNavigate(route) }.heightIn(min = 68.dp).padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                    ) {
                        Icon(painterResource(icon), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun SupportingText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun SectionHeading(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (action != null) TextButton(
            onClick = onClick,
            modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(action)
        }
    }
}

@Composable
fun ReminderRow(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(icon), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        SupportingText(text)
    }
}

@Composable
fun AgendaEntry(time: String, kind: String, title: String, detail: String, highlighted: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            time,
            Modifier.widthIn(min = 44.dp, max = 84.dp).padding(top = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            Modifier.weight(1f).height(IntrinsicSize.Min).clip(RoundedCornerShape(12.dp))
                .background(if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onClick)
        ) {
            if (highlighted) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(kind, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                Text(title, style = MaterialTheme.typography.titleMedium)
                SupportingText(detail)
            }
        }
    }
}
