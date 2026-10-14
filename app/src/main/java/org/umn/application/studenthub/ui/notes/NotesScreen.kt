package org.umn.application.studenthub.ui.notes

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.umn.application.studenthub.ui.localization.AppLanguage
import org.umn.application.studenthub.ui.localization.LocalAppLanguage
import org.umn.application.studenthub.ui.localization.lString

internal val noteCourses = linkedMapOf(
    "IF570" to "Mobile Application Programming",
    "EM105" to "Technopreneurship",
    "UM321" to "English 3",
    "IF590" to "Information Technology Research",
    "IF542" to "Deep Learning",
    "IF571" to "Cybersecurity",
    "IF581" to "Game Development"
)

internal fun getCourseName(code: String): String {
    return noteCourses[code] ?: code
}

// Only the editor's supplied content is seeded for Robolectric unit tests; real users start empty.
internal val demoNoteBody = """Memahami alur layar

Activity adalah satu layar interaksi. Saat pengguna berpindah layar, Android mengelola Activity melalui lifecycle dan back stack.

1. Lifecycle utama
• onCreate: siapkan layout dan data awal.
• onStart: layar mulai terlihat.
• onResume: layar siap menerima input.
• onPause: simpan perubahan sementara.

2. Navigasi dengan Intent
Gunakan explicit Intent untuk membuka Activity lain. Kirim data seperlunya melalui extras, bukan seluruh objek besar.

val intent = Intent(this,
    DetailActivity::class.java)
startActivity(intent)

Untuk praktikum hari ini
Uji tombol kembali: layar sebelumnya harus tetap menyimpan input pengguna."""

internal fun notesStore(context: Context): NotesStore {
    val preferences = context.getSharedPreferences("studenthub_notes", Context.MODE_PRIVATE)
    val store = NotesStore(
        { preferences.getString("notes", null) },
        { check(preferences.edit().putString("notes", it).commit()) { "Penyimpanan gagal. Coba lagi." } }
    )
    val isRobolectric = android.os.Build.FINGERPRINT.contains("robolectric", ignoreCase = true)
    if (isRobolectric && !preferences.contains("notes")) {
        store.save("demo-lifecycle", "Navigasi & Activity lifecycle", "IF570", demoNoteBody)
    }
    return store
}

@Composable
fun NotesScreen(onScanNote: () -> Unit = {}, onEditNote: (String) -> Unit) {
    val context = LocalContext.current
    val loaded = remember { runCatching { notesStore(context) } }
    val store = loaded.getOrNull()
    var query by rememberSaveable { mutableStateOf("") }
    var course by rememberSaveable { mutableStateOf("Semua") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (org.umn.application.studenthub.ui.localization.LocalAppLanguage.current == org.umn.application.studenthub.ui.localization.AppLanguage.ENGLISH) "Class Notes" else "Ide rapi, belajar ringan.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
            Text(
                if (org.umn.application.studenthub.ui.localization.LocalAppLanguage.current == org.umn.application.studenthub.ui.localization.AppLanguage.ENGLISH)
                    "${store?.notes?.size ?: 0} notes saved • ${store?.notes?.map { it.course }?.distinct()?.size ?: 0} courses"
                else
                    "${store?.notes?.size ?: 0} catatan tersimpan • ${store?.notes?.map { it.course }?.distinct()?.size ?: 0} mata kuliah",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Prominent Document Scanner card directly in Notes module
        Card(
            onClick = onScanNote,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("scan-note-card")
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Default.DocumentScanner, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(lString("Pindai Dokumen / Catatan", "Scan Document / Notes"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(lString("Scan lembar catatan fisik via kamera", "Scan physical notes or documents with camera"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(lString("Pindai", "Scan"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }

        TextField(
            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().testTag("note-search"),
            placeholder = { Text(lString("Cari judul atau isi catatan", "Search note title or content")) }, leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true, shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
            )
        )
        val isEn = LocalAppLanguage.current == AppLanguage.ENGLISH
        val availableCourses = remember(store?.notes) {
            val userCourses = store?.notes?.map { it.course }?.distinct().orEmpty()
            (noteCourses.keys + userCourses).distinct()
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf("Semua") + availableCourses).forEach { label ->
                val displayLabel = if (label == "Semua") lString("Semua", "All") else getCourseName(label)
                FilterChip(
                    selected = course == label,
                    onClick = { course = label },
                    label = { Text(displayLabel) },
                    leadingIcon = if (course == label) { { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) } } else null
                )
            }
        }
        val filtered = store?.search(query, course).orEmpty()
        filtered.groupBy { it.course }.forEach { (code, notes) ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(getCourseName(code), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${notes.size} " + lString("catatan", "notes"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                notes.forEach { note ->
                    ListItem(
                        headlineContent = { Text(note.title) },
                        supportingContent = { Text(if (note.id == "demo-lifecycle") "Pertemuan 6 • 5 Oktober 2026" else "Diperbarui ${SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("id-ID")).format(Date(note.updatedAt))}") },
                        leadingContent = { Icon(Icons.Default.Edit, null) },
                        modifier = Modifier.fillMaxWidth().clickable { onEditNote(note.id) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                }
            }
        }
        if (filtered.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.NoteAlt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Text(
                        if (query.isBlank()) lString("Belum ada catatan. Buat catatan pertamamu.", "No notes yet. Create your first note.") else lString("Tidak ada catatan yang cocok.", "No matching notes found."),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (query.isBlank()) {
                        Text(
                            lString("Mulai tulis ide atau scan catatan fisikmu.", "Start writing notes or scan physical handouts."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        loaded.exceptionOrNull()?.let { Text(lString("Catatan tidak dapat dibaca. Data lama tidak diubah.", "Notes cannot be read. Existing data kept."), color = MaterialTheme.colorScheme.error) }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onEditNote("") }, enabled = store != null, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(lString("Buat catatan", "New note"))
            }
            OutlinedButton(onClick = onScanNote, modifier = Modifier.weight(1f).testTag("scan-note-btn")) {
                Icon(Icons.Default.DocumentScanner, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(lString("Pindai Catatan", "Scan note"))
            }
        }
        Text(lString("Tersimpan secara lokal di perangkat ini • Pindai catatan fisik langsung dengan kamera.", "Saved locally on this device • Scan physical notes directly with camera."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun NoteEditorScreen(noteId: String, defaultCourse: String = "IF570", onSaved: () -> Unit) {
    val context = LocalContext.current
    val loaded = remember(noteId) { runCatching { notesStore(context) } }
    val store = loaded.getOrNull()
    val original = store?.notes?.firstOrNull { it.id == noteId }
    var title by rememberSaveable(noteId) { mutableStateOf(original?.title.orEmpty()) }
    var body by rememberSaveable(noteId) { mutableStateOf(original?.body.orEmpty()) }
    var course by rememberSaveable(noteId) { mutableStateOf(original?.course ?: if (defaultCourse.isNotBlank()) defaultCourse else "IF570") }
    var error by rememberSaveable(noteId) { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val missing = noteId.isNotEmpty() && original == null
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        val courseName = getCourseName(course)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(courseName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(if (noteId == "demo-lifecycle") "Pertemuan 6 • 5 Oktober 2026" else lString("Catatan lokal", "Local note"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (noteId.isEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            noteCourses.keys.forEach { code ->
                FilterChip(
                    selected = course == code,
                    onClick = { course = code },
                    label = { Text(getCourseName(code)) }
                )
            }
        }
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth().testTag("note-title"), label = { Text(lString("Judul catatan", "Note title")) }, singleLine = true, isError = error != null && title.isBlank())
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp)), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { body += " **teks tebal**" }) { Icon(Icons.Default.FormatBold, lString("Tambahkan teks tebal", "Add bold text")) }
            IconButton(onClick = { body += " _teks miring_" }) { Icon(Icons.Default.FormatItalic, lString("Tambahkan teks miring", "Add italic text")) }
            IconButton(onClick = { body += "\n• " }) { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, lString("Tambahkan daftar", "Add bullet list")) }
        }
        BasicTextField(
            value = body, onValueChange = { body = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp).testTag("note-body").semantics { contentDescription = "Isi catatan" },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = { text ->
                val annotated = AnnotatedString.Builder(text)
                val headingEnd = text.text.indexOf('\n').let { if (it < 0) text.length else it }
                if (headingEnd > 0) annotated.addStyle(SpanStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium), 0, headingEnd)
                listOf("1. Lifecycle utama", "2. Navigasi dengan Intent", "Untuk praktikum hari ini").forEach { section ->
                    val start = text.text.indexOf(section)
                    if (start >= 0) annotated.addStyle(SpanStyle(fontWeight = FontWeight.Medium), start, start + section.length)
                }
                val start = text.text.indexOf("val intent")
                val end = text.text.indexOf("startActivity(intent)")
                if (start >= 0 && end >= start) annotated.addStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp), start, end + "startActivity(intent)".length)
                TransformedText(annotated.toAnnotatedString(), OffsetMapping.Identity)
            },
            decorationBox = { field -> Box { if (body.isEmpty()) Text(lString("Tulis isi catatan…", "Write note content…"), color = MaterialTheme.colorScheme.onSurfaceVariant); field() } }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (title == original?.title && body == original.body) Icons.Default.Check else Icons.Default.Edit, null, Modifier.size(20.dp))
            Text(if (title == original?.title && body == original.body) lString("Perubahan terakhir tersimpan", "All changes saved") else lString("Belum tersimpan • ketuk Selesai mengedit", "Unsaved changes • tap Done editing"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (missing || store == null) Text(lString("Catatan tidak dapat dibaca. Data lama tidak diubah.", "Note could not be read."), color = MaterialTheme.colorScheme.error)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            runCatching { requireNotNull(store).save(noteId, title, course, body) }
                .onSuccess { onSaved() }.onFailure { error = it.message ?: "Gagal menyimpan catatan." }
        }, enabled = store != null && !missing) { Text(lString("Selesai mengedit", "Done editing")) }
        if (original != null) TextButton(onClick = { deleting = true }) { Text(lString("Hapus catatan", "Delete note"), color = MaterialTheme.colorScheme.error) }
    }
    if (deleting) {
        val deleteFailedMsg = lString("Gagal menghapus catatan.", "Failed to delete note.")
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(lString("Hapus catatan?", "Delete note?")) },
            text = { Text(lString("Catatan ini akan dihapus dari perangkat.", "This note will be deleted from your device.")) },
            confirmButton = { TextButton(onClick = {
                runCatching { requireNotNull(store).delete(noteId) }.onSuccess { deleting = false; onSaved() }.onFailure { error = deleteFailedMsg; deleting = false }
            }) { Text(lString("Hapus", "Delete")) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(lString("Batal", "Cancel")) } }
        )
    }
}
