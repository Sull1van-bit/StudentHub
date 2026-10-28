package org.umn.application.studenthub.ui.notes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.umn.application.studenthub.BuildConfig
import org.umn.application.studenthub.data.local.AgendaRow
import org.umn.application.studenthub.data.local.AiChatMessageRow
import org.umn.application.studenthub.data.local.AiStudyProgressRow
import org.umn.application.studenthub.data.local.CourseRow
import org.umn.application.studenthub.data.local.NoteRow
import org.umn.application.studenthub.data.local.StudentHubDatabase
import org.umn.application.studenthub.data.local.StudyPlanRow
import org.umn.application.studenthub.data.local.TaskRow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URI
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection
import kotlin.math.roundToInt

data class NoteSummary(
    val overview: String,
    val keyPoints: List<String>,
    val glossaryTerms: List<Pair<String, String>>
)

data class StudySection(
    val heading: String,
    val details: List<String>
)

data class StudyGuide(
    val topic: String,
    val overview: String,
    val sections: List<StudySection>,
    val examFocusPoints: List<String>
)

data class QuizQuestion(
    val id: String,
    val question: String,
    val answer: String,
    val explanation: String
)

data class StudyQuiz(
    val questions: List<QuizQuestion>
)

data class CanvasConceptNode(
    val id: String,
    val label: String,
    val detail: String,
    val quote: String,
    val angleDeg: Float,
    val radiusFraction: Float
)

data class ConceptMatchPair(
    val id: String,
    val term: String,
    val definition: String
)

data class CanvasStudyData(
    val centralTopic: String,
    val nodes: List<CanvasConceptNode>,
    val matchPairs: List<ConceptMatchPair>,
    val eli5Text: String,
    val mnemonicText: String,
    val caseStudyText: String
)

data class ScoreReasonItem(
    val label: String,
    val detail: String,
    val rawPercent: Int,
    val weightPercent: Int,
    val contributionPercent: Int
)

data class StudentProgressAssessment(
    val hasData: Boolean = true,
    val readinessScore: Int,
    val statusLabel: String,
    val summaryText: String,
    val scoreReasoningSummary: String = "",
    val scoreBreakdown: List<ScoreReasonItem> = emptyList(),
    val noteCoveragePercent: Int,
    val quizMasteryPercent: Int,
    val taskCompletionPercent: Int,
    val semesterGradePercent: Int = 0,
    val currentGpa: Double,
    val targetGpa: Double,
    val strengths: List<String>,
    val focusAreas: List<String>
)

data class RecommendedStudySlot(
    val id: String,
    val dayIndex: Int,
    val dayLabel: String,
    val startTime: String,
    val timeRangeLabel: String,
    val courseCode: String,
    val title: String,
    val reason: String
)

object NoteAiAssistant {

    private val STOP_WORDS = setOf(
        "yang", "dan", "di", "ke", "dari", "ini", "itu", "dengan", "untuk", "pada", "adalah",
        "sebagai", "dalam", "atau", "juga", "akan", "oleh", "karena", "bisa", "dapat", "tidak",
        "the", "and", "for", "with", "that", "this", "from", "are", "was", "were", "have", "has",
        "not", "but", "can", "will", "into", "about", "more", "other", "such", "than", "then",
        "universitas", "fakultas", "program", "studi", "prodi", "kampus", "penerbit", "halaman",
        "dokumen", "tanggal", "jadwal", "ditulis", "disusun", "revisi", "semester", "dosen",
        "mahasiswa", "matakuliah", "kuliah", "modul", "bab", "bagian", "daftar", "pustaka",
        "isbn", "issn", "copyright", "cetakan", "edisi", "pengampu", "koordinator", "kaprodi"
    )

    private val INSTRUCTIONAL_SIGNAL_REGEX = Regex(
        "\\b(adalah|merupakan|ialah|yaitu|berfungsi|digunakan|bertujuan|proses|tahap|langkah|prinsip|konsep|metode|rumus|algoritma|sistem|struktur|komponen|faktor|perbedaan|hubungan|contoh|definisi|karakteristik|kelebihan|kekurangan|analisis|fungsi|cara kerja)\\b",
        RegexOption.IGNORE_CASE
    )

    private val ADMIN_SENTENCE_NOISE_REGEX = Regex(
        "\\b(jadwal ditulis|tanggal ditulis|tanggal disusun|tanggal revisi|diterbitkan oleh|penerbit|hak cipta|all rights reserved|isbn|issn|kode dokumen|nomor dokumen|disusun oleh|direvisi oleh|disetujui oleh|rencana pembelajaran semester|universitas multimedia nusantara|scientia boulevard|gading serpong|fakultas teknik|program studi)\\b",
        RegexOption.IGNORE_CASE
    )

    @Volatile
    var lastRemoteError: String? = null
        private set

    fun isRemoteApiConfigured(): Boolean = BuildConfig.AI_API_KEY.isNotBlank()

    private fun redactSecrets(raw: String): String {
        val key = BuildConfig.AI_API_KEY
        val withoutEmDash = raw.replace("—", ", ").replace("–", "-")
        return if (key.isNotBlank() && key.length >= 6) {
            withoutEmDash.replace(key, "[REDACTED]")
        } else {
            withoutEmDash
        }
    }

    private fun sanitizeInput(text: String, maxChars: Int): String =
        text.replace("\u0000", "").replace("—", "-").replace("–", "-").trim().take(maxChars)

    // Identitas aplikasi untuk header X-Android-Package / X-Android-Cert.
    // Dipakai Google untuk memvalidasi API key yang dibatasi ke aplikasi Android (package + SHA-1).
    @Volatile
    private var androidPackageName: String = ""

    @Volatile
    private var androidCertSha1: String = ""

    fun init(context: android.content.Context) {
        androidPackageName = context.packageName
        androidCertSha1 = runCatching { signingCertSha1(context) }.getOrDefault("")
    }

    @Suppress("DEPRECATION")
    private fun signingCertSha1(context: android.content.Context): String {
        val pm = context.packageManager
        val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners
        } else {
            pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNATURES).signatures
        }
        val cert = signatures?.firstOrNull()?.toByteArray() ?: return ""
        return java.security.MessageDigest.getInstance("SHA-1").digest(cert)
            .joinToString("") { "%02X".format(it) }
    }

    private fun isRetryableStatus(status: Int): Boolean = status == 408 || status == 429 || status in 500..599

    private fun describeHttpError(status: Int): String = when (status) {
        400 -> "Permintaan ke AI tidak valid (HTTP 400). Cek nama model di AI_MODEL."
        401, 403 -> "API key ditolak (HTTP $status). Cek key, batasan key, atau izin project di Google AI Studio."
        404 -> "Model atau endpoint tidak ditemukan (HTTP 404). Cek AI_MODEL dan AI_BASE_URL."
        429 -> "Batas pemakaian AI tercapai (HTTP 429). Coba lagi sebentar lagi."
        in 500..599 -> "Server AI sedang sibuk (HTTP $status). Coba lagi sebentar lagi."
        else -> "Gagal menghubungi AI (HTTP $status)."
    }

    /** Hapus "/models" di ujung base URL supaya endpoint tidak menjadi ".../models/models/...". */
    private fun normalizeBaseUrl(raw: String): String {
        var url = raw.trim().trimEnd('/')
        if (url.endsWith("/models", ignoreCase = true)) url = url.dropLast("/models".length)
        return url
    }

    private class AiHttpResult(val status: Int, val text: String?, val error: String?, val retryAfterMs: Long?)

    /**
     * Memanggil endpoint AI lewat HTTPS dengan key di header (tidak pernah di URL).
     *
     * Untuk Gemini API mengikuti panduan resmi Google:
     * - `thinkingLevel = low` dan `maxOutputTokens` longgar, karena token berpikir ikut dihitung
     *   dalam batas output. Batas kecil membuat jawaban terpotong atau kosong.
     * - `temperature` tidak dikirim (diabaikan di Gemini 3.6+ dan akan ditolak di rilis berikutnya).
     * - Retry hanya untuk error sementara (408, 429, 5xx) dengan exponential backoff + jitter,
     *   lalu pindah ke model cadangan jika model utama sedang penuh.
     */
    suspend fun requestRemoteCompletion(
        systemPrompt: String,
        userPrompt: String,
        preferredMaxTokens: Int = 2048,
        base64JpegImage: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.AI_API_KEY.trim()
        val baseUrl = normalizeBaseUrl(BuildConfig.AI_BASE_URL)
        if (apiKey.isBlank() || !baseUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext null
        }

        val isGeminiNative = baseUrl.contains("generativelanguage.googleapis.com", ignoreCase = true)
        val primaryModel = BuildConfig.AI_MODEL.trim().ifBlank { "gemini-3.5-flash" }
        val models = listOf(primaryModel, BuildConfig.AI_FALLBACK_MODEL.trim())
            .filter { it.isNotBlank() }
            .distinct()

        val maxAttemptsPerModel = 3
        for (model in models) {
            for (attempt in 0 until maxAttemptsPerModel) {
                val result = runCatching {
                    callModelOnce(baseUrl, apiKey, model, isGeminiNative, systemPrompt, userPrompt, preferredMaxTokens, base64JpegImage)
                }.getOrElse { err ->
                    // Timeout / jaringan putus dianggap error sementara.
                    AiHttpResult(-1, null, "Koneksi ke AI terputus (${err.javaClass.simpleName}).", null)
                }

                if (result.status in 200..299 && !result.text.isNullOrBlank()) {
                    lastRemoteError = null
                    return@withContext result.text
                }

                lastRemoteError = result.error ?: describeHttpError(result.status)
                val retryable = result.status == -1 || isRetryableStatus(result.status)
                if (!retryable) {
                    // 400/401/403: mengulang tidak akan membantu, model cadangan pun pakai key yang sama.
                    // 404 (model tidak ada) masih bisa ditolong model cadangan.
                    if (result.status in 400..499 && result.status != 404) return@withContext null
                    break
                }
                if (attempt < maxAttemptsPerModel - 1) {
                    val backoff = 1000L shl attempt // 1s, 2s, 4s
                    val jitter = kotlin.random.Random.nextLong(0, 500)
                    kotlinx.coroutines.delay(maxOf(result.retryAfterMs ?: 0L, backoff + jitter).coerceAtMost(10_000L))
                }
            }
        }
        null
    }

    private fun callModelOnce(
        baseUrl: String,
        apiKey: String,
        model: String,
        isGeminiNative: Boolean,
        systemPrompt: String,
        userPrompt: String,
        preferredMaxTokens: Int,
        base64JpegImage: String?
    ): AiHttpResult {
        val endpoint = if (isGeminiNative) "$baseUrl/models/$model:generateContent" else "$baseUrl/chat/completions"
        val conn = (URI(endpoint).toURL().openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (isGeminiNative) {
                setRequestProperty("x-goog-api-key", apiKey)
                if (androidPackageName.isNotBlank()) setRequestProperty("X-Android-Package", androidPackageName)
                if (androidCertSha1.isNotBlank()) setRequestProperty("X-Android-Cert", androidCertSha1)
            } else {
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
        }

        val userPromptText = userPrompt.take(12_000)
        val payloadJson = if (isGeminiNative) {
            JSONObject().apply {
                put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
                val userParts = JSONArray().put(JSONObject().put("text", userPromptText))
                if (!base64JpegImage.isNullOrBlank()) {
                    userParts.put(JSONObject().put("inlineData", JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", base64JpegImage)
                    }))
                }
                put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", userParts)))
                put("generationConfig", JSONObject().apply {
                    // Batas keras saja; kontrol biaya/latensi lewat thinkingLevel, bukan batas output kecil.
                    put("maxOutputTokens", maxOf(preferredMaxTokens, 2048).coerceAtMost(8192))
                    put("thinkingConfig", JSONObject().put("thinkingLevel", "low"))
                })
            }.toString()
        } else {
            JSONObject().apply {
                put("model", model)
                put("max_tokens", preferredMaxTokens)
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                    if (!base64JpegImage.isNullOrBlank()) {
                        val contentArr = JSONArray()
                            .put(JSONObject().put("type", "text").put("text", userPromptText))
                            .put(JSONObject().put("type", "image_url")
                                .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$base64JpegImage")))
                        put(JSONObject().put("role", "user").put("content", contentArr))
                    } else {
                        put(JSONObject().put("role", "user").put("content", userPromptText))
                    }
                })
            }.toString()
        }

        try {
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(payloadJson) }
            val status = conn.responseCode
            if (status !in 200..299) {
                val retryAfterMs = conn.getHeaderField("Retry-After")?.trim()?.toLongOrNull()?.times(1000)
                return AiHttpResult(status, null, null, retryAfterMs)
            }
            val responseText = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
            val root = JSONObject(responseText)

            if (!isGeminiNative) {
                val content = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                return AiHttpResult(status, content?.let { redactSecrets(it).trim() }?.takeIf { it.isNotBlank() }, null, null)
            }

            root.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotBlank() }?.let {
                return AiHttpResult(status, null, "Permintaan diblokir filter keamanan Gemini ($it).", null)
            }
            val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
            val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
            // Gabungkan semua bagian teks jawaban; lewati ringkasan proses berpikir (thought = true).
            val text = buildString {
                if (parts != null) for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    if (part.optBoolean("thought", false)) continue
                    append(part.optString("text", ""))
                }
            }.let { redactSecrets(it).trim() }

            if (text.isBlank()) {
                val reason = candidate?.optString("finishReason").orEmpty()
                return AiHttpResult(status, null, "AI tidak mengembalikan jawaban${if (reason.isNotBlank()) " ($reason)" else ""}.", null)
            }
            return AiHttpResult(status, text, null, null)
        } finally {
            conn.disconnect()
        }
    }

    data class AiNoteDraft(
        val title: String,
        val body: String
    )

    /**
     * AI-First Note Writer:
     * Directly asks the AI to write a structured, clean student lecture note from raw document/OCR text
     * (and optional photo of whiteboard/handwriting), returning AiNoteDraft(title, body).
     */
    suspend fun writeNoteWithAi(
        rawSourceText: String,
        fallbackTitle: String,
        courseHint: String = "",
        sourceMode: ScanSourceMode = ScanSourceMode.DOCUMENT,
        base64JpegImage: String? = null
    ): AiNoteDraft? {
        if (!isRemoteApiConfigured()) return null
        if (rawSourceText.isBlank() && base64JpegImage.isNullOrBlank()) return null

        val sysPrompt = when (sourceMode) {
            ScanSourceMode.WHITEBOARD -> {
                "Kamu adalah asisten pencatat kuliah mahasiswa. Tugasmu adalah menulis catatan kuliah yang rapi, jelas, dan mudah dipelajari dari foto/teks papan tulis kelas ini.\n" +
                    "Aturan wajib:\n" +
                    "1. Baris pertama WAJIB ditulis dengan format: JUDUL: <Judul Topik Materi>\n" +
                    "2. Di bawahnya, tulis langsung isi catatan yang terdiri dari penjelasan konsep utama, poin-poin penting, rumus atau tahapan yang ada di papan tulis.\n" +
                    "3. Jangan gunakan kalimat pembuka basa-basi (seperti 'Berikut adalah...') dan jangan gunakan karakter em dash."
            }
            ScanSourceMode.HANDWRITING -> {
                "Kamu adalah asisten pencatat kuliah mahasiswa. Tugasmu adalah menulis ulang catatan tulis tangan mahasiswa ini menjadi catatan digital yang rapi, terstruktur, dan enak dibaca ulang.\n" +
                    "Aturan wajib:\n" +
                    "1. Baris pertama WAJIB ditulis dengan format: JUDUL: <Judul Topik Materi>\n" +
                    "2. Di bawahnya, susun materi menjadi paragraf singkat dan poin-poin jelas sesuai isi catatan.\n" +
                    "3. Jangan gunakan kalimat pembuka basa-basi dan jangan gunakan karakter em dash."
            }
            ScanSourceMode.DOCUMENT -> {
                "Kamu adalah asisten pencatat kuliah mahasiswa. Tugasmu adalah membaca teks dokumen/PDF kuliah berikut lalu MENULIS ULANG catatan materi kuliah yang rapi dan mudah dipelajari.\n" +
                    "Aturan wajib:\n" +
                    "1. Baris pertama WAJIB ditulis dengan format: JUDUL: <Judul Topik Materi Kuliah>\n" +
                    "2. Fokus 100% pada substansi ilmu/pelajarannya (pengertian, konsep inti, cara kerja, rumus/langkah, dan contoh).\n" +
                    "3. JANGAN PERNAH mencatat bagian administrasi dokumen seperti nama kampus/universitas, fakultas, penerbit, jadwal ditulis/revisi, kode dokumen, tabel RPS, nama dosen, atau daftar pustaka.\n" +
                    "4. Jangan gunakan pembuka basa-basi dan jangan gunakan karakter em dash."
            }
        }

        val userPrompt = buildString {
            if (courseHint.isNotBlank()) appendLine("Mata Kuliah: $courseHint")
            if (fallbackTitle.isNotBlank()) appendLine("Nama File/Sumber: $fallbackTitle")
            if (rawSourceText.isNotBlank()) {
                appendLine("\nIsi Teks Dokumen/Hasil Bacaan:\n${rawSourceText.take(11_000)}")
            } else {
                appendLine("\nBaca langsung tulisan pada gambar terlampir dan tuliskan catatannya.")
            }
        }

        val aiResponse = requestRemoteCompletion(
            systemPrompt = sysPrompt,
            userPrompt = userPrompt,
            preferredMaxTokens = 4096,
            base64JpegImage = base64JpegImage
        ) ?: return null

        val lines = aiResponse.lines()
        var extractedTitle = ""
        val bodyLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (extractedTitle.isBlank() && (trimmed.startsWith("JUDUL:", ignoreCase = true) || trimmed.startsWith("TITLE:", ignoreCase = true))) {
                extractedTitle = trimmed.substringAfter(":").trim().removeSurrounding("\"").removePrefix("#").trim()
            } else if (extractedTitle.isBlank() && bodyLines.isEmpty() && trimmed.startsWith("# ")) {
                extractedTitle = trimmed.removePrefix("# ").trim()
            } else {
                bodyLines.add(line)
            }
        }

        val finalBody = bodyLines.joinToString("\n").trim().ifBlank { aiResponse.trim() }
        val finalTitle = extractedTitle.ifBlank { fallbackTitle.ifBlank { "Catatan Kuliah" } }
        return AiNoteDraft(title = finalTitle, body = finalBody)
    }

    private fun extractCleanSentences(body: String): List<String> {
        if (body.isBlank()) return emptyList()
        return body
            .replace(Regex("[*_#>`~]"), "")
            .split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim().removePrefix("•").removePrefix("-").trim().replace("—", ", ").replace("–", "-") }
            .filter { it.length >= 14 && it.count { ch -> ch.isLetter() } >= 8 }
            .distinct()
    }

    private fun extractGlossary(body: String, sentences: List<String>): List<Pair<String, String>> {
        if (sentences.isEmpty()) return emptyList()
        val pairs = mutableListOf<Pair<String, String>>()
        val lines = body.lines().map { it.trim().removePrefix("•").removePrefix("-").trim() }
        val defRegex = Regex("^([A-Za-z0-9 /()_-]{3,40})\\s*(?::|=|\\badalah\\b|\\bmerupakan\\b|\\bis\\b|\\brefers to\\b)\\s*(.{12,200})$", RegexOption.IGNORE_CASE)

        for (line in lines) {
            val m = defRegex.find(line)
            if (m != null) {
                val term = m.groupValues[1].replace(Regex("[*_]"), "").trim()
                val def = m.groupValues[2].replace("—", ", ").replace("–", "-").trim()
                val termLower = term.lowercase()
                if (term.isNotBlank() &&
                    def.isNotBlank() &&
                    termLower !in STOP_WORDS &&
                    pairs.none { it.first.equals(term, true) }
                ) {
                    pairs.add(term to def)
                }
            }
            if (pairs.size >= 5) break
        }
        return pairs
    }

    fun generateSummary(title: String, body: String, isEn: Boolean): NoteSummary {
        val sentences = extractCleanSentences(body)
        if (sentences.isEmpty()) {
            return NoteSummary(
                overview = "",
                keyPoints = emptyList(),
                glossaryTerms = emptyList()
            )
        }

        val overview = sentences.take(2).joinToString(" ")
        val keyPoints = sentences.take(5).map { s ->
            if (s.length > 160) s.take(157) + "..." else s
        }
        val glossary = extractGlossary(body, sentences)
        return NoteSummary(overview = overview, keyPoints = keyPoints, glossaryTerms = glossary)
    }

    suspend fun enrichSummaryRemote(
        title: String,
        body: String,
        isEn: Boolean,
        fallback: NoteSummary,
        context: android.content.Context? = null
    ): NoteSummary {
        val cleanBody = sanitizeInput(body, 6_500)
        if (cleanBody.length < 20) return fallback
        val cleanTitle = sanitizeInput(title, 150).ifBlank { if (isEn) "Class Note" else "Catatan Kuliah" }

        // Jika layanan NotebookLM aktif di Docker / server lokal, sinkronkan materi dan ambil ringkasan resmi NotebookLM
        if (context != null && NotebookLmRestApiClient.isHealthy(context)) {
            val notebookId = NotebookLmRestApiClient.ensureNotebook(context, cleanTitle).getOrNull()
            if (notebookId != null) {
                NotebookLmRestApiClient.syncTextSource(context, notebookId, cleanTitle, cleanBody)
                val nlmSummary = NotebookLmRestApiClient.getNotebookSummary(context, notebookId).getOrNull()
                if (!nlmSummary.isNullOrBlank()) {
                    return fallback.copy(overview = nlmSummary.trim())
                }
            }
        }

        if (!isRemoteApiConfigured()) return fallback
        val sysPrompt = if (isEn) {
            "Summarize this student lecture note focusing strictly on the academic subject matter (concepts, theories, formulas, mechanisms, and examples). " +
                "Completely IGNORE administrative document metadata such as university/campus names, publisher info, ISBN, document creation/revision schedules, syllabus tables, or lecturer names. " +
                "Return 1 short overview paragraph on the first line, then 3-4 lines starting with 'POINT: ' for key concepts, then 2-3 lines starting with 'TERM: name | explanation'. Do not use em dashes."
        } else {
            "Rangkum catatan kuliah berikut dengan fokus penuh pada isi materi pelajarannya (konsep inti, pengertian, cara kerja, rumus, atau contoh penerapan). " +
                "ABAIKAN sama sekali informasi administrasi dokumen seperti nama kampus/universitas, penerbit, jadwal ditulis/revisi, kode dokumen, tabel RPS, atau nama penyusun. " +
                "Tulis 1 paragraf ringkasan yang mudah dipahami mahasiswa di baris pertama, lalu 3-4 baris diawali 'POIN: ' untuk poin materi penting, lalu 2-3 baris diawali 'ISTILAH: nama istilah | penjelasan singkat'. Jangan gunakan karakter em dash."
        }
        val raw = requestRemoteCompletion(sysPrompt, "Topik: $cleanTitle\nIsi Materi:\n$cleanBody") ?: return fallback
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        val points = mutableListOf<String>()
        val terms = mutableListOf<Pair<String, String>>()
        val overviewLines = mutableListOf<String>()

        for (line in lines) {
            when {
                line.startsWith("POIN:", true) || line.startsWith("POINT:", true) -> {
                    val p = line.substringAfter(":").trim().removePrefix("-").removePrefix("•").trim()
                    if (p.isNotBlank()) points.add(p)
                }
                line.startsWith("ISTILAH:", true) || line.startsWith("TERM:", true) -> {
                    val content = line.substringAfter(":").trim()
                    val split = content.split("|", limit = 2)
                    if (split.size == 2 && split[0].isNotBlank() && split[1].isNotBlank()) {
                        terms.add(split[0].trim() to split[1].trim())
                    }
                }
                points.isEmpty() && terms.isEmpty() -> {
                    overviewLines.add(line.removePrefix("Ringkasan:").removePrefix("Overview:").trim())
                }
            }
        }

        val finalOverview = overviewLines.joinToString(" ").ifBlank { fallback.overview }
        val finalPoints = if (points.isNotEmpty()) points else fallback.keyPoints
        val finalTerms = if (terms.isNotEmpty()) terms else fallback.glossaryTerms
        return NoteSummary(overview = finalOverview, keyPoints = finalPoints, glossaryTerms = finalTerms)
    }

    suspend fun enrichQuizRemote(
        title: String,
        body: String,
        isEn: Boolean,
        fallback: StudyQuiz
    ): StudyQuiz {
        val cleanBody = sanitizeInput(body, 5_000)
        if (!isRemoteApiConfigured() || cleanBody.length < 25) return fallback
        val cleanTitle = sanitizeInput(title, 120).ifBlank { if (isEn) "Class Note" else "Catatan Kuliah" }
        val sysPrompt = if (isEn) {
            "Create 4 study flashcards based strictly on the core course concepts in this note (ignore any university/publisher/schedule metadata). " +
                "Format each line strictly as: Q: <question> | A: <concise answer>"
        } else {
            "Buat 4 kartu kuis latihan ujian berdasarkan konsep inti materi kuliah di catatan ini (abaikan nama kampus, penerbit, atau jadwal dokumen). " +
                "Tulis tiap baris dengan format persis: Q: <pertanyaan konsep> | A: <jawaban singkat & jelas>"
        }
        val raw = requestRemoteCompletion(sysPrompt, "Topik: $cleanTitle\nMateri:\n$cleanBody") ?: return fallback
        val parsed = mutableListOf<QuizQuestion>()
        raw.lines().forEachIndexed { idx, line ->
            val trimmed = line.trim().removePrefix("-").removePrefix("•").trim()
            if (trimmed.startsWith("Q:", ignoreCase = true) && trimmed.contains("|")) {
                val parts = trimmed.split("|", limit = 2)
                val q = parts[0].substringAfter(":").trim()
                val a = parts[1].substringAfter(":").trim()
                if (q.isNotBlank() && a.isNotBlank()) {
                    parsed.add(
                        QuizQuestion(
                            id = "ai-q-$idx",
                            question = q,
                            answer = a,
                            explanation = if (isEn) "Core concept from $cleanTitle" else "Konsep inti dari $cleanTitle"
                        )
                    )
                }
            }
        }
        return if (parsed.isNotEmpty()) StudyQuiz(parsed) else fallback
    }

    fun generateStudyGuide(title: String, body: String, isEn: Boolean): StudyGuide {
        val cleanTitle = title.trim().ifBlank { if (isEn) "Class Note" else "Catatan Kuliah" }
        val sentences = extractCleanSentences(body)
        if (sentences.isEmpty()) {
            return StudyGuide(
                topic = cleanTitle,
                overview = "",
                sections = emptyList(),
                examFocusPoints = emptyList()
            )
        }

        val glossary = extractGlossary(body, sentences)
        val corePoints = sentences.take(4)
        val applicationPoints = if (sentences.size > 4) sentences.drop(4).take(4) else glossary.map { "${it.first}: ${it.second}" }

        val sections = buildList {
            add(
                StudySection(
                    heading = if (isEn) "Core Concepts" else "Inti Materi",
                    details = corePoints
                )
            )
            if (applicationPoints.isNotEmpty()) {
                add(
                    StudySection(
                        heading = if (isEn) "Important Terms & Details" else "Istilah & Penjelasan Penting",
                        details = applicationPoints
                    )
                )
            }
        }

        val examPoints = buildList {
            glossary.take(3).forEach { (term, def) ->
                add(
                    if (isEn) "Understand $term: $def"
                    else "Pahami $term: $def"
                )
            }
            if (sentences.isNotEmpty()) {
                add(sentences.first().take(140))
            }
        }

        return StudyGuide(
            topic = cleanTitle,
            overview = if (isEn) "Key study points from $cleanTitle."
            else "Ringkasan poin belajar dari $cleanTitle.",
            sections = sections,
            examFocusPoints = examPoints
        )
    }

    fun generateQuiz(title: String, body: String, isEn: Boolean): StudyQuiz {
        val cleanTitle = title.trim().ifBlank { if (isEn) "Class Note" else "Catatan Kuliah" }
        val sentences = extractCleanSentences(body)
        if (sentences.isEmpty()) {
            return StudyQuiz(questions = emptyList())
        }

        val glossary = extractGlossary(body, sentences)
        val questions = mutableListOf<QuizQuestion>()

        glossary.forEachIndexed { idx, (term, def) ->
            questions.add(
                QuizQuestion(
                    id = "q-term-$idx",
                    question = if (isEn) "What is \"$term\"?"
                    else "Apa yang dimaksud dengan \"$term\"?",
                    answer = def,
                    explanation = if (isEn) "From your note: $term - $def"
                    else "Dari catatanmu: $term - $def"
                )
            )
        }

        sentences.take(4).forEachIndexed { idx, sentence ->
            if (questions.size < 6) {
                val words = sentence.split(" ")
                    .map { it.replace(Regex("[^A-Za-z0-9]"), "") }
                    .filter { it.length >= 5 && it.lowercase() !in STOP_WORDS }
                val focusWord = words.maxByOrNull { it.length } ?: cleanTitle
                questions.add(
                    QuizQuestion(
                        id = "q-sent-$idx",
                        question = if (isEn) "How does your note explain \"$focusWord\"?"
                        else "Bagaimana penjelasan tentang \"$focusWord\" di materi ini?",
                        answer = sentence,
                        explanation = if (isEn) "Taken from $cleanTitle."
                        else "Diambil dari catatan $cleanTitle."
                    )
                )
            }
        }

        return StudyQuiz(questions = questions.distinctBy { it.answer }.take(6))
    }

    fun generateCanvasData(title: String, body: String, isEn: Boolean, aiSummary: NoteSummary? = null): CanvasStudyData {
        val cleanTitle = title.trim().ifBlank { if (isEn) "Study Topic" else "Topik Belajar" }
        val sentences = extractCleanSentences(body)
        if (sentences.isEmpty()) {
            return CanvasStudyData(
                centralTopic = cleanTitle.take(28),
                nodes = emptyList(),
                matchPairs = emptyList(),
                eli5Text = "",
                mnemonicText = "",
                caseStudyText = ""
            )
        }

        val glossary = if (aiSummary != null && aiSummary.glossaryTerms.isNotEmpty()) {
            aiSummary.glossaryTerms
        } else {
            extractGlossary(body, sentences)
        }
        val keySentences = if (aiSummary != null && aiSummary.keyPoints.isNotEmpty()) {
            aiSummary.keyPoints
        } else {
            sentences
        }

        val rawNodeItems = mutableListOf<Triple<String, String, String>>()
        glossary.forEach { (term, def) ->
            rawNodeItems.add(Triple(term.take(22), def, "$term: $def"))
        }
        keySentences.forEach { s ->
            if (rawNodeItems.size < 6) {
                val cleanWords = s.split(" ")
                    .map { it.replace(Regex("[^A-Za-z0-9-]"), "") }
                    .filter { it.length >= 3 && it.lowercase() !in STOP_WORDS }
                val label = cleanWords.take(3).joinToString(" ").take(22)
                if (label.isNotBlank() && rawNodeItems.none { it.first.equals(label, true) }) {
                    rawNodeItems.add(Triple(label, s, s))
                }
            }
        }

        val count = rawNodeItems.size.coerceAtMost(6)
        val nodes = rawNodeItems.take(count).mapIndexed { index, (label, detail, quote) ->
            val angle = (360f / count) * index - 90f
            CanvasConceptNode(
                id = "node-$index",
                label = label,
                detail = detail,
                quote = quote,
                angleDeg = angle,
                radiusFraction = if (index % 2 == 0) 0.36f else 0.32f
            )
        }

        val matchPairs = rawNodeItems.take(4).mapIndexed { idx, (label, detail, _) ->
            ConceptMatchPair(
                id = "pair-$idx",
                term = label,
                definition = detail.take(110)
            )
        }

        val acronym = rawNodeItems.take(5).mapNotNull { it.first.firstOrNull()?.uppercaseChar() }.joinToString("-")
        val mnemonicWords = rawNodeItems.take(5).joinToString(", ") { it.first }
        val mnemonicText = if (rawNodeItems.isNotEmpty()) {
            if (isEn) {
                "Initials to remember ($acronym): $mnemonicWords."
            } else {
                "Jembatan keledai ($acronym): $mnemonicWords."
            }
        } else ""

        val eli5Text = aiSummary?.overview?.takeIf { it.isNotBlank() } ?: keySentences.take(2).joinToString(" ")

        val caseStudyText = if (keySentences.size >= 3) {
            keySentences.drop(2).take(2).joinToString(" ")
        } else {
            rawNodeItems.joinToString("\n") { "• ${it.first}: ${it.second}" }
        }

        return CanvasStudyData(
            centralTopic = cleanTitle.take(24),
            nodes = nodes,
            matchPairs = matchPairs,
            eli5Text = eli5Text,
            mnemonicText = mnemonicText,
            caseStudyText = caseStudyText
        )
    }

    fun getSuggestedPrompts(isEn: Boolean): List<String> = if (isEn) {
        listOf(
            "What is the main point of this material?",
            "Explain this in simpler words",
            "Which parts usually come up on exams?",
            "List the key terms I need to memorize"
        )
    } else {
        listOf(
            "Apa inti dari materi ini?",
            "Jelaskan pakai bahasa sehari-hari",
            "Bagian mana yang sering keluar saat ujian?",
            "Apa saja istilah penting yang perlu dihafal?"
        )
    }

    suspend fun answerQuestion(
        query: String,
        noteTitle: String,
        noteBody: String,
        history: List<AiChatMessageRow>,
        isEn: Boolean,
        context: android.content.Context? = null
    ): Pair<String, String> {
        val cleanQuery = sanitizeInput(query, 600)
        val cleanTitle = sanitizeInput(noteTitle, 150).ifBlank { if (isEn) "Class Note" else "Catatan Kuliah" }
        val cleanedAcademic = DocumentNoteExtractor.cleanAcademicText(noteBody)
        val cleanBody = sanitizeInput(cleanedAcademic.ifBlank { noteBody }, 10_000)
        val sentences = extractCleanSentences(cleanBody)

        if (sentences.isEmpty()) {
            val emptyMsg = if (isEn) {
                "This note doesn't have any material yet. Write or photograph your class notes first so we can discuss them."
            } else {
                "Catatan ini belum ada isinya. Tulis materi atau foto catatan kuliahmu dulu supaya kita bisa bahas bareng."
            }
            return emptyMsg to ""
        }

        val qLower = cleanQuery.lowercase()
        val bestQuote = findBestMatchingSnippet(qLower, sentences)

        // Coba tanyakan ke layanan NotebookLM (Docker / REST API) jika aktif
        if (context != null && NotebookLmRestApiClient.isHealthy(context)) {
            val notebookId = NotebookLmRestApiClient.ensureNotebook(context, cleanTitle).getOrNull()
            if (notebookId != null) {
                NotebookLmRestApiClient.syncTextSource(context, notebookId, cleanTitle, cleanBody)
                val nlmAnswer = NotebookLmRestApiClient.askNotebookChat(context, notebookId, cleanQuery).getOrNull()
                if (!nlmAnswer.isNullOrBlank()) {
                    return nlmAnswer to (bestQuote ?: sentences.firstOrNull().orEmpty())
                }
            }
        }

        if (isRemoteApiConfigured()) {
            val historyContext = history.takeLast(6).joinToString("\n") { msg ->
                "${msg.sender}: ${sanitizeInput(msg.text, 300)}"
            }
            val sysPrompt = if (isEn) {
                "You are a friendly university study partner. Answer based on the student's lecture note. Ignore any administrative metadata (campus name, publisher, syllabus dates) and focus on the actual course concepts. Keep answers natural, clear, and free of em dashes."
            } else {
                "Kamu adalah teman belajar kuliah yang asik dan jelas saat menjelaskan. Jawab pertanyaan berdasarkan isi materi catatan kuliah ini. Abaikan info administrasi seperti nama kampus, penerbit, atau tanggal penulisan dokumen. Gunakan bahasa Indonesia yang luwes, langsung ke intinya, dan jangan gunakan karakter em dash."
            }
            val userPrompt = buildString {
                appendLine("Judul Materi: $cleanTitle")
                appendLine("Isi Materi:\n$cleanBody")
                if (historyContext.isNotBlank()) {
                    appendLine("\nPercakapan Sebelumnya:\n$historyContext")
                }
                appendLine("\nPertanyaan Mahasiswa: $cleanQuery")
            }
            val remoteReply = requestRemoteCompletion(sysPrompt, userPrompt)
            if (!remoteReply.isNullOrBlank()) {
                return remoteReply to (bestQuote ?: sentences.firstOrNull().orEmpty())
            }
        }

        val summary = generateSummary(cleanTitle, cleanBody, isEn)
        val studyGuide = generateStudyGuide(cleanTitle, cleanBody, isEn)
        val canvasData = generateCanvasData(cleanTitle, cleanBody, isEn)

        val responseText = when {
            qLower.contains("terpenting") || qLower.contains("utama") || qLower.contains("intinya") ||
                qLower.contains("inti") || qLower.contains("rangkum") || qLower.contains("important") ||
                qLower.contains("summary") || qLower.contains("main point") -> {
                val points = summary.keyPoints.take(3).joinToString("\n") { "• $it" }
                if (isEn) {
                    "Main points from \"$cleanTitle\":\n$points"
                } else {
                    "Inti materi dari \"$cleanTitle\":\n$points"
                }
            }
            qLower.contains("analogi") || qLower.contains("sederhana") || qLower.contains("mudah") ||
                qLower.contains("sehari-hari") || qLower.contains("simpler") || qLower.contains("simple") || qLower.contains("eli5") -> {
                listOf(canvasData.eli5Text, canvasData.mnemonicText).filter { it.isNotBlank() }.joinToString("\n\n")
            }
            qLower.contains("ujian") || qLower.contains("kuis") || qLower.contains("soal") ||
                qLower.contains("exam") || qLower.contains("quiz") || qLower.contains("test") -> {
                val focuses = studyGuide.examFocusPoints.take(3).joinToString("\n") { "• $it" }
                if (isEn) {
                    "Parts worth reviewing for exams in \"$cleanTitle\":\n$focuses"
                } else {
                    "Bagian yang perlu kamu kuasai buat ujian dari \"$cleanTitle\":\n$focuses"
                }
            }
            qLower.contains("istilah") || qLower.contains("definisi") || qLower.contains("glosarium") ||
                qLower.contains("hafal") || qLower.contains("term") || qLower.contains("definition") -> {
                if (summary.glossaryTerms.isEmpty()) {
                    if (isEn) "There aren't any specific term definitions written in this note yet."
                    else "Belum ada daftar istilah khusus yang tertulis di catatan ini."
                } else {
                    val terms = summary.glossaryTerms.joinToString("\n") { "• ${it.first}: ${it.second}" }
                    if (isEn) "Key terms in your note:\n$terms" else "Istilah penting di catatanmu:\n$terms"
                }
            }
            bestQuote != null -> {
                if (isEn) {
                    "According to \"$cleanTitle\": $bestQuote"
                } else {
                    "Di catatan \"$cleanTitle\" dijelaskan: $bestQuote"
                }
            }
            else -> {
                if (isEn) {
                    "That specific detail isn't in \"$cleanTitle\" yet. The closest part in your note is: ${sentences.first()}"
                } else {
                    "Bagian itu belum tertulis di catatan \"$cleanTitle\". Bahasan yang paling dekat di catatanmu: ${sentences.first()}"
                }
            }
        }

        return responseText to (bestQuote ?: sentences.firstOrNull().orEmpty())
    }

    private fun findBestMatchingSnippet(queryLower: String, sentences: List<String>): String? {
        if (sentences.isEmpty()) return null
        val queryTokens = queryLower
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 && it !in STOP_WORDS }
        if (queryTokens.isEmpty()) return sentences.firstOrNull()

        var bestSentence: String? = null
        var bestScore = 0
        for (sentence in sentences) {
            val sLower = sentence.lowercase()
            val score = queryTokens.count { token -> sLower.contains(token) }
            if (score > bestScore) {
                bestScore = score
                bestSentence = sentence
            }
        }
        return if (bestScore > 0) bestSentence else null
    }

    fun assessStudentProgress(
        database: StudentHubDatabase,
        isEn: Boolean
    ): StudentProgressAssessment {
        val courses = runCatching { database.courses() }.getOrDefault(emptyList())
        val notes = runCatching { database.notes() }.getOrDefault(emptyList())
        val tasks = runCatching { database.tasks() }.getOrDefault(emptyList())
        val studyPlans = runCatching { database.studyPlans() }.getOrDefault(emptyList())
        val aiProgress = runCatching { database.allAiStudyProgress() }.getOrDefault(emptyList())
        val profile = runCatching { database.profile() }.getOrNull()
        val targetGpa = runCatching { database.goal() }.getOrDefault(0.0)

        val nonBlankNotes = notes.filter { it.body.isNotBlank() }
        val hasAnyData = courses.isNotEmpty() || nonBlankNotes.isNotEmpty() || tasks.isNotEmpty() || studyPlans.isNotEmpty()

        if (!hasAnyData) {
            return StudentProgressAssessment(
                hasData = false,
                readinessScore = 0,
                statusLabel = if (isEn) "No Activity Yet" else "Belum Mulai",
                summaryText = if (isEn) {
                    "Add your courses, tasks, or class notes first to see your study progress."
                } else {
                    "Isi dulu jadwal matkul, tugas, atau catatan kuliahmu supaya progres belajarmu bisa terlihat di sini."
                },
                noteCoveragePercent = 0,
                quizMasteryPercent = 0,
                taskCompletionPercent = 0,
                currentGpa = 0.0,
                targetGpa = targetGpa,
                strengths = emptyList(),
                focusAreas = emptyList()
            )
        }

        val coursesWithNotes = courses.count { course ->
            nonBlankNotes.any { n ->
                n.course.equals(course.code, true) ||
                    n.course.equals(course.id, true) ||
                    n.course.equals(course.title, true)
            }
        }
        val noteCoveragePercent = if (courses.isNotEmpty()) {
            ((coursesWithNotes.toDouble() / courses.size) * 100).roundToInt().coerceIn(0, 100)
        } else if (nonBlankNotes.isNotEmpty()) 100 else 0

        val validProgress = aiProgress.filter { prog -> nonBlankNotes.any { it.id == prog.noteId } }
        val totalMastered = validProgress.sumOf { it.masteredCount }
        val totalCards = validProgress.sumOf { it.totalCount }
        val avgCanvas = if (validProgress.isNotEmpty()) validProgress.map { it.canvasScore }.average().roundToInt() else 0
        val quizMasteryPercent = if (totalCards > 0) {
            (((totalMastered.toDouble() / totalCards) * 70) + (avgCanvas * 0.3)).roundToInt().coerceIn(0, 100)
        } else if (avgCanvas > 0) {
            avgCanvas
        } else 0

        val totalActionItems = tasks.size + studyPlans.size
        val completedActionItems = tasks.count { it.completed } + studyPlans.count { it.completed }
        val taskCompletionPercent = if (totalActionItems > 0) {
            ((completedActionItems.toDouble() / totalActionItems) * 100).roundToInt().coerceIn(0, 100)
        } else 0

        val gradedCourses = courses.filter { it.grade != null }
        val semesterGpa = calculateCurrentGpa(gradedCourses, 0.0, 0)
        val currentGpa = calculateCurrentGpa(courses, profile?.priorIpk ?: 0.0, profile?.priorSks ?: 0)
        val effectiveTargetGpa = if (targetGpa > 0.0) targetGpa else 3.5
        val semesterGradePercent = if (gradedCourses.isNotEmpty() && semesterGpa > 0.0) {
            ((semesterGpa / effectiveTargetGpa) * 100).roundToInt().coerceIn(0, 100)
        } else 0

        val noteWeight = 35
        val taskWeight = 35
        val quizWeight = 15
        val gradeWeight = 15

        val noteContribution = ((noteCoveragePercent * noteWeight) / 100.0).roundToInt()
        val taskContribution = ((taskCompletionPercent * taskWeight) / 100.0).roundToInt()
        val quizContribution = ((quizMasteryPercent * quizWeight) / 100.0).roundToInt()
        val gradeContribution = ((semesterGradePercent * gradeWeight) / 100.0).roundToInt()

        val readinessScore = (noteContribution + taskContribution + quizContribution + gradeContribution).coerceIn(0, 100)

        val scoreBreakdown = listOf(
            ScoreReasonItem(
                label = if (isEn) "Class notes (max 35%)" else "Catatan kuliah (maks 35%)",
                detail = if (courses.isNotEmpty()) {
                    if (coursesWithNotes == 0) {
                        if (isEn) "None of your ${courses.size} courses have notes yet"
                        else "Belum ada dari ${courses.size} matkul yang punya catatan"
                    } else {
                        if (isEn) "$coursesWithNotes of ${courses.size} courses already have notes"
                        else "$coursesWithNotes dari ${courses.size} matkul sudah ada catatannya"
                    }
                } else {
                    if (isEn) "${nonBlankNotes.size} notes saved"
                    else "${nonBlankNotes.size} catatan tersimpan"
                },
                rawPercent = noteCoveragePercent,
                weightPercent = noteWeight,
                contributionPercent = noteContribution
            ),
            ScoreReasonItem(
                label = if (isEn) "Tasks & study targets (max 35%)" else "Tugas & target belajar (maks 35%)",
                detail = if (totalActionItems > 0) {
                    if (isEn) "$completedActionItems of $totalActionItems tasks/targets completed"
                    else "$completedActionItems dari $totalActionItems tugas/target belajar sudah beres"
                } else {
                    if (isEn) "No tasks or study targets added yet"
                    else "Belum ada tugas atau target belajar yang dicatat"
                },
                rawPercent = taskCompletionPercent,
                weightPercent = taskWeight,
                contributionPercent = taskContribution
            ),
            ScoreReasonItem(
                label = if (isEn) "Quiz & concept practice (max 15%)" else "Latihan kuis & kanvas (maks 15%)",
                detail = if (validProgress.isNotEmpty()) {
                    if (isEn) "$totalMastered/$totalCards flashcards mastered across ${validProgress.size} notes"
                    else "$totalMastered dari $totalCards kartu latihan sudah dikuasai"
                } else {
                    if (isEn) "Haven't tried the flashcards or concept match yet"
                    else "Belum pernah coba latihan kuis atau cocok konsep"
                },
                rawPercent = quizMasteryPercent,
                weightPercent = quizWeight,
                contributionPercent = quizContribution
            ),
            ScoreReasonItem(
                label = if (isEn) "This semester's grades (max 15%)" else "Nilai matkul semester ini (maks 15%)",
                detail = if (gradedCourses.isNotEmpty()) {
                    if (isEn) "${gradedCourses.size}/${courses.size} courses graded (GPA ${String.format("%.2f", semesterGpa)})"
                    else "${gradedCourses.size} dari ${courses.size} matkul sudah ada nilainya"
                } else {
                    if (isEn) "No course grades entered this semester (past GPA isn't counted here)"
                    else "Belum ada nilai matkul semester ini (IPK semester lalu tidak dihitung)"
                },
                rawPercent = semesterGradePercent,
                weightPercent = gradeWeight,
                contributionPercent = gradeContribution
            )
        )

        val scoreReasoningSummary = if (readinessScore == 0) {
            if (isEn) {
                "Your score is 0% because you haven't added notes, finished tasks, practiced quizzes, or entered grades for this semester yet. Your previous semester GPA isn't mixed in here so this number stays honest to your current semester."
            } else {
                "Skormu masih 0% karena belum ada catatan materi, tugas yang selesai, latihan kuis, atau nilai matkul di semester ini. IPK semester lalu sengaja tidak ikut dihitung supaya angka ini jujur menggambarkan progres belajarmu sekarang."
            }
        } else {
            if (isEn) {
                "Your $readinessScore% comes from class notes (+$noteContribution%), completed tasks & targets (+$taskContribution%), quiz practice (+$quizContribution%), and this semester's grades (+$gradeContribution%)."
            } else {
                "Angka $readinessScore% ini berasal dari kelengkapan catatan (+$noteContribution%), tugas & target belajar (+$taskContribution%), latihan kuis (+$quizContribution%), serta nilai semester ini (+$gradeContribution%)."
            }
        }

        val statusLabel = when {
            readinessScore >= 80 -> if (isEn) "Ready" else "Siap Ujian"
            readinessScore >= 55 -> if (isEn) "On Track" else "Cukup Aman"
            readinessScore > 0 -> if (isEn) "Warming Up" else "Baru Mulai Jalan"
            else -> if (isEn) "0% (No Activity Yet)" else "0% (Belum Mulai)"
        }

        val strengths = mutableListOf<String>()
        val focusAreas = mutableListOf<String>()

        if (nonBlankNotes.isNotEmpty()) {
            strengths.add(
                if (isEn) "You already have ${nonBlankNotes.size} class notes."
                else "Sudah ada ${nonBlankNotes.size} catatan kuliah yang kamu simpan."
            )
        } else {
            focusAreas.add(
                if (isEn) "You haven't written or imported any class notes yet."
                else "Belum ada catatan materi yang ditulis atau diimpor."
            )
        }

        val unnotedCourses = courses.filter { course ->
            nonBlankNotes.none { n ->
                n.course.equals(course.code, true) ||
                    n.course.equals(course.id, true) ||
                    n.course.equals(course.title, true)
            }
        }
        if (unnotedCourses.isNotEmpty()) {
            val names = unnotedCourses.take(2).joinToString(", ") { it.title }
            focusAreas.add(
                if (isEn) "Courses that still need notes: $names."
                else "Matkul yang belum ada catatannya: $names."
            )
        } else if (courses.isNotEmpty()) {
            strengths.add(
                if (isEn) "All of your courses already have at least one note."
                else "Semua matkul kamu sudah punya catatan."
            )
        }

        val pendingTasks = tasks.filter { !it.completed }
        if (pendingTasks.isNotEmpty()) {
            focusAreas.add(
                if (isEn) "${pendingTasks.size} tasks waiting to be finished (${pendingTasks.first().title})."
                else "Ada ${pendingTasks.size} tugas yang belum beres (${pendingTasks.first().title})."
            )
        } else if (tasks.isNotEmpty()) {
            strengths.add(
                if (isEn) "All your listed tasks are finished."
                else "Semua tugas yang tercatat sudah selesai."
            )
        }

        val lowGradeCourses = courses.filter { (it.grade?.score ?: 100) < 75 }
        if (lowGradeCourses.isNotEmpty()) {
            val lowNames = lowGradeCourses.joinToString(", ") { "${it.title} (${it.grade?.letter})" }
            focusAreas.add(
                if (isEn) "Spend extra time on courses below 75: $lowNames."
                else "Perlu kejar materi untuk matkul dengan nilai di bawah 75: $lowNames."
            )
        } else if (courses.any { it.grade != null }) {
            strengths.add(
                if (isEn) "Your graded courses are all at 75 or above."
                else "Nilai matkul yang sudah masuk berada di batas aman."
            )
        }

        if (totalCards > 0 && quizMasteryPercent >= 70) {
            strengths.add(
                if (isEn) "Good progress on flashcard & canvas practice ($quizMasteryPercent%)."
                else "Latihan kartu & peta konsep cukup rutin ($quizMasteryPercent%)."
            )
        }

        val summaryText = if (isEn) {
            "Summary from ${courses.size} courses, ${nonBlankNotes.size} notes, and ${tasks.size} tasks."
        } else {
            "Dilihat dari ${courses.size} matkul, ${nonBlankNotes.size} catatan materi, dan ${tasks.size} tugas."
        }

        return StudentProgressAssessment(
            hasData = true,
            readinessScore = readinessScore,
            statusLabel = statusLabel,
            summaryText = summaryText,
            scoreReasoningSummary = scoreReasoningSummary,
            scoreBreakdown = scoreBreakdown,
            noteCoveragePercent = noteCoveragePercent,
            quizMasteryPercent = quizMasteryPercent,
            taskCompletionPercent = taskCompletionPercent,
            semesterGradePercent = semesterGradePercent,
            currentGpa = currentGpa,
            targetGpa = targetGpa,
            strengths = strengths,
            focusAreas = focusAreas
        )
    }

    private fun calculateCurrentGpa(courses: List<CourseRow>, priorIpk: Double, priorSks: Int): Double {
        val letterWeights = mapOf(
            "A" to 4.0, "A-" to 3.7, "B+" to 3.3, "B" to 3.0,
            "B-" to 2.7, "C+" to 2.3, "C" to 2.0, "D" to 1.0, "E" to 0.0
        )
        var totalPoints = priorIpk * priorSks
        var totalSks = priorSks
        courses.forEach { c ->
            val letter = c.grade?.letter
            val weight = if (letter != null) letterWeights[letter] else null
            if (weight != null && c.sks > 0) {
                totalPoints += weight * c.sks
                totalSks += c.sks
            }
        }
        return if (totalSks > 0) totalPoints / totalSks else priorIpk
    }

    fun recommendStudySchedule(
        database: StudentHubDatabase,
        focusCourseCode: String = "",
        focusNoteTitle: String = "",
        isEn: Boolean
    ): List<RecommendedStudySlot> {
        val courses = runCatching { database.courses() }.getOrDefault(emptyList())
        val agendas = runCatching { database.allAgendas() }.getOrDefault(emptyList())
        val tasks = runCatching { database.tasks() }.getOrDefault(emptyList())
        val notes = runCatching { database.notes() }.getOrDefault(emptyList()).filter { it.body.isNotBlank() }

        val dayNamesId = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
        val dayNamesEn = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

        val candidateWindows = listOf(
            "09.00" to "09.00 - 10.30",
            "13.30" to "13.30 - 15.00",
            "16.00" to "16.00 - 17.30",
            "19.30" to "19.30 - 21.00"
        )

        data class PriorityTopic(
            val courseCode: String,
            val title: String,
            val reason: String,
            val score: Int
        )

        val priorities = mutableListOf<PriorityTopic>()

        if (focusNoteTitle.isNotBlank() && notes.any { it.title.equals(focusNoteTitle, true) }) {
            val code = focusCourseCode.ifBlank { "Catatan" }
            priorities.add(
                PriorityTopic(
                    courseCode = code,
                    title = if (isEn) "Review: $focusNoteTitle" else "Ulas: $focusNoteTitle",
                    reason = if (isEn) "From note $focusNoteTitle ($code)"
                    else "Berdasarkan catatan $focusNoteTitle ($code)",
                    score = 100
                )
            )
        }

        tasks.filter { !it.completed }.take(3).forEach { task ->
            priorities.add(
                PriorityTopic(
                    courseCode = task.courseId,
                    title = if (isEn) "Task: ${task.title}" else "Tugas: ${task.title}",
                    reason = if (task.deadline.isNotBlank()) {
                        if (isEn) "${task.courseId} • Due ${task.deadline}" else "${task.courseId} • Tenggat ${task.deadline}"
                    } else {
                        if (isEn) "Unfinished task (${task.courseId})" else "Tugas belum selesai (${task.courseId})"
                    },
                    score = 90
                )
            )
        }

        courses.sortedByDescending { c ->
            val lowGradeBonus = if ((c.grade?.score ?: 85) < 75) 30 else 0
            val noNoteBonus = if (notes.none { it.course.equals(c.code, true) }) 15 else 0
            c.sks * 10 + lowGradeBonus + noNoteBonus
        }.forEach { c ->
            val hasNote = notes.any { it.course.equals(c.code, true) }
            val gradeInfo = c.grade?.let { "${it.score} (${it.letter})" }
            val reason = when {
                gradeInfo != null && (c.grade.score < 75) ->
                    if (isEn) "${c.sks} Credits • Grade $gradeInfo"
                    else "${c.sks} SKS • Nilai saat ini $gradeInfo"
                !hasNote ->
                    if (isEn) "${c.sks} Credits • No notes written yet"
                    else "${c.sks} SKS • Belum ada catatan materi"
                else ->
                    if (isEn) "${c.sks} Credits • Weekly review"
                    else "${c.sks} SKS • Ulas materi mingguan"
            }
            priorities.add(
                PriorityTopic(
                    courseCode = c.code,
                    title = if (isEn) "Study: ${c.title}" else "Belajar: ${c.title}",
                    reason = reason,
                    score = c.sks * 10
                )
            )
        }

        if (priorities.isEmpty()) {
            return emptyList()
        }

        val recommendations = mutableListOf<RecommendedStudySlot>()
        val distinctPriorities = priorities.distinctBy { it.title }
        var dayCursor = 0

        for (topic in distinctPriorities) {
            if (recommendations.size >= 4) break
            var placed = false
            for (offset in 0..6) {
                val dayIdx = (dayCursor + offset) % 7
                val dayAgendas = agendas.filter { it.day == dayIdx }
                val freeWindow = candidateWindows.firstOrNull { (startHour, _) ->
                    val targetHour = startHour.substringBefore('.').toIntOrNull() ?: 16
                    dayAgendas.none { ag ->
                        val agHour = ag.time.substringBefore('.').toIntOrNull() ?: -10
                        kotlin.math.abs(agHour - targetHour) <= 1
                    } && recommendations.none { it.dayIndex == dayIdx && it.startTime == startHour }
                }
                if (freeWindow != null) {
                    val dayLabel = if (isEn) dayNamesEn[dayIdx] else dayNamesId[dayIdx]
                    recommendations.add(
                        RecommendedStudySlot(
                            id = "rec-$dayIdx-${freeWindow.first}-${recommendations.size}",
                            dayIndex = dayIdx,
                            dayLabel = dayLabel,
                            startTime = freeWindow.first,
                            timeRangeLabel = freeWindow.second,
                            courseCode = topic.courseCode,
                            title = topic.title.take(180),
                            reason = topic.reason
                        )
                    )
                    dayCursor = (dayIdx + 1) % 7
                    placed = true
                    break
                }
            }
            if (!placed) dayCursor = (dayCursor + 1) % 7
        }

        return recommendations
    }

    fun formatSummaryForNote(summary: NoteSummary, isEn: Boolean): String = buildString {
        appendLine()
        appendLine(if (isEn) "--- Note Summary ---" else "--- Rangkuman Catatan ---")
        appendLine(summary.overview)
        if (summary.keyPoints.isNotEmpty()) {
            appendLine()
            appendLine(if (isEn) "Key Points:" else "Poin Kunci:")
            summary.keyPoints.forEach { appendLine("• $it") }
        }
        if (summary.glossaryTerms.isNotEmpty()) {
            appendLine()
            appendLine(if (isEn) "Key Terms:" else "Istilah Penting:")
            summary.glossaryTerms.forEach { (t, d) -> appendLine("• $t: $d") }
        }
    }

    fun formatStudyGuideForNote(guide: StudyGuide, isEn: Boolean): String = buildString {
        appendLine()
        appendLine(if (isEn) "--- Study Guide: ${guide.topic} ---" else "--- Panduan Belajar: ${guide.topic} ---")
        guide.sections.forEach { sec ->
            appendLine("${sec.heading}:")
            sec.details.forEach { appendLine("• $it") }
            appendLine()
        }
        if (guide.examFocusPoints.isNotEmpty()) {
            appendLine(if (isEn) "Exam Focus:" else "Fokus Ujian:")
            guide.examFocusPoints.forEach { appendLine("• $it") }
        }
    }
}
