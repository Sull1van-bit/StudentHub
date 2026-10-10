package org.umn.application.studenthub.ui.finance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.umn.application.studenthub.data.local.StudentHubDatabase

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clear() {
        StudentHubDatabase.resetForTests()
        context.deleteDatabase("studenthub.db")
        listOf("academic", "studenthub_agenda", "studenthub_finance").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    @After fun close() = StudentHubDatabase.resetForTests()
    @Test fun existingMondayFixturesAndLegacyAgendaSurviveRestart() {
        context.getSharedPreferences("studenthub_agenda", Context.MODE_PRIVATE).edit()
            .putString("entries", """[{"day":1,"time":"18.30","title":"Study"}]""").commit()
        val db = StudentHubDatabase.get(context)
        assertEquals(4, db.agenda(0).size)
        assertEquals("Pemrograman Aplikasi Mobile", db.agenda(0).first().title)
        db.addAgenda(1, "19.00", "Review")
        db.saveGrade("IF570", 91, "A-")
        db.saveGoal(3.75)
        db.saveBudgets(mapOf("Makan" to 700000L))
        StudentHubDatabase.resetForTests()
        val restored = StudentHubDatabase.get(context)
        assertEquals(listOf("Study", "Review"), restored.agenda(1).map { it.title })
        assertEquals(91, restored.grades().getValue("IF570").score)
        assertEquals("A-", restored.grades().getValue("IF570").letter)
        assertEquals(3.75, restored.goal(), 0.001)
        assertEquals(700000L, restored.budgets().getValue("Makan"))
        assertEquals(4, restored.agenda(0).size)
    }
    @Test fun malformedLegacyRecordRollsBackEveryTableAndCanBeRetried() {
        val prefs = context.getSharedPreferences("studenthub_finance", Context.MODE_PRIVATE)
        val broken = "id\tLunch\t12000\tMakan\tfalse\t2026-10-05\nbroken"
        prefs.edit().putString("transactions", broken).commit()
        assertThrows(Exception::class.java) { StudentHubDatabase.get(context).transactions() }
        assertEquals(broken, prefs.getString("transactions", null))
        StudentHubDatabase.resetForTests()
        prefs.edit().putString("transactions", broken.substringBefore('\n')).commit()
        val db = StudentHubDatabase.get(context)
        assertEquals(1, db.transactions().size)
        assertEquals(4, db.grades().size)
        StudentHubDatabase.resetForTests()
        assertEquals(1, StudentHubDatabase.get(context).transactions().size)
    }
    @Test fun invalidWritesNeverReplaceExistingData() {
        val db = StudentHubDatabase.get(context)
        db.addAgenda(1, "18.30", "Study")
        val agenda = db.agenda(1)
        assertThrows(IllegalArgumentException::class.java) { db.addAgenda(1, "99.00", "Study") }
        assertThrows(IllegalArgumentException::class.java) { db.saveGrade("missing", 80, "B") }
        assertThrows(IllegalArgumentException::class.java) { db.saveBudgets(mapOf("Makan" to 700000L, "Unknown" to 10L)) }
        assertEquals(600000L, db.budgets().getValue("Makan"))
        assertEquals(agenda, db.agenda(1))
    }
}
