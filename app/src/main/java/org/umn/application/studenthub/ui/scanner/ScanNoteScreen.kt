package org.umn.application.studenthub.ui.scanner

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.umn.application.studenthub.data.local.StudentHubDatabase
import org.umn.application.studenthub.ui.localization.AppLanguage
import org.umn.application.studenthub.ui.localization.LocalAppLanguage
import org.umn.application.studenthub.ui.localization.lString
import org.umn.application.studenthub.ui.notes.DocumentNoteExtractor
import org.umn.application.studenthub.ui.notes.ScanSourceMode
import java.util.UUID

@Composable
fun ScanNoteScreen(
    initialCourseId: String = "",
    onNavigateBack: () -> Unit = {},
    onSaveToNote: ((title: String, course: String, content: String) -> Unit)? = null,
    onSavedToNotes: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEn = LocalAppLanguage.current == AppLanguage.ENGLISH
    val courses = remember(context) {
        runCatching { StudentHubDatabase.get(context).courses() }.getOrDefault(emptyList())
    }

    val initialIdx = remember(courses, initialCourseId) {
        if (initialCourseId.isBlank()) 0
        else courses.indexOfFirst { it.code.equals(initialCourseId, ignoreCase = true) || it.id.equals(initialCourseId, ignoreCase = true) }.coerceAtLeast(0)
    }
    var selectedCourseIndex by rememberSaveable(initialIdx) { mutableIntStateOf(initialIdx) }
    var scanModeKey by rememberSaveable { mutableStateOf("WHITEBOARD") }
    var captured by rememberSaveable { mutableStateOf(false) }
    var isExtracting by remember { mutableStateOf(false) }
    var sourceSummary by rememberSaveable { mutableStateOf("") }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val activeScanMode = when (scanModeKey) {
        "HANDWRITING" -> ScanSourceMode.HANDWRITING
        "DOCUMENT" -> ScanSourceMode.DOCUMENT
        else -> ScanSourceMode.WHITEBOARD
    }

    val selectedCourse = courses.getOrNull(selectedCourseIndex)
    val defaultCourseCode = selectedCourse?.code ?: "Umum"
    val defaultCourseName = selectedCourse?.title ?: lString("Catatan Umum", "General Notes")

    var customTitle by rememberSaveable(selectedCourseIndex) {
        mutableStateOf("")
    }
    var scannedBody by rememberSaveable(selectedCourseIndex) {
        mutableStateOf("")
    }

    val modeDisplayName = when (activeScanMode) {
        ScanSourceMode.WHITEBOARD -> lString("Papan Tulis", "Whiteboard")
        ScanSourceMode.HANDWRITING -> lString("Tulisan Tangan", "Handwriting")
        ScanSourceMode.DOCUMENT -> lString("Slide / Modul", "Slide / Handout")
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            captured = true
            previewBitmap = bitmap
            isExtracting = true
            statusMessage = if (isEn) {
                "Reading text from your $modeDisplayName photo..."
            } else {
                "Sedang membaca tulisan dari foto $modeDisplayName..."
            }
            scope.launch {
                DocumentNoteExtractor.extractFromBitmap(
                    bitmap = bitmap,
                    sourceName = "$modeDisplayName $defaultCourseName",
                    scanMode = activeScanMode,
                    courseTitleHint = defaultCourseName
                ).onSuccess { result ->
                    if (customTitle.isBlank()) {
                        customTitle = result.suggestedTitle
                    }
                    sourceSummary = if (isEn) "Photo: $modeDisplayName" else "Foto $modeDisplayName"
                    if (result.extractedText.isNotBlank()) {
                        scannedBody = if (scannedBody.isBlank()) {
                            result.extractedText
                        } else {
                            scannedBody.trimEnd() + "\n\n" + result.extractedText
                        }
                        statusMessage = if (isEn) {
                            "Done reading photo. Check the note below before saving."
                        } else {
                            "Tulisan berhasil dibaca dan dirapikan. Cek dulu isinya di bawah sebelum disimpan."
                        }
                    } else {
                        statusMessage = if (isEn) {
                            "Photo taken, but the writing wasn't clear enough to read automatically. Try taking a closer photo or type below."
                        } else {
                            "Foto sudah masuk, tapi tulisannya kurang jelas terbaca. Coba foto lebih dekat atau ketik langsung di bawah."
                        }
                    }
                }.onFailure {
                    statusMessage = if (isEn) "Couldn't read the photo." else "Foto belum berhasil diproses."
                }
                isExtracting = false
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            captured = true
            isExtracting = true
            statusMessage = if (isEn) "Reading writing from image..." else "Sedang membaca tulisan dari gambar..."
            scope.launch {
                DocumentNoteExtractor.extractFromUri(context, uri, courseTitleHint = defaultCourseName)
                    .onSuccess { result ->
                        previewBitmap = result.previewBitmap
                        if (customTitle.isBlank()) {
                            customTitle = result.suggestedTitle
                        }
                        sourceSummary = result.fileName
                        if (result.extractedText.isNotBlank()) {
                            scannedBody = if (scannedBody.isBlank()) {
                                result.extractedText
                            } else {
                                scannedBody.trimEnd() + "\n\n" + result.extractedText
                            }
                            statusMessage = if (isEn) {
                                "Notes loaded from ${result.fileName}."
                            } else {
                                "Isi catatan dari ${result.fileName} sudah masuk."
                            }
                        } else {
                            statusMessage = if (isEn) {
                                "Image loaded, but no readable text was found. You can type your notes below."
                            } else {
                                "Gambar sudah dimuat, tapi belum ada tulisan yang terbaca jelas. Kamu bisa mengetik catatannya di bawah."
                            }
                        }
                    }
                    .onFailure { err ->
                        statusMessage = err.message ?: if (isEn) "Couldn't open image." else "Gambar tidak bisa dibuka."
                    }
                isExtracting = false
            }
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            captured = true
            isExtracting = true
            statusMessage = if (isEn) {
                "Reading course material from document..."
            } else {
                "Sedang mengambil inti materi dari file..."
            }
            scope.launch {
                DocumentNoteExtractor.extractFromUri(context, uri, courseTitleHint = defaultCourseName)
                    .onSuccess { result ->
                        if (result.previewBitmap != null) {
                            previewBitmap = result.previewBitmap
                        }
                        if (customTitle.isBlank()) {
                            customTitle = result.suggestedTitle
                        }
                        val pageLabel = if (result.pageCount > 1) " • ${result.pageCount} hal" else ""
                        sourceSummary = "${result.fileName}$pageLabel"
                        if (result.extractedText.isNotBlank()) {
                            scannedBody = if (scannedBody.isBlank()) {
                                result.extractedText
                            } else {
                                scannedBody.trimEnd() + "\n\n" + result.extractedText
                            }
                            statusMessage = if (isEn) {
                                "Course material from ${result.fileName} is ready."
                            } else {
                                "Materi dari ${result.fileName} sudah diambil dan dibersihkan dari halaman sampul/administrasi."
                            }
                        } else {
                            statusMessage = if (isEn) {
                                "Opened ${result.fileName}, but couldn't find readable text."
                            } else {
                                "File ${result.fileName} terbuka, tapi teks materinya belum terbaca."
                            }
                        }
                    }
                    .onFailure { err ->
                        statusMessage = err.message ?: if (isEn) "Couldn't read file." else "File tidak bisa dibaca."
                    }
                isExtracting = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                lString("Foto Papan Tulis & Catatan", "Photo Whiteboard & Notes"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                lString(
                    "Malas salin ulang papan tulis atau mau mengubah catatan buku tulis jadi digital? Foto saja atau masukkan file materi kuliahmu.",
                    "Photograph a classroom whiteboard, your handwritten notebook, or import a course PDF to turn it into clean notes."
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Pilihan Jenis Sumber (Papan Tulis / Tulisan Tangan / Slide & Modul)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                lString("Apa yang mau kamu foto?", "What are you capturing?"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple(
                        "WHITEBOARD",
                        lString("Papan Tulis Kelas", "Classroom Whiteboard"),
                        lString("Fokus baca spidol, poin materi, dan rumus di papan tulis", "Optimized for marker text, bullet points, and formulas")
                    ),
                    Triple(
                        "HANDWRITING",
                        lString("Tulisan Tangan / Buku", "Handwritten Notebook"),
                        lString("Susun ulang catatan tulis tangan jadi paragraf rapi", "Turns handwritten pages into clean paragraphs")
                    ),
                    Triple(
                        "DOCUMENT",
                        lString("Slide / Modul Cetak", "Slide / Printed Handout"),
                        lString("Ambil inti materi tanpa header kampus atau halaman sampul", "Extracts core material without cover-page noise")
                    )
                ).forEach { (key, label, _) ->
                    FilterChip(
                        selected = scanModeKey == key,
                        onClick = { scanModeKey = key },
                        label = { Text(label) }
                    )
                }
            }
            Text(
                text = when (activeScanMode) {
                    ScanSourceMode.WHITEBOARD -> lString(
                        "Mode Papan Tulis menajamkan kontras spidol dan merapikan poin-poin yang terpencar di papan tulis.",
                        "Whiteboard mode boosts marker contrast and organizes scattered board points."
                    )
                    ScanSourceMode.HANDWRITING -> lString(
                        "Mode Tulisan Tangan membantu menyatukan baris catatan buku tulismu supaya enak dibaca ulang.",
                        "Handwriting mode stitches handwritten lines into readable paragraphs."
                    )
                    ScanSourceMode.DOCUMENT -> lString(
                        "Mode Slide/Modul membuang informasi sampul, nama kampus, atau penerbit dan hanya mengambil isi materinya.",
                        "Slide/Handout mode strips cover metadata and keeps the actual subject matter."
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Kotak Pratinjau Foto / Dokumen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            val bmp = previewBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = lString("Foto catatan", "Note photo"),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(bottomStart = 12.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = if (sourceSummary.isNotBlank()) sourceSummary else modeDisplayName,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            } else {
                val guideColor = MaterialTheme.colorScheme.primary
                Canvas(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                    val cornerLen = 28.dp.toPx()
                    val stroke = 3.dp.toPx()
                    drawLine(guideColor, Offset(0f, 0f), Offset(cornerLen, 0f), stroke)
                    drawLine(guideColor, Offset(0f, 0f), Offset(0f, cornerLen), stroke)
                    drawLine(guideColor, Offset(size.width, 0f), Offset(size.width - cornerLen, 0f), stroke)
                    drawLine(guideColor, Offset(size.width, 0f), Offset(size.width, cornerLen), stroke)
                    drawLine(guideColor, Offset(0f, size.height), Offset(cornerLen, size.height), stroke)
                    drawLine(guideColor, Offset(0f, size.height), Offset(0f, size.height - cornerLen), stroke)
                    drawLine(guideColor, Offset(size.width, size.height), Offset(size.width - cornerLen, size.height), stroke)
                    drawLine(guideColor, Offset(size.width, size.height), Offset(size.width, size.height - cornerLen), stroke)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = if (captured) Icons.Default.Description else Icons.Default.DocumentScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp)
                    )
                    Text(
                        text = if (sourceSummary.isNotBlank()) {
                            sourceSummary
                        } else {
                            lString("Arahkan kamera ke $modeDisplayName", "Point camera at $modeDisplayName")
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = lString(
                            "Pastikan tulisan tidak tertutup pantulan lampu agar terbaca utuh.",
                            "Keep the writing clear of light glare for accurate reading."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isExtracting) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                        Text(
                            lString("Membaca & merapikan catatan...", "Reading & tidying notes..."),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Tombol Ambil Foto / Pilih dari Galeri / Masukkan File PDF
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    captured = true
                    runCatching { cameraLauncher.launch(null) }
                },
                modifier = Modifier.weight(1f).testTag("scan-capture")
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (captured) lString("Foto Lagi", "Take Another") else lString("Buka Kamera", "Open Camera"))
            }
            OutlinedButton(
                onClick = {
                    captured = true
                    runCatching { galleryLauncher.launch("image/*") }
                },
                modifier = Modifier.weight(1f).testTag("scan-gallery")
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(lString("Pilih Foto Galeri", "From Gallery"))
            }
        }

        OutlinedButton(
            onClick = {
                captured = true
                scanModeKey = "DOCUMENT"
                runCatching { fileLauncher.launch(DocumentNoteExtractor.SUPPORTED_MIME_TYPES) }
            },
            modifier = Modifier.fillMaxWidth().testTag("scan-file")
        ) {
            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(lString("Masukkan File Materi (PDF, Word, atau TXT)", "Import Course File (PDF, Word, or TXT)"))
        }

        statusMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        // Pilihan Mata Kuliah
        Text(
            lString("Simpan untuk Mata Kuliah", "Save Under Course"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (courses.isEmpty()) {
            Text(
                lString(
                    "Belum ada mata kuliah yang ditambahkan. Catatan ini akan masuk ke kategori Umum.",
                    "No courses added yet. This note will be saved under General."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                courses.forEachIndexed { index, course ->
                    FilterChip(
                        selected = selectedCourseIndex == index,
                        onClick = { selectedCourseIndex = index },
                        label = { Text("${course.code} • ${course.title}") }
                    )
                }
            }
        }

        // Isi Catatan & Tombol Simpan
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        lString("Isi Catatan", "Note Content"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (scannedBody.isNotBlank() && !isExtracting) {
                            TextButton(
                                onClick = {
                                    isExtracting = true
                                    statusMessage = if (isEn) "Tidying up note content..." else "Sedang merapikan susunan catatan..."
                                    scope.launch {
                                        val cleaned = DocumentNoteExtractor.cleanAcademicText(scannedBody)
                                        val refined = DocumentNoteExtractor.refineExtractedMaterialWithAi(
                                            rawCleanedText = cleaned,
                                            titleHint = customTitle.ifBlank { defaultCourseName },
                                            courseHint = defaultCourseName,
                                            sourceMode = activeScanMode
                                        )
                                        scannedBody = refined.ifBlank { cleaned }
                                        if (customTitle.isBlank()) {
                                            customTitle = DocumentNoteExtractor.suggestCleanTitle(scannedBody, defaultCourseName)
                                        }
                                        isExtracting = false
                                        statusMessage = if (isEn) "Note content tidied up." else "Susunan catatan sudah dirapikan."
                                    }
                                }
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(lString("Rapikan", "Tidy Up"))
                            }
                        }
                        if (scannedBody.isNotBlank() || previewBitmap != null) {
                            TextButton(
                                onClick = {
                                    scannedBody = ""
                                    customTitle = ""
                                    previewBitmap = null
                                    sourceSummary = ""
                                    statusMessage = null
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(lString("Kosongkan", "Clear"))
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { customTitle = it },
                    label = { Text(lString("Judul Catatan", "Note Title")) },
                    placeholder = { Text(lString("Contoh: Pertemuan 4 $defaultCourseName", "e.g., Week 4 $defaultCourseName")) },
                    modifier = Modifier.fillMaxWidth().testTag("scan-title-input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = scannedBody,
                    onValueChange = { scannedBody = it },
                    label = { Text(lString("Tulisan Catatan", "Note Text")) },
                    placeholder = {
                        Text(
                            lString(
                                "Hasil bacaan foto papan tulis, buku tulis, atau PDF akan muncul di sini. Kamu bebas mengeditnya sebelum disimpan.",
                                "Text from your whiteboard photo, notebook, or PDF will appear here. Feel free to edit before saving."
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("scan-text-input"),
                    minLines = 6
                )
                Button(
                    onClick = {
                        val finalTitle = customTitle.trim().ifBlank { "Catatan $defaultCourseName" }
                        val finalBody = scannedBody.trim()
                        if (onSaveToNote != null) {
                            onSaveToNote(finalTitle, defaultCourseCode, finalBody)
                        } else {
                            val db = StudentHubDatabase.get(context)
                            val noteId = "scan-" + UUID.randomUUID().toString().take(8)
                            db.saveNote(
                                id = noteId,
                                title = finalTitle,
                                course = defaultCourseCode,
                                body = finalBody
                            )
                            onSavedToNotes()
                        }
                    },
                    enabled = (captured || scannedBody.isNotBlank()) && scannedBody.trim().isNotBlank() && !isExtracting,
                    modifier = Modifier.fillMaxWidth().testTag("scan-save")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(lString("Simpan ke Catatan $defaultCourseCode", "Save to $defaultCourseCode Notes"))
                }
            }
        }
    }
}
