package org.umn.application.studenthub.ui.onboarding

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import org.umn.application.studenthub.data.local.AgendaRow
import org.umn.application.studenthub.data.local.CourseRow
import org.umn.application.studenthub.data.local.GradeRow
import org.umn.application.studenthub.data.local.ProfileRow
import org.umn.application.studenthub.data.local.StudentHubDatabase
import org.umn.application.studenthub.data.local.TransactionRow
import org.umn.application.studenthub.ui.localization.lString
import java.time.LocalDate
import java.util.UUID

internal data class OnboardingCourseItem(
    val code: String,
    val title: String,
    val sks: Int,
    val day: Int, // 0 = Senin, 1 = Selasa, etc.
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

    // Step 1: Identitas
    var name by rememberSaveable { mutableStateOf("Nadia Putri") }
    var program by rememberSaveable { mutableStateOf("Informatika") }
    var nameError by rememberSaveable { mutableStateOf(false) }

    // Step 2: Semester
    var semesterText by rememberSaveable { mutableStateOf("5") }
    var semesterError by rememberSaveable { mutableStateOf(false) }

    // Step 3: Riwayat IPK (jika semester > 1)
    var inputPriorGpa by rememberSaveable { mutableStateOf(true) }
    var priorGpaText by rememberSaveable { mutableStateOf("3.15") }
    var priorGpaError by rememberSaveable { mutableStateOf(false) }

    // Step 4: Uang saku bulanan
    var allowanceText by rememberSaveable { mutableStateOf("2000000") }
    var allowanceError by rememberSaveable { mutableStateOf(false) }

    // Step 5: List Mata Kuliah & Jadwal (Default Semester 5: 19 SKS)
    val defaultCourses = remember {
        mutableStateListOf(
            OnboardingCourseItem("EM105", "Technopreneurship", 3, 0, "08.00", "C.401", "Dosen Pengampu"),
            OnboardingCourseItem("UM321", "English 3", 2, 0, "11.00", "B.201", "Dosen Pengampu"),
            OnboardingCourseItem("IF570", "Mobile Application Programming", 3, 1, "09.00", "D.302", "Budi Santoso, M.Kom."),
            OnboardingCourseItem("IF590", "Information Technology Research", 2, 2, "10.00", "D.201", "Dosen Pengampu"),
            OnboardingCourseItem("IF542", "Deep Learning", 3, 3, "08.00", "Lab AI", "Dosen Pengampu"),
            OnboardingCourseItem("IF571", "Cybersecurity", 3, 3, "13.00", "Lab Cyber", "Dosen Pengampu"),
            OnboardingCourseItem("IF581", "Game Development", 3, 4, "09.00", "Lab Game", "Dosen Pengampu")
        )
    }
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
    val totalSteps = 5

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(lString("Mulai Menggunakan StudentHub", "Get Started with StudentHub")) },
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
            // Stepper progress indicator
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
                // STEP 1: NAMA & PROGRAM STUDI
                1 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = lString("Halo, selamat datang!", "Hello, welcome!"),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = lString("Kenalkan dirimu agar StudentHub dapat mempersonalisasi jadwal, nilai, dan keuanganmu.", "Introduce yourself so StudentHub can personalize your schedule, grades, and finances."),
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
                                else Text(lString("Contoh: Nadia Putri", "Example: Nadia Putri"))
                            },
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = program,
                            onValueChange = { program = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(lString("Program Studi", "Study Program")) },
                            supportingText = { Text(lString("Contoh: Informatika / Sistem Informasi", "Example: Informatics / Information Systems")) },
                            singleLine = true
                        )
                    }
                }

                // STEP 2: SEMESTER
                2 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = lString("Semester berapa kamu saat ini?", "Which semester are you in?"),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = lString("Pilih atau masukkan semester berjalan untuk menyusun beban SKS dan riwayat akademikmu.", "Select or enter your current semester to organize your credits and academic history."),
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
                            label = { Text(lString("Semester (1–14)", "Semester (1–14)")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = semesterError,
                            supportingText = {
                                if (semesterError) Text(lString("Semester harus berupa angka antara 1 dan 14", "Semester must be a number between 1 and 14"))
                                else Text(lString("Semester perkuliahan aktif saat ini", "Current active academic semester"))
                            },
                            singleLine = true
                        )

                        Text(lString("Pilih cepat semester:", "Quick select semester:"), style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(1, 3, 5, 7).forEach { sem ->
                                FilterChip(
                                    selected = semesterText == sem.toString(),
                                    onClick = {
                                        semesterText = sem.toString()
                                        semesterError = false
                                    },
                                    label = { Text("Sem $sem") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // STEP 3: RIWAYAT IPK
                3 -> {
                    val currentSem = semesterText.toIntOrNull() ?: 5
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
                                    Text(lString("🎉 Mahasiswa Baru Semester 1", "🎉 New Student - Semester 1"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        lString(
                                            "Sebagai mahasiswa semester 1, kamu memulai perjalanan akademik dari lembaran baru. Nilai dan IPK-mu akan dihitung dari mata kuliah semester ini.",
                                            "As a 1st semester student, you are starting your academic journey with a clean slate. Your grades and GPA will be calculated from this semester's courses."
                                        ),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = lString(
                                    "Karena kamu sudah berada di semester $currentSem, apakah kamu ingin memasukkan IPK kumulatif dari semester 1 hingga ${currentSem - 1}?",
                                    "Since you are in semester $currentSem, would you like to enter your cumulative GPA from semester 1 to ${currentSem - 1}?"
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                FilterChip(
                                    selected = inputPriorGpa,
                                    onClick = { inputPriorGpa = true },
                                    label = { Text(lString("Ya, masukkan IPK lalu", "Yes, enter prior GPA")) }
                                )
                                FilterChip(
                                    selected = !inputPriorGpa,
                                    onClick = { inputPriorGpa = false },
                                    label = { Text(lString("Gunakan estimasi (3.50)", "Use estimate (3.50)")) }
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
                                    label = { Text(lString("IPK Kumulatif Sebelumnya (0.00–4.00)", "Previous Cumulative GPA (0.00–4.00)")) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    isError = priorGpaError,
                                    supportingText = {
                                        if (priorGpaError) Text(lString("Masukkan nilai IPK yang valid antara 0.00 dan 4.00", "Enter a valid GPA between 0.00 and 4.00"))
                                        else Text(lString("Contoh: 3.56 (dari ${(currentSem - 1) * 15} SKS)", "Example: 3.56 (from ${(currentSem - 1) * 15} credits)"))
                                    },
                                    singleLine = true
                                )
                            }
                        }
                    }
                }

                // STEP 4: PEMASUKAN BULANAN
                4 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = lString("Rencana Keuangan Kuliah", "College Financial Plan"),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = lString(
                                "Berapa perkiraan uang saku / pemasukan bulananmu? Nominal ini akan otomatis menjadi saldo awal pencatatan keuanganmu.",
                                "What is your estimated monthly allowance / income? This amount will automatically become your starting financial balance."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = allowanceText,
                            onValueChange = {
                                allowanceText = it
                                val num = it.toLongOrNull()
                                allowanceError = num == null || num <= 0 || num > 1_000_000_000L
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding-allowance"),
                            label = { Text(lString("Pemasukan Bulanan (Rp)", "Monthly Allowance (Rp)")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = allowanceError,
                            supportingText = {
                                if (allowanceError) Text(lString("Masukkan angka bulat positif tanpa titik atau koma", "Enter a positive whole number without dots or commas"))
                                else Text(lString("Contoh: 2000000 untuk Rp2.000.000", "Example: 2000000 for Rp2,000,000"))
                            },
                            singleLine = true
                        )

                        Text(lString("Pilih cepat nominal:", "Quick select amount:"), style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "1500000" to lString("1.5 Jt", "1.5 M"),
                                "2000000" to lString("2 Jt", "2 M"),
                                "2500000" to lString("2.5 Jt", "2.5 M")
                            ).forEach { (amount, label) ->
                                FilterChip(
                                    selected = allowanceText == amount,
                                    onClick = {
                                        allowanceText = amount
                                        allowanceError = false
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // STEP 5: MATA KULIAH & JADWAL
                5 -> {
                    val roomPrefix = lString("Ruang ", "Room ")
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = lString("Mata Kuliah Semester Ini", "Courses This Semester"),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = lString(
                                "Berikut adalah daftar mata kuliah dan jadwal mingguanmu. Kamu bisa menambah atau menyesuaikannya.",
                                "Here is your course list and weekly schedule. You can add or customize them."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        defaultCourses.forEachIndexed { index, course ->
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
                                        Text(
                                            text = "${days[course.day]}, ${course.time} • $roomPrefix${course.room} • ${course.lecturer}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { defaultCourses.removeAt(index) }) {
                                        Icon(Icons.Default.Delete, lString("Hapus", "Delete"), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { showAddCourseDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors()
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(lString("Tambah Mata Kuliah Lain", "Add Another Course"))
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f, fill = false))

            // Navigation Button (Lanjut / Mulai)
            Button(
                onClick = {
                    when (step) {
                        1 -> {
                            if (name.isBlank()) nameError = true
                            else step++
                        }
                        2 -> {
                            val s = semesterText.toIntOrNull()
                            if (s == null || s !in 1..14) semesterError = true
                            else step++
                        }
                        3 -> {
                            if (inputPriorGpa && (semesterText.toIntOrNull() ?: 5) > 1) {
                                val gpa = priorGpaText.replace(',', '.').toDoubleOrNull()
                                if (gpa == null || gpa !in 0.0..4.0) priorGpaError = true
                                else step++
                            } else {
                                step++
                            }
                        }
                        4 -> {
                            val a = allowanceText.toLongOrNull()
                            if (a == null || a <= 0) allowanceError = true
                            else step++
                        }
                        5 -> {
                            // FINISH ONBOARDING
                            saving = true
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val db = StudentHubDatabase.get(context)
                                    val semesterNum = semesterText.toIntOrNull() ?: 5
                                    // 1. Save Profile
                                    db.saveProfile(ProfileRow(name.trim(), program.trim(), semesterNum))

                                    // 2. Save Prior GPA in Academic SharedPreferences
                                    val finalPriorGpa = if (inputPriorGpa && semesterNum > 1) {
                                        priorGpaText.replace(',', '.').toDoubleOrNull() ?: 3.15
                                    } else 3.15
                                    val prefs = context.getSharedPreferences("academic", Context.MODE_PRIVATE)
                                    prefs.edit()
                                        .putString("prior_ipk", finalPriorGpa.toString())
                                        .putString("prior_sks", ((semesterNum - 1) * 19).toString())
                                        .putBoolean("onboarding_completed", true)
                                        .apply()

                                    // 3. Clear/empty user-fillable notes so user starts with a clean slate
                                    val notesPrefs = context.getSharedPreferences("studenthub_notes", Context.MODE_PRIVATE)
                                    notesPrefs.edit().putString("notes", "AAAAAA==").apply()

                                    // 4. Save initial allowance transaction in SQLite
                                    val allowance = allowanceText.toLongOrNull() ?: 2000000L
                                    val initialTx = TransactionRow(
                                        id = "init-allowance-" + UUID.randomUUID().toString().take(8),
                                        title = "Uang Saku Bulanan Awal",
                                        amount = allowance,
                                        category = "Lainnya",
                                        income = true,
                                        date = LocalDate.now().toString()
                                    )
                                    runCatching { db.addTransaction(initialTx) }

                                    // 5. Purge old demo fixtures and save Courses & Agenda
                                    runCatching {
                                        db.writableDatabase.delete("agenda", "id LIKE 'demo-%' OR id LIKE 'legacy-%' OR title LIKE '%Rekayasa Perangkat Lunak%'", null)
                                        db.writableDatabase.delete("courses", "is_demo = 1 OR id IN ('IF550', 'IF510', 'IF530')", null)
                                    }
                                    defaultCourses.forEach { item ->
                                        val courseRow = CourseRow(
                                            id = item.code,
                                            code = item.code,
                                            title = item.title,
                                            sks = item.sks,
                                            semester = semesterNum,
                                            grade = GradeRow(85, "A"),
                                            isDemo = false
                                        )
                                        runCatching { db.saveCourse(courseRow) }
                                        runCatching {
                                            db.addAgenda(item.day, item.time, item.title)
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
        var addSks by remember { mutableStateOf("3") }
        var addDay by remember { mutableIntStateOf(0) }
        var addTime by remember { mutableStateOf("08.00") }
        var addRoom by remember { mutableStateOf("D.101") }
        var addLecturer by remember { mutableStateOf("") }
        var addError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddCourseDialog = false },
            title = { Text(lString("Tambah Mata Kuliah", "Add Course")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(addCode, { addCode = it }, label = { Text(lString("Kode (misal: IF520)", "Code (e.g. IF520)")) }, singleLine = true)
                    OutlinedTextField(addTitle, { addTitle = it }, label = { Text(lString("Nama Mata Kuliah", "Course Name")) }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(addSks, { addSks = it }, label = { Text(lString("SKS", "Credits")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(addTime, { addTime = it }, label = { Text(lString("Jam (HH.mm)", "Time (HH:mm)")) }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    OutlinedTextField(addRoom, { addRoom = it }, label = { Text(lString("Ruangan", "Room")) }, singleLine = true)
                    OutlinedTextField(addLecturer, { addLecturer = it }, label = { Text(lString("Nama Dosen", "Lecturer Name")) }, singleLine = true)
                    Text(lString("Hari Kuliah:", "Class Day:"), style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            lString("Sen", "Mon"),
                            lString("Sel", "Tue"),
                            lString("Rab", "Wed"),
                            lString("Kam", "Thu"),
                            lString("Jum", "Fri")
                        ).forEachIndexed { i, d ->
                            FilterChip(selected = addDay == i, onClick = { addDay = i }, label = { Text(d) })
                        }
                    }
                    if (addError) Text(lString("Isi kode, nama, dan SKS dengan benar.", "Please fill in code, name, and credits correctly."), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val sksNum = addSks.toIntOrNull()
                    if (addCode.isNotBlank() && addTitle.isNotBlank() && sksNum != null && sksNum in 1..6) {
                        defaultCourses.add(
                            OnboardingCourseItem(
                                code = addCode.trim().uppercase(),
                                title = addTitle.trim(),
                                sks = sksNum,
                                day = addDay,
                                time = addTime.trim(),
                                room = addRoom.trim().ifBlank { "Ruang Kuliah" },
                                lecturer = addLecturer.trim().ifBlank { "Dosen Pengampu" }
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
