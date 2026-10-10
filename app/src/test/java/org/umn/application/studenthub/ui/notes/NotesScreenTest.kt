package org.umn.application.studenthub.ui.notes

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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotesScreenTest {
    @get:Rule val compose = createComposeRule()
    @Before fun clearNotes() {
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("studenthub_notes", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun createNoteActionUsesEmptyId() {
        var selected: String? = null
        compose.setContent { MaterialTheme { NotesScreen { selected = it } } }
        compose.onNodeWithText("Buat catatan").performScrollTo().performClick()
        org.junit.Assert.assertEquals("", selected)
    }
    @Test fun searchFiltersTheSeededNote() {
        compose.setContent { MaterialTheme { NotesScreen {} } }
        compose.onNodeWithText("Navigasi & Activity lifecycle").assertExists()
        compose.onNodeWithTag("note-search").performTextInput("tidak-ada")
        compose.onNodeWithText("Navigasi & Activity lifecycle").assertDoesNotExist()
        compose.onNodeWithText("Tidak ada catatan yang cocok.").assertExists()
    }
    @Test fun deletingExistingNoteRequiresConfirmationAndPersists() {
        var saved = false
        compose.setContent { MaterialTheme { NoteEditorScreen("demo-lifecycle") { saved = true } } }
        compose.onNodeWithText("Hapus catatan").performScrollTo().performClick()
        compose.onNodeWithText("Hapus catatan?").assertExists()
        compose.onNodeWithText("Hapus", useUnmergedTree = true).performClick()
        compose.runOnIdle {
            org.junit.Assert.assertTrue(saved)
            org.junit.Assert.assertTrue(notesStore(ApplicationProvider.getApplicationContext()).notes.isEmpty())
        }
    }
    @Test fun editorRejectsAnEmptyTitle() {
        var saved = false
        compose.setContent { MaterialTheme { NoteEditorScreen("") { saved = true } } }
        compose.onNodeWithText("Selesai mengedit").performScrollTo().performClick()
        compose.onNodeWithText("Judul catatan wajib diisi.").assertExists()
        compose.runOnIdle { org.junit.Assert.assertFalse(saved) }
    }
    @Test fun editorSavesTitleAndBodyToPreferences() {
        var saved = false
        compose.setContent { MaterialTheme { NoteEditorScreen("") { saved = true } } }
        compose.onNodeWithTag("note-title").performTextInput("Catatan uji")
        compose.onNodeWithTag("note-body").performTextInput("Isi yang tersimpan")
        compose.onNodeWithText("Selesai mengedit").performScrollTo().performClick()
        compose.runOnIdle {
            org.junit.Assert.assertTrue(saved)
            val notes = notesStore(ApplicationProvider.getApplicationContext()).notes
            org.junit.Assert.assertTrue(notes.any { it.title == "Catatan uji" && it.body == "Isi yang tersimpan" })
        }
    }
}
