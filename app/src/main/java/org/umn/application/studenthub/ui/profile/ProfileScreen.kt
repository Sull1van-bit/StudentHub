package org.umn.application.studenthub.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.umn.application.studenthub.data.local.CourseRow
import org.umn.application.studenthub.data.local.ProfileRow
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
    var courseCredits by rememberSaveable { mutableStateOf("3") }
    var courseSemester by rememberSaveable { mutableStateOf("") }
    fun load() { scope.launch { runCatching { withContext(Dispatchers.IO) { val db = StudentHubDatabase.get(context); db.profile() to db.courses() } }.onSuccess { (p, c) -> profile = p; courses = c; name = p.name; program = p.program; semester = p.semester.toString() }.onFailure { error = "Data profil tidak dapat dibaca." } } }
    LaunchedEffect(Unit) { load() }
    if (profile == null || courses == null) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(); Text(error ?: lString("Memuat profil…", "Loading profile…")) }; return }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(lString("Profil Mahasiswa", "Student Profile"), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text(lString("Nama Lengkap", "Full Name")) })
        OutlinedTextField(value = program, onValueChange = { program = it }, modifier = Modifier.fillMaxWidth(), label = { Text(lString("Program Studi", "Study Program")) })
        OutlinedTextField(value = semester, onValueChange = { semester = it }, modifier = Modifier.fillMaxWidth(), label = { Text(lString("Semester", "Semester")) })
        Button(onClick = { scope.launch { runCatching { withContext(Dispatchers.IO) { StudentHubDatabase.get(context).saveProfile(ProfileRow(name, program, semester.toInt())) } }.onSuccess { onProfileSaved() }.onFailure { error = "Profil belum tersimpan. Periksa isian." } } }, modifier = Modifier.fillMaxWidth()) {
            Text(lString("Simpan profil", "Save profile"))
        }
        OutlinedButton(
            onClick = {
                context.getSharedPreferences("studenthub_prefs", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("has_completed_onboarding", false)
                    .apply()
                onResetOnboarding()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(lString("Mulai Ulang Setup Pengguna (Onboarding)", "Restart Setup Wizard (Onboarding)"))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        // Language switcher section
        languageContent()

        // Theme settings section
        themeContent()

        Text(lString("Daftar Mata Kuliah", "Courses"), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(courseCode, { courseCode = it }, Modifier.fillMaxWidth(), label = { Text(lString("Kode mata kuliah", "Course code")) }, singleLine = true)
        OutlinedTextField(courseTitle, { courseTitle = it }, Modifier.fillMaxWidth(), label = { Text(lString("Nama mata kuliah", "Course title")) }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(courseCredits, { courseCredits = it }, Modifier.weight(1f), label = { Text(lString("SKS", "Credits")) }, singleLine = true)
            OutlinedTextField(courseSemester.ifBlank { semester }, { courseSemester = it }, Modifier.weight(1f), label = { Text(lString("Semester", "Semester")) }, singleLine = true)
        }
        Button(
            enabled = courseCode.isNotBlank() && courseTitle.isNotBlank(),
            onClick = { scope.launch { runCatching {
                withContext(Dispatchers.IO) { StudentHubDatabase.get(context).saveCourse(CourseRow(UUID.randomUUID().toString(), courseCode, courseTitle, courseCredits.toInt(), (courseSemester.ifBlank { semester }).toInt(), null)) }
            }.onSuccess { courseCode = ""; courseTitle = ""; courseCredits = "3"; courseSemester = ""; load() }.onFailure { error = "Mata kuliah belum tersimpan. Periksa isian." } } },
            modifier = Modifier.fillMaxWidth()
        ) { Text(lString("Tambah mata kuliah", "Add course")) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            courses!!.groupBy { it.semester }.forEach { (term, rows) ->
                Text("Semester $term", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                rows.forEach { course ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) { Text(course.title); Text("${course.code} · ${course.sks} SKS", style = MaterialTheme.typography.bodySmall) }
                        Text(course.grade?.let { "${it.letter} · ${it.score}" } ?: lString("Belum ada nilai", "No grade yet"))
                    }
                    HorizontalDivider()
                }
            }
            if (courses!!.isEmpty()) Text(lString("Belum ada mata kuliah.", "No courses registered yet."))
        }
    }
}
