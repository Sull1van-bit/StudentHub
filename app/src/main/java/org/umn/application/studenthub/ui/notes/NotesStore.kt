package org.umn.application.studenthub.ui.notes

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import java.util.UUID

data class StudentNote(val id: String, val title: String, val course: String, val body: String, val updatedAt: Long)

/** A small local notebook; the callbacks bind to private SharedPreferences on Android. */
class NotesStore(private val read: () -> String?, private val write: (String) -> Unit) {
    var notes: List<StudentNote> = read()?.let(::decode) ?: emptyList()
        private set

    fun save(id: String, title: String, course: String, body: String): StudentNote {
        require(title.isNotBlank()) { "Judul catatan wajib diisi." }
        require(title.length <= 200 && body.length <= 100000) { "Catatan terlalu panjang." }
        require(course.isNotBlank()) { "Mata kuliah wajib dipilih." }
        val note = StudentNote(id.ifEmpty { UUID.randomUUID().toString() }, title.trim(), course, body, System.currentTimeMillis())
        val updated = notes.filterNot { it.id == note.id } + note
        write(encode(updated))
        notes = updated
        return note
    }

    fun delete(id: String) {
        val updated = notes.filterNot { it.id == id }
        write(encode(updated))
        notes = updated
    }

    fun search(query: String, course: String = "Semua"): List<StudentNote> = notes.filter {
        (course == "Semua" || it.course == course) &&
            (it.title.contains(query.trim(), ignoreCase = true) || it.body.contains(query.trim(), ignoreCase = true))
    }.sortedByDescending { it.updatedAt }

    private fun encode(notes: List<StudentNote>): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(notes.size)
            notes.forEach { note ->
                listOf(note.id, note.title, note.course, note.body).forEach { value ->
                    val utf8 = value.toByteArray(Charsets.UTF_8)
                    out.writeInt(utf8.size)
                    out.write(utf8)
                }
                out.writeLong(note.updatedAt)
            }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    private fun decode(value: String): List<StudentNote> = DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(value))).use { input ->
        List(input.readInt().also { require(it in 0..10000) }) {
            val fields = List(4) {
                val size = input.readInt().also { require(it in 0..1_000_000) }
                ByteArray(size).also(input::readFully).toString(Charsets.UTF_8)
            }
            StudentNote(fields[0], fields[1], fields[2], fields[3], input.readLong())
        }
    }
}
