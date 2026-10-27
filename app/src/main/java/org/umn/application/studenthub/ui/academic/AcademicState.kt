package org.umn.application.studenthub.ui.academic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.umn.application.studenthub.data.local.CourseRow
import org.umn.application.studenthub.data.local.StudentHubDatabase
import org.umn.application.studenthub.data.local.StudyPlanRow
import org.umn.application.studenthub.ui.dashboard.computeClassTimeRange
import java.util.UUID

internal data class AcademicCourse(
    val id: String,
    val title: String,
    val completed: Int = 0,
    val total: Int = 0,
    val sks: Int = 3,
    val semester: Int = 1,
    val lecturer: String = "",
    val room: String = "",
    val schedule: String = "",
    val description: String = ""
)

internal data class AcademicTask(
    val id: String,
    val courseId: String,
    val title: String,
    val detail: String = "",
    val deadline: String = "",
    val initiallyDone: Boolean = false,
    val reminderEnabled: Boolean = false
)

internal fun scoreToLetter(s: Int): String = when {
    s >= 85 -> "A"
    s >= 80 -> "A-"
    s >= 75 -> "B+"
    s >= 70 -> "B"
    s >= 65 -> "B-"
    s >= 60 -> "C+"
    s >= 55 -> "C"
    s >= 40 -> "D"
    else -> "E"
}

internal val gradePoints = linkedMapOf(
    "A" to 4.0,
    "A-" to 3.7,
    "B+" to 3.5,
    "B" to 3.0,
    "B-" to 2.7,
    "C+" to 2.5,
    "C" to 2.0,
    "D" to 1.0,
    "E" to 0.0
)

internal data class AcademicGrade(val score: Int, val letter: String)

internal class AcademicState(
    private val read: (String) -> String? = { null },
    private val write: (String, String) -> Unit = { _, _ -> },
) {
    private var database: StudentHubDatabase? = null

    var courses by mutableStateOf(
        read("courses")?.lines()?.filter { it.isNotBlank() }?.mapNotNull { line ->
            val p = line.split("\t")
            if (p.size >= 4) AcademicCourse(id = p[0], title = p[1], sks = p[2].toIntOrNull() ?: 3, semester = p[3].toIntOrNull() ?: 1) else null
        }.orEmpty()
    )
        private set

    var grades by mutableStateOf(
        courses.mapNotNull { c ->
            val s = read("score.${c.id}")?.toIntOrNull()
            val l = read("grade.${c.id}")
            if (s != null && l != null) c.id to AcademicGrade(s, l) else null
        }.toMap()
    )
        private set

    var goal by mutableStateOf(
        read("goal")?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..4.0 } ?: 0.0
    )
        private set

    var tasks by mutableStateOf(
        read("tasks")?.lines()?.filter { it.isNotBlank() }?.mapNotNull { line ->
            val p = line.split("\t")
            if (p.size >= 4) AcademicTask(id = p[0], courseId = p[1], title = p[2], detail = p[3]) else null
        }.orEmpty()
    )
        private set

    var completion by mutableStateOf(
        tasks.associate { it.id to (read("task.${it.id}")?.toBooleanStrictOrNull() ?: it.initiallyDone) }
    )
        private set

    var studyPlans by mutableStateOf<List<StudyPlanRow>>(emptyList())
        private set

    constructor(
        database: StudentHubDatabase,
        read: (String) -> String? = { null },
        write: (String, String) -> Unit = { _, _ -> }
    ) : this(read, write) {
        this.database = database
        reloadFromDatabase()
    }

    fun reloadFromDatabase() {
        val db = database ?: return
        val dbTasks = db.tasks()
        val taskModels = dbTasks.map { row ->
            AcademicTask(
                id = row.id,
                courseId = row.courseId,
                title = row.title,
                detail = row.detail,
                deadline = row.deadline,
                initiallyDone = row.completed,
                reminderEnabled = row.reminderEnabled
            )
        }
        val compMap = taskModels.associate { it.id to it.initiallyDone }
        val dbCourses = db.courses().map { row ->
            val courseTasks = taskModels.filter { it.courseId == row.code }
            AcademicCourse(
                id = row.code,
                title = row.title,
                completed = courseTasks.count { compMap[it.id] == true },
                total = courseTasks.size,
                sks = row.sks,
                semester = row.semester,
                lecturer = row.lecturer,
                room = row.room,
                schedule = row.schedule,
                description = row.description
            )
        }
        courses = dbCourses
        grades = db.grades().mapValues { (_, g) -> AcademicGrade(g.score, g.letter) }
        goal = db.goal()
        tasks = taskModels
        completion = compMap
        studyPlans = db.studyPlans()
    }

    fun currentCourses(): List<AcademicCourse> = courses

    val semesterGpa: Double
        get() {
            val gradedCourses = currentCourses().filter { it.id in grades }
            val totalSks = gradedCourses.sumOf { it.sks }
            if (totalSks <= 0) return 0.0
            val totalPoints = gradedCourses.sumOf { course ->
                val letter = grades[course.id]?.letter ?: return@sumOf 0.0
                (gradePoints[letter] ?: 0.0) * course.sks
            }
            val gpa = totalPoints / totalSks
            return if (gpa.isFinite()) gpa else 0.0
        }

    val cumulativeGpa: Double
        get() {
            val dbProfile = database?.let { runCatching { it.profile() }.getOrNull() }
            val priorIpk = dbProfile?.priorIpk?.takeIf { it.isFinite() && it in 0.0..4.0 }
                ?: read("prior_ipk")?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..4.0 }
                ?: 0.0
            val priorSks = dbProfile?.priorSks?.takeIf { it >= 0 }
                ?: read("prior_sks")?.toIntOrNull()?.takeIf { it >= 0 }
                ?: 0

            val gradedCourses = currentCourses().filter { it.id in grades }
            val currentSks = gradedCourses.sumOf { it.sks }
            val currentPoints = gradedCourses.sumOf { course ->
                val letter = grades[course.id]?.letter ?: return@sumOf 0.0
                (gradePoints[letter] ?: 0.0) * course.sks
            }
            val totalPoints = (priorIpk * priorSks) + currentPoints
            val totalSks = priorSks + currentSks
            return if (totalSks > 0) {
                val cgpa = totalPoints / totalSks
                if (cgpa.isFinite()) cgpa else semesterGpa
            } else {
                semesterGpa
            }
        }

    fun addCourse(code: String, title: String, sks: String, semester: String): Boolean {
        val sksInt = sks.trim().toIntOrNull() ?: return false
        val semInt = semester.trim().toIntOrNull() ?: return false
        return addCourse(code, title, sksInt, semInt)
    }

    fun addCourse(
        code: String,
        title: String,
        sks: Int,
        semester: Int,
        lecturer: String = "",
        room: String = "",
        day: Int? = null,
        time: String = "",
        description: String = ""
    ): Boolean {
        val cleanCode = code.trim().uppercase()
        val cleanTitle = title.trim()
        if (cleanCode.isBlank() || cleanCode.length > 30 || cleanTitle.isBlank() || cleanTitle.length > 200 || sks !in 1..24 || semester !in 1..20) {
            return false
        }
        val daysId = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
        val normalizedTime = if (time.isNotBlank()) StudentHubDatabase.normalizeTimeFormat(time) else ""
        val hasValidSchedule = day != null && day in 0..6 &&
            Regex("(?:[01][0-9]|2[0-3])\\.[0-5][0-9](?:–(?:[01][0-9]|2[0-3])\\.[0-5][0-9])?").matches(normalizedTime)
        val rangeDisplay = if (hasValidSchedule) computeClassTimeRange(normalizedTime) else ""
        val scheduleStr = if (hasValidSchedule) "${daysId[day!!]}, $rangeDisplay" else ""

        val db = database
        if (db != null) {
            return runCatching {
                db.saveCourse(
                    CourseRow(
                        id = cleanCode,
                        code = cleanCode,
                        title = cleanTitle,
                        sks = sks,
                        semester = semester,
                        grade = null,
                        isDemo = false,
                        lecturer = lecturer.trim(),
                        room = room.trim(),
                        schedule = scheduleStr,
                        description = description.trim()
                    )
                )
                if (hasValidSchedule) {
                    val detailText = buildList {
                        add(cleanCode)
                        if (room.isNotBlank()) add(room.trim())
                    }.joinToString(" • ")
                    db.addAgenda(
                        day = day!!,
                        time = rangeDisplay.ifBlank { normalizedTime },
                        title = cleanTitle,
                        kind = "KELAS",
                        detail = detailText,
                        courseId = cleanCode
                    )
                    db.addReminder(
                        title = "Kelas $cleanTitle",
                        detail = "${daysId[day]}, $rangeDisplay" + if (room.isNotBlank()) " • ${room.trim()}" else "",
                        time = normalizedTime.substringBefore('–'),
                        category = "Kelas",
                        enabled = true,
                        sourceId = "course:$cleanCode"
                    )
                }
                reloadFromDatabase()
                true
            }.getOrDefault(false)
        } else {
            val newCourse = AcademicCourse(
                id = cleanCode,
                title = cleanTitle,
                completed = 0,
                total = 0,
                sks = sks,
                semester = semester,
                lecturer = lecturer.trim(),
                room = room.trim(),
                schedule = scheduleStr,
                description = description.trim()
            )
            courses = courses.filterNot { it.id == cleanCode } + newCourse
            write("courses", courses.joinToString("\n") { "${it.id}\t${it.title}\t${it.sks}\t${it.semester}" })
            return true
        }
    }

    fun deleteCourse(code: String) {
        val db = database
        if (db != null) {
            db.deleteCourse(code)
            db.deleteReminderBySource("course:$code")
            reloadFromDatabase()
        } else {
            courses = courses.filterNot { it.id == code }
            grades = grades - code
            tasks = tasks.filterNot { it.courseId == code }
            write("courses", courses.joinToString("\n") { "${it.id}\t${it.title}\t${it.sks}\t${it.semester}" })
        }
    }

    fun updateGrade(id: String, rawScore: String, letter: String): Boolean {
        val score = rawScore.toIntOrNull() ?: return false
        val validCourseIds = currentCourses().map { it.id }.toSet()
        if (id !in validCourseIds || score !in 0..100 || letter !in gradePoints) return false
        val db = database
        if (db != null) {
            runCatching { db.saveGrade(id, score, letter) }.onFailure { return false }
            reloadFromDatabase()
        } else {
            write("score.$id", score.toString())
            write("grade.$id", letter)
            grades = grades + (id to AcademicGrade(score, letter))
        }
        return true
    }

    fun updateGoal(raw: String): Boolean {
        val value = raw.replace(',', '.').toDoubleOrNull() ?: return false
        if (!value.isFinite() || value !in 0.0..4.0) return false
        database?.saveGoal(value) ?: write("goal", value.toString())
        goal = value
        return true
    }

    fun totalFor(courseId: String): Int {
        return tasks.count { it.courseId == courseId }
    }

    fun completedFor(courseId: String): Int {
        return tasks.count { it.courseId == courseId && completion[it.id] == true }
    }

    fun addTask(
        courseId: String,
        title: String,
        detail: String = "",
        deadline: String = "",
        reminderEnabled: Boolean = false,
        reminderTime: String = "08.00"
    ): Boolean {
        val courseExists = currentCourses().any { it.id == courseId }
        if (title.isBlank() || title.trim().length > 200 || !courseExists) return false
        val db = database
        if (db != null) {
            return runCatching {
                val row = db.addTask(courseId, title.trim(), detail.trim(), deadline.trim(), reminderEnabled)
                if (reminderEnabled) {
                    val normalizedTime = StudentHubDatabase.normalizeTimeFormat(reminderTime).let {
                        if (Regex("(?:[01][0-9]|2[0-3])\\.[0-5][0-9]").matches(it)) it else "08.00"
                    }
                    db.addReminder(
                        title = "Tugas $courseId: ${row.title}",
                        detail = deadline.trim().ifBlank { detail.trim() },
                        time = normalizedTime,
                        category = "Tugas",
                        enabled = true,
                        sourceId = "task:${row.id}"
                    )
                }
                reloadFromDatabase()
                true
            }.getOrDefault(false)
        } else {
            val task = AcademicTask(
                id = "local-$courseId-${UUID.randomUUID()}",
                courseId = courseId,
                title = title.trim(),
                detail = detail.trim().ifBlank { "Tugas lokal" },
                deadline = deadline.trim(),
                initiallyDone = false,
                reminderEnabled = reminderEnabled
            )
            tasks = tasks + task
            completion = completion + (task.id to false)
            write("tasks", tasks.joinToString("\n") { "${it.id}\t${it.courseId}\t${it.title}\t${it.detail}" })
            return true
        }
    }

    fun toggleTask(id: String) {
        val db = database
        if (db != null) {
            db.toggleTask(id)
            reloadFromDatabase()
        } else {
            if (id !in completion) return
            val next = completion.getValue(id).not()
            completion = completion + (id to next)
            write("task.$id", next.toString())
        }
    }

    fun toggleTaskReminder(taskId: String, time: String = "08.00") {
        val db = database ?: return
        val task = tasks.firstOrNull { it.id == taskId } ?: return
        val nextEnabled = !task.reminderEnabled
        db.setTaskReminder(taskId, nextEnabled)
        db.deleteReminderBySource("task:$taskId")
        if (nextEnabled) {
            val normalizedTime = StudentHubDatabase.normalizeTimeFormat(time).let {
                if (Regex("(?:[01][0-9]|2[0-3])\\.[0-5][0-9]").matches(it)) it else "08.00"
            }
            runCatching {
                db.addReminder(
                    title = "Tugas ${task.courseId}: ${task.title}",
                    detail = task.deadline.ifBlank { task.detail },
                    time = normalizedTime,
                    category = "Tugas",
                    enabled = true,
                    sourceId = "task:$taskId"
                )
            }
        }
        reloadFromDatabase()
    }

    fun deleteTask(id: String) {
        val db = database
        if (db != null) {
            db.deleteTask(id)
            reloadFromDatabase()
        } else {
            tasks = tasks.filterNot { it.id == id }
            completion = completion - id
        }
    }

    fun addStudyPlan(courseId: String, title: String, detail: String): Boolean {
        if (title.isBlank() || title.trim().length > 200) return false
        val db = database
        if (db != null) {
            return runCatching {
                db.addStudyPlan(courseId.trim(), title.trim(), detail.trim())
                reloadFromDatabase()
                true
            }.getOrDefault(false)
        } else {
            val row = StudyPlanRow(UUID.randomUUID().toString(), courseId.trim(), title.trim(), detail.trim(), false)
            studyPlans = studyPlans + row
            return true
        }
    }

    fun toggleStudyPlan(id: String) {
        val db = database
        if (db != null) {
            db.toggleStudyPlan(id)
            reloadFromDatabase()
        } else {
            studyPlans = studyPlans.map { if (it.id == id) it.copy(completed = !it.completed) else it }
        }
    }

    fun deleteStudyPlan(id: String) {
        val db = database
        if (db != null) {
            db.deleteStudyPlan(id)
            reloadFromDatabase()
        } else {
            studyPlans = studyPlans.filterNot { it.id == id }
        }
    }

    val activeTaskCount: Int
        get() = currentCourses().sumOf { totalFor(it.id) - completedFor(it.id) }
}
