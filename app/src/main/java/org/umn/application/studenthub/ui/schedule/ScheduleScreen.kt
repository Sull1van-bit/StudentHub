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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(onCourseClick: (String) -> Unit, onLearningClick: () -> Unit) {
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
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var inspecting by remember { mutableStateOf<AgendaRow?>(null) }
    LaunchedEffect(version) {
        loading = true
        withContext(Dispatchers.IO) { runCatching { (0..6).flatMap(database::agenda) } }
            .onSuccess { entries = it; error = null }
            .onFailure { error = "Agenda tidak dapat dibaca. Data lama tidak diubah." }
        loading = false
    }
    val days = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
    val fullDays = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Minggu yang terarah.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
            SupportingText("5–11 Oktober 2026 • Minggu kuliah 6")
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Minggu", "Hari").forEachIndexed { index, label ->
                SegmentedButton(selected = daily == (index == 1), onClick = { daily = index == 1 }, shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(label) }
            }
        }
        if (!daily) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.forEachIndexed { index, day ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .background(if (selectedDay == index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .clickable { selectedDay = index }.height(88.dp).padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(day, style = MaterialTheme.typography.bodySmall)
                    Text("${index + 5}", style = MaterialTheme.typography.titleMedium)
                    Icon(painterResource(R.drawable.hub_day_dot), null, Modifier.size(4.dp), tint = if (entries.any { it.day == index }) MaterialTheme.colorScheme.primary else Color.Transparent)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading("${fullDays[selectedDay]}, ${selectedDay + 5} Oktober", if (selectedDay == 0) "Hari ini" else null) { selectedDay = 0 }
            if (loading) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { version++ }) { Text("Coba lagi") } }
            if (!loading && error == null && selectedDay == 0) {
                SupportingText("2 kelas • 1 tenggat • 1 sesi belajar")
            }
            if (!loading && error == null && entries.none { it.day == selectedDay }) {
                SupportingText("Belum ada agenda tersimpan untuk hari ini.")
            }
            entries.filter { it.day == selectedDay }.forEach { entry ->
                when (entry.id) {
                    "demo-class-if570" -> AgendaEntry(entry.time, "KELAS • PENGINGAT 15 MENIT", entry.title, "IF570 • D.302 • 09.00–10.40", true) { onCourseClick("IF570") }
                    "demo-class-if550" -> AgendaEntry(entry.time, "KELAS", entry.title, "IF550 • D.204 • 13.00–14.40") { onCourseClick("IF550") }
                    "demo-study-if570" -> AgendaEntry(entry.time, "BELAJAR", entry.title, "Target IF570 • 30 menit", onClick = onLearningClick)
                    "demo-deadline-if570" -> AgendaEntry(entry.time, "TENGGAT", entry.title, "IF570 • Unggah laporan & kode") { onCourseClick("IF570") }
                    else -> AgendaEntry(entry.time, "AGENDA", entry.title, "Agenda pribadi") { inspecting = entry }
                }
            }
        }
        ReminderRow(R.drawable.hub_bell, "Pengingat kelas aktif 15 menit sebelumnya.")
        Button(onClick = { adding = true }, enabled = !loading && error == null, modifier = Modifier.fillMaxWidth()) { Text("Tambah agenda") }
    }
    if (adding) AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(16.dp),
        onDismissRequest = { adding = false },
        title = { Text("Tambah agenda") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Judul agenda") }, singleLine = true)
                OutlinedTextField(time, { time = it }, label = { Text("Waktu (HH.mm)") }, singleLine = true,
                    supportingText = { Text("Contoh: 18.30") })
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && title.isNotBlank() && title.length <= 200 && Regex("(?:[01][0-9]|2[0-3])\\.[0-5][0-9]").matches(time),
                onClick = {
                    saving = true
                    scope.launch {
                        withContext(Dispatchers.IO) { runCatching { database.addAgenda(selectedDay, time, title) } }
                            .onSuccess { version++; adding = false; title = ""; time = "" }
                            .onFailure { error = "Agenda gagal disimpan. Coba lagi." }
                        saving = false
                    }
                }
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = { adding = false }) { Text("Batal") } }
    )
    inspecting?.let { entry -> AlertDialog(onDismissRequest = { inspecting = null }, title = { Text(entry.title) },
        text = { Text("${fullDays[entry.day]} • ${entry.time}") }, confirmButton = { TextButton(onClick = { inspecting = null }) { Text("Tutup") } }) }
}
