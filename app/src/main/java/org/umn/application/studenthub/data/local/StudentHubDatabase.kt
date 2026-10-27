package org.umn.application.studenthub.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate
import java.util.UUID

class StudentHubDatabase private constructor(private val context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE profile (
            id INTEGER PRIMARY KEY CHECK(id=1),
            name TEXT NOT NULL,
            program TEXT NOT NULL,
            semester INTEGER NOT NULL CHECK(semester BETWEEN 1 AND 20),
            prior_ipk REAL NOT NULL DEFAULT 0.0 CHECK(prior_ipk BETWEEN 0.0 AND 4.0),
            prior_sks INTEGER NOT NULL DEFAULT 0 CHECK(prior_sks >= 0),
            onboarding_completed INTEGER NOT NULL DEFAULT 0 CHECK(onboarding_completed IN (0,1)))""")
        db.execSQL("INSERT INTO profile(id,name,program,semester,prior_ipk,prior_sks,onboarding_completed) VALUES(1,'','',1,0.0,0,0)")

        db.execSQL("""CREATE TABLE courses (
            id TEXT PRIMARY KEY NOT NULL,
            code TEXT NOT NULL CHECK(length(trim(code)) BETWEEN 1 AND 30),
            title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 200),
            sks INTEGER NOT NULL CHECK(sks BETWEEN 1 AND 24),
            semester INTEGER NOT NULL CHECK(semester BETWEEN 1 AND 20),
            score INTEGER,
            letter TEXT,
            is_demo INTEGER NOT NULL DEFAULT 0 CHECK(is_demo IN (0,1)),
            lecturer TEXT NOT NULL DEFAULT '',
            room TEXT NOT NULL DEFAULT '',
            schedule TEXT NOT NULL DEFAULT '',
            description TEXT NOT NULL DEFAULT '',
            CHECK((score IS NULL AND letter IS NULL) OR
                (score IS NOT NULL AND letter IS NOT NULL AND score BETWEEN 0 AND 100 AND letter IN ('A','A-','B+','B','B-','C+','C','D','E'))),
            UNIQUE(code,semester))""")

        db.execSQL("""CREATE TABLE grades (
            course_id TEXT PRIMARY KEY NOT NULL,
            score INTEGER NOT NULL CHECK(score BETWEEN 0 AND 100),
            letter TEXT NOT NULL CHECK(letter IN ('A','A-','B+','B','B-','C+','C','D','E')))""")

        db.execSQL("CREATE TABLE academic_goal (id INTEGER PRIMARY KEY CHECK(id=1), goal REAL NOT NULL CHECK(goal BETWEEN 0 AND 4))")
        db.execSQL("INSERT INTO academic_goal(id,goal) VALUES(1,0.0)")

        db.execSQL("""CREATE TABLE agenda (
            id TEXT PRIMARY KEY NOT NULL,
            day INTEGER NOT NULL CHECK(day BETWEEN 0 AND 6),
            time TEXT NOT NULL CHECK(length(time)=5 AND time GLOB '[0-2][0-9].[0-5][0-9]' AND substr(time,1,2) <= '23'),
            title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 200),
            kind TEXT NOT NULL DEFAULT 'AGENDA',
            detail TEXT NOT NULL DEFAULT '',
            course_id TEXT NOT NULL DEFAULT '')""")

        db.execSQL("""CREATE TABLE tasks (
            id TEXT PRIMARY KEY NOT NULL,
            course_id TEXT NOT NULL CHECK(length(trim(course_id)) BETWEEN 1 AND 30),
            title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 200),
            detail TEXT NOT NULL DEFAULT '',
            deadline TEXT NOT NULL DEFAULT '',
            completed INTEGER NOT NULL DEFAULT 0 CHECK(completed IN (0,1)),
            reminder_enabled INTEGER NOT NULL DEFAULT 0 CHECK(reminder_enabled IN (0,1)))""")

        db.execSQL("""CREATE TABLE notes (
            id TEXT PRIMARY KEY NOT NULL,
            title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 200),
            course TEXT NOT NULL CHECK(length(trim(course)) BETWEEN 1 AND 50),
            body TEXT NOT NULL CHECK(length(body) <= 100000),
            updated_at INTEGER NOT NULL)""")

        db.execSQL("""CREATE TABLE transactions (
            id TEXT PRIMARY KEY NOT NULL CHECK(length(id)>0),
            title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 200),
            amount INTEGER NOT NULL CHECK(amount BETWEEN 1 AND 1000000000000),
            category TEXT NOT NULL CHECK(category IN ('Makan','Transportasi','Kuliah','Kos','Lainnya')),
            income INTEGER NOT NULL CHECK(income IN (0,1)),
            date TEXT NOT NULL CHECK(length(date)=10))""")

        db.execSQL("""CREATE TABLE budgets (
            category TEXT PRIMARY KEY NOT NULL CHECK(category IN ('Makan','Transportasi','Kuliah','Kos','Lainnya')),
            amount INTEGER NOT NULL CHECK(amount BETWEEN 1 AND 1000000000000))""")

        db.execSQL("""CREATE TABLE reminders (
            id TEXT PRIMARY KEY NOT NULL,
            title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 200),
            detail TEXT NOT NULL DEFAULT '',
            time TEXT NOT NULL CHECK(length(time)=5 AND time GLOB '[0-2][0-9].[0-5][0-9]' AND substr(time,1,2) <= '23'),
            category TEXT NOT NULL CHECK(category IN ('Kelas','Tugas','Keuangan','Belajar','Umum')),
            enabled INTEGER NOT NULL DEFAULT 1 CHECK(enabled IN (0,1)),
            source_id TEXT NOT NULL DEFAULT '')""")

        db.execSQL("""CREATE TABLE study_plans (
            id TEXT PRIMARY KEY NOT NULL,
            course_id TEXT NOT NULL DEFAULT '',
            title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 200),
            detail TEXT NOT NULL DEFAULT '',
            completed INTEGER NOT NULL DEFAULT 0 CHECK(completed IN (0,1)))""")

        ensureAiTables(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            ensureAiTables(db)
        }
    }

    private fun ensureAiTables(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS ai_chat_sessions (
            id TEXT PRIMARY KEY NOT NULL,
            note_id TEXT NOT NULL DEFAULT '',
            course_id TEXT NOT NULL DEFAULT '',
            title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 200),
            updated_at INTEGER NOT NULL)""")

        db.execSQL("""CREATE TABLE IF NOT EXISTS ai_chat_messages (
            id TEXT PRIMARY KEY NOT NULL,
            session_id TEXT NOT NULL,
            sender TEXT NOT NULL CHECK(sender IN ('USER','AI')),
            text TEXT NOT NULL,
            quote_snippet TEXT NOT NULL DEFAULT '',
            created_at INTEGER NOT NULL)""")

        db.execSQL("""CREATE TABLE IF NOT EXISTS ai_study_progress (
            note_id TEXT PRIMARY KEY NOT NULL,
            mastered_count INTEGER NOT NULL DEFAULT 0,
            total_count INTEGER NOT NULL DEFAULT 0,
            canvas_score INTEGER NOT NULL DEFAULT 0,
            updated_at INTEGER NOT NULL)""")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS profile (
                id INTEGER PRIMARY KEY CHECK(id=1), name TEXT NOT NULL, program TEXT NOT NULL,
                semester INTEGER NOT NULL CHECK(semester BETWEEN 1 AND 20))""")
            db.execSQL("INSERT OR IGNORE INTO profile(id,name,program,semester) VALUES(1,'','',1)")
            db.execSQL("""CREATE TABLE IF NOT EXISTS courses (
                id TEXT PRIMARY KEY NOT NULL, code TEXT NOT NULL CHECK(length(trim(code)) BETWEEN 1 AND 30),
                title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 200),
                sks INTEGER NOT NULL CHECK(sks BETWEEN 1 AND 24),
                semester INTEGER NOT NULL CHECK(semester BETWEEN 1 AND 20),
                score INTEGER, letter TEXT, is_demo INTEGER NOT NULL DEFAULT 0 CHECK(is_demo IN (0,1)),
                CHECK((score IS NULL AND letter IS NULL) OR
                    (score IS NOT NULL AND letter IS NOT NULL AND score BETWEEN 0 AND 100 AND letter IN ('A','A-','B+','B','B-','C+','C','D','E'))),
                UNIQUE(code,semester))""")
        }
        if (oldVersion < 3) {
            runCatching { db.execSQL("ALTER TABLE profile ADD COLUMN prior_ipk REAL NOT NULL DEFAULT 0.0") }
            runCatching { db.execSQL("ALTER TABLE profile ADD COLUMN prior_sks INTEGER NOT NULL DEFAULT 0") }
            runCatching { db.execSQL("ALTER TABLE profile ADD COLUMN onboarding_completed INTEGER NOT NULL DEFAULT 0") }
            runCatching { db.execSQL("ALTER TABLE courses ADD COLUMN lecturer TEXT NOT NULL DEFAULT ''") }
            runCatching { db.execSQL("ALTER TABLE courses ADD COLUMN room TEXT NOT NULL DEFAULT ''") }
            runCatching { db.execSQL("ALTER TABLE courses ADD COLUMN schedule TEXT NOT NULL DEFAULT ''") }
            runCatching { db.execSQL("ALTER TABLE courses ADD COLUMN description TEXT NOT NULL DEFAULT ''") }
            runCatching { db.execSQL("ALTER TABLE agenda ADD COLUMN kind TEXT NOT NULL DEFAULT 'AGENDA'") }
            runCatching { db.execSQL("ALTER TABLE agenda ADD COLUMN detail TEXT NOT NULL DEFAULT ''") }
            runCatching { db.execSQL("ALTER TABLE agenda ADD COLUMN course_id TEXT NOT NULL DEFAULT ''") }

            db.execSQL("""CREATE TABLE IF NOT EXISTS tasks (
                id TEXT PRIMARY KEY NOT NULL,
                course_id TEXT NOT NULL CHECK(length(trim(course_id)) BETWEEN 1 AND 30),
                title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 200),
                detail TEXT NOT NULL DEFAULT '',
                deadline TEXT NOT NULL DEFAULT '',
                completed INTEGER NOT NULL DEFAULT 0 CHECK(completed IN (0,1)),
                reminder_enabled INTEGER NOT NULL DEFAULT 0 CHECK(reminder_enabled IN (0,1)))""")

            db.execSQL("""CREATE TABLE IF NOT EXISTS notes (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 200),
                course TEXT NOT NULL CHECK(length(trim(course)) BETWEEN 1 AND 50),
                body TEXT NOT NULL CHECK(length(body) <= 100000),
                updated_at INTEGER NOT NULL)""")

            db.execSQL("""CREATE TABLE IF NOT EXISTS reminders (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 200),
                detail TEXT NOT NULL DEFAULT '',
                time TEXT NOT NULL CHECK(length(time)=5 AND time GLOB '[0-2][0-9].[0-5][0-9]' AND substr(time,1,2) <= '23'),
                category TEXT NOT NULL CHECK(category IN ('Kelas','Tugas','Keuangan','Belajar','Umum')),
                enabled INTEGER NOT NULL DEFAULT 1 CHECK(enabled IN (0,1)),
                source_id TEXT NOT NULL DEFAULT '')""")

            db.execSQL("""CREATE TABLE IF NOT EXISTS study_plans (
                id TEXT PRIMARY KEY NOT NULL,
                course_id TEXT NOT NULL DEFAULT '',
                title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 200),
                detail TEXT NOT NULL DEFAULT '',
                completed INTEGER NOT NULL DEFAULT 0 CHECK(completed IN (0,1)))""")

            runCatching {
                db.delete("agenda", "id LIKE 'demo-%' OR id LIKE 'legacy-%'", null)
                db.delete("courses", "is_demo = 1", null)
            }
        }
        ensureAiTables(db)
    }

    fun profile(): ProfileRow = readableDatabase.rawQuery(
        "SELECT name,program,semester,prior_ipk,prior_sks,onboarding_completed FROM profile WHERE id=1",
        null
    ).use {
        check(it.moveToFirst())
        ProfileRow(
            name = it.getString(0),
            program = it.getString(1),
            semester = it.getInt(2),
            priorIpk = it.getDouble(3),
            priorSks = it.getInt(4),
            onboardingCompleted = it.getInt(5) == 1
        )
    }

    fun saveProfile(row: ProfileRow) {
        require(
            row.name.isNotBlank() && row.name.length <= 100 &&
            row.program.isNotBlank() && row.program.length <= 100 &&
            row.semester in 1..20 &&
            row.priorIpk.isFinite() && row.priorIpk in 0.0..4.0 &&
            row.priorSks >= 0
        ) { "Isi nama, program studi, dan semester 1–20." }
        check(writableDatabase.update("profile", ContentValues().apply {
            put("name", row.name.trim())
            put("program", row.program.trim())
            put("semester", row.semester)
            put("prior_ipk", row.priorIpk)
            put("prior_sks", row.priorSks)
            put("onboarding_completed", if (row.onboardingCompleted) 1 else 0)
        }, "id=1", null) == 1)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        writableDatabase.update("profile", ContentValues().apply {
            put("onboarding_completed", if (completed) 1 else 0)
        }, "id=1", null)
    }

    fun clearProfile() {
        writableDatabase.update("profile", ContentValues().apply {
            put("name", "")
            put("program", "")
            put("semester", 1)
            put("prior_ipk", 0.0)
            put("prior_sks", 0)
            put("onboarding_completed", 0)
        }, "id=1", null)
    }

    fun resetAllData() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("courses", null, null)
            db.delete("grades", null, null)
            db.delete("agenda", null, null)
            db.delete("tasks", null, null)
            db.delete("notes", null, null)
            db.delete("transactions", null, null)
            db.delete("budgets", null, null)
            db.delete("reminders", null, null)
            db.delete("study_plans", null, null)
            db.delete("ai_chat_messages", null, null)
            db.delete("ai_chat_sessions", null, null)
            db.delete("ai_study_progress", null, null)
            db.execSQL("INSERT OR REPLACE INTO academic_goal(id,goal) VALUES(1,0.0)")
            db.update("profile", ContentValues().apply {
                put("name", "")
                put("program", "")
                put("semester", 1)
                put("prior_ipk", 0.0)
                put("prior_sks", 0)
                put("onboarding_completed", 0)
            }, "id=1", null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun isOnboardingCompleted(): Boolean {
        return runCatching {
            val p = profile()
            p.onboardingCompleted || p.name.isNotBlank()
        }.getOrDefault(false)
    }


    fun courses(): List<CourseRow> = readableDatabase.rawQuery(
        "SELECT id,code,title,sks,semester,score,letter,is_demo,lecturer,room,schedule,description FROM courses ORDER BY semester, rowid",
        null
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    CourseRow(
                        id = cursor.getString(0),
                        code = cursor.getString(1),
                        title = cursor.getString(2),
                        sks = cursor.getInt(3),
                        semester = cursor.getInt(4),
                        grade = if (cursor.isNull(5)) null else GradeRow(cursor.getInt(5), cursor.getString(6)),
                        isDemo = cursor.getInt(7) == 1,
                        lecturer = cursor.getString(8) ?: "",
                        room = cursor.getString(9) ?: "",
                        schedule = cursor.getString(10) ?: "",
                        description = cursor.getString(11) ?: ""
                    )
                )
            }
        }
    }

    fun saveCourse(row: CourseRow) {
        require(
            row.id.isNotBlank() &&
            row.code.isNotBlank() && row.code.length <= 30 &&
            row.title.isNotBlank() && row.title.length <= 200 &&
            row.sks in 1..24 && row.semester in 1..20
        ) { "Isi kode, nama mata kuliah, SKS 1-24, dan semester 1-20." }
        require(row.grade == null || (row.grade.score in 0..100 && row.grade.letter in LETTERS)) {
            "Nilai harus 0-100 dengan huruf A sampai E."
        }
        val values = ContentValues().apply {
            put("id", row.id.trim())
            put("code", row.code.trim())
            put("title", row.title.trim())
            put("sks", row.sks)
            put("semester", row.semester)
            put("score", row.grade?.score)
            put("letter", row.grade?.letter)
            put("is_demo", if (row.isDemo) 1 else 0)
            put("lecturer", row.lecturer.trim())
            put("room", row.room.trim())
            put("schedule", row.schedule.trim())
            put("description", row.description.trim())
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val updatedById = db.update("courses", values, "id=?", arrayOf(row.id.trim()))
            if (updatedById == 0) {
                val updatedByCodeSem = db.update("courses", values, "code=? AND semester=?", arrayOf(row.code.trim(), row.semester.toString()))
                if (updatedByCodeSem == 0) {
                    db.insertOrThrow("courses", null, values)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteCourse(id: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val course = courses().firstOrNull { it.id == id || it.code == id }
            db.delete("courses", "id=? OR code=?", arrayOf(id, id))
            if (course != null) {
                db.delete("tasks", "course_id=?", arrayOf(course.code))
                db.delete("agenda", "course_id=?", arrayOf(course.code))
                db.delete("study_plans", "course_id=?", arrayOf(course.code))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun grades(): Map<String, GradeRow> = readableDatabase.query(
        "courses", arrayOf("code", "score", "letter"), "score IS NOT NULL AND letter IS NOT NULL", null, null, null, "rowid"
    ).use { cursor ->
        buildMap {
            while (cursor.moveToNext()) {
                put(cursor.getString(0), GradeRow(cursor.getInt(1), cursor.getString(2)))
            }
        }
    }

    fun saveGrade(course: String, score: Int, letter: String) {
        require(course in courses().map { it.code }) { "Mata kuliah tidak ditemukan." }
        require(score in 0..100 && letter in LETTERS) { "Nilai tidak valid." }
        check(writableDatabase.update("courses", ContentValues().apply {
            put("score", score)
            put("letter", letter)
        }, "code=?", arrayOf(course)) > 0) { "Nilai tidak ditemukan." }
    }

    fun goal(): Double = readableDatabase.rawQuery("SELECT goal FROM academic_goal WHERE id=1", null).use {
        if (it.moveToFirst()) it.getDouble(0) else 0.0
    }

    fun saveGoal(goal: Double) {
        require(goal.isFinite() && goal in 0.0..4.0)
        writableDatabase.execSQL("INSERT OR REPLACE INTO academic_goal(id,goal) VALUES(1,?)", arrayOf(goal))
    }


    fun agenda(day: Int): List<AgendaRow> = readableDatabase.query(
        "agenda",
        arrayOf("id", "day", "time", "title", "kind", "detail", "course_id"),
        "day=?",
        arrayOf(day.toString()),
        null,
        null,
        "time, id"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    AgendaRow(
                        id = cursor.getString(0),
                        day = cursor.getInt(1),
                        time = cursor.getString(2),
                        title = cursor.getString(3),
                        kind = cursor.getString(4) ?: "AGENDA",
                        detail = cursor.getString(5) ?: "",
                        courseId = cursor.getString(6) ?: ""
                    )
                )
            }
        }
    }

    fun allAgendas(): List<AgendaRow> = (0..6).flatMap(::agenda)

    fun addAgenda(
        day: Int,
        time: String,
        title: String,
        kind: String = "AGENDA",
        detail: String = "",
        courseId: String = ""
    ): AgendaRow {
        val normalizedTime = normalizeTimeFormat(time)
        validateAgenda(day, normalizedTime, title)
        val row = AgendaRow(
            id = UUID.randomUUID().toString(),
            day = day,
            time = normalizedTime,
            title = title.trim(),
            kind = kind.trim().ifBlank { "AGENDA" },
            detail = detail.trim(),
            courseId = courseId.trim()
        )
        insertAgenda(writableDatabase, row)
        return row
    }

    private fun insertAgenda(db: SQLiteDatabase, row: AgendaRow) {
        db.insertOrThrow("agenda", null, ContentValues().apply {
            put("id", row.id)
            put("day", row.day)
            put("time", row.time)
            put("title", row.title)
            put("kind", row.kind)
            put("detail", row.detail)
            put("course_id", row.courseId)
        })
    }

    fun deleteAgenda(id: String) {
        writableDatabase.delete("agenda", "id=?", arrayOf(id))
    }



    fun tasks(courseId: String? = null): List<TaskRow> = readableDatabase.query(
        "tasks",
        arrayOf("id", "course_id", "title", "detail", "deadline", "completed", "reminder_enabled"),
        if (courseId != null) "course_id=?" else null,
        if (courseId != null) arrayOf(courseId) else null,
        null,
        null,
        "completed ASC, rowid DESC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    TaskRow(
                        id = cursor.getString(0),
                        courseId = cursor.getString(1),
                        title = cursor.getString(2),
                        detail = cursor.getString(3) ?: "",
                        deadline = cursor.getString(4) ?: "",
                        completed = cursor.getInt(5) == 1,
                        reminderEnabled = cursor.getInt(6) == 1
                    )
                )
            }
        }
    }

    fun addTask(
        courseId: String,
        title: String,
        detail: String = "",
        deadline: String = "",
        reminderEnabled: Boolean = false
    ): TaskRow {
        require(courseId.isNotBlank() && courseId.trim().length <= 30) { "Kode mata kuliah wajib diisi." }
        require(title.isNotBlank() && title.trim().length <= 200) { "Judul tugas wajib diisi (maks. 200 karakter)." }
        val row = TaskRow(
            id = UUID.randomUUID().toString(),
            courseId = courseId.trim(),
            title = title.trim(),
            detail = detail.trim(),
            deadline = deadline.trim(),
            completed = false,
            reminderEnabled = reminderEnabled
        )
        writableDatabase.insertOrThrow("tasks", null, ContentValues().apply {
            put("id", row.id)
            put("course_id", row.courseId)
            put("title", row.title)
            put("detail", row.detail)
            put("deadline", row.deadline)
            put("completed", 0)
            put("reminder_enabled", if (row.reminderEnabled) 1 else 0)
        })
        return row
    }

    fun toggleTask(id: String): Boolean {
        val current = tasks().firstOrNull { it.id == id } ?: return false
        val next = !current.completed
        writableDatabase.update("tasks", ContentValues().apply {
            put("completed", if (next) 1 else 0)
        }, "id=?", arrayOf(id))
        return next
    }

    fun setTaskReminder(id: String, enabled: Boolean) {
        writableDatabase.update("tasks", ContentValues().apply {
            put("reminder_enabled", if (enabled) 1 else 0)
        }, "id=?", arrayOf(id))
    }

    fun deleteTask(id: String) {
        writableDatabase.delete("tasks", "id=?", arrayOf(id))
        deleteReminderBySource("task:$id")
    }

    fun notes(): List<NoteRow> = readableDatabase.query(
        "notes",
        arrayOf("id", "title", "course", "body", "updated_at"),
        null,
        null,
        null,
        null,
        "updated_at DESC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    NoteRow(
                        id = cursor.getString(0),
                        title = cursor.getString(1),
                        course = cursor.getString(2),
                        body = cursor.getString(3),
                        updatedAt = cursor.getLong(4)
                    )
                )
            }
        }
    }

    fun saveNote(id: String, title: String, course: String, body: String): NoteRow {
        require(title.isNotBlank()) { "Judul catatan wajib diisi." }
        require(title.trim().length <= 200 && body.length <= 100000) { "Catatan terlalu panjang." }
        require(course.isNotBlank() && course.trim().length <= 50) { "Mata kuliah wajib dipilih." }
        val noteId = id.ifBlank { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()
        val row = NoteRow(noteId, title.trim(), course.trim(), body, now)
        val values = ContentValues().apply {
            put("id", row.id)
            put("title", row.title)
            put("course", row.course)
            put("body", row.body)
            put("updated_at", row.updatedAt)
        }
        val db = writableDatabase
        if (db.update("notes", values, "id=?", arrayOf(row.id)) == 0) {
            db.insertOrThrow("notes", null, values)
        }
        return row
    }

    fun deleteNote(id: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val sessions = aiChatSessions(id)
            sessions.forEach { session ->
                db.delete("ai_chat_messages", "session_id=?", arrayOf(session.id))
            }
            db.delete("ai_chat_sessions", "note_id=?", arrayOf(id))
            db.delete("ai_study_progress", "note_id=?", arrayOf(id))
            db.delete("notes", "id=?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }


    fun transactions(): List<TransactionRow> = readableDatabase.query(
        "transactions", arrayOf("id", "title", "amount", "category", "income", "date"), null, null, null, null, "rowid"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    TransactionRow(
                        id = cursor.getString(0),
                        title = cursor.getString(1),
                        amount = cursor.getLong(2),
                        category = cursor.getString(3),
                        income = cursor.getInt(4) != 0,
                        date = cursor.getString(5)
                    )
                )
            }
        }
    }

    fun addTransaction(row: TransactionRow) {
        validateTransaction(row)
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.rawQuery("SELECT count(*) FROM transactions", null).use {
                check(it.moveToFirst())
                require(it.getInt(0) < 10000) { "Batas transaksi lokal tercapai." }
            }
            insertTransaction(db, row)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun insertTransaction(db: SQLiteDatabase, row: TransactionRow) {
        db.insertOrThrow("transactions", null, ContentValues().apply {
            put("id", row.id)
            put("title", row.title)
            put("amount", row.amount)
            put("category", row.category)
            put("income", if (row.income) 1 else 0)
            put("date", row.date)
        })
    }

    fun deleteTransaction(id: String) {
        writableDatabase.delete("transactions", "id=?", arrayOf(id))
    }

    fun budgets(): Map<String, Long> {
        val saved = readableDatabase.query(
            "budgets", arrayOf("category", "amount"), null, null, null, null, "rowid"
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    put(cursor.getString(0), cursor.getLong(1))
                }
            }
        }
        return linkedMapOf<String, Long>().apply {
            BUDGET_CATEGORIES.forEach { cat ->
                put(cat, saved[cat] ?: 0L)
            }
        }
    }

    fun saveBudgets(values: Map<String, Long>) {
        values.forEach { (category, amount) -> validateBudget(category, amount) }
        val db = writableDatabase
        db.beginTransaction()
        try {
            values.forEach { (category, amount) ->
                val cv = ContentValues().apply {
                    put("category", category)
                    put("amount", amount)
                }
                if (db.update("budgets", cv, "category=?", arrayOf(category)) == 0) {
                    db.insertOrThrow("budgets", null, cv)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }


    fun reminders(): List<ReminderItemRow> = readableDatabase.query(
        "reminders",
        arrayOf("id", "title", "detail", "time", "category", "enabled", "source_id"),
        null,
        null,
        null,
        null,
        "time ASC, rowid DESC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    ReminderItemRow(
                        id = cursor.getString(0),
                        title = cursor.getString(1),
                        detail = cursor.getString(2) ?: "",
                        time = cursor.getString(3),
                        category = cursor.getString(4) ?: "Umum",
                        enabled = cursor.getInt(5) == 1,
                        sourceId = cursor.getString(6) ?: ""
                    )
                )
            }
        }
    }

    fun addReminder(
        title: String,
        detail: String,
        time: String,
        category: String = "Umum",
        enabled: Boolean = true,
        sourceId: String = ""
    ): ReminderItemRow {
        val normalizedTime = normalizeSingleTime(time.substringBefore('–').substringBefore('-'))
        require(title.isNotBlank() && title.trim().length <= 200) { "Judul pengingat wajib diisi." }
        require(SINGLE_TIME_REGEX.matches(normalizedTime)) { "Waktu pengingat harus berformat HH.mm (00.00-23.59)." }
        require(category in REMINDER_CATEGORIES) { "Kategori pengingat tidak valid." }

        val row = ReminderItemRow(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            detail = detail.trim(),
            time = normalizedTime,
            category = category,
            enabled = enabled,
            sourceId = sourceId
        )
        writableDatabase.insertOrThrow("reminders", null, ContentValues().apply {
            put("id", row.id)
            put("title", row.title)
            put("detail", row.detail)
            put("time", row.time)
            put("category", row.category)
            put("enabled", if (row.enabled) 1 else 0)
            put("source_id", row.sourceId)
        })
        runCatching { ReminderScheduler.scheduleReminder(context, row) }
        return row
    }

    fun toggleReminder(id: String, enabled: Boolean) {
        writableDatabase.update("reminders", ContentValues().apply {
            put("enabled", if (enabled) 1 else 0)
        }, "id=?", arrayOf(id))
        runCatching {
            val updated = reminders().firstOrNull { it.id == id }
            if (updated != null) {
                ReminderScheduler.scheduleReminder(context, updated)
            }
        }
    }

    fun deleteReminder(id: String) {
        writableDatabase.delete("reminders", "id=?", arrayOf(id))
        runCatching { ReminderScheduler.cancelReminder(context, id) }
    }

    fun deleteReminderBySource(sourceId: String) {
        if (sourceId.isBlank()) return
        val matching = reminders().filter { it.sourceId == sourceId }
        writableDatabase.delete("reminders", "source_id=?", arrayOf(sourceId))
        matching.forEach { rem ->
            runCatching { ReminderScheduler.cancelReminder(context, rem.id) }
        }
    }


    fun studyPlans(): List<StudyPlanRow> = readableDatabase.query(
        "study_plans",
        arrayOf("id", "course_id", "title", "detail", "completed"),
        null,
        null,
        null,
        null,
        "completed ASC, rowid DESC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    StudyPlanRow(
                        id = cursor.getString(0),
                        courseId = cursor.getString(1) ?: "",
                        title = cursor.getString(2),
                        detail = cursor.getString(3) ?: "",
                        completed = cursor.getInt(4) == 1
                    )
                )
            }
        }
    }

    fun addStudyPlan(courseId: String, title: String, detail: String): StudyPlanRow {
        require(title.isNotBlank() && title.trim().length <= 200) { "Judul rencana belajar wajib diisi." }
        val row = StudyPlanRow(
            id = UUID.randomUUID().toString(),
            courseId = courseId.trim(),
            title = title.trim(),
            detail = detail.trim(),
            completed = false
        )
        writableDatabase.insertOrThrow("study_plans", null, ContentValues().apply {
            put("id", row.id)
            put("course_id", row.courseId)
            put("title", row.title)
            put("detail", row.detail)
            put("completed", 0)
        })
        return row
    }

    fun toggleStudyPlan(id: String): Boolean {
        val current = studyPlans().firstOrNull { it.id == id } ?: return false
        val next = !current.completed
        writableDatabase.update("study_plans", ContentValues().apply {
            put("completed", if (next) 1 else 0)
        }, "id=?", arrayOf(id))
        return next
    }

    fun deleteStudyPlan(id: String) {
        writableDatabase.delete("study_plans", "id=?", arrayOf(id))
    }

    fun aiChatSessions(noteId: String? = null): List<AiChatSessionRow> = readableDatabase.query(
        "ai_chat_sessions",
        arrayOf("id", "note_id", "course_id", "title", "updated_at"),
        if (!noteId.isNullOrBlank()) "note_id=?" else null,
        if (!noteId.isNullOrBlank()) arrayOf(noteId) else null,
        null,
        null,
        "updated_at DESC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    AiChatSessionRow(
                        id = cursor.getString(0),
                        noteId = cursor.getString(1) ?: "",
                        courseId = cursor.getString(2) ?: "",
                        title = cursor.getString(3),
                        updatedAt = cursor.getLong(4)
                    )
                )
            }
        }
    }

    fun createAiChatSession(noteId: String, courseId: String, title: String): AiChatSessionRow {
        val cleanTitle = title.trim().take(200).ifBlank { "Diskusi Catatan" }
        val now = System.currentTimeMillis()
        val row = AiChatSessionRow(
            id = UUID.randomUUID().toString(),
            noteId = noteId.trim(),
            courseId = courseId.trim(),
            title = cleanTitle,
            updatedAt = now
        )
        writableDatabase.insertOrThrow("ai_chat_sessions", null, ContentValues().apply {
            put("id", row.id)
            put("note_id", row.noteId)
            put("course_id", row.courseId)
            put("title", row.title)
            put("updated_at", row.updatedAt)
        })
        return row
    }

    fun deleteAiChatSession(sessionId: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("ai_chat_messages", "session_id=?", arrayOf(sessionId))
            db.delete("ai_chat_sessions", "id=?", arrayOf(sessionId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun aiChatMessages(sessionId: String): List<AiChatMessageRow> = readableDatabase.query(
        "ai_chat_messages",
        arrayOf("id", "session_id", "sender", "text", "quote_snippet", "created_at"),
        "session_id=?",
        arrayOf(sessionId),
        null,
        null,
        "created_at ASC, rowid ASC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    AiChatMessageRow(
                        id = cursor.getString(0),
                        sessionId = cursor.getString(1),
                        sender = cursor.getString(2),
                        text = cursor.getString(3),
                        quoteSnippet = cursor.getString(4) ?: "",
                        createdAt = cursor.getLong(5)
                    )
                )
            }
        }
    }

    fun addAiChatMessage(
        sessionId: String,
        sender: String,
        text: String,
        quoteSnippet: String = ""
    ): AiChatMessageRow {
        val validSender = if (sender == "USER") "USER" else "AI"
        val now = System.currentTimeMillis()
        val row = AiChatMessageRow(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId.trim(),
            sender = validSender,
            text = text.trim(),
            quoteSnippet = quoteSnippet.trim(),
            createdAt = now
        )
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertOrThrow("ai_chat_messages", null, ContentValues().apply {
                put("id", row.id)
                put("session_id", row.sessionId)
                put("sender", row.sender)
                put("text", row.text)
                put("quote_snippet", row.quoteSnippet)
                put("created_at", row.createdAt)
            })
            db.update("ai_chat_sessions", ContentValues().apply {
                put("updated_at", now)
            }, "id=?", arrayOf(row.sessionId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return row
    }

    fun aiStudyProgress(noteId: String): AiStudyProgressRow? = readableDatabase.query(
        "ai_study_progress",
        arrayOf("note_id", "mastered_count", "total_count", "canvas_score", "updated_at"),
        "note_id=?",
        arrayOf(noteId),
        null,
        null,
        null
    ).use { cursor ->
        if (cursor.moveToFirst()) {
            AiStudyProgressRow(
                noteId = cursor.getString(0),
                masteredCount = cursor.getInt(1),
                totalCount = cursor.getInt(2),
                canvasScore = cursor.getInt(3),
                updatedAt = cursor.getLong(4)
            )
        } else null
    }

    fun allAiStudyProgress(): List<AiStudyProgressRow> = readableDatabase.query(
        "ai_study_progress",
        arrayOf("note_id", "mastered_count", "total_count", "canvas_score", "updated_at"),
        null,
        null,
        null,
        null,
        "updated_at DESC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    AiStudyProgressRow(
                        noteId = cursor.getString(0),
                        masteredCount = cursor.getInt(1),
                        totalCount = cursor.getInt(2),
                        canvasScore = cursor.getInt(3),
                        updatedAt = cursor.getLong(4)
                    )
                )
            }
        }
    }

    fun saveAiStudyProgress(
        noteId: String,
        masteredCount: Int,
        totalCount: Int,
        canvasScore: Int
    ) {
        if (noteId.isBlank()) return
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("note_id", noteId.trim())
            put("mastered_count", masteredCount.coerceAtLeast(0))
            put("total_count", totalCount.coerceAtLeast(0))
            put("canvas_score", canvasScore.coerceIn(0, 100))
            put("updated_at", now)
        }
        val db = writableDatabase
        if (db.update("ai_study_progress", cv, "note_id=?", arrayOf(noteId.trim())) == 0) {
            db.insertOrThrow("ai_study_progress", null, cv)
        }
    }

    companion object {
        const val NAME = "studenthub.db"
        const val VERSION = 3
        val LETTERS = setOf("A", "A-", "B+", "B", "B-", "C+", "C", "D", "E")
        val BUDGET_CATEGORIES = listOf("Makan", "Transportasi", "Kuliah", "Kos", "Lainnya")
        val REMINDER_CATEGORIES = setOf("Kelas", "Tugas", "Keuangan", "Belajar", "Umum")

        @Volatile private var instance: StudentHubDatabase? = null
        fun get(context: Context): StudentHubDatabase = instance ?: synchronized(this) {
            instance ?: StudentHubDatabase(context.applicationContext).also { instance = it }
        }
        fun resetForTests() = synchronized(this) { instance?.close(); instance = null }

        private val SINGLE_TIME_REGEX = Regex("(?:[01][0-9]|2[0-3])\\.[0-5][0-9]")
        private val TIME_OR_RANGE_REGEX = Regex("(?:[01][0-9]|2[0-3])\\.[0-5][0-9](?:–(?:[01][0-9]|2[0-3])\\.[0-5][0-9])?")

        private fun normalizeSingleTime(raw: String): String {
            val clean = raw.trim().replace(':', '.')
            val parts = clean.split('.')
            if (parts.size == 2) {
                val h = parts[0].toIntOrNull()
                val m = parts[1].toIntOrNull()
                if (h != null && m != null && h in 0..23 && m in 0..59) {
                    return String.format("%02d.%02d", h, m)
                }
            } else if (parts.size == 1) {
                val h = parts[0].toIntOrNull()
                if (h != null && h in 0..23) {
                    return String.format("%02d.00", h)
                }
            }
            return clean
        }

        fun normalizeTimeFormat(raw: String): String {
            val trimmed = raw.trim()
            val rangeParts = trimmed.split(Regex("\\s*(?:[–-]|s/?d|sampai|to)\\s*", RegexOption.IGNORE_CASE))
            if (rangeParts.size == 2 && rangeParts[0].isNotBlank() && rangeParts[1].isNotBlank()) {
                val start = normalizeSingleTime(rangeParts[0])
                val end = normalizeSingleTime(rangeParts[1])
                if (SINGLE_TIME_REGEX.matches(start) && SINGLE_TIME_REGEX.matches(end)) {
                    return if (start == end) start else "$start–$end"
                }
            }
            return normalizeSingleTime(trimmed)
        }

        private fun validateAgenda(day: Int, time: String, title: String) {
            require(day in 0..6 && title.isNotBlank() && title.length <= 200 && TIME_OR_RANGE_REGEX.matches(time)) {
                "Judul, hari, atau waktu agenda tidak valid."
            }
        }
        private fun validateBudget(category: String, amount: Long) {
            require(category in BUDGET_CATEGORIES && amount in 1..1_000_000_000_000L) { "Anggaran tidak valid." }
        }
        private fun validateTransaction(row: TransactionRow) {
            require(row.id.isNotBlank() && row.title.isNotBlank() && row.title.length <= 200)
            require(row.amount in 1..1_000_000_000_000L && row.category in BUDGET_CATEGORIES)
            require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(row.date) && runCatching { LocalDate.parse(row.date) }.isSuccess)
        }
    }
}

data class AgendaRow(
    val id: String,
    val day: Int,
    val time: String,
    val title: String,
    val kind: String = "AGENDA",
    val detail: String = "",
    val courseId: String = ""
)
data class GradeRow(val score: Int, val letter: String)
data class ProfileRow(
    val name: String,
    val program: String,
    val semester: Int,
    val priorIpk: Double = 0.0,
    val priorSks: Int = 0,
    val onboardingCompleted: Boolean = false
)
data class CourseRow(
    val id: String,
    val code: String,
    val title: String,
    val sks: Int,
    val semester: Int,
    val grade: GradeRow? = null,
    val isDemo: Boolean = false,
    val lecturer: String = "",
    val room: String = "",
    val schedule: String = "",
    val description: String = ""
)
data class TaskRow(
    val id: String,
    val courseId: String,
    val title: String,
    val detail: String = "",
    val deadline: String = "",
    val completed: Boolean = false,
    val reminderEnabled: Boolean = false
)
data class NoteRow(
    val id: String,
    val title: String,
    val course: String,
    val body: String,
    val updatedAt: Long
)
data class TransactionRow(
    val id: String,
    val title: String,
    val amount: Long,
    val category: String,
    val income: Boolean,
    val date: String
)
data class ReminderItemRow(
    val id: String,
    val title: String,
    val detail: String = "",
    val time: String,
    val category: String = "Umum",
    val enabled: Boolean = true,
    val sourceId: String = ""
)
data class StudyPlanRow(
    val id: String,
    val courseId: String = "",
    val title: String,
    val detail: String = "",
    val completed: Boolean = false
)
data class AiChatSessionRow(
    val id: String,
    val noteId: String,
    val courseId: String,
    val title: String,
    val updatedAt: Long
)
data class AiChatMessageRow(
    val id: String,
    val sessionId: String,
    val sender: String,
    val text: String,
    val quoteSnippet: String = "",
    val createdAt: Long
)
data class AiStudyProgressRow(
    val noteId: String,
    val masteredCount: Int,
    val totalCount: Int,
    val canvasScore: Int,
    val updatedAt: Long
)

