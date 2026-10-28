package org.umn.application.studenthub.ui.notes

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.umn.application.studenthub.BuildConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * REST Client for the external Google NotebookLM microservice
 * powered by https://github.com/gnh1201/notebooklm-rest-api
 *
 * Keeps the mobile app decoupled (non-monolithic).
 * The service runs in Docker or a separate server, and this client communicates via HTTP.
 */
object NotebookLmRestApiClient {

    private const val PREFS_NAME = "notebooklm_service_prefs"
    private const val KEY_BASE_URL = "custom_base_url"
    private const val KEY_API_KEY = "custom_api_key"

    @Volatile
    private var lastHealthStatus: Boolean? = null

    @Volatile
    private var lastCheckedTimestamp: Long = 0L

    fun getBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_BASE_URL, null)
        if (!saved.isNullOrBlank()) return saved.trim().trimEnd('/')
        return BuildConfig.NOTEBOOKLM_BASE_URL.trim().trimEnd('/')
    }

    fun setBaseUrl(context: Context, url: String) {
        val clean = url.trim().trimEnd('/')
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE_URL, clean)
            .apply()
        lastHealthStatus = null
    }

    fun getApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_API_KEY, null)
        if (!saved.isNullOrBlank()) return saved.trim()
        return BuildConfig.NOTEBOOKLM_API_KEY.trim()
    }

    fun setApiKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_API_KEY, key.trim())
            .apply()
    }

    /**
     * Cek apakah microservice NotebookLM aktif dan bisa diakses.
     * Menggunakan cache 8 detik agar tidak membebani jaringan di UI.
     */
    suspend fun isHealthy(context: Context, forceRefresh: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && lastHealthStatus != null && now - lastCheckedTimestamp < 8_000L) {
            return@withContext lastHealthStatus == true
        }

        val baseUrl = getBaseUrl(context)
        if (baseUrl.isBlank()) {
            lastHealthStatus = false
            lastCheckedTimestamp = now
            return@withContext false
        }

        val healthy = runCatching {
            val url = URL("$baseUrl/health")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3_000
                readTimeout = 3_000
                val apiKey = getApiKey(context)
                if (apiKey.isNotBlank()) {
                    setRequestProperty("X-API-Key", apiKey)
                }
            }
            try {
                conn.responseCode in 200..299
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)

        lastHealthStatus = healthy
        lastCheckedTimestamp = now
        healthy
    }

    /**
     * Dapatkan ID notebook berdasarkan nama judul atau buat baru di NotebookLM.
     */
    suspend fun ensureNotebook(context: Context, title: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = getBaseUrl(context)
            // 1. Cek list notebook yang sudah ada
            val listConn = openConnection(context, "$baseUrl/v1/notebooks", "GET")
            val listJson = readResponseAndDisconnect(listConn)
            val items = JSONObject(listJson).optJSONArray("items") ?: JSONArray()
            val cleanTarget = title.trim().lowercase()

            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val existingTitle = item.optString("title").trim().lowercase()
                val id = item.optString("id").ifBlank { item.optString("notebook_id") }
                if (existingTitle == cleanTarget && id.isNotBlank()) {
                    return@runCatching id
                }
            }

            // 2. Buat notebook baru jika belum ada
            val createConn = openConnection(context, "$baseUrl/v1/notebooks", "POST")
            val payload = JSONObject().apply { put("title", title.trim()) }.toString()
            writeBody(createConn, payload)
            val createJson = readResponseAndDisconnect(createConn)
            val created = JSONObject(createJson)
            val notebookObj = created.optJSONObject("notebook")
            val createdId = notebookObj?.optString("id")
                ?: notebookObj?.optString("notebook_id")
                ?: created.optString("id")
            if (createdId.isBlank()) error("Gagal mendapatkan ID notebook dari respon layanan.")
            createdId
        }
    }

    /**
     * Tambahkan atau perbarui materi catatan sebagai Text Source di notebook NotebookLM.
     */
    suspend fun syncTextSource(
        context: Context,
        notebookId: String,
        sourceTitle: String,
        content: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = getBaseUrl(context)
            val conn = openConnection(context, "$baseUrl/v1/notebooks/$notebookId/sources/text", "POST")
            val payload = JSONObject().apply {
                put("title", sourceTitle.trim().take(100).ifBlank { "Catatan Kuliah" })
                put("content", content.trim())
            }.toString()
            writeBody(conn, payload)
            val res = readResponseAndDisconnect(conn)
            val root = JSONObject(res)
            val sourceObj = root.optJSONObject("source")
            sourceObj?.optString("id") ?: root.optString("id", "synced")
        }
    }

    /**
     * Tanyakan materi langsung ke Google NotebookLM berbasis sumber catatan.
     */
    suspend fun askNotebookChat(
        context: Context,
        notebookId: String,
        question: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = getBaseUrl(context)
            val conn = openConnection(context, "$baseUrl/v1/notebooks/$notebookId/chat/ask", "POST")
            conn.readTimeout = 45_000
            val payload = JSONObject().apply {
                put("question", question.trim())
            }.toString()
            writeBody(conn, payload)
            val res = readResponseAndDisconnect(conn)
            val root = JSONObject(res)
            val resultObj = root.optJSONObject("result")
            val answer = resultObj?.optString("answer")
                ?: resultObj?.optString("text")
                ?: root.optString("answer")
            if (answer.isNullOrBlank()) error("NotebookLM tidak memberikan jawaban.")
            answer
        }
    }

    /**
     * Ambil ringkasan resmi notebook dari Google NotebookLM.
     */
    suspend fun getNotebookSummary(
        context: Context,
        notebookId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = getBaseUrl(context)
            val conn = openConnection(context, "$baseUrl/v1/notebooks/$notebookId/summary", "GET")
            conn.readTimeout = 30_000
            val res = readResponseAndDisconnect(conn)
            val root = JSONObject(res)
            root.optString("summary")
        }
    }

    /**
     * Memicu pembuatan artefak (quiz, flashcards, mind_map, audio, report).
     */
    suspend fun generateArtifact(
        context: Context,
        notebookId: String,
        artifactType: String
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = getBaseUrl(context)
            val conn = openConnection(context, "$baseUrl/v1/notebooks/$notebookId/artifacts/generate", "POST")
            conn.readTimeout = 60_000
            val payload = JSONObject().apply {
                put("type", artifactType)
                put("options", JSONObject())
            }.toString()
            writeBody(conn, payload)
            val res = readResponseAndDisconnect(conn)
            JSONObject(res)
        }
    }

    private fun openConnection(context: Context, urlString: String, method: String): HttpURLConnection {
        val url = URI(urlString).toURL()
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 25_000
            doInput = true
            if (method == "POST" || method == "PUT" || method == "PATCH") {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            val apiKey = getApiKey(context)
            if (apiKey.isNotBlank()) {
                setRequestProperty("X-API-Key", apiKey)
            }
        }
    }

    private fun writeBody(conn: HttpURLConnection, bodyJson: String) {
        OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(bodyJson)
            writer.flush()
        }
    }

    private fun readResponseAndDisconnect(conn: HttpURLConnection): String {
        try {
            val status = conn.responseCode
            val isSuccess = status in 200..299
            val stream = if (isSuccess) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
            if (!isSuccess) {
                val detail = runCatching { JSONObject(text).optString("detail") }.getOrNull()
                error(detail?.ifBlank { "HTTP $status" } ?: "HTTP $status: $text")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }
}
