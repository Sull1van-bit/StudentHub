package org.umn.application.studenthub.ui.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Inflater
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume

enum class ScanSourceMode {
    WHITEBOARD,
    HANDWRITING,
    DOCUMENT
}

data class ExtractedDocumentResult(
    val fileName: String,
    val suggestedTitle: String,
    val extractedText: String,
    val previewBitmap: Bitmap? = null,
    val pageCount: Int = 1,
    val sourceType: String = "FILE"
)

object DocumentNoteExtractor {

    val SUPPORTED_MIME_TYPES = arrayOf(
        "application/pdf",
        "text/plain",
        "text/markdown",
        "text/csv",
        "text/html",
        "application/json",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "image/*"
    )

    private val ADMIN_LINE_PATTERNS = listOf(
        Regex("^---\\s*halaman\\s*\\d+\\s*---$", RegexOption.IGNORE_CASE),
        Regex("^(halaman|page|hal\\.?)\\s*\\d+(\\s*(dari|of|/)\\s*\\d+)?$", RegexOption.IGNORE_CASE),
        Regex("\\b(universitas|fakultas|program studi|prodi|jurusan|departemen|kampus|scientia boulevard|gading serpong)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(penerbit|diterbitkan oleh|published by|percetakan|hak cipta|copyright|all rights reserved|isbn|issn|cetakan ke|edisi ke)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(tanggal ditulis|jadwal ditulis|tanggal disusun|tanggal pembuatan|tanggal revisi|tanggal berlaku|tanggal efektif|jadwal perkuliahan|jadwal kuliah)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(disusun oleh|ditulis oleh|dibuat oleh|direvisi oleh|diperiksa oleh|disetujui oleh|disahkan oleh|dosen pengampu|koordinator mata kuliah|kaprodi|ketua program studi|dekan)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(kode dokumen|nomor dokumen|status dokumen|revisi ke|rencana pembelajaran semester|silabus mata kuliah|kontrak perkuliahan|bobot sks|prasyarat mata kuliah)\\b", RegexOption.IGNORE_CASE),
        Regex("^(daftar isi|table of contents|kata pengantar|prakata|daftar pustaka|referensi|bibliografi|lampiran)$", RegexOption.IGNORE_CASE),
        Regex("^.{3,}\\.{4,}\\s*\\d+$")
    )

    private val ADMIN_KEY_VALUE_PREFIX = Regex(
        "^(nama kampus|perguruan tinggi|institusi|fakultas|program studi|penerbit|penulis|penyusun|dosen|jadwal|tanggal|revisi|semester|tahun akademik|kode matkul|kode mk|bobot|sks|ruang|kelas|no\\.? dokumen|isbn|hak cipta)\\s*[:=-]",
        RegexOption.IGNORE_CASE
    )

    fun isAdministrativeNoiseLine(rawLine: String): Boolean {
        val line = rawLine.trim()
        if (line.isBlank()) return true
        if (line.length <= 2 && !line.any { it.isLetterOrDigit() }) return true
        if (ADMIN_KEY_VALUE_PREFIX.containsMatchIn(line)) return true

        // Short lines that match administrative headers/footers/publisher/campus metadata
        if (line.length < 130 && ADMIN_LINE_PATTERNS.any { it.containsMatchIn(line) }) {
            // Keep the line only if it clearly contains a conceptual definition ("adalah", "merupakan", etc.)
            val hasConceptVerb = Regex("\\b(adalah|merupakan|berfungsi|didefinisikan|yaitu|ialah|digunakan untuk|bertujuan untuk)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(line)
            if (!hasConceptVerb) return true
        }
        return false
    }

    fun cleanAcademicText(rawText: String): String {
        if (rawText.isBlank()) return ""
        val lines = rawText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()

        val cleanedLines = mutableListOf<String>()
        for (rawLine in lines) {
            val trimmed = rawLine.trim().replace(Regex("\\s+"), " ")
            if (trimmed.isBlank()) {
                if (cleanedLines.isNotEmpty() && cleanedLines.last().isNotBlank()) {
                    cleanedLines.add("")
                }
                continue
            }
            if (isAdministrativeNoiseLine(trimmed)) continue
            cleanedLines.add(trimmed)
        }

        val joined = cleanedLines
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

        // Fallback: if the entire document was very short and everything got filtered, return basic trimmed lines without page headers
        return if (joined.length >= 20) {
            joined
        } else {
            lines.map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("--- Halaman", ignoreCase = true) }
                .joinToString("\n")
                .trim()
        }
    }

    fun suggestCleanTitle(cleanedText: String, fallbackTitle: String): String {
        val candidate = cleanedText.lineSequence()
            .map { it.trim().removePrefix("#").removePrefix("•").removePrefix("-").trim() }
            .firstOrNull { line ->
                line.length in 5..70 &&
                    !isAdministrativeNoiseLine(line) &&
                    !line.endsWith(".") &&
                    line.count { it.isLetter() } >= 4
            }
        val rawTitle = candidate ?: fallbackTitle
        return rawTitle
            .replace(Regex("[_\\-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(65)
            .ifBlank { "Catatan Kuliah" }
    }

    suspend fun extractFromUri(
        context: Context,
        uri: Uri,
        courseTitleHint: String = ""
    ): Result<ExtractedDocumentResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val fileName = resolveFileName(context, uri)
                val mimeType = context.contentResolver.getType(uri).orEmpty().lowercase()
                val lowerName = fileName.lowercase()
                val baseTitle = fileName.substringBeforeLast('.').ifBlank { "Catatan Kuliah" }

                val rawResult = when {
                    mimeType == "application/pdf" || lowerName.endsWith(".pdf") -> {
                        extractPdf(context, uri, fileName, baseTitle)
                    }
                    mimeType.startsWith("image/") ||
                        lowerName.endsWith(".jpg") ||
                        lowerName.endsWith(".jpeg") ||
                        lowerName.endsWith(".png") ||
                        lowerName.endsWith(".webp") -> {
                        extractImageUri(context, uri, fileName, baseTitle, ScanSourceMode.WHITEBOARD)
                    }
                    mimeType.contains("wordprocessingml") || lowerName.endsWith(".docx") -> {
                        extractDocx(context, uri, fileName, baseTitle)
                    }
                    else -> {
                        extractPlainText(context, uri, fileName, baseTitle)
                    }
                }

                val cleaned = cleanAcademicText(rawResult.extractedText)
                val refined = refineExtractedMaterialWithAi(
                    rawCleanedText = cleaned,
                    titleHint = suggestCleanTitle(cleaned, rawResult.suggestedTitle),
                    courseHint = courseTitleHint,
                    sourceMode = if (rawResult.sourceType == "IMAGE") ScanSourceMode.WHITEBOARD else ScanSourceMode.DOCUMENT
                )
                val finalTitle = suggestCleanTitle(refined, rawResult.suggestedTitle)
                rawResult.copy(
                    suggestedTitle = finalTitle,
                    extractedText = refined
                )
            }
        }

    suspend fun extractFromBitmap(
        bitmap: Bitmap,
        sourceName: String = "Catatan Papan Tulis",
        scanMode: ScanSourceMode = ScanSourceMode.WHITEBOARD,
        courseTitleHint: String = ""
    ): Result<ExtractedDocumentResult> = withContext(Dispatchers.IO) {
        runCatching {
            val processedBitmap = preprocessBitmapForOcr(bitmap, scanMode)
            val recognizedRaw = recognizeTextFromBitmap(processedBitmap).trim()
            val fallbackRaw = if (recognizedRaw.length < 10 && processedBitmap !== bitmap) {
                recognizeTextFromBitmap(bitmap).trim()
            } else {
                recognizedRaw
            }
            if (processedBitmap !== bitmap) {
                runCatching { processedBitmap.recycle() }
            }

            val cleaned = cleanAcademicText(fallbackRaw)
            val refined = if (cleaned.isNotBlank()) {
                refineExtractedMaterialWithAi(
                    rawCleanedText = cleaned,
                    titleHint = sourceName,
                    courseHint = courseTitleHint,
                    sourceMode = scanMode
                )
            } else {
                ""
            }
            val suggestedTitle = suggestCleanTitle(refined.ifBlank { cleaned }, sourceName)

            ExtractedDocumentResult(
                fileName = "$sourceName.jpg",
                suggestedTitle = suggestedTitle,
                extractedText = refined.ifBlank { cleaned },
                previewBitmap = bitmap,
                pageCount = 1,
                sourceType = "IMAGE"
            )
        }
    }

    suspend fun refineExtractedMaterialWithAi(
        rawCleanedText: String,
        titleHint: String,
        courseHint: String = "",
        sourceMode: ScanSourceMode = ScanSourceMode.DOCUMENT
    ): String {
        val trimmed = rawCleanedText.trim()
        if (trimmed.length < 35 || !NoteAiAssistant.isRemoteApiConfigured()) {
            return trimmed
        }

        val systemPrompt = when (sourceMode) {
            ScanSourceMode.WHITEBOARD -> {
                "Kamu adalah asisten belajar mahasiswa. Rapikan hasil bacaan OCR dari foto papan tulis kelas berikut menjadi catatan kuliah yang rapi, jelas, dan mudah dipelajari. " +
                    "Pertahankan semua konsep, definisi, rumus, langkah penyelesaian, dan contoh yang ada di papan tulis. " +
                    "Perbaiki kata yang salah terbaca karena silau atau tulisan spidol, tetapi JANGAN mengarang materi di luar yang tertulis. " +
                    "Jangan gunakan pembuka basa-basi dan jangan gunakan karakter em dash."
            }
            ScanSourceMode.HANDWRITING -> {
                "Kamu adalah asisten belajar mahasiswa. Rapikan hasil bacaan OCR dari foto catatan tulis tangan mahasiswa berikut menjadi catatan yang terstruktur dan mudah dibaca ulang. " +
                    "Susun poin-poin, definisi, dan rumus agar rapi, perbaiki ejaan yang terpotong karena tulisan tangan, tetapi JANGAN menambah fakta di luar isi catatan. " +
                    "Jangan gunakan pembuka basa-basi dan jangan gunakan karakter em dash."
            }
            ScanSourceMode.DOCUMENT -> {
                "Kamu adalah asisten belajar mahasiswa. Susun isi dokumen/PDF kuliah berikut menjadi catatan materi kuliah yang fokus pada substansi pelajaran (pengertian, konsep inti, poin penting, rumus/tahapan, dan contoh). " +
                    "ABAIKAN dan BUANG seluruh informasi administrasi dokumen seperti nama kampus/universitas, fakultas, penerbit, ISBN, hak cipta, jadwal penulisan/revisi dokumen, nama dosen/penyusun, daftar isi, atau tabel pengesahan RPS. " +
                    "Jangan gunakan pembuka basa-basi dan jangan gunakan karakter em dash."
            }
        }

        val userPrompt = buildString {
            if (courseHint.isNotBlank()) appendLine("Mata Kuliah: $courseHint")
            if (titleHint.isNotBlank()) appendLine("Topik/Judul: $titleHint")
            appendLine("\nTeks Sumber:\n${trimmed.take(9000)}")
        }

        val aiResult = NoteAiAssistant.requestRemoteCompletion(systemPrompt, userPrompt)
        return if (!aiResult.isNullOrBlank() && aiResult.length >= 25) {
            aiResult.trim()
        } else {
            trimmed
        }
    }

    private fun preprocessBitmapForOcr(source: Bitmap, mode: ScanSourceMode): Bitmap {
        if (mode == ScanSourceMode.DOCUMENT) return source
        return runCatching {
            val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val contrast = if (mode == ScanSourceMode.WHITEBOARD) 1.35f else 1.25f
            val translate = (-0.5f * contrast + 0.5f) * 255f
            val matrix = ColorMatrix(
                floatArrayOf(
                    0.3f * contrast, 0.59f * contrast, 0.11f * contrast, 0f, translate,
                    0.3f * contrast, 0.59f * contrast, 0.11f * contrast, 0f, translate,
                    0.3f * contrast, 0.59f * contrast, 0.11f * contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(matrix)
            }
            canvas.drawBitmap(source, 0f, 0f, paint)
            out
        }.getOrDefault(source)
    }

    private suspend fun extractImageUri(
        context: Context,
        uri: Uri,
        fileName: String,
        baseTitle: String,
        scanMode: ScanSourceMode = ScanSourceMode.WHITEBOARD
    ): ExtractedDocumentResult {
        val bitmap = decodeSampledBitmap(context, uri, maxDimension = 2200)
            ?: error("Gambar tidak bisa dibuka.")
        val processed = preprocessBitmapForOcr(bitmap, scanMode)
        val recognized = recognizeTextFromBitmap(processed).trim()
        if (processed !== bitmap) {
            runCatching { processed.recycle() }
        }
        val cleaned = cleanAcademicText(recognized)
        return ExtractedDocumentResult(
            fileName = fileName,
            suggestedTitle = suggestCleanTitle(cleaned, baseTitle),
            extractedText = cleaned,
            previewBitmap = bitmap,
            pageCount = 1,
            sourceType = "IMAGE"
        )
    }

    private suspend fun extractPdf(
        context: Context,
        uri: Uri,
        fileName: String,
        baseTitle: String
    ): ExtractedDocumentResult {
        val tempFile = File.createTempFile("studenthub_import_", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: error("File PDF tidak bisa dibuka.")

            val pageTexts = mutableListOf<String>()
            var firstPageBitmap: Bitmap? = null
            var totalPages = 0

            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    totalPages = renderer.pageCount
                    val pagesToRead = totalPages.coerceAtMost(15)
                    for (pageIndex in 0 until pagesToRead) {
                        renderer.openPage(pageIndex).use { page ->
                            val scale = (1800f / maxOf(page.width, page.height, 1)).coerceIn(1.5f, 3.2f)
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bmp)
                            canvas.drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val rawPageText = recognizeTextFromBitmap(bmp).trim()
                            val cleanedPageText = cleanAcademicText(rawPageText)
                            if (cleanedPageText.isNotBlank()) {
                                // Skip page 1 if it only contains a couple of short cover-page lines and there are more pages
                                val nonBlankLines = cleanedPageText.lines().filter { it.isNotBlank() }
                                val isLikelyCoverPage = pageIndex == 0 && totalPages > 1 &&
                                    nonBlankLines.size <= 4 &&
                                    cleanedPageText.length < 180
                                if (!isLikelyCoverPage) {
                                    pageTexts.add(cleanedPageText)
                                }
                            }

                            if (pageIndex == 0) {
                                firstPageBitmap = bmp
                            } else {
                                bmp.recycle()
                            }
                        }
                    }
                }
            }

            var combinedText = pageTexts.joinToString("\n\n").trim()
            if (combinedText.isBlank()) {
                val streamRaw = extractTextFromPdfStreams(tempFile.readBytes()).trim()
                combinedText = cleanAcademicText(streamRaw)
            }

            return ExtractedDocumentResult(
                fileName = fileName,
                suggestedTitle = suggestCleanTitle(combinedText, baseTitle),
                extractedText = combinedText,
                previewBitmap = firstPageBitmap,
                pageCount = totalPages.coerceAtLeast(1),
                sourceType = "PDF"
            )
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    private fun extractDocx(
        context: Context,
        uri: Uri,
        fileName: String,
        baseTitle: String
    ): ExtractedDocumentResult {
        val paragraphs = mutableListOf<String>()
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.equals("word/document.xml", ignoreCase = true)) {
                        val xml = zis.readBytes().toString(StandardCharsets.UTF_8)
                        val pRegex = Regex("<w:p[\\s>].*?</w:p>", RegexOption.DOT_MATCHES_ALL)
                        val tRegex = Regex("<w:t(?:\\s[^>]*)?>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL)
                        pRegex.findAll(xml).forEach { pMatch ->
                            val line = tRegex.findAll(pMatch.value)
                                .map { decodeXmlEntities(it.groupValues[1]) }
                                .joinToString("")
                                .trim()
                            if (line.isNotBlank() && !isAdministrativeNoiseLine(line)) {
                                paragraphs.add(line)
                            }
                        }
                        break
                    }
                    entry = zis.nextEntry
                }
            }
        } ?: error("File dokumen tidak bisa dibuka.")

        val combined = cleanAcademicText(paragraphs.joinToString("\n\n"))
        return ExtractedDocumentResult(
            fileName = fileName,
            suggestedTitle = suggestCleanTitle(combined, baseTitle),
            extractedText = combined,
            previewBitmap = null,
            pageCount = 1,
            sourceType = "DOCX"
        )
    }

    private fun extractPlainText(
        context: Context,
        uri: Uri,
        fileName: String,
        baseTitle: String
    ): ExtractedDocumentResult {
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var total = 0
            while (total < 512 * 1024) {
                val read = input.read(chunk)
                if (read <= 0) break
                buffer.write(chunk, 0, read)
                total += read
            }
            buffer.toByteArray()
        } ?: error("File tidak bisa dibaca.")

        var content = rawBytes.toString(StandardCharsets.UTF_8)
        if (fileName.endsWith(".html", ignoreCase = true) || fileName.endsWith(".htm", ignoreCase = true)) {
            content = content
                .replace(Regex("<script.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
                .replace(Regex("<style.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
                .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
                .replace(Regex("<[^>]+>"), " ")
                .lines()
                .joinToString("\n") { it.trim().replace(Regex("\\s+"), " ") }
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
        }

        val cleaned = cleanAcademicText(content)
        return ExtractedDocumentResult(
            fileName = fileName,
            suggestedTitle = suggestCleanTitle(cleaned, baseTitle),
            extractedText = cleaned,
            previewBitmap = null,
            pageCount = 1,
            sourceType = "TEXT"
        )
    }

    private suspend fun recognizeTextFromBitmap(bitmap: Bitmap): String =
        suspendCancellableCoroutine { cont ->
            runCatching {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val image = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        recognizer.close()
                        if (cont.isActive) cont.resume(visionText.text.orEmpty())
                    }
                    .addOnFailureListener {
                        recognizer.close()
                        if (cont.isActive) cont.resume("")
                    }
            }.onFailure {
                if (cont.isActive) cont.resume("")
            }
        }

    private fun decodeSampledBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, boundsOptions)
        }
        val width = boundsOptions.outWidth
        val height = boundsOptions.outHeight
        if (width <= 0 || height <= 0) return null

        var sampleSize = 1
        while (width / sampleSize > maxDimension || height / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        }
    }

    private fun extractTextFromPdfStreams(pdfBytes: ByteArray): String {
        val rawLatin = String(pdfBytes, StandardCharsets.ISO_8859_1)
        val extractedChunks = mutableListOf<String>()
        val streamRegex = Regex("stream\\r?\\n(.*?)\\r?\\nendstream", RegexOption.DOT_MATCHES_ALL)

        for (match in streamRegex.findAll(rawLatin)) {
            val streamData = match.groupValues[1].toByteArray(StandardCharsets.ISO_8859_1)
            val decoded = tryInflate(streamData) ?: streamData
            val text = String(decoded, StandardCharsets.ISO_8859_1)
            extractPdfTextOperators(text, extractedChunks)
        }
        if (extractedChunks.isEmpty()) {
            extractPdfTextOperators(rawLatin, extractedChunks)
        }
        return extractedChunks.joinToString("\n").replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    private fun tryInflate(data: ByteArray): ByteArray? = runCatching {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!inflater.finished() && !inflater.needsInput()) {
            val count = inflater.inflate(buf)
            if (count <= 0) break
            out.write(buf, 0, count)
        }
        inflater.end()
        out.toByteArray().takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun extractPdfTextOperators(content: String, out: MutableList<String>) {
        val btEtRegex = Regex("BT(.*?)ET", RegexOption.DOT_MATCHES_ALL)
        val literalRegex = Regex("\\(([^()\\\\]*(?:\\\\.[^()\\\\]*)*)\\)")
        for (block in btEtRegex.findAll(content)) {
            val pieces = literalRegex.findAll(block.groupValues[1])
                .map { unescapePdfLiteral(it.groupValues[1]) }
                .filter { it.isNotBlank() }
                .toList()
            if (pieces.isNotEmpty()) {
                val line = pieces.joinToString(" ").replace(Regex("\\s+"), " ").trim()
                if (line.any { it.isLetterOrDigit() }) {
                    out.add(line)
                }
            }
        }
    }

    private fun unescapePdfLiteral(raw: String): String {
        return raw
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", " ")
            .replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\\\", "\\")
    }

    private fun decodeXmlEntities(raw: String): String {
        return raw
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    fun resolveFileName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            val name = cursor.getString(idx)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "dokumen_kuliah.txt" } ?: "dokumen_kuliah.txt"
    }
}
