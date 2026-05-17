package com.biasharaai.cloud

import com.biasharaai.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private val JSON = "application/json; charset=utf-8".toMediaType()
private val OCTET = "application/octet-stream".toMediaType()

@Singleton
class CloudAnalysisHttpClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun requireHttps(url: String) {
        require(url.startsWith("https://", ignoreCase = true)) {
            "Only https:// endpoints are allowed."
        }
    }

    /**
     * POST JSON body to the user-configured analytics URL. Returns response body string on success.
     */
    fun postJson(url: String, jsonBody: String, bearerToken: String?): Result<String> = runCatching {
        requireHttps(url)
        val body = jsonBody.toRequestBody(JSON)
        val req = Request.Builder()
            .url(url)
            .post(body)
            .header("User-Agent", "BiasharaAI-Android/${BuildConfig.VERSION_NAME}")
            .header("X-Biashara-Export-Type", "business-analytics-json")
            .apply {
                if (!bearerToken.isNullOrBlank()) {
                    header("Authorization", "Bearer ${bearerToken.trim()}")
                }
            }
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                error("HTTP ${resp.code}: ${text.take(500)}")
            }
            text
        }
    }

    /**
     * Multipart upload of a SQLite file (same logical DB as Room). Server must accept multipart.
     */
    fun postSqliteFile(url: String, dbFile: File, bearerToken: String?): Result<String> = runCatching {
        requireHttps(url)
        require(dbFile.isFile && dbFile.canRead()) { "Database file is not readable." }
        val part = MultipartBody.Part.createFormData(
            "database",
            dbFile.name,
            dbFile.asRequestBody(OCTET),
        )
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("export_kind", "room-sqlite")
            .addPart(part)
            .build()
        val req = Request.Builder()
            .url(url)
            .post(multipart)
            .header("User-Agent", "BiasharaAI-Android/${BuildConfig.VERSION_NAME}")
            .header("X-Biashara-Export-Type", "sqlite-database")
            .apply {
                if (!bearerToken.isNullOrBlank()) {
                    header("Authorization", "Bearer ${bearerToken.trim()}")
                }
            }
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                error("HTTP ${resp.code}: ${text.take(500)}")
            }
            text
        }
    }
}
