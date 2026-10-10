package org.umn.application.studenthub.ui.finance

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FinanceSqliteTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clear() {
        org.umn.application.studenthub.data.local.StudentHubDatabase.resetForTests()
        context.deleteDatabase("studenthub.db")
        listOf("academic", "studenthub_agenda", "studenthub_finance").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    @Test fun transactionIsStoredInTypedSqliteTableAndReloaded() {
        financeStore(context).add("Makan uji", "10000", "Makan", false, "2026-10-05")
        assertTrue("A real SQLite database must exist", context.getDatabasePath("studenthub.db").exists())
        SQLiteDatabase.openDatabase(context.getDatabasePath("studenthub.db").path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT title, amount, income FROM transactions", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Makan uji", cursor.getString(0))
                assertEquals(10000L, cursor.getLong(1))
                assertEquals(0, cursor.getInt(2))
                assertFalse(cursor.moveToNext())
            }
        }
        assertEquals(-10000L, financeStore(context).summary("2026-10").balance)
        assertFalse(context.getSharedPreferences("studenthub_finance", Context.MODE_PRIVATE).contains("transactions"))
    }
}
