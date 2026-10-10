package org.umn.application.studenthub.ui.profile

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
class ProfileDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clear() {
        StudentHubDatabase.resetForTests()
        context.deleteDatabase(StudentHubDatabase.NAME)
        listOf("academic", "studenthub_agenda", "studenthub_finance").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    @After fun close() = StudentHubDatabase.resetForTests()

    @Test fun freshDatabaseHasSemesterEnrollmentAndProfileTables() {
        val db = StudentHubDatabase.get(context).writableDatabase
        val tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertTrue("Missing editable profile table", "profile" in tables)
        assertTrue("Missing semester course enrollment table", "courses" in tables)
        assertEquals(2, db.version)
    }
}
