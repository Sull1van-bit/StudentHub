package org.umn.application.studenthub.ui.academic

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.umn.application.studenthub.data.local.StudentHubDatabase

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AcademicScreensTest {
    @get:Rule val compose = createComposeRule()
    @Before fun clearPreferences() {
        StudentHubDatabase.resetForTests()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase("studenthub.db")
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("academic", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun learningGoalAndChecklistPersist() {
        compose.setContent { MaterialTheme { LearningScreen() } }
        compose.onNodeWithText("Target IPK: 3,60").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("3,75")
        compose.onNodeWithText("Simpan").performClick()
        compose.onNodeWithText("Target IPK: 3,75").assertExists()
        compose.onNodeWithTag("task-back-stack").performScrollTo().performClick().assertIsOn()
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("academic", Context.MODE_PRIVATE)
        org.junit.Assert.assertTrue(prefs.getBoolean("learning.back-stack", false))
    }
    @Test fun gradeEditorSavesLocalGrade() {
        compose.setContent { MaterialTheme { GradesScreen() } }
        compose.onNodeWithText("Edit nilai").performScrollTo().performClick()
        compose.onNodeWithTag("grade-score").performTextReplacement("90")
        compose.onNodeWithText("Simpan").performClick()
        compose.onNodeWithText("IF570 · 3 SKS · Skor 90").assertExists()
        compose.runOnIdle {
            StudentHubDatabase.resetForTests()
            org.junit.Assert.assertEquals(90, StudentHubDatabase.get(ApplicationProvider.getApplicationContext()).grades().getValue("IF570").score)
        }
    }
    @Test fun courseNotesCallbackAndGradeTabWork() {
        var notesOpened = false
        compose.setContent { MaterialTheme { CourseDetailScreen("IF570") { notesOpened = true } } }
        compose.onNodeWithText("Catatan").performClick()
        assert(notesOpened)
        compose.onNodeWithText("Nilai", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Skor 88").assertExists()
    }
    @Test fun academicCheckboxUpdatesSummaryAndCourseNavigation() {
        var selected = ""
        compose.setContent { MaterialTheme { AcademicScreen { selected = it } } }
        compose.onNodeWithText("4 mata kuliah · 12 SKS · 4 tugas aktif").assertExists()
        compose.onNodeWithTag("task-navigation").performClick()
        compose.onNodeWithText("4 mata kuliah · 12 SKS · 3 tugas aktif").assertExists()
        compose.onNodeWithTag("course-IF570").performScrollTo().performClick()
        assert(selected == "IF570")
    }
}
