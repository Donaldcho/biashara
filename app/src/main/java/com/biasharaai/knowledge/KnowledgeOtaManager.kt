package com.biasharaai.knowledge

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks for and applies over-the-air knowledge base updates.
 *
 * The manifest URL is checked on demand (not on a schedule) and only downloads
 * when the remote version exceeds the locally-installed version.
 */
@Singleton
class KnowledgeOtaManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ingestor: KnowledgeIngestor,
    private val httpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "KnowledgeOtaManager"
        private const val PREFS_NAME = "knowledge_ota"
        private const val KEY_INSTALLED_VERSION = "installed_version"
        private const val MANIFEST_URL =
            "https://raw.githubusercontent.com/Deviceterra/biasharaai-knowledge/main/manifest.json"
        private const val OTA_DIR = "knowledge_ota"
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val gson = Gson()

    val installedVersion: Int get() = prefs.getInt(KEY_INSTALLED_VERSION, 0)

    /** Checks the remote manifest and returns it if an update is available, null otherwise. */
    suspend fun checkForUpdate(): KnowledgeManifest? = withContext(Dispatchers.IO) {
        runCatching {
            val manifest = fetchManifest() ?: return@withContext null
            if (manifest.version > installedVersion) manifest else null
        }.onFailure { Log.w(TAG, "OTA check failed", it) }.getOrNull()
    }

    /** Downloads and applies the OTA knowledge package. Returns true on success. */
    suspend fun applyUpdate(manifest: KnowledgeManifest): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val zipFile = downloadPackage(manifest) ?: return@withContext false
            if (!verifyChecksum(zipFile, manifest.sha256)) {
                zipFile.delete()
                Log.e(TAG, "OTA package checksum mismatch — aborted")
                return@withContext false
            }
            extractAndIngest(zipFile, manifest)
            zipFile.delete()
            prefs.edit().putInt(KEY_INSTALLED_VERSION, manifest.version).apply()
            Log.i(TAG, "Knowledge OTA v${manifest.version} applied successfully")
            true
        }.onFailure { Log.e(TAG, "OTA apply failed", it) }.getOrDefault(false)
    }

    private fun fetchManifest(): KnowledgeManifest? {
        val request = Request.Builder().url(MANIFEST_URL).build()
        val body = httpClient.newCall(request).execute().use { it.body?.string() } ?: return null
        return gson.fromJson(body, KnowledgeManifest::class.java)
    }

    private fun downloadPackage(manifest: KnowledgeManifest): File? {
        val outDir = File(context.filesDir, OTA_DIR).also { it.mkdirs() }
        val outFile = File(outDir, "knowledge_v${manifest.version}.zip")
        val request = Request.Builder().url(manifest.downloadUrl).build()
        httpClient.newCall(request).execute().use { response ->
            val bytes = response.body?.bytes() ?: return null
            outFile.writeBytes(bytes)
        }
        return outFile
    }

    private fun verifyChecksum(file: File, expectedSha256: String): Boolean {
        if (expectedSha256.isBlank()) return true // skip verification if no checksum provided
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buf = ByteArray(8192)
            var n: Int
            while (stream.read(buf).also { n = it } != -1) {
                digest.update(buf, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedSha256, ignoreCase = true)
    }

    private suspend fun extractAndIngest(zipFile: File, manifest: KnowledgeManifest) {
        // Extract the zip to a temp dir, then trigger re-ingestion for affected languages.
        val extractDir = File(context.filesDir, "$OTA_DIR/extract_${manifest.version}")
        extractDir.mkdirs()
        try {
            java.util.zip.ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(extractDir, entry.name)
                    if (!entry.isDirectory) {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { out -> zis.copyTo(out) }
                    }
                    entry = zis.nextEntry
                }
            }
            // Re-ingest all languages that were updated
            for (lang in manifest.languagesIncluded) {
                ingestor.ingestLanguage(lang, forceReingest = true)
            }
        } finally {
            extractDir.deleteRecursively()
        }
    }
}
