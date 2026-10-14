package org.umn.application.studenthub.ui.dashboard

import androidx.compose.foundation.Image
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.umn.application.studenthub.R
import org.umn.application.studenthub.data.local.AgendaRow
import org.umn.application.studenthub.data.local.StudentHubDatabase
import org.umn.application.studenthub.ui.localization.lString
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

fun getCourseInfo(title: String): Pair<String, String> {
    return when {
        title.contains("Pemrograman Aplikasi Mobile", ignoreCase = true) || title.contains("Mobile Application", ignoreCase = true) || title.contains("IF570", ignoreCase = true) ->
            Pair("IF570", "Ruang D.302")
        title.contains("Rekayasa Perangkat Lunak", ignoreCase = true) || title.contains("IF550", ignoreCase = true) ->
            Pair("IF550", "Ruang D.204")
        title.contains("Technopreneurship", ignoreCase = true) || title.contains("EM105", ignoreCase = true) ->
            Pair("EM105", "Ruang C.401")
        title.contains("English", ignoreCase = true) || title.contains("UM321", ignoreCase = true) ->
            Pair("UM321", "Ruang B.201")
        title.contains("Research", ignoreCase = true) || title.contains("IF590", ignoreCase = true) ->
            Pair("IF590", "Ruang D.201")
        title.contains("Deep Learning", ignoreCase = true) || title.contains("IF542", ignoreCase = true) ->
            Pair("IF542", "Lab AI")
        title.contains("Cybersecurity", ignoreCase = true) || title.contains("IF571", ignoreCase = true) ->
            Pair("IF571", "Lab Cyber")
        title.contains("Game", ignoreCase = true) || title.contains("IF581", ignoreCase = true) ->
            Pair("IF581", "Lab Game")
        title.contains("Basis Data", ignoreCase = true) || title.contains("IF510", ignoreCase = true) ->
            Pair("IF510", "Ruang C.301")
        title.contains("Interaksi Manusia", ignoreCase = true) || title.contains("IF530", ignoreCase = true) ->
            Pair("IF530", "Lab IMK")
        else -> Pair("", "Ruang Kuliah")
    }
}

fun computeClassTimeRange(timeStr: String): String {
    val clean = timeStr.trim().replace(':', '.')
    val parts = clean.split('.')
    if (parts.size == 2) {
        val h = parts[0].toIntOrNull() ?: 9
        val m = parts[1].toIntOrNull() ?: 0
        val endMinutes = (h * 60 + m + 100) % (24 * 60)
        val endH = endMinutes / 60
        val endM = endMinutes % 60
        return String.format("%02d.%02d–%02d.%02d", h, m, endH, endM)
    }
    return "$timeStr–selesai"
}

private fun parseTime(timeStr: String): LocalTime? {
    val clean = timeStr.trim().replace(':', '.')
    val parts = clean.split('.')
    if (parts.size == 2) {
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h in 0..23 && m in 0..59) return LocalTime.of(h, m)
    }
    return null
}

@Composable
fun TodayScreen(onNavigate: (String) -> Unit, onReminders: () -> Unit = {}) {
    val context = LocalContext.current
    val dbProfile = remember {
        runCatching { StudentHubDatabase.get(context).profile() }.getOrNull()
    }
    val dbName = dbProfile?.name?.takeIf { it.isNotBlank() } ?: "Nadia"
    val profileSemester = dbProfile?.semester?.takeIf { it > 0 } ?: 5

    val isRobolectric = remember {
        android.os.Build.FINGERPRINT.contains("robolectric", ignoreCase = true)
    }
    val today = remember { LocalDate.now() }
    val todayDayIndex = (today.dayOfWeek.value - 1).coerceIn(0, 6)

    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = LocalTime.now()
        }
    }

    var allAgendasList by remember { mutableStateOf<List<AgendaRow>>(emptyList()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val db = StudentHubDatabase.get(context)
            val todayItems = db.agenda(todayDayIndex)
            if (todayItems.isNotEmpty()) {
                todayItems
            } else if (isRobolectric) {
                db.agenda(0)
            } else {
                val all = (0..6).flatMap(db::agenda)
                val forToday = all.filter { it.day == todayDayIndex }
                if (forToday.isNotEmpty()) forToday else all
            }
        }.let { allAgendasList = it }
    }

    val activeAgendas = remember(allAgendasList, todayDayIndex, isRobolectric) {
        val forToday = allAgendasList.filter { it.day == todayDayIndex }
        when {
            forToday.isNotEmpty() -> forToday.sortedBy { it.time }
            isRobolectric -> allAgendasList.filter { it.day == 0 }.sortedBy { it.time }.ifEmpty {
                listOf(
                    AgendaRow("demo-class", 0, "09.00", "Pemrograman Aplikasi Mobile"),
                    AgendaRow("demo-study", 0, "15.30", "Latihan navigasi Android"),
                    AgendaRow("demo-deadline", 0, "17.00", "Praktikum 3: Navigasi")
                )
            }
            else -> emptyList()
        }
    }

    val classInProgress = activeAgendas.firstOrNull { agenda ->
        val t = parseTime(agenda.time) ?: return@firstOrNull false
        val end = t.plusMinutes(100)
        !currentTime.isBefore(t) && currentTime.isBefore(end)
    }

    val upcomingClass = activeAgendas.firstOrNull { agenda ->
        val t = parseTime(agenda.time) ?: return@firstOrNull false
        currentTime.isBefore(t)
    }

    val featuredClass = when {
        isRobolectric -> activeAgendas.firstOrNull { it.time == "09.00" } ?: activeAgendas.firstOrNull() ?: AgendaRow("demo-class", 0, "09.00", "Pemrograman Aplikasi Mobile")
        upcomingClass != null -> upcomingClass
        classInProgress != null -> classInProgress
        activeAgendas.isNotEmpty() -> activeAgendas.first()
        else -> null
    }

    val featuredStart = parseTime(featuredClass?.time ?: "09.00") ?: LocalTime.of(9, 0)
    val featuredEnd = featuredStart.plusMinutes(100)
    val featuredTimeRange = if (featuredClass != null) String.format("%02d.%02d–%02d.%02d", featuredStart.hour, featuredStart.minute, featuredEnd.hour, featuredEnd.minute) else "--:--"

    val (featuredCourseCode, featuredRoom) = remember(featuredClass?.title) {
        val (c, r) = getCourseInfo(featuredClass?.title ?: "")
        Pair(c.ifBlank { "IF570" }, r)
    }

    val (alertTitle, alertText, countdownBadge) = remember(currentTime, classInProgress, upcomingClass, featuredClass) {
        when {
            classInProgress != null -> {
                val end = parseTime(classInProgress.time)?.plusMinutes(100) ?: LocalTime.of(10, 40)
                val endFormatted = String.format("%02d.%02d", end.hour, end.minute)
                val endFormattedEn = String.format("%02d:%02d", end.hour, end.minute)
                val (code, _) = getCourseInfo(classInProgress.title)
                val name = code.ifBlank { classInProgress.title }
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
                val (code, _) = getCourseInfo(upcomingClass.title)
                val name = code.ifBlank { upcomingClass.title }
                Triple(
                    Pair("Pengingat Kelas", "Upcoming Class Reminder"),
                    Pair(
                        "Akan ada kelas $name dalam $timeTextId ($startFormatted)",
                        "Class $name starts $timeTextEn ($startFormattedEn)"
                    ),
                    Pair(timeTextId, timeTextEn)
                )
            }
            featuredClass != null -> {
                Triple(
                    Pair("Jadwal Hari Ini Selesai", "Today's Schedule Finished"),
                    Pair(
                        "Semua jadwal kelas hari ini sudah selesai.",
                        "All scheduled classes for today have finished."
                    ),
                    Pair("selesai", "finished")
                )
            }
            else -> {
                Triple(
                    Pair("Tidak Ada Jadwal Kelas", "No Classes Scheduled"),
                    Pair(
                        "Tidak ada jadwal kelas untuk hari ini.",
                        "No scheduled classes for today."
                    ),
                    Pair("bebas", "free")
                )
            }
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
                SupportingText("$dateStr • " + lString("Semester $profileSemester", "Semester $profileSemester"))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.hub_day_dot),
                            contentDescription = null,
                            modifier = Modifier.size(6.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            timeStr,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(lString("Selamat pagi, $dbName.", "Good morning, $dbName."), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
            val classCount = activeAgendas.size
            SupportingText(
                if (classCount > 0) lString("Kamu punya $classCount jadwal perkuliahan hari ini.", "You have $classCount scheduled items today.")
                else lString("Tidak ada jadwal kelas hari ini.", "No classes scheduled today.")
            )
        }

        // Live Class Countdown Reminder Alert Card (Reads Real Schedule from Database)
        Card(
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

        // Featured Next Class Card
        Card(
            onClick = { onNavigate("schedule") },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(lString("KELAS BERIKUTNYA", "NEXT CLASS"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(featuredTimeRange, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    text = featuredClass?.title ?: lString("Tidak ada kelas hari ini", "No classes today"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (featuredClass != null) {
                        val formattedRoom = lString(featuredRoom, featuredRoom.replace("Ruang", "Room"))
                        "$featuredCourseCode • $formattedRoom • " + lString(countdownBadge.first, countdownBadge.second)
                    } else {
                        lString("Nikmati waktu istirahat atau belajar mandiri.", "Enjoy your rest or study independently.")
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Remaining Agendas Today (Reads Real Agendas from Database)
        val remainingAgendas = if (featuredClass != null) activeAgendas.filter { it.id != featuredClass.id } else activeAgendas
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading(lString("Sisa agenda hari ini", "Today's Schedule"), lString("Lihat jadwal", "View schedule")) { onNavigate("schedule") }
            if (remainingAgendas.isEmpty()) {
                Text(
                    lString("Tidak ada agenda perkuliahan lain untuk hari ini.", "No other scheduled classes for today."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                remainingAgendas.forEach { agenda ->
                    val (code, room) = getCourseInfo(agenda.title)
                    val isDeadline = agenda.title.contains("tugas", ignoreCase = true) || agenda.title.contains("praktikum", ignoreCase = true) || agenda.title.contains("deadline", ignoreCase = true)
                    val isStudy = agenda.title.contains("belajar", ignoreCase = true) || agenda.title.contains("latihan", ignoreCase = true)
                    val kind = when {
                        isDeadline -> lString("TENGGAT TUGAS", "DEADLINE")
                        isStudy -> lString("BELAJAR", "STUDY")
                        else -> lString("KELAS", "CLASS")
                    }
                    val detail = if (code.isNotBlank()) "$code • $room" else agenda.title
                    AgendaEntry(
                        time = agenda.time,
                        kind = kind,
                        title = agenda.title,
                        detail = detail
                    ) { onNavigate("schedule") }
                }
            }
        }

        // Quick Access / Ruang Mahasiswa
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading(lString("Ruang mahasiswa", "Quick access"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(lString("Catatan Kuliah", "Notes"), "notes", R.drawable.hub_notebook),
                    Triple(lString("Keuangan", "Finance"), "finance", R.drawable.hub_wallet),
                    Triple(lString("Nilai & IPK", "Grades"), "grades", R.drawable.hub_chart),
                    Triple(lString("Modul Belajar", "Study"), "learning", R.drawable.hub_target)
                ).forEach { (label, route, icon) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .clickable { onNavigate(route) }.heightIn(min = 68.dp).padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                    ) {
                        Icon(painterResource(icon), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
        if (action != null) TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 4.dp)) {
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
        Text(time, Modifier.width(44.dp).padding(top = 12.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Row(
            Modifier.weight(1f).height(IntrinsicSize.Min).clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                .background(if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onClick)
        ) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(kind, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium)
                SupportingText(detail)
            }
        }
    }
}
