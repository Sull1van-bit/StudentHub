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

internal val noteCourses = linkedMapOf(
    "IF570" to "Pemrograman Aplikasi Mobile",
    "IF550" to "Rekayasa Perangkat Lunak",
    "IF510" to "Basis Data Lanjut",
    "Interaksi Manusia & Komputer" to "Interaksi Manusia & Komputer"
)

// Only the editor's supplied content is seeded; hidden list slots are not invented.
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
    if (!preferences.contains("notes")) {
        store.save("demo-lifecycle", "Navigasi & Activity lifecycle", "IF570", demoNoteBody)
    }
    return store
}

@Composable
fun NotesScreen(onEditNote: (String) -> Unit) {
    val context = LocalContext.current
    val loaded = remember { runCatching { notesStore(context) } }
    val store = loaded.getOrNull()
    var query by rememberSaveable { mutableStateOf("") }
    var course by rememberSaveable { mutableStateOf("Semua") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Ide rapi, belajar ringan.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
            Text("${store?.notes?.size ?: 0} catatan tersimpan • ${store?.notes?.map { it.course }?.distinct()?.size ?: 0} mata kuliah", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextField(
            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().testTag("note-search"),
            placeholder = { Text("Cari judul atau isi catatan") }, leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true, shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
            )
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf("Semua") + noteCourses.keys).forEach { label ->
                FilterChip(selected = course == label, onClick = { course = label }, label = { Text(label) },
                    leadingIcon = if (course == label) { { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) } } else null)
            }
        }
        val filtered = store?.search(query, course).orEmpty()
        filtered.groupBy { it.course }.forEach { (code, notes) ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(noteCourses[code] ?: code, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text("${notes.size} catatan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
        if (filtered.isEmpty()) Text(if (query.isBlank()) "Belum ada catatan. Buat catatan pertamamu." else "Tidak ada catatan yang cocok.", style = MaterialTheme.typography.bodyMedium)
        loaded.exceptionOrNull()?.let { Text("Catatan tidak dapat dibaca. Data lama tidak diubah.", color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onEditNote("") }, enabled = store != null) {
            Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Buat catatan")
        }
        Text("Contoh lokal dari desain • Tersimpan hanya di perangkat ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun NoteEditorScreen(noteId: String, onSaved: () -> Unit) {
    val context = LocalContext.current
    val loaded = remember(noteId) { runCatching { notesStore(context) } }
    val store = loaded.getOrNull()
    val original = store?.notes?.firstOrNull { it.id == noteId }
    var title by rememberSaveable(noteId) { mutableStateOf(original?.title.orEmpty()) }
    var body by rememberSaveable(noteId) { mutableStateOf(original?.body.orEmpty()) }
    var course by rememberSaveable(noteId) { mutableStateOf(original?.course ?: "IF570") }
    var error by rememberSaveable(noteId) { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val missing = noteId.isNotEmpty() && original == null
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$course • ${noteCourses[course].orEmpty()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(if (noteId == "demo-lifecycle") "Pertemuan 6 • 5 Oktober 2026" else "Catatan lokal", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (noteId.isEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            noteCourses.keys.forEach { code -> FilterChip(course == code, { course = code }, { Text(code) }) }
        }
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth().testTag("note-title"), label = { Text("Judul catatan") }, singleLine = true, isError = error != null && title.isBlank())
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp)), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { body += " **teks tebal**" }) { Icon(Icons.Default.FormatBold, "Tambahkan teks tebal") }
            IconButton(onClick = { body += " _teks miring_" }) { Icon(Icons.Default.FormatItalic, "Tambahkan teks miring") }
            IconButton(onClick = { body += "\n• " }) { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, "Tambahkan daftar") }
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
            decorationBox = { field -> Box { if (body.isEmpty()) Text("Tulis isi catatan…", color = MaterialTheme.colorScheme.onSurfaceVariant); field() } }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (title == original?.title && body == original.body) Icons.Default.Check else Icons.Default.Edit, null, Modifier.size(20.dp))
            Text(if (title == original?.title && body == original.body) "Perubahan terakhir tersimpan" else "Belum tersimpan • ketuk Selesai mengedit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (missing || store == null) Text("Catatan tidak dapat dibaca. Data lama tidak diubah.", color = MaterialTheme.colorScheme.error)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            runCatching { requireNotNull(store).save(noteId, title, course, body) }
                .onSuccess { onSaved() }.onFailure { error = it.message ?: "Gagal menyimpan catatan." }
        }, enabled = store != null && !missing) { Text("Selesai mengedit") }
        if (original != null) TextButton(onClick = { deleting = true }) { Text("Hapus catatan", color = MaterialTheme.colorScheme.error) }
    }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false }, title = { Text("Hapus catatan?") }, text = { Text("Catatan ini akan dihapus dari perangkat.") },
        confirmButton = { TextButton(onClick = {
            runCatching { requireNotNull(store).delete(noteId) }.onSuccess { deleting = false; onSaved() }.onFailure { error = "Gagal menghapus catatan."; deleting = false }
        }) { Text("Hapus") } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("Batal") } }
    )
}
