package org.umn.application.studenthub.ui.academic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.umn.application.studenthub.data.local.StudentHubDatabase

internal data class AcademicCourse(val id: String, val title: String, val completed: Int, val total: Int)
internal val academicCourses = listOf(
    AcademicCourse("IF570", "Pemrograman Aplikasi Mobile", 8, 10),
    AcademicCourse("IF550", "Rekayasa Perangkat Lunak", 4, 5),
    AcademicCourse("IF510", "Basis Data Lanjut", 1, 2),
    AcademicCourse("IF530", "Interaksi Manusia & Komputer", 5, 5),
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
        grades = database.grades().mapValues { (_, grade) -> AcademicGrade(grade.score, grade.letter) }
        goal = database.goal()
    }
    var grades by mutableStateOf(academicCourses.mapIndexed { i, course ->
        val scores = listOf(88, 82, 74, 80)
        val letters = listOf("A", "B+", "B", "B+")
        course.id to (database?.grades()?.get(course.id)?.let { AcademicGrade(it.score, it.letter) } ?: AcademicGrade(read("score.${course.id}")?.toIntOrNull()?.takeIf { it in 0..100 } ?: scores[i], read("grade.${course.id}")?.takeIf { it in gradePoints } ?: letters[i]))
    }.toMap())
        private set
    var goal by mutableStateOf(database?.goal() ?: read("goal")?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..4.0 } ?: 3.6)
        private set
    val semesterGpa: Double get() = grades.values.map { gradePoints.getValue(it.letter) }.average().takeIf { it.isFinite() } ?: 0.0

    fun updateGrade(id: String, rawScore: String, letter: String): Boolean {
        val score = rawScore.toIntOrNull() ?: return false
        if (id !in grades || score !in 0..100 || letter !in gradePoints) return false
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

    var tasks by mutableStateOf(academicTasks + academicCourses.flatMap { course ->
        val count = read("added.count.${course.id}")?.toIntOrNull()?.coerceIn(0, 1000) ?: 0
        (0 until count).mapNotNull { index ->
            read("added.${course.id}.$index")?.let { AcademicTask("local-${course.id}-$index", course.id, it, "Tugas lokal") }
        }
    })
        private set
    var completion by mutableStateOf(tasks.associate { it.id to (read("task.${it.id}")?.toBooleanStrictOrNull() ?: it.initiallyDone) })
        private set

    fun totalFor(courseId: String): Int = academicCourses.first { it.id == courseId }.total + tasks.count { it.courseId == courseId && it.id.startsWith("local-") }

    fun addTask(courseId: String, title: String): Boolean {
        if (title.isBlank() || academicCourses.none { it.id == courseId }) return false
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
        val course = academicCourses.first { it.id == courseId }
        val delta = tasks.filter { it.courseId == courseId }.sumOf {
            (if (completion[it.id] == true) 1 else 0) - (if (it.initiallyDone) 1 else 0)
        }
        return (course.completed + delta).coerceIn(0, totalFor(courseId))
    }

    val activeTaskCount: Int get() = academicCourses.sumOf { totalFor(it.id) - completedFor(it.id) }
}
