package org.umn.application.studenthub.ui.notes

import org.junit.Assert.*
import org.junit.Test

class NotesStoreTest {
    @Test fun deletingAllNotesPersistsAnEmptyNotebook() {
        var disk: String? = null
        val store = NotesStore({ disk }, { disk = it })
        val note = store.save("", "Judul", "IF570", "Isi")
        store.delete(note.id)
        assertTrue(NotesStore({ disk }, { disk = it }).notes.isEmpty())
    }
    @Test fun invalidTitleDoesNotWrite() {
        var writes = 0
        val store = NotesStore({ null }, { writes++ })
        assertThrows(IllegalArgumentException::class.java) { store.save("", "   ", "IF570", "Isi") }
        assertEquals(0, writes)
    }
    @Test fun failedWriteDoesNotChangeNotebook() {
        val store = NotesStore({ null }, { error("Storage full") })
        assertThrows(IllegalStateException::class.java) { store.save("", "Judul", "IF570", "Isi") }
        assertTrue(store.notes.isEmpty())
    }

    @Test fun editingReplacesExistingNoteAndSearchMatchesBodyAndCourse() {
        var disk: String? = null
        val store = NotesStore({ disk }, { disk = it })
        val original = store.save("", "Awal", "IF570", "Lifecycle")
        store.save(original.id, "Revisi", "IF570", "Back stack")
        assertEquals(1, store.notes.size)
        assertEquals("Revisi", store.search("STACK", "IF570").single().title)
        assertTrue(store.search("stack", "IF550").isEmpty())
        assertTrue(store.search("Lifecycle", "Semua").isEmpty())
    }

    @Test fun newNoteSurvivesReloadWithoutLosingMultilineUnicode() {
        var disk: String? = null
        val store = NotesStore({ disk }, { disk = it })
        val saved = store.save("", "  Catatan baru  ", "IF570", "Baris satu\n• Dua\t✓")
        val reloaded = NotesStore({ disk }, { disk = it })
        assertEquals("Catatan baru", reloaded.notes.single { it.id == saved.id }.title)
        assertEquals("Baris satu\n• Dua\t✓", reloaded.notes.single { it.id == saved.id }.body)
    }
}
