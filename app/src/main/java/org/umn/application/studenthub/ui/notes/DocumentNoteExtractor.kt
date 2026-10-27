package org.umn.application.studenthub.ui.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
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

    suspend fun extractFromUri(context: Context, uri: Uri): Result<ExtractedDocumentResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val fileName = resolveFileName(context, uri)
                val mimeType = context.contentResolver.getType(uri).orEmpty().lowercase()
                val lowerName = fileName.lowercase()
                val baseTitle = fileName.substringBeforeLast('.').ifBlank { "Catatan Impor" }

                when {
                    mimeType == "application/pdf" || lowerName.endsWith(".pdf") -> {
                        extractPdf(context, uri, fileName, baseTitle)
                    }
                    mimeType.startsWith("image/") ||
                        lowerName.endsWith(".jpg") ||
                        lowerName.endsWith(".jpeg") ||
                        lowerName.endsWith(".png") ||
                        lowerName.endsWith(".webp") -> {
                        extractImageUri(context, uri, fileName, baseTitle)
                    }
                    mimeType.contains("wordprocessingml") || lowerName.endsWith(".docx") -> {
                        extractDocx(context, uri, fileName, baseTitle)
                    }
                    else -> {
                        extractPlainText(context, uri, fileName, baseTitle)
                    }
                }
            }
        }

    suspend fun extractFromBitmap(
        bitmap: Bitmap,
        sourceName: String = "Hasil Pindai Kamera"
    ): Result<ExtractedDocumentResult> = withContext(Dispatchers.IO) {
        runCatching {
            val recognized = recognizeTextFromBitmap(bitmap).trim()
            val firstLine = recognized.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.length >= 4 }
                ?.take(60)
            val suggestedTitle = firstLine ?: sourceName
            val finalBody = if (recognized.isNotBlank()) {
                recognized
            } else {
                ""
            }
            ExtractedDocumentResult(
                fileName = "$sourceName.jpg",
                suggestedTitle = suggestedTitle,
                extractedText = finalBody,
                previewBitmap = bitmap,
                pageCount = 1,
                sourceType = "IMAGE"
            )
        }
    }

    private suspend fun extractImageUri(
        context: Context,
        uri: Uri,
        fileName: String,
        baseTitle: String
    ): ExtractedDocumentResult {
        val bitmap = decodeSampledBitmap(context, uri, maxDimension = 2048)
            ?: error("Gambar tidak dapat dibaca.")
        val recognized = recognizeTextFromBitmap(bitmap).trim()
        val firstLine = recognized.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.length >= 4 }
            ?.take(60)
        return ExtractedDocumentResult(
            fileName = fileName,
            suggestedTitle = firstLine ?: baseTitle,
            extractedText = recognized,
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
            } ?: error("File PDF tidak dapat dibuka.")

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
                            val scale = (1600f / maxOf(page.width, page.height, 1)).coerceIn(1.5f, 3.0f)
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bmp)
                            canvas.drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val pageText = recognizeTextFromBitmap(bmp).trim()
                            if (pageText.isNotBlank()) {
                                pageTexts.add(
                                    if (totalPages > 1) "--- Halaman ${pageIndex + 1} ---\n$pageText"
                                    else pageText
                                )
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
                combinedText = extractTextFromPdfStreams(tempFile.readBytes()).trim()
            }

            if (totalPages > 15 && combinedText.isNotBlank()) {
                combinedText += "\n\n[Catatan: Menampilkan 15 dari $totalPages halaman PDF]"
            }

            return ExtractedDocumentResult(
                fileName = fileName,
                suggestedTitle = baseTitle,
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
                            if (line.isNotBlank()) {
                                paragraphs.add(line)
                            }
                        }
                        break
                    }
                    entry = zis.nextEntry
                }
            }
        } ?: error("File dokumen tidak dapat dibuka.")

        return ExtractedDocumentResult(
            fileName = fileName,
            suggestedTitle = baseTitle,
            extractedText = paragraphs.joinToString("\n\n"),
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
        } ?: error("File tidak dapat dibaca.")

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

        return ExtractedDocumentResult(
            fileName = fileName,
            suggestedTitle = baseTitle,
            extractedText = content.trim(),
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
        return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "dokumen_impor.txt" } ?: "dokumen_impor.txt"
    }
}
