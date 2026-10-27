package org.umn.application.studenthub.ui.notes

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.umn.application.studenthub.data.local.StudentHubDatabase
import org.umn.application.studenthub.ui.localization.AppLanguage
import org.umn.application.studenthub.ui.localization.LocalAppLanguage
import org.umn.application.studenthub.ui.localization.lString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun notesStore(context: Context): NotesStore {
    return NotesStore(StudentHubDatabase.get(context))
}

@Composable
fun NotesScreen(
    onScanNote: () -> Unit = {},
    onEditNote: (String) -> Unit
) {
    NotesScreen(
        initialCourseFilter = "",
        onScanNote = { onScanNote() },
        onEditNoteWithCourse = { noteId, _ -> onEditNote(noteId) }
    )
}

@Composable
fun NotesScreen(
    initialCourseFilter: String = "",
    onScanNote: (String) -> Unit = {},
    onEditNoteWithCourse: (noteId: String, courseId: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEn = LocalAppLanguage.current == AppLanguage.ENGLISH
    val loaded = remember { runCatching { notesStore(context) } }
    val store = loaded.getOrNull()
    val database = remember(context) { StudentHubDatabase.get(context) }
    val dbCoursesList = remember {
        runCatching { database.courses() }.getOrDefault(emptyList())
    }
    val dbCourses = remember(dbCoursesList) {
        dbCoursesList.associate { it.code to it.title }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var course by rememberSaveable(initialCourseFilter) {
        mutableStateOf(initialCourseFilter.takeIf { it.isNotBlank() } ?: "Semua")
    }
    var studioNote by remember { mutableStateOf<StudentNote?>(null) }
    var studioInitialTab by rememberSaveable { mutableIntStateOf(0) }
    var studioInitialSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var showChatHistory by rememberSaveable { mutableStateOf(false) }
    var isImportingFile by remember { mutableStateOf(false) }
    var importFeedback by remember { mutableStateOf<String?>(null) }

    fun displayCourseTitle(code: String): String = dbCourses[code]?.let { "$code • $it" } ?: code

    val selectedCourseForNewNote = if (course != "Semua") course else (dbCoursesList.firstOrNull()?.code ?: "")

    val fileUploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && store != null) {
            isImportingFile = true
            importFeedback = if (isEn) "Importing file into notebook..." else "Mengimpor file ke buku catatan..."
            scope.launch {
                DocumentNoteExtractor.extractFromUri(context, uri)
                    .onSuccess { extracted ->
                        val targetCourse = selectedCourseForNewNote.ifBlank { "Umum" }
                        val bodyContent = extracted.extractedText.ifBlank {
                            if (isEn) "[Imported from ${extracted.fileName}]" else "[Diimpor dari ${extracted.fileName}]"
                        }
                        val savedNote = runCatching {
                            store.save(
                                id = "",
                                title = extracted.suggestedTitle.ifBlank { extracted.fileName },
                                course = targetCourse,
                                body = bodyContent
                            )
                        }.getOrNull()
                        isImportingFile = false
                        if (savedNote != null) {
                            importFeedback = if (isEn) {
                                "Imported \"${savedNote.title}\" (${extracted.pageCount} page(s))."
                            } else {
                                "Berhasil mengimpor \"${savedNote.title}\" (${extracted.pageCount} halaman)."
                            }
                            onEditNoteWithCourse(savedNote.id, savedNote.course)
                        } else {
                            importFeedback = if (isEn) "Failed to save imported note." else "Gagal menyimpan catatan dari file."
                        }
                    }
                    .onFailure { err ->
                        isImportingFile = false
                        importFeedback = err.message ?: if (isEn) "Could not read selected file." else "File tidak dapat dibaca."
                    }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (isEn) "Class Notes & Notebook" else "Catatan & Notebook",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (isEn)
                        "${store?.notes?.size ?: 0} notes saved • ${dbCoursesList.size} registered courses"
                    else
                        "${store?.notes?.size ?: 0} catatan tersimpan • ${dbCoursesList.size} mata kuliah terdaftar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(
                onClick = { showChatHistory = true }
            ) {
                Icon(Icons.Default.Forum, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(lString("Riwayat Diskusi", "Chat History"))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                onClick = { onScanNote(selectedCourseForNewNote) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).testTag("scan-note-card")
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.DocumentScanner, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text(lString("Pindai Catatan", "Scan Notes"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(lString("Kamera & OCR foto", "Camera & photo OCR"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Card(
                onClick = {
                    if (!isImportingFile) {
                        runCatching { fileUploadLauncher.launch(DocumentNoteExtractor.SUPPORTED_MIME_TYPES) }
                    }
                },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).testTag("upload-file-card")
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isImportingFile) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.UploadFile, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(lString("Unggah PDF / File", "Upload PDF / File"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(lString("PDF, DOCX, TXT, MD", "PDF, DOCX, TXT, MD"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                }
            }
        }

        importFeedback?.let { feedback ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = feedback,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().testTag("note-search"),
            placeholder = { Text(lString("Cari judul, mata kuliah, atau isi catatan", "Search note title, course, or content")) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
            )
        )
        val availableCourses = remember(store?.notes, dbCourses) {
            val userCourses = store?.notes?.map { it.course }?.distinct().orEmpty()
            (dbCourses.keys + userCourses).distinct()
        }
        if (availableCourses.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("Semua") + availableCourses).forEach { label ->
                    val displayLabel = if (label == "Semua") lString("Semua", "All") else displayCourseTitle(label)
                    FilterChip(
                        selected = course == label,
                        onClick = { course = label },
                        label = { Text(displayLabel) },
                        leadingIcon = if (course == label) {
                            { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                        } else null
                    )
                }
            }
        }
        val filtered = store?.search(query, course).orEmpty()
        filtered.groupBy { it.course }.forEach { (code, notes) ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.School, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(displayCourseTitle(code), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${notes.size} " + lString("catatan", "notes"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                notes.forEach { note ->
                    ListItem(
                        headlineContent = { Text(note.title) },
                        supportingContent = {
                            Text(
                                displayCourseTitle(note.course) + " • " +
                                    lString("Diperbarui ", "Updated ") +
                                    SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("id-ID")).format(Date(note.updatedAt))
                            )
                        },
                        leadingContent = { Icon(Icons.Default.Edit, null) },
                        trailingContent = {
                            FilledTonalIconButton(
                                onClick = {
                                    studioInitialTab = 0
                                    studioInitialSessionId = null
                                    studioNote = note
                                }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = lString("Buka Studio Belajar", "Open Study Studio")
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { onEditNoteWithCourse(note.id, note.course) },
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
                        if (query.isBlank()) lString("Belum ada catatan. Buat catatan pertamamu.", "No notes yet. Create your first note.")
                        else lString("Tidak ada catatan yang cocok.", "No matching notes found."),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (query.isBlank()) {
                        Text(
                            lString("Pilih mata kuliah dan mulai tulis catatan kuliahmu.", "Select a course and start writing your class notes."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        loaded.exceptionOrNull()?.let { Text(lString("Catatan tidak dapat dibaca.", "Notes cannot be read."), color = MaterialTheme.colorScheme.error) }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onEditNoteWithCourse("", selectedCourseForNewNote) },
                enabled = store != null,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(lString("Buat catatan", "New note"))
            }
            OutlinedButton(
                onClick = { onScanNote(selectedCourseForNewNote) },
                modifier = Modifier.weight(1f).testTag("scan-note-btn")
            ) {
                Icon(Icons.Default.DocumentScanner, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(lString("Pindai Catatan", "Scan note"))
            }
        }
    }

    studioNote?.let { activeNote ->
        NotebookLmStudioSheet(
            noteId = activeNote.id,
            noteTitle = activeNote.title,
            noteCourse = activeNote.course,
            noteBody = activeNote.body,
            initialTab = studioInitialTab,
            initialSessionId = studioInitialSessionId,
            onInsertToNote = { snippet ->
                runCatching {
                    val updatedBody = (activeNote.body.trimEnd() + snippet).trimStart()
                    store?.save(activeNote.id, activeNote.title, activeNote.course, updatedBody)
                    studioNote = store?.notes?.firstOrNull { it.id == activeNote.id }
                }
            },
            onDismiss = {
                studioNote = null
                studioInitialSessionId = null
            }
        )
    }

    if (showChatHistory) {
        AiChatHistorySheet(
            database = database,
            onOpenSession = { session ->
                showChatHistory = false
                val matched = store?.notes?.firstOrNull { it.id == session.noteId }
                if (matched != null) {
                    studioInitialTab = 3
                    studioInitialSessionId = session.id
                    studioNote = matched
                } else {
                    studioInitialTab = 3
                    studioInitialSessionId = session.id
                    studioNote = StudentNote(
                        id = session.noteId,
                        title = session.title,
                        course = session.courseId.ifBlank { "Umum" },
                        body = "",
                        updatedAt = session.updatedAt
                    )
                }
            },
            onDismiss = { showChatHistory = false }
        )
    }
}

@Composable
fun NoteEditorScreen(noteId: String, defaultCourse: String = "", onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEn = LocalAppLanguage.current == AppLanguage.ENGLISH
    val loaded = remember(noteId) { runCatching { notesStore(context) } }
    val store = loaded.getOrNull()
    val dbCourses = remember {
        runCatching { StudentHubDatabase.get(context).courses() }.getOrDefault(emptyList())
    }
    val original = store?.notes?.firstOrNull { it.id == noteId }
    var title by rememberSaveable(noteId) { mutableStateOf(original?.title.orEmpty()) }
    var body by rememberSaveable(noteId) { mutableStateOf(original?.body.orEmpty()) }
    var course by rememberSaveable(noteId) {
        mutableStateOf(
            original?.course
                ?: defaultCourse.takeIf { it.isNotBlank() }
                ?: dbCourses.firstOrNull()?.code
                ?: "Umum"
        )
    }
    var error by rememberSaveable(noteId) { mutableStateOf<String?>(null) }
    var importNotice by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var showStudio by rememberSaveable { mutableStateOf(false) }
    var studioTab by rememberSaveable { mutableIntStateOf(0) }
    val missing = noteId.isNotEmpty() && original == null

    val editorFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isImporting = true
            importNotice = if (isEn) "Extracting file content..." else "Mengekstrak isi file..."
            scope.launch {
                DocumentNoteExtractor.extractFromUri(context, uri)
                    .onSuccess { extracted ->
                        if (title.isBlank()) {
                            title = extracted.suggestedTitle
                        }
                        if (extracted.extractedText.isNotBlank()) {
                            val header = "\n\n--- [${extracted.fileName}] ---\n"
                            body = if (body.isBlank()) {
                                extracted.extractedText
                            } else {
                                body.trimEnd() + header + extracted.extractedText
                            }
                            importNotice = if (isEn) {
                                "Inserted content from ${extracted.fileName}."
                            } else {
                                "Isi dari ${extracted.fileName} berhasil ditambahkan ke catatan."
                            }
                        } else {
                            importNotice = if (isEn) {
                                "No readable text found in ${extracted.fileName}."
                            } else {
                                "Tidak ditemukan teks yang dapat dibaca pada ${extracted.fileName}."
                            }
                        }
                    }
                    .onFailure { err ->
                        importNotice = err.message ?: if (isEn) "Failed to read file." else "Gagal membaca file."
                    }
                isImporting = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        val matchedCourse = dbCourses.firstOrNull { it.code.equals(course, ignoreCase = true) }
        val courseLabel = matchedCourse?.let { "${it.code} • ${it.title}" } ?: course
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(courseLabel.ifBlank { lString("Catatan Kuliah", "Class Note") }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                if (matchedCourse != null) {
                    lString("Terhubung ke mata kuliah ${matchedCourse.title}", "Linked to course ${matchedCourse.title}")
                } else {
                    lString("Pilih mata kuliah yang terdaftar di bawah ini", "Select a registered course below")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                lString("Mata Kuliah Terkait", "Linked Course"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (dbCourses.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    dbCourses.forEach { c ->
                        FilterChip(
                            selected = course.equals(c.code, ignoreCase = true),
                            onClick = { course = c.code },
                            label = { Text("${c.code} • ${c.title}") },
                            leadingIcon = if (course.equals(c.code, ignoreCase = true)) {
                                { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                    FilterChip(
                        selected = course.equals("Umum", ignoreCase = true),
                        onClick = { course = "Umum" },
                        label = { Text(lString("Umum", "General")) }
                    )
                }
            } else {
                Text(
                    lString(
                        "Belum ada mata kuliah terdaftar. Catatan akan disimpan dengan kategori Umum (tambahkan mata kuliah di menu Akademik).",
                        "No courses registered yet. Note will be saved under General (add courses in Academics)."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth().testTag("note-title"),
            label = { Text(lString("Judul catatan", "Note title")) },
            singleLine = true,
            isError = error != null && title.isBlank()
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            lString("Studio Belajar", "Study Studio"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            lString(
                                "Ringkasan, kanvas konsep, kartu latihan, dan diskusi dari isi catatanmu.",
                                "Summary, concept canvas, flashcards, and Q&A from your note."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { studioTab = 0; showStudio = true },
                        label = { Text(lString("Rangkuman", "Summary")) },
                        leadingIcon = { Icon(Icons.Default.Summarize, null, Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = { studioTab = 1; showStudio = true },
                        label = { Text(lString("Kanvas", "Canvas")) },
                        leadingIcon = { Icon(Icons.Default.AccountTree, null, Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = { studioTab = 2; showStudio = true },
                        label = { Text(lString("Kuis & Panduan", "Quiz & Guide")) },
                        leadingIcon = { Icon(Icons.Default.Quiz, null, Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = { studioTab = 3; showStudio = true },
                        label = { Text(lString("Tanya Catatan", "Ask Note")) },
                        leadingIcon = { Icon(Icons.Default.Forum, null, Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = { studioTab = 4; showStudio = true },
                        label = { Text(lString("Perkembangan", "Progress")) },
                        leadingIcon = { Icon(Icons.Default.Assessment, null, Modifier.size(16.dp)) }
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp)),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { body += " **teks tebal**" }) { Icon(Icons.Default.FormatBold, lString("Tambahkan teks tebal", "Add bold text")) }
            IconButton(onClick = { body += " _teks miring_" }) { Icon(Icons.Default.FormatItalic, lString("Tambahkan teks miring", "Add italic text")) }
            IconButton(onClick = { body += "\n• " }) { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, lString("Tambahkan daftar", "Add bullet list")) }
            TextButton(
                onClick = {
                    if (!isImporting) {
                        runCatching { editorFileLauncher.launch(DocumentNoteExtractor.SUPPORTED_MIME_TYPES) }
                    }
                }
            ) {
                if (isImporting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.UploadFile, null, Modifier.size(16.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text(lString("Impor PDF/File", "Import PDF/File"))
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { studioTab = 0; showStudio = true },
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(lString("Studio", "Studio"))
            }
        }
        importNotice?.let { notice ->
            Text(
                text = notice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        BasicTextField(
            value = body,
            onValueChange = { body = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp).testTag("note-body").semantics { contentDescription = "Isi catatan" },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = { text ->
                val annotated = AnnotatedString.Builder(text)
                val headingEnd = text.text.indexOf('\n').let { if (it < 0) text.length else it }
                if (headingEnd > 0) annotated.addStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium), 0, headingEnd)
                TransformedText(annotated.toAnnotatedString(), OffsetMapping.Identity)
            },
            decorationBox = { field ->
                Box {
                    if (body.isEmpty()) Text(lString("Tulis isi catatan…", "Write note content…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    field()
                }
            }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (title == original?.title && body == original.body) Icons.Default.Check else Icons.Default.Edit, null, Modifier.size(20.dp))
            Text(
                if (title == original?.title && body == original.body) lString("Perubahan terakhir tersimpan", "All changes saved")
                else lString("Belum tersimpan • ketuk Selesai mengedit", "Unsaved changes • tap Done editing"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (missing || store == null) Text(lString("Catatan tidak ditemukan.", "Note not found."), color = MaterialTheme.colorScheme.error)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            runCatching { requireNotNull(store).save(noteId, title, course.ifBlank { dbCourses.firstOrNull()?.code ?: "Umum" }, body) }
                .onSuccess { onSaved() }
                .onFailure { error = it.message ?: "Gagal menyimpan catatan." }
        }, enabled = store != null && !missing) { Text(lString("Selesai mengedit", "Done editing")) }
        if (original != null) TextButton(onClick = { deleting = true }) { Text(lString("Hapus catatan", "Delete note"), color = MaterialTheme.colorScheme.error) }
    }

    if (showStudio) {
        NotebookLmStudioSheet(
            noteId = noteId,
            noteTitle = title,
            noteCourse = course,
            noteBody = body,
            initialTab = studioTab,
            onInsertToNote = { snippet ->
                body = (body.trimEnd() + snippet).trimStart()
            },
            onDismiss = { showStudio = false }
        )
    }

    if (deleting) {
        val deleteFailedMsg = lString("Gagal menghapus catatan.", "Failed to delete note.")
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(lString("Hapus catatan?", "Delete note?")) },
            text = { Text(lString("Catatan ini akan dihapus dan tidak dapat dikembalikan.", "This note will be deleted and cannot be recovered.")) },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { requireNotNull(store).delete(noteId) }
                        .onSuccess { deleting = false; onSaved() }
                        .onFailure { error = deleteFailedMsg; deleting = false }
                }) { Text(lString("Hapus", "Delete")) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(lString("Batal", "Cancel")) } }
        )
    }
}
