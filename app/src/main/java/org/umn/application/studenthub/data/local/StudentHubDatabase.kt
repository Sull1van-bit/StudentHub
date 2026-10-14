package org.umn.application.studenthub.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import java.net.URLDecoder
import java.time.LocalDate
import java.util.UUID

/** SQLite owns domain records; preferences are retained only as a migration backup. Call on IO. */
class StudentHubDatabase private constructor(private val context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {
    init {
        cleanupStaleAgendas()
    }

    private fun cleanupStaleAgendas() {
        runCatching {
            val db = writableDatabase
            // Check if onboarding was completed or if real Monday courses exist (Technopreneurship, English 3, etc.)
            val prefs = context.getSharedPreferences("academic", Context.MODE_PRIVATE)
            val appPrefs = context.getSharedPreferences("studenthub_prefs", Context.MODE_PRIVATE)
            val hasCompletedOnboarding = prefs.getBoolean("onboarding_completed", false) || appPrefs.getBoolean("has_completed_onboarding", false)

            val hasRealMondayCourse = db.rawQuery(
                "SELECT 1 FROM agenda WHERE day = 0 AND (title LIKE '%Technopreneurship%' OR title LIKE '%English%' OR title LIKE '%EM105%' OR title LIKE '%UM321%')",
                null
            ).use { it.moveToFirst() }

            if (hasCompletedOnboarding || hasRealMondayCourse) {
                db.delete("agenda", "id LIKE 'demo-%' OR id LIKE 'legacy-%' OR title LIKE '%Rekayasa Perangkat Lunak%'", null)
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE agenda (
            id TEXT PRIMARY KEY NOT NULL,
            day INTEGER NOT NULL CHECK(day BETWEEN 0 AND 6),
            time TEXT NOT NULL CHECK(length(time)=5 AND time GLOB '[0-2][0-9].[0-5][0-9]' AND substr(time,1,2) <= '23'),
            title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 200))""")
        db.execSQL("""CREATE TABLE grades (
            course_id TEXT PRIMARY KEY NOT NULL,
            score INTEGER NOT NULL CHECK(score BETWEEN 0 AND 100),
            letter TEXT NOT NULL CHECK(letter IN ('A','A-','B+','B','B-','C+','C','D','E')))""")
        db.execSQL("CREATE TABLE academic_goal (id INTEGER PRIMARY KEY CHECK(id=1), goal REAL NOT NULL CHECK(goal BETWEEN 0 AND 4))")
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
        db.execSQL("CREATE TABLE migration_marker (name TEXT PRIMARY KEY NOT NULL)")
        migratePreferences(db)
        createProfileAndCourses(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        check(oldVersion == 1 && newVersion == 2) { "Unsupported database upgrade: $oldVersion to $newVersion" }
        createProfileAndCourses(db)
    }

    private fun createProfileAndCourses(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE profile (
            id INTEGER PRIMARY KEY CHECK(id=1), name TEXT NOT NULL, program TEXT NOT NULL,
            semester INTEGER NOT NULL CHECK(semester BETWEEN 1 AND 20))""")
        db.execSQL("INSERT INTO profile(id,name,program,semester) VALUES(1,'','',5)")
        db.execSQL("""CREATE TABLE courses (
            id TEXT PRIMARY KEY NOT NULL, code TEXT NOT NULL CHECK(length(trim(code)) BETWEEN 1 AND 30),
            title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 200),
            sks INTEGER NOT NULL CHECK(sks BETWEEN 1 AND 24),
            semester INTEGER NOT NULL CHECK(semester BETWEEN 1 AND 20),
            score INTEGER, letter TEXT, is_demo INTEGER NOT NULL DEFAULT 0 CHECK(is_demo IN (0,1)),
            CHECK((score IS NULL AND letter IS NULL) OR
                (score IS NOT NULL AND letter IS NOT NULL AND score BETWEEN 0 AND 100 AND letter IN ('A','A-','B+','B','B-','C+','C','D','E'))),
            UNIQUE(code,semester))""")
        // v1 has no enrollment metadata. Preserve every grade, even unknown IDs, and
        // label the inherited semester-5 fixtures rather than claiming a transcript.
        val titles = mapOf("IF570" to "Pemrograman Aplikasi Mobile", "IF550" to "Rekayasa Perangkat Lunak",
            "IF510" to "Basis Data Lanjut", "IF530" to "Interaksi Manusia & Komputer")
        db.rawQuery("SELECT course_id,score,letter FROM grades", null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                db.insertOrThrow("courses", null, ContentValues().apply {
                    put("id", id); put("code", id); put("title", titles[id] ?: id)
                    put("sks", 3); put("semester", 5); put("score", cursor.getInt(1))
                    put("letter", cursor.getString(2)); put("is_demo", 1)
                })
            }
        }
        // Keep the v1 grades table as a migration backup; all live reads use courses.
    }

    fun profile(): ProfileRow = readableDatabase.rawQuery("SELECT name,program,semester FROM profile WHERE id=1", null).use {
        check(it.moveToFirst()); ProfileRow(it.getString(0), it.getString(1), it.getInt(2))
    }

    fun saveProfile(row: ProfileRow) {
        require(row.name.isNotBlank() && row.name.length <= 100 && row.program.isNotBlank() && row.program.length <= 100 && row.semester in 1..20) { "Isi nama, program studi, dan semester 1–20." }
        check(writableDatabase.update("profile", ContentValues().apply {
            put("name", row.name.trim()); put("program", row.program.trim()); put("semester", row.semester)
        }, "id=1", null) == 1)
    }

    fun courses(): List<CourseRow> = readableDatabase.rawQuery("SELECT id,code,title,sks,semester,score,letter,is_demo FROM courses ORDER BY semester, rowid", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(CourseRow(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getInt(3), cursor.getInt(4),
            if (cursor.isNull(5)) null else GradeRow(cursor.getInt(5), cursor.getString(6)), cursor.getInt(7) == 1)) }
    }

    fun saveCourse(row: CourseRow) {
        require(row.id.isNotBlank() && row.code.isNotBlank() && row.code.length <= 30 && row.title.isNotBlank() && row.title.length <= 200 && row.sks in 1..24 && row.semester in 1..20) { "Isi kode, nama mata kuliah, SKS 1–24, dan semester 1–20." }
        require(row.grade == null || (row.grade.score in 0..100 && row.grade.letter in LETTERS)) { "Nilai harus 0–100 dengan huruf A sampai E." }
        val values = ContentValues().apply {
            put("id", row.id); put("code", row.code.trim()); put("title", row.title.trim()); put("sks", row.sks); put("semester", row.semester)
            put("score", row.grade?.score); put("letter", row.grade?.letter); put("is_demo", if (row.isDemo) 1 else 0)
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (db.update("courses", values, "id=?", arrayOf(row.id)) == 0) db.insertOrThrow("courses", null, values)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun deleteCourse(id: String) { writableDatabase.delete("courses", "id=?", arrayOf(id)) }


    private fun migratePreferences(db: SQLiteDatabase) {
        if (db.query("migration_marker", arrayOf("name"), "name=?", arrayOf(MIGRATION), null, null, null).use { it.moveToFirst() }) return
        val agenda = context.getSharedPreferences("studenthub_agenda", Context.MODE_PRIVATE)
        val academic = context.getSharedPreferences("academic", Context.MODE_PRIVATE)
        val finance = context.getSharedPreferences("studenthub_finance", Context.MODE_PRIVATE)
        // SQLiteOpenHelper wraps onCreate and user_version in a transaction too. Any parse or
        // constraint failure rolls everything back, including the marker, without touching prefs.
        db.beginTransaction()
        try {
            val entries = JSONArray(agenda.getString("entries", "[]") ?: "[]")
            for (i in 0 until entries.length()) {
                val entry = entries.getJSONObject(i)
                val day = entry.getInt("day")
                val time = entry.getString("time")
                val title = entry.getString("title")
                validateAgenda(day, time, title)
                insertAgenda(db, AgendaRow("legacy-$i", day, time, title))
            }
            listOf(
                AgendaRow("demo-class", 0, "09.00", "Pemrograman Aplikasi Mobile"),
                AgendaRow("demo-class-2", 0, "13.00", "Rekayasa Perangkat Lunak"),
                AgendaRow("demo-study", 0, "15.30", "Latihan navigasi Android"),
                AgendaRow("demo-deadline", 0, "17.00", "Praktikum 3: Navigasi")
            ).forEach { row -> if (db.query("agenda", arrayOf("id"), "id=?", arrayOf(row.id), null, null, null).use { !it.moveToFirst() }) insertAgenda(db, row) }
            DEFAULT_GRADES.forEach { (id, fallback) ->
                val score = academic.getString("score.$id", null)?.let { raw ->
                    requireNotNull(raw.toIntOrNull()) { "Nilai lama tidak valid: $id" }
                } ?: fallback.score
                val letter = academic.getString("grade.$id", null) ?: fallback.letter
                require(score in 0..100 && letter in LETTERS) { "Nilai lama tidak valid: $id" }
                db.insertOrThrow("grades", null, ContentValues().apply {
                    put("course_id", id); put("score", score); put("letter", letter)
                })
            }
            val goal = academic.getString("goal", null)?.let { requireNotNull(it.replace(',', '.').toDoubleOrNull()) } ?: 3.6
            require(goal.isFinite() && goal in 0.0..4.0)
            db.execSQL("INSERT INTO academic_goal(id,goal) VALUES(1,?)", arrayOf(goal))
            finance.getString("transactions", null)?.takeIf { it.isNotEmpty() }?.lines()?.forEach { line ->
                val fields = line.split('\t').map { URLDecoder.decode(it, "UTF-8") }
                require(fields.size == 6) { "Transaksi lama tidak lengkap." }
                val row = TransactionRow(fields[0], fields[1], fields[2].toLong(), fields[3], fields[4].toBooleanStrict(), fields[5])
                validateTransaction(row)
                insertTransaction(db, row)
            }
            DEFAULT_BUDGETS.forEach { (category, fallback) ->
                val amount = finance.getLong("budget_$category", fallback)
                validateBudget(category, amount)
                db.execSQL("INSERT INTO budgets(category,amount) VALUES(?,?)", arrayOf<Any>(category, amount))
            }
            db.execSQL("INSERT INTO migration_marker(name) VALUES(?)", arrayOf(MIGRATION))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun agenda(day: Int): List<AgendaRow> = readableDatabase.query(
        "agenda", arrayOf("id", "day", "time", "title"), "day=?", arrayOf(day.toString()), null, null, "time, id"
    ).use { cursor -> buildList {
        while (cursor.moveToNext()) add(AgendaRow(cursor.getString(0), cursor.getInt(1), cursor.getString(2), cursor.getString(3)))
    } }

    fun addAgenda(day: Int, time: String, title: String) {
        validateAgenda(day, time, title)
        insertAgenda(writableDatabase, AgendaRow(UUID.randomUUID().toString(), day, time, title.trim()))
    }

    private fun insertAgenda(db: SQLiteDatabase, row: AgendaRow) {
        db.insertOrThrow("agenda", null, ContentValues().apply {
            put("id", row.id); put("day", row.day); put("time", row.time); put("title", row.title)
        })
    }

    fun grades(): Map<String, GradeRow> = readableDatabase.query(
        "courses", arrayOf("code", "score", "letter"), "score IS NOT NULL", null, null, null, "rowid"
    ).use { cursor -> buildMap {
        while (cursor.moveToNext()) put(cursor.getString(0), GradeRow(cursor.getInt(1), cursor.getString(2)))
    } }

    fun saveGrade(course: String, score: Int, letter: String) {
        require(course in courses().map { it.code })
        require(score in 0..100 && letter in LETTERS)
        check(writableDatabase.update("courses", ContentValues().apply {
            put("score", score); put("letter", letter)
        }, "code=?", arrayOf(course)) > 0) { "Nilai tidak ditemukan." }
    }

    fun goal(): Double = readableDatabase.rawQuery("SELECT goal FROM academic_goal WHERE id=1", null).use {
        if (it.moveToFirst()) it.getDouble(0) else 3.6
    }

    fun saveGoal(goal: Double) {
        require(goal.isFinite() && goal in 0.0..4.0)
        writableDatabase.execSQL("INSERT OR REPLACE INTO academic_goal(id,goal) VALUES(1,?)", arrayOf(goal))
    }

    fun transactions(): List<TransactionRow> = readableDatabase.query(
        "transactions", arrayOf("id", "title", "amount", "category", "income", "date"), null, null, null, null, "rowid"
    ).use { cursor -> buildList {
        while (cursor.moveToNext()) add(TransactionRow(cursor.getString(0), cursor.getString(1), cursor.getLong(2), cursor.getString(3), cursor.getInt(4) != 0, cursor.getString(5)))
    } }

    fun addTransaction(row: TransactionRow) {
        validateTransaction(row)
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.rawQuery("SELECT count(*) FROM transactions", null).use { check(it.moveToFirst()); require(it.getInt(0) < 10000) { "Batas transaksi lokal tercapai." } }
            insertTransaction(db, row)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun deleteAgenda(id: String) {
        writableDatabase.delete("agenda", "id=?", arrayOf(id))
    }

    fun deleteTransaction(id: String) {
        writableDatabase.delete("transactions", "id=?", arrayOf(id))
    }

    fun isOnboardingCompleted(): Boolean {
        return runCatching {
            val p = profile()
            p.name.isNotBlank()
        }.getOrDefault(false)
    }

    private fun insertTransaction(db: SQLiteDatabase, row: TransactionRow) {
        db.insertOrThrow("transactions", null, ContentValues().apply {
            put("id", row.id); put("title", row.title); put("amount", row.amount)
            put("category", row.category); put("income", if (row.income) 1 else 0); put("date", row.date)
        })
    }

    fun budgets(): Map<String, Long> = readableDatabase.query(
        "budgets", arrayOf("category", "amount"), null, null, null, null, "rowid"
    ).use { cursor -> buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getLong(1)) } }

    fun saveBudgets(values: Map<String, Long>) {
        values.forEach { (category, amount) -> validateBudget(category, amount) }
        val db = writableDatabase
        db.beginTransaction()
        try {
            values.forEach { (category, amount) ->
                check(db.update("budgets", ContentValues().apply { put("amount", amount) }, "category=?", arrayOf(category)) == 1)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    companion object {
        const val NAME = "studenthub.db"
        const val VERSION = 2
        private const val MIGRATION = "prefs-v1"
        val LETTERS = setOf("A", "A-", "B+", "B", "B-", "C+", "C", "D", "E")
        val DEFAULT_BUDGETS = linkedMapOf("Makan" to 600000L, "Transportasi" to 200000L, "Kuliah" to 250000L, "Kos" to 800000L, "Lainnya" to 150000L)
        private val DEFAULT_GRADES = linkedMapOf("IF570" to GradeRow(88, "A"), "IF550" to GradeRow(82, "B+"), "IF510" to GradeRow(74, "B"), "IF530" to GradeRow(80, "B+"))
        @Volatile private var instance: StudentHubDatabase? = null
        fun get(context: Context): StudentHubDatabase = instance ?: synchronized(this) {
            instance ?: StudentHubDatabase(context.applicationContext).also { instance = it }
        }
        fun resetForTests() = synchronized(this) { instance?.close(); instance = null }

        private fun validateAgenda(day: Int, time: String, title: String) {
            require(day in 0..6 && title.isNotBlank() && title.length <= 200 && Regex("(?:[01][0-9]|2[0-3])\\.[0-5][0-9]").matches(time)) { "Judul, hari, atau waktu agenda tidak valid." }
        }
        private fun validateBudget(category: String, amount: Long) {
            require(category in DEFAULT_BUDGETS && amount in 1..1_000_000_000_000L) { "Anggaran tidak valid." }
        }
        private fun validateTransaction(row: TransactionRow) {
            require(row.id.isNotBlank() && row.title.isNotBlank() && row.title.length <= 200)
            require(row.amount in 1..1_000_000_000_000L && row.category in DEFAULT_BUDGETS)
            require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(row.date) && runCatching { LocalDate.parse(row.date) }.isSuccess)
        }
    }
}

data class AgendaRow(val id: String, val day: Int, val time: String, val title: String)
data class GradeRow(val score: Int, val letter: String)
data class ProfileRow(val name: String, val program: String, val semester: Int)
data class CourseRow(val id: String, val code: String, val title: String, val sks: Int, val semester: Int, val grade: GradeRow?, val isDemo: Boolean = false)
data class TransactionRow(val id: String, val title: String, val amount: Long, val category: String, val income: Boolean, val date: String)
