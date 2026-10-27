package org.umn.application.studenthub.ui.notes

import org.umn.application.studenthub.data.local.StudentHubDatabase

data class StudentNote(val id: String, val title: String, val course: String, val body: String, val updatedAt: Long)

class NotesStore(private val database: StudentHubDatabase) {
    var notes: List<StudentNote> = database.notes().map { StudentNote(it.id, it.title, it.course, it.body, it.updatedAt) }
        private set

    fun save(id: String, title: String, course: String, body: String): StudentNote {
        require(title.isNotBlank()) { "Judul catatan wajib diisi." }
        require(title.length <= 200 && body.length <= 100000) { "Catatan terlalu panjang." }
        require(course.isNotBlank()) { "Mata kuliah wajib dipilih." }
        val saved = database.saveNote(id, title, course, body)
        val note = StudentNote(saved.id, saved.title, saved.course, saved.body, saved.updatedAt)
        notes = database.notes().map { StudentNote(it.id, it.title, it.course, it.body, it.updatedAt) }
        return note
    }

    fun delete(id: String) {
        database.deleteNote(id)
        notes = database.notes().map { StudentNote(it.id, it.title, it.course, it.body, it.updatedAt) }
    }

    fun search(query: String, course: String = "Semua"): List<StudentNote> = notes.filter {
        (course == "Semua" || it.course == course) &&
            (it.title.contains(query.trim(), ignoreCase = true) || it.body.contains(query.trim(), ignoreCase = true))
    }.sortedByDescending { it.updatedAt }
}
