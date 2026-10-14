package org.umn.application.studenthub.ui.academic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.umn.application.studenthub.data.local.StudentHubDatabase

internal data class AcademicCourse(
    val id: String,
    val title: String,
    val completed: Int,
    val total: Int,
    val sks: Int = 3,
    val lecturer: String = "Budi Santoso, M.Kom.",
    val room: String = "Ruang D.302",
    val schedule: String = "Senin, 09.00–10.40",
    val description: String = "Pemrograman aplikasi mobile menggunakan Android Studio dan Jetpack Compose."
)
internal val academicCourses = listOf(
    AcademicCourse("IF570", "Pemrograman Aplikasi Mobile", 8, 10, 3, "Budi Santoso, M.Kom.", "Ruang D.302", "Senin, 09.00–10.40", "Pengembangan aplikasi mobile modern menggunakan Kotlin dan Jetpack Compose."),
    AcademicCourse("IF550", "Rekayasa Perangkat Lunak", 4, 5, 3, "Dr. Eng. Farida, S.Kom., M.T.", "Ruang D.204", "Senin, 13.00–14.40", "Siklus hidup perangkat lunak, pemodelan arsitektur MVVM, dan software testing."),
    AcademicCourse("IF510", "Basis Data Lanjut", 1, 2, 3, "Prof. Dr. Antonius, M.Sc.", "Ruang C.301", "Selasa, 10.00–11.40", "Indeks B-Tree, optimasi query relasional, dan transaksi database ACID."),
    AcademicCourse("IF530", "Interaksi Manusia & Komputer", 5, 5, 3, "Citra Dewi, S.Kom., M.Sc.", "Lab IMK", "Rabu, 08.00–09.40", "Prinsip heuristik usability, desain interaksi UI/UX, dan evaluasi kemudahan penggunaan.")
)

internal val semester5Courses = listOf(
    AcademicCourse("EM105", "Technopreneurship", 4, 4, 3, "Dosen Pengampu", "Ruang C.401", "Senin, 08.00–09.40", "Pengembangan bisnis berbasis teknologi, validasi pasar, dan inovasi startup digital."),
    AcademicCourse("UM321", "English 3", 3, 3, 2, "Dosen Pengampu", "Ruang B.201", "Senin, 11.00–12.40", "Kemampuan komunikasi bahasa Inggris tingkat lanjut untuk akademis dan presentasi profesional."),
    AcademicCourse("IF570", "Mobile Application Programming", 8, 10, 3, "Budi Santoso, M.Kom.", "Ruang D.302", "Selasa, 09.00–10.40", "Pengembangan aplikasi mobile modern menggunakan Kotlin dan Jetpack Compose."),
    AcademicCourse("IF590", "Information Technology Research", 3, 4, 2, "Dosen Pengampu", "Ruang D.201", "Rabu, 10.00–11.40", "Metodologi penelitian, tinjauan pustaka, dan penulisan ilmiah bidang teknologi informasi."),
    AcademicCourse("IF542", "Deep Learning", 4, 5, 3, "Dosen Pengampu", "Lab AI", "Kamis, 08.00–09.40", "Arsitektur neural network mendalam, CNN, RNN, transfer learning, dan computer vision."),
    AcademicCourse("IF571", "Cybersecurity", 4, 4, 3, "Dosen Pengampu", "Lab Cyber", "Kamis, 13.00–14.40", "Keamanan informasi, analisis kerentanan, kriptografi, dan mitigasi ancaman siber."),
    AcademicCourse("IF581", "Game Development", 4, 4, 3, "Dosen Pengampu", "Lab Game", "Jumat, 09.00–10.40", "Perancangan mekanik game, gameplay loop, engine game, dan pengembangan interaktif 2D/3D.")
)

internal data class AcademicTask(
    val id: String, val courseId: String, val title: String,
    val detail: String, val deadline: String = "", val initiallyDone: Boolean = false,
)
internal val academicTasks = listOf(
    AcademicTask("navigation", "IF570", "Praktikum 3: Navigasi", "Laporan PDF & kode proyek Android", "HARI INI • 17.00"),
    AcademicTask("architecture", "IF550", "Diagram arsitektur", "Rabu, 7 Okt • IF550"),
    AcademicTask("mini-project", "IF570", "Proyek mini: Daftar tugas", "Implementasi penyimpanan lokal", "JUMAT, 9 OKT • 23.59"),
    AcademicTask("layout", "IF570", "Praktikum 2: Layout", "Dikumpulkan 28 Sep • Nilai 88", initiallyDone = true),
    AcademicTask("components", "IF570", "Praktikum 1: Komponen UI", "Dikumpulkan 21 Sep • Nilai 85", initiallyDone = true),
)

internal val semester5Tasks = listOf(
    AcademicTask("navigation", "IF570", "Praktikum 3: Navigasi", "Laporan PDF & kode proyek Android", "HARI INI • 17.00"),
    AcademicTask("mini-project", "IF570", "Proyek mini: Daftar tugas", "Implementasi penyimpanan lokal", "JUMAT, 9 OKT • 23.59"),
    AcademicTask("research-prop", "IF590", "Proposal Topik Riset", "Bab 1 & kajian literatur", "RABU, 14 OKT • 23.59"),
    AcademicTask("deep-learning-lab", "IF542", "Lab 1: Pengenalan PyTorch", "Implementasi feedforward net", "KAMIS, 15 OKT • 17.00"),
    AcademicTask("layout", "IF570", "Praktikum 2: Layout", "Dikumpulkan 28 Sep • Nilai 88", initiallyDone = true),
    AcademicTask("components", "IF570", "Praktikum 1: Komponen UI", "Dikumpulkan 21 Sep • Nilai 85", initiallyDone = true),
)

internal val gradePoints = linkedMapOf("A" to 4.0, "A-" to 3.7, "B+" to 3.5, "B" to 3.0, "B-" to 2.7, "C+" to 2.5, "C" to 2.0, "D" to 1.0, "E" to 0.0)
internal data class AcademicGrade(val score: Int, val letter: String)

/** Local demo records; no backend or submission is implied by checking a task. */
internal class AcademicState(
    private val read: (String) -> String? = { null },
    private val write: (String, String) -> Unit = { _, _ -> },
) {
    private var database: StudentHubDatabase? = null
    constructor(database: StudentHubDatabase, read: (String) -> String? = { null }, write: (String, String) -> Unit = { _, _ -> }) : this(read, write) {
        this.database = database
        val dbGrades = database.grades().mapValues { (_, grade) -> AcademicGrade(grade.score, grade.letter) }
        grades = grades + dbGrades
        goal = database.goal()
    }

    private val isOnboarded: Boolean
        get() = runCatching { read("onboarding_completed")?.toBooleanStrictOrNull() == true }.getOrDefault(false)

    fun currentCourses(): List<AcademicCourse> = if (isOnboarded) semester5Courses else academicCourses

    var grades by mutableStateOf(buildMap {
        val scores = listOf(88, 82, 74, 80)
        val letters = listOf("A", "B+", "B", "B+")
        academicCourses.forEachIndexed { i, course ->
            val score = database?.grades()?.get(course.id)?.score ?: read("score.${course.id}")?.toIntOrNull()?.takeIf { it in 0..100 } ?: scores[i]
            val letter = database?.grades()?.get(course.id)?.letter ?: read("grade.${course.id}")?.takeIf { it in gradePoints } ?: letters[i]
            put(course.id, AcademicGrade(score, letter))
        }
        semester5Courses.forEach { course ->
            if (!containsKey(course.id)) {
                val score = database?.grades()?.get(course.id)?.score ?: read("score.${course.id}")?.toIntOrNull()?.takeIf { it in 0..100 } ?: 85
                val letter = database?.grades()?.get(course.id)?.letter ?: read("grade.${course.id}")?.takeIf { it in gradePoints } ?: "A"
                put(course.id, AcademicGrade(score, letter))
            }
        }
    })
        private set
    var goal by mutableStateOf(database?.goal() ?: read("goal")?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..4.0 } ?: 3.6)
        private set

    val semesterGpa: Double get() {
        val courses = currentCourses()
        return courses.map { gradePoints.getValue(grades[it.id]?.letter ?: "A") }.average().takeIf { it.isFinite() } ?: 0.0
    }

    val cumulativeGpa: Double get() {
        val priorIpk = read("prior_ipk")?.toDoubleOrNull() ?: 3.56
        val priorSks = read("prior_sks")?.toIntOrNull() ?: 60
        val courses = currentCourses()
        val currentSks = courses.sumOf { it.sks }
        val currentPoints = courses.sumOf { course ->
            val letter = grades[course.id]?.letter ?: "A"
            gradePoints.getValue(letter) * course.sks
        }
        val totalPoints = (priorIpk * priorSks) + currentPoints
        val totalSks = priorSks + currentSks
        return if (totalSks > 0) totalPoints / totalSks else semesterGpa
    }

    fun updateGrade(id: String, rawScore: String, letter: String): Boolean {
        val score = rawScore.toIntOrNull() ?: return false
        val allValidCourseIds = (academicCourses.map { it.id } + semester5Courses.map { it.id }).toSet()
        if (id !in allValidCourseIds || score !in 0..100 || letter !in gradePoints) return false
        database?.let { it.saveGrade(id, score, letter) } ?: run { write("score.$id", score.toString()); write("grade.$id", letter) }
        grades = grades + (id to AcademicGrade(score, letter))
        return true
    }

    fun updateGoal(raw: String): Boolean {
        val value = raw.replace(',', '.').toDoubleOrNull() ?: return false
        if (!value.isFinite() || value !in 0.0..4.0) return false
        database?.let { it.saveGoal(value) } ?: write("goal", value.toString())
        goal = value
        return true
    }

    var tasks by mutableStateOf((academicTasks + semester5Tasks).distinctBy { it.id } + (academicCourses + semester5Courses).distinctBy { it.id }.flatMap { course ->
        val count = read("added.count.${course.id}")?.toIntOrNull()?.coerceIn(0, 1000) ?: 0
        (0 until count).mapNotNull { index ->
            read("added.${course.id}.$index")?.let { AcademicTask("local-${course.id}-$index", course.id, it, "Tugas lokal") }
        }
    })
        private set
    var completion by mutableStateOf(tasks.associate { it.id to (read("task.${it.id}")?.toBooleanStrictOrNull() ?: it.initiallyDone) })
        private set

    fun totalFor(courseId: String): Int {
        val course = currentCourses().firstOrNull { it.id == courseId } ?: semester5Courses.firstOrNull { it.id == courseId } ?: academicCourses.firstOrNull { it.id == courseId }
        return (course?.total ?: 0) + tasks.count { it.courseId == courseId && it.id.startsWith("local-") }
    }

    fun addTask(courseId: String, title: String): Boolean {
        val courseExists = academicCourses.any { it.id == courseId } || semester5Courses.any { it.id == courseId }
        if (title.isBlank() || !courseExists) return false
        val index = tasks.count { it.courseId == courseId && it.id.startsWith("local-") }
        if (index >= 1000) return false
        val task = AcademicTask("local-$courseId-$index", courseId, title.trim(), "Tugas lokal")
        tasks = tasks + task
        completion = completion + (task.id to false)
        write("added.$courseId.$index", task.title)
        write("added.count.$courseId", (index + 1).toString())
        return true
    }

    fun toggleTask(id: String) {
        if (id !in completion) return
        val next = completion.getValue(id).not()
        completion = completion + (id to next)
        write("task.$id", next.toString())
    }

    fun completedFor(courseId: String): Int {
        val course = currentCourses().firstOrNull { it.id == courseId } ?: semester5Courses.firstOrNull { it.id == courseId } ?: academicCourses.firstOrNull { it.id == courseId } ?: return 0
        val delta = tasks.filter { it.courseId == courseId }.sumOf {
            (if (completion[it.id] == true) 1 else 0) - (if (it.initiallyDone) 1 else 0)
        }
        return (course.completed + delta).coerceIn(0, totalFor(courseId))
    }

    val activeTaskCount: Int get() = currentCourses().sumOf { totalFor(it.id) - completedFor(it.id) }
}
