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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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
    var filterMode by rememberSaveable { mutableStateOf("B&W Dokumen") }
    var captured by rememberSaveable { mutableStateOf(false) }
    var isExtracting by remember { mutableStateOf(false) }
    var sourceSummary by rememberSaveable { mutableStateOf("") }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val selectedCourse = courses.getOrNull(selectedCourseIndex)
    val defaultCourseCode = selectedCourse?.code ?: "Umum"
    val defaultCourseName = selectedCourse?.title ?: lString("Catatan Umum", "General Notes")

    var customTitle by rememberSaveable(selectedCourseIndex) {
        mutableStateOf("")
    }
    var scannedBody by rememberSaveable(selectedCourseIndex) {
        mutableStateOf("")
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            captured = true
            previewBitmap = bitmap
            isExtracting = true
            statusMessage = if (isEn) "Extracting text from captured photo..." else "Mengekstrak teks dari foto kamera..."
            scope.launch {
                DocumentNoteExtractor.extractFromBitmap(
                    bitmap = bitmap,
                    sourceName = if (isEn) "Camera Scan" else "Pindaian Kamera"
                ).onSuccess { result ->
                    if (customTitle.isBlank()) {
                        customTitle = result.suggestedTitle
                    }
                    sourceSummary = if (isEn) "Source: Camera Photo" else "Sumber: Foto Kamera"
                    if (result.extractedText.isNotBlank()) {
                        scannedBody = if (scannedBody.isBlank()) {
                            result.extractedText
                        } else {
                            scannedBody.trimEnd() + "\n\n" + result.extractedText
                        }
                        statusMessage = if (isEn) {
                            "Text extracted from photo. Review or edit below before saving."
                        } else {
                            "Teks berhasil diekstrak dari foto. Periksa atau sunting di bawah sebelum disimpan."
                        }
                    } else {
                        statusMessage = if (isEn) {
                            "Photo captured, but no clear text was detected. You can type or edit manually below."
                        } else {
                            "Foto berhasil diambil, namun teks belum terbaca jelas. Kamu bisa mengetik atau melengkapi isi di bawah."
                        }
                    }
                }.onFailure {
                    statusMessage = if (isEn) "Failed to process camera photo." else "Gagal memproses foto kamera."
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
            statusMessage = if (isEn) "Reading image and extracting text..." else "Membaca gambar dan mengekstrak teks..."
            scope.launch {
                DocumentNoteExtractor.extractFromUri(context, uri)
                    .onSuccess { result ->
                        previewBitmap = result.previewBitmap
                        if (customTitle.isBlank()) {
                            customTitle = result.suggestedTitle
                        }
                        sourceSummary = "${result.fileName} (${result.sourceType})"
                        if (result.extractedText.isNotBlank()) {
                            scannedBody = if (scannedBody.isBlank()) {
                                result.extractedText
                            } else {
                                scannedBody.trimEnd() + "\n\n" + result.extractedText
                            }
                            statusMessage = if (isEn) {
                                "Extracted text from ${result.fileName}."
                            } else {
                                "Teks berhasil diekstrak dari ${result.fileName}."
                            }
                        } else {
                            statusMessage = if (isEn) {
                                "Image loaded (${result.fileName}), but no text was detected. Add notes manually below."
                            } else {
                                "Gambar dimuat (${result.fileName}), namun tidak ditemukan teks yang terbaca. Tambahkan catatan di bawah."
                            }
                        }
                    }
                    .onFailure { err ->
                        statusMessage = err.message ?: if (isEn) "Failed to read image." else "Gagal membaca gambar."
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
            statusMessage = if (isEn) "Extracting text from document..." else "Mengekstrak isi dokumen/PDF..."
            scope.launch {
                DocumentNoteExtractor.extractFromUri(context, uri)
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
                                "Imported ${result.fileName} (${result.pageCount} page(s))."
                            } else {
                                "Berhasil mengimpor ${result.fileName} (${result.pageCount} halaman)."
                            }
                        } else {
                            statusMessage = if (isEn) {
                                "Opened ${result.fileName}, but no extractable text was found."
                            } else {
                                "File ${result.fileName} dibuka, namun belum ditemukan teks yang dapat diekstrak."
                            }
                        }
                    }
                    .onFailure { err ->
                        statusMessage = err.message ?: if (isEn) "Failed to import file." else "Gagal mengimpor file."
                    }
                isExtracting = false
            }
        }
    }

    val activeColorFilter = remember(filterMode) {
        when (filterMode) {
            "B&W Dokumen" -> {
                val matrix = ColorMatrix().apply {
                    setToSaturation(0f)
                }
                ColorFilter.colorMatrix(matrix)
            }
            "Kontras Tinggi" -> {
                val contrast = 1.45f
                val translate = (-0.5f * contrast + 0.5f) * 255f
                val bwMatrix = ColorMatrix(
                    floatArrayOf(
                        0.3f * contrast, 0.59f * contrast, 0.11f * contrast, 0f, translate,
                        0.3f * contrast, 0.59f * contrast, 0.11f * contrast, 0f, translate,
                        0.3f * contrast, 0.59f * contrast, 0.11f * contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                ColorFilter.colorMatrix(bwMatrix)
            }
            else -> null
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
                lString("Pindai & Impor Catatan", "Scan & Import Notes"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                lString(
                    "Ambil foto papan tulis/buku dengan kamera, pilih gambar dari galeri, atau unggah file PDF/dokumen kuliah untuk diekstrak menjadi catatan.",
                    "Take a photo of a whiteboard or notebook, pick an image from gallery, or upload a PDF/document file to extract into a note."
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Pratinjau Kamera / Dokumen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            val bmp = previewBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = lString("Pratinjau dokumen", "Document preview"),
                    contentScale = ContentScale.Crop,
                    colorFilter = activeColorFilter,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                    shape = RoundedCornerShape(bottomStart = 12.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = if (sourceSummary.isNotBlank()) "$sourceSummary • $filterMode" else filterMode,
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
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = if (sourceSummary.isNotBlank()) {
                            sourceSummary
                        } else if (captured) {
                            lString("Mode Input Manual / Hasil Ekstraksi ($filterMode)", "Manual Input / Extracted Mode ($filterMode)")
                        } else {
                            lString("Pilih sumber pindaian di bawah (Kamera, Galeri, atau PDF/File)", "Choose a source below (Camera, Gallery, or PDF/File)")
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = lString(
                            "Teks pada foto atau dokumen PDF akan dibaca otomatis (OCR) dan dapat disunting sebelum disimpan.",
                            "Text in photos or PDF documents will be automatically extracted (OCR) and can be edited before saving."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isExtracting) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                        Text(
                            lString("Mengekstrak teks...", "Extracting text..."),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Filter Mode
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "B&W Dokumen" to lString("B&W Dokumen", "B&W Document"),
                "Asli (Warna)" to lString("Asli (Warna)", "Original (Color)"),
                "Kontras Tinggi" to lString("Kontras Tinggi", "High Contrast")
            ).forEach { (modeKey, modeLabel) ->
                FilterChip(
                    selected = filterMode == modeKey,
                    onClick = { filterMode = modeKey },
                    label = { Text(modeLabel) }
                )
            }
        }

        // Tombol Ambil Foto / Dari Galeri / Impor PDF & File
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
                Text(if (captured) lString("Foto Ulang", "Retake") else lString("Ambil Foto", "Take Photo"))
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
                Text(lString("Dari Galeri", "From Gallery"))
            }
        }

        OutlinedButton(
            onClick = {
                captured = true
                runCatching { fileLauncher.launch(DocumentNoteExtractor.SUPPORTED_MIME_TYPES) }
            },
            modifier = Modifier.fillMaxWidth().testTag("scan-file")
        ) {
            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(lString("Unggah File PDF / DOCX / TXT ke Catatan", "Upload PDF / DOCX / TXT File to Note"))
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

        // Pilihan Mata Kuliah Terkait
        Text(
            lString("Hubungkan ke Mata Kuliah", "Link to Course"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (courses.isEmpty()) {
            Text(
                lString(
                    "Belum ada mata kuliah terdaftar. Catatan akan disimpan ke kategori Umum.",
                    "No courses registered yet. Note will be saved under General."
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

        // Hasil Ekstraksi Teks & Simpan
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
                        lString("Hasil Ekstraksi & Isi Catatan", "Extracted Text & Note Content"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
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
                            Text(lString("Bersihkan", "Clear"))
                        }
                    }
                }
                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { customTitle = it },
                    label = { Text(lString("Judul Catatan", "Note Title")) },
                    placeholder = { Text(lString("Misal: Rangkuman Bab 3 $defaultCourseName", "e.g., Chapter 3 Summary $defaultCourseName")) },
                    modifier = Modifier.fillMaxWidth().testTag("scan-title-input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = scannedBody,
                    onValueChange = { scannedBody = it },
                    label = { Text(lString("Isi Catatan (Hasil OCR / PDF / Ketik Manual)", "Note Content (OCR / PDF / Manual Text)")) },
                    placeholder = {
                        Text(
                            lString(
                                "Ambil foto, pilih gambar dari galeri, atau unggah PDF di atas untuk mengisi otomatis, atau ketik langsung di sini...",
                                "Take a photo, pick an image from gallery, or upload a PDF above to auto-fill, or type directly here..."
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("scan-text-input"),
                    minLines = 6
                )
                Button(
                    onClick = {
                        val finalTitle = customTitle.trim().ifBlank { "Catatan Pindai $defaultCourseName" }
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
                    Text(lString("Simpan ke Catatan Kuliah ($defaultCourseCode)", "Save to Class Notes ($defaultCourseCode)"))
                }
            }
        }
    }
}
