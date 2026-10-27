package org.umn.application.studenthub.ui.onboarding

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.umn.application.studenthub.data.local.CourseRow
import org.umn.application.studenthub.data.local.ProfileRow
import org.umn.application.studenthub.data.local.StudentHubDatabase
import org.umn.application.studenthub.data.local.TransactionRow
import org.umn.application.studenthub.ui.components.CurriculumCourseDropdown
import org.umn.application.studenthub.ui.dashboard.computeClassTimeRange
import org.umn.application.studenthub.ui.localization.lString
import java.time.LocalDate
import java.util.UUID

internal data class OnboardingCourseItem(
    val code: String,
    val title: String,
    val sks: Int,
    val day: Int,
    val time: String,
    val room: String,
    val lecturer: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(1) }

    var name by rememberSaveable { mutableStateOf("") }
    var program by rememberSaveable { mutableStateOf("") }
    var nameError by rememberSaveable { mutableStateOf(false) }
    var programError by rememberSaveable { mutableStateOf(false) }

    var semesterText by rememberSaveable { mutableStateOf("") }
    var semesterError by rememberSaveable { mutableStateOf(false) }

    var inputPriorGpa by rememberSaveable { mutableStateOf(true) }
    var priorGpaText by rememberSaveable { mutableStateOf("") }
    var priorSksText by rememberSaveable { mutableStateOf("") }
    var priorGpaError by rememberSaveable { mutableStateOf(false) }
    var priorSksError by rememberSaveable { mutableStateOf(false) }

    var allowanceText by rememberSaveable { mutableStateOf("") }
    var allowanceError by rememberSaveable { mutableStateOf(false) }

    val courses = remember { mutableStateListOf<OnboardingCourseItem>() }
    var showAddCourseDialog by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val days = listOf(
        lString("Senin", "Monday"),
        lString("Selasa", "Tuesday"),
        lString("Rabu", "Wednesday"),
        lString("Kamis", "Thursday"),
        lString("Jumat", "Friday"),
        lString("Sabtu", "Saturday"),
        lString("Minggu", "Sunday")
    )
    val daysId = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
    val totalSteps = 5

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(lString("Setup Awal StudentHub", "StudentHub Setup")) },
                navigationIcon = {
                    if (step > 1) {
                        IconButton(onClick = { step-- }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, lString("Kembali", "Back"))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = lString("Langkah $step dari $totalSteps", "Step $step of $totalSteps"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = when (step) {
                            1 -> lString("Identitas", "Profile")
                            2 -> lString("Semester", "Semester")
                            3 -> lString("Riwayat IPK", "GPA History")
                            4 -> lString("Keuangan", "Finances")
                            else -> lString("Mata Kuliah & Jadwal", "Courses & Schedule")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LinearProgressIndicator(
                    progress = { step.toFloat() / totalSteps },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }

            when (step) {
                1 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = lString("Profil Mahasiswa", "Student Profile"),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = lString(
                                "Masukkan nama dan program studimu.",
                                "Enter your name and study program."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                nameError = it.isBlank()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding-name"),
                            label = { Text(lString("Nama Lengkap", "Full Name")) },
                            isError = nameError,
                            supportingText = {
                                if (nameError) Text(lString("Nama lengkap wajib diisi", "Full name is required"))
                                else Text(lString("Masukkan nama lengkapmu", "Enter your full name"))
                            },
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = program,
                            onValueChange = {
                                program = it
                                programError = it.isBlank()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding-program"),
                            label = { Text(lString("Program Studi", "Study Program")) },
                            isError = programError,
                            supportingText = {
                                if (programError) Text(lString("Program studi wajib diisi", "Study program is required"))
                                else Text(lString("Masukkan program studimu", "Enter your study program"))
                            },
                            singleLine = true
                        )
                    }
                }

                2 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = lString("Semester Berjalan", "Current Semester"),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = lString(
                                "Masukkan semester aktif saat ini (1-14) untuk menyusun beban SKS dan riwayat akademikmu.",
                                "Enter your current active semester (1-14) to organize your credits and academic history."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = semesterText,
                            onValueChange = {
                                semesterText = it
                                val s = it.toIntOrNull()
                                semesterError = s == null || s !in 1..14
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding-semester"),
                            label = { Text(lString("Semester (1-14)", "Semester (1-14)")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = semesterError,
                            supportingText = {
                                if (semesterError) Text(lString("Semester harus berupa angka antara 1 dan 14", "Semester must be a number between 1 and 14"))
                                else Text(lString("Semester perkuliahan aktif saat ini", "Current active academic semester"))
                            },
                            singleLine = true
                        )
                    }
                }

                3 -> {
                    val currentSem = semesterText.toIntOrNull() ?: 1
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = lString("Riwayat IPK Sebelumnya", "Previous GPA History"),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )

                        if (currentSem <= 1) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(lString("Mahasiswa Semester 1", "Semester 1 Student"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        lString(
                                            "Riwayat IPK dimulai dari 0 dan dihitung dari mata kuliah yang kamu tambahkan semester ini.",
                                            "Prior GPA starts at 0 and will be calculated from the courses you add this semester."
                                        ),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = lString(
                                    "Masukkan IPK kumulatif dan total SKS dari semester 1 hingga ${currentSem - 1}, atau pilih belum ada riwayat.",
                                    "Enter your cumulative GPA and total credits from semester 1 to ${currentSem - 1}, or choose no prior history."
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                FilterChip(
                                    selected = inputPriorGpa,
                                    onClick = { inputPriorGpa = true },
                                    label = { Text(lString("Masukkan IPK lalu", "Enter prior GPA")) }
                                )
                                FilterChip(
                                    selected = !inputPriorGpa,
                                    onClick = {
                                        inputPriorGpa = false
                                        priorGpaError = false
                                        priorSksError = false
                                    },
                                    label = { Text(lString("Kosongkan (Mulai dari 0)", "Leave empty (Start at 0)")) }
                                )
                            }

                            if (inputPriorGpa) {
                                OutlinedTextField(
                                    value = priorGpaText,
                                    onValueChange = {
                                        priorGpaText = it
                                        val gpa = it.replace(',', '.').toDoubleOrNull()
                                        priorGpaError = gpa == null || gpa !in 0.0..4.0
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("onboarding-ipk"),
                                    label = { Text(lString("IPK Kumulatif Sebelumnya (0.00-4.00)", "Previous Cumulative GPA (0.00-4.00)")) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    isError = priorGpaError,
                                    supportingText = {
                                        if (priorGpaError) Text(lString("Masukkan nilai IPK antara 0.00 dan 4.00", "Enter a valid GPA between 0.00 and 4.00"))
                                        else Text(lString("Masukkan IPK semester lalu", "Enter your previous GPA"))
                                    },
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = priorSksText,
                                    onValueChange = {
                                        priorSksText = it
                                        val sks = it.toIntOrNull()
                                        priorSksError = sks == null || sks !in 0..250
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("onboarding-prior-sks"),
                                    label = { Text(lString("Total SKS Lulus Sebelumnya", "Previous Completed Credits (SKS)")) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    isError = priorSksError,
                                    supportingText = {
                                        if (priorSksError) Text(lString("Masukkan jumlah SKS antara 0 dan 250", "Enter credits between 0 and 250"))
                                        else Text(lString("Jumlah SKS yang sudah ditempuh", "Total credits completed so far"))
                                    },
                                    singleLine = true
                                )
                            }
                        }
                    }
                }

                4 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = lString("Rencana Keuangan Kuliah", "College Financial Plan"),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = lString(
                                "Masukkan pemasukan atau uang saku awal bulananmu (isi 0 jika belum ingin mencatat saldo awal).",
                                "Enter your starting monthly allowance or income (enter 0 if you don't want an initial balance yet)."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = allowanceText,
                            onValueChange = { raw ->
                                val cleaned = org.umn.application.studenthub.ui.components.cleanRupiahInput(raw)
                                allowanceText = cleaned
                                val num = cleaned.toLongOrNull()
                                allowanceError = num == null || num < 0 || num > 1_000_000_000_000L
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding-allowance"),
                            label = { Text(lString("Pemasukan Awal Bulanan (Rp)", "Initial Monthly Allowance (Rp)")) },
                            prefix = { Text("Rp ") },
                            visualTransformation = remember { org.umn.application.studenthub.ui.components.RupiahVisualTransformation() },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = allowanceError,
                            supportingText = {
                                if (allowanceError) Text(lString("Masukkan nominal rupiah yang valid (0 atau lebih)", "Enter a valid IDR amount (0 or more)"))
                                else Text(lString("Masukkan estimasi uang saku bulanan", "Enter your estimated monthly allowance"))
                            },
                            singleLine = true
                        )
                    }
                }

                5 -> {
                    val roomPrefix = lString("Ruang ", "Room ")
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = lString("Mata Kuliah & Jadwal", "Courses & Schedule"),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = lString(
                                "Tambahkan mata kuliah beserta hari, jam mulai, dan jam selesainya.",
                                "Add your courses along with their day, start time, and end time."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (courses.isEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.School, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                    Text(
                                        lString("Belum ada mata kuliah & jadwal", "No courses & schedule added yet"),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        lString(
                                            "Tambahkan mata kuliah semester ini atau lanjutkan dan isi nanti dari menu Akademik.",
                                            "Add this semester's courses now, or continue and add them later from Academics."
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        courses.forEachIndexed { index, course ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "${course.code} • ${course.sks} SKS",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = course.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        val details = buildList {
                                            add("${days[course.day]}, ${computeClassTimeRange(course.time)}")
                                            if (course.room.isNotBlank()) add("$roomPrefix${course.room}")
                                            if (course.lecturer.isNotBlank()) add(course.lecturer)
                                        }.joinToString(" • ")
                                        Text(
                                            text = details,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { courses.removeAt(index) }) {
                                        Icon(Icons.Default.Delete, lString("Hapus", "Delete"), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { showAddCourseDialog = true },
                            modifier = Modifier.fillMaxWidth().testTag("onboarding-add-course")
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(lString("Tambah Mata Kuliah & Jadwal", "Add Course & Schedule"))
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f, fill = false))

            Button(
                onClick = {
                    when (step) {
                        1 -> {
                            nameError = name.isBlank()
                            programError = program.isBlank()
                            if (!nameError && !programError) step++
                        }
                        2 -> {
                            val s = semesterText.toIntOrNull()
                            if (s == null || s !in 1..14) semesterError = true
                            else step++
                        }
                        3 -> {
                            val currentSem = semesterText.toIntOrNull() ?: 1
                            if (inputPriorGpa && currentSem > 1) {
                                val gpa = priorGpaText.replace(',', '.').toDoubleOrNull()
                                val sks = priorSksText.toIntOrNull()
                                priorGpaError = gpa == null || gpa !in 0.0..4.0
                                priorSksError = sks == null || sks !in 0..250
                                if (!priorGpaError && !priorSksError) step++
                            } else {
                                step++
                            }
                        }
                        4 -> {
                            val a = allowanceText.toLongOrNull()
                            if (a == null || a < 0 || a > 1_000_000_000_000L) allowanceError = true
                            else step++
                        }
                        5 -> {
                            saving = true
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val db = StudentHubDatabase.get(context)
                                    val semesterNum = semesterText.toIntOrNull() ?: 1
                                    val finalPriorGpa = if (inputPriorGpa && semesterNum > 1) {
                                        priorGpaText.replace(',', '.').toDoubleOrNull() ?: 0.0
                                    } else 0.0
                                    val finalPriorSks = if (inputPriorGpa && semesterNum > 1) {
                                        priorSksText.toIntOrNull() ?: 0
                                    } else 0

                                    db.resetAllData()

                                    db.saveProfile(
                                        ProfileRow(
                                            name = name.trim(),
                                            program = program.trim(),
                                            semester = semesterNum,
                                            priorIpk = finalPriorGpa,
                                            priorSks = finalPriorSks,
                                            onboardingCompleted = true
                                        )
                                    )

                                    val allowance = allowanceText.toLongOrNull() ?: 0L
                                    if (allowance > 0L) {
                                        val initialTx = TransactionRow(
                                            id = "init-allowance-" + UUID.randomUUID().toString().take(8),
                                            title = "Uang Saku Bulanan Awal",
                                            amount = allowance,
                                            category = "Lainnya",
                                            income = true,
                                            date = LocalDate.now().toString()
                                        )
                                        runCatching { db.addTransaction(initialTx) }
                                    }

                                    courses.forEach { item ->
                                        val normalizedTime = StudentHubDatabase.normalizeTimeFormat(item.time)
                                        val rangeLabel = computeClassTimeRange(normalizedTime)
                                        val scheduleLabel = "${daysId[item.day]}, $rangeLabel"
                                        val courseRow = CourseRow(
                                            id = item.code,
                                            code = item.code,
                                            title = item.title,
                                            sks = item.sks,
                                            semester = semesterNum,
                                            grade = null,
                                            isDemo = false,
                                            lecturer = item.lecturer,
                                            room = item.room,
                                            schedule = scheduleLabel,
                                            description = ""
                                        )
                                        runCatching { db.saveCourse(courseRow) }
                                        runCatching {
                                            val detailText = buildList {
                                                add(item.code)
                                                if (item.room.isNotBlank()) add(item.room)
                                            }.joinToString(" • ")
                                            db.addAgenda(
                                                day = item.day,
                                                time = normalizedTime,
                                                title = item.title,
                                                kind = "KELAS",
                                                detail = detailText,
                                                courseId = item.code
                                            )
                                        }
                                        runCatching {
                                            db.addReminder(
                                                title = "Kelas ${item.title}",
                                                detail = "${daysId[item.day]}, $rangeLabel" + if (item.room.isNotBlank()) " • ${item.room}" else "",
                                                time = normalizedTime.substringBefore('–'),
                                                category = "Kelas",
                                                enabled = true,
                                                sourceId = "course:${item.code}"
                                            )
                                        }
                                    }
                                }
                                saving = false
                                onFinished()
                            }
                        }
                    }
                },
                enabled = !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding-next")
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(if (step < totalSteps) lString("Lanjut", "Next") else lString("Mulai Gunakan StudentHub", "Start Using StudentHub"))
                    if (step < totalSteps) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                    } else {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    if (showAddCourseDialog) {
        var addCode by remember { mutableStateOf("") }
        var addTitle by remember { mutableStateOf("") }
        var addSks by remember { mutableStateOf("") }
        var addDay by remember { mutableIntStateOf(0) }
        var addStartTime by remember { mutableStateOf("") }
        var addEndTime by remember { mutableStateOf("") }
        var addRoom by remember { mutableStateOf("") }
        var addLecturer by remember { mutableStateOf("") }
        var addError by remember { mutableStateOf(false) }

        val semInt = semesterText.toIntOrNull() ?: 5
        AlertDialog(
            onDismissRequest = { showAddCourseDialog = false },
            title = { Text(lString("Tambah Mata Kuliah & Jadwal", "Add Course & Schedule")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CurriculumCourseDropdown(
                        currentSemester = semInt,
                        selectedTitle = addTitle,
                        onCourseSelected = { c ->
                            if (c != null) {
                                addCode = c.code
                                addTitle = c.title
                                addSks = c.sks.toString()
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
                        { addSks = it.filter { ch -> ch.isDigit() }; addError = false },
                        label = { Text(lString("Jumlah SKS (1-24)", "Credits / SKS (1-24)")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text(lString("Jam Perkuliahan (Mulai - Selesai):", "Class Hours (Start - End):"), style = MaterialTheme.typography.labelMedium)
                    org.umn.application.studenthub.ui.components.TimeRangePickerRow(
                        startTime = addStartTime,
                        endTime = addEndTime,
                        onStartTimeChange = { addStartTime = it; addError = false },
                        onEndTimeChange = { addEndTime = it; addError = false },
                        isError = addError && addStartTime.isBlank()
                    )
                    OutlinedTextField(addRoom, { addRoom = it }, label = { Text(lString("Ruangan (opsional)", "Room (optional)")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(addLecturer, { addLecturer = it }, label = { Text(lString("Nama Dosen (opsional)", "Lecturer Name (optional)")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text(lString("Hari Kuliah:", "Class Day:"), style = MaterialTheme.typography.labelMedium)
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
                            lString("Pastikan kode, nama, SKS (1-24), serta jam mulai & selesai kuliah diisi dengan benar.", "Please fill in code, name, credits (1-24), and start & end time correctly."),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val sksNum = addSks.toIntOrNull()
                    val combinedTime = org.umn.application.studenthub.ui.components.combineTimeRange(addStartTime, addEndTime)
                    val normalizedTime = StudentHubDatabase.normalizeTimeFormat(combinedTime)
                    val timeValid = Regex("(?:[01][0-9]|2[0-3])\\.[0-5][0-9](?:–(?:[01][0-9]|2[0-3])\\.[0-5][0-9])?").matches(normalizedTime)
                    if (addCode.isNotBlank() && addTitle.isNotBlank() && sksNum != null && sksNum in 1..24 && timeValid) {
                        courses.add(
                            OnboardingCourseItem(
                                code = addCode.trim().uppercase(),
                                title = addTitle.trim(),
                                sks = sksNum,
                                day = addDay,
                                time = normalizedTime,
                                room = addRoom.trim(),
                                lecturer = addLecturer.trim()
                            )
                        )
                        showAddCourseDialog = false
                    } else {
                        addError = true
                    }
                }) { Text(lString("Tambahkan", "Add")) }
            },
            dismissButton = { TextButton(onClick = { showAddCourseDialog = false }) { Text(lString("Batal", "Cancel")) } }
        )
    }
}
