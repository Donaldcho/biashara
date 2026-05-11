package com.biasharaai.ai

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the download lifecycle of the Gemma LLM model.
 *
 * Downloads the Gemma 4 E2B model (~2.58 GB) from HuggingFace's
 * litert-community repository to `getFilesDir()/models/` on first launch
 * when the device is AI-capable. No HuggingFace token is required.
 *
 * Model acquisition priority:
 * 1. Check if model already downloaded to `getFilesDir()/models/`
 * 2. Download from HuggingFace litert-community (no auth required)
 * 3. If download fails, fall back to RULES_BASED tier
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    private val context: Context,
) {
    companion object {
        private const val PREFS_NAME = "model_download_prefs"
        private const val KEY_DOWNLOAD_STATE = "download_state"
        private const val KEY_MODEL_FILE_PATH = "model_file_path"
        private const val KEY_MODEL_SIZE_BYTES = "model_size_bytes"

        /** Directory under filesDir where models are stored. */
        const val MODELS_DIR = "models"

        /** Expected model filename. */
        const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"

        /**
         * Gemma 4 E2B model download URL (HuggingFace litert-community).
         *
         * The litert-community repository is publicly accessible
         * without authentication (no license gate).
         * HuggingFace /resolve/ URLs redirect to their CDN.
         */
        const val MODEL_DOWNLOAD_URL =
            "https://huggingface.co/litert-community/" +
                "gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"

        /** Approximate model size for UI display (~2,580 MB). */
        const val APPROX_MODEL_SIZE_MB = 2580L
    }

    /** Download progress info for UI display. */
    data class DownloadProgress(
        val bytesDownloaded: Long = 0L,
        val totalBytes: Long = 0L,
        val percent: Int = 0,
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadPersistedState())
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(DownloadProgress())
    val progress: StateFlow<DownloadProgress> = _progress.asStateFlow()

    /** Directory where models are stored. */
    val modelsDir: File
        get() = File(context.filesDir, MODELS_DIR).also { it.mkdirs() }

    /** Full path to the model file. */
    val modelFilePath: File
        get() = File(modelsDir, MODEL_FILENAME)

    /** Whether the model file exists on disk. */
    val isModelDownloaded: Boolean
        get() = modelFilePath.exists() && modelFilePath.length() > 0

    /** Size of the downloaded model in bytes, or 0 if not present. */
    val modelSizeBytes: Long
        get() = if (modelFilePath.exists()) modelFilePath.length() else 0L

    /**
     * Start downloading the model.
     *
     * This is a suspending function that runs on [Dispatchers.IO].
     * Progress updates are emitted via [state].
     *
     * Google Drive large-file download flow:
     * 1. Hit the `/uc?export=download` URL
     * 2. If the response is an HTML virus-scan warning page,
     *    extract the real download URL and replay with cookies.
     * 3. Stream the binary payload to a temp file, then rename.
     */
    suspend fun downloadModel() {
        if (_state.value == DownloadState.DOWNLOADING) return

        _state.value = DownloadState.DOWNLOADING
        _progress.value = DownloadProgress()
        persistState(DownloadState.DOWNLOADING)

        try {
            withContext(Dispatchers.IO) {
                modelsDir.mkdirs()
                // Clean up old Gemma 3n model if present
                val oldModel3n = File(modelsDir, "gemma-3n-E2B-it-int4.litertlm")
                if (oldModel3n.exists()) oldModel3n.delete()

                val tempFile = File(modelsDir, "$MODEL_FILENAME.tmp")
                tempFile.delete() // Remove any partial previous download

                downloadFromHuggingFace(tempFile)

                // Validate — a real model file is > 100MB
                if (tempFile.length() < 100_000_000L) {
                    tempFile.delete()
                    throw Exception(
                        "Downloaded file too small (${tempFile.length()} bytes). " +
                            "The Google Drive link may have expired or returned an error page."
                    )
                }

                // Rename temp file to final
                val target = modelFilePath
                target.delete()
                if (!tempFile.renameTo(target)) {
                    // renameTo can fail across mount points; copy instead
                    tempFile.copyTo(target, overwrite = true)
                    tempFile.delete()
                }

                _state.value = DownloadState.DOWNLOADED
                persistState(DownloadState.DOWNLOADED)
                prefs.edit()
                    .putString(KEY_MODEL_FILE_PATH, modelFilePath.absolutePath)
                    .putLong(KEY_MODEL_SIZE_BYTES, modelFilePath.length())
                    .apply()
            }
        } catch (e: Exception) {
            _state.value = DownloadState.FAILED
            persistState(DownloadState.FAILED)
            throw e
        }
    }

    /**
     * Download from HuggingFace litert-community.
     *
     * HuggingFace /resolve/ URLs redirect to their CDN.
     * instanceFollowRedirects handles this automatically.
     */
    private fun downloadFromHuggingFace(destFile: File) {
        val conn = (URL(MODEL_DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 60_000
            readTimeout = 300_000  // 5 min read timeout for large file
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "BiasharaAI/1.0")
        }

        try {
            conn.connect()
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HuggingFace returned HTTP ${conn.responseCode}")
            }
            streamToFile(conn, destFile)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Stream the HTTP response body to a file with progress updates.
     */
    private fun streamToFile(connection: HttpURLConnection, destFile: File) {
        val contentLength = connection.contentLengthLong.let {
            if (it > 0) it else APPROX_MODEL_SIZE_MB * 1024 * 1024
        }

        connection.inputStream.use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(32_768)
                var totalBytesRead = 0L
                var lastProgressUpdate = 0L
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    // Emit progress every ~256KB
                    if (totalBytesRead - lastProgressUpdate > 256_000) {
                        lastProgressUpdate = totalBytesRead
                        val pct = ((totalBytesRead * 100) / contentLength)
                            .toInt().coerceIn(0, 99)
                        _progress.value = DownloadProgress(
                            bytesDownloaded = totalBytesRead,
                            totalBytes = contentLength,
                            percent = pct,
                        )
                    }
                }

                // Final progress
                _progress.value = DownloadProgress(
                    bytesDownloaded = totalBytesRead,
                    totalBytes = contentLength,
                    percent = 100,
                )
            }
        }
    }

    /** Delete the downloaded model file and reset state. */
    fun deleteModel() {
        modelFilePath.delete()
        File(modelsDir, "$MODEL_FILENAME.tmp").delete()
        _state.value = DownloadState.NOT_DOWNLOADED
        persistState(DownloadState.NOT_DOWNLOADED)
        prefs.edit()
            .remove(KEY_MODEL_FILE_PATH)
            .remove(KEY_MODEL_SIZE_BYTES)
            .apply()
    }

    /** Reset to NOT_DOWNLOADED after a failure so the user can retry. */
    fun resetAfterFailure() {
        if (_state.value == DownloadState.FAILED) {
            _state.value = DownloadState.NOT_DOWNLOADED
            persistState(DownloadState.NOT_DOWNLOADED)
        }
    }

    private fun persistState(state: DownloadState) {
        prefs.edit().putString(KEY_DOWNLOAD_STATE, state.name).apply()
    }

    private fun loadPersistedState(): DownloadState {
        // If the file exists, trust the filesystem over prefs
        if (isModelDownloaded) return DownloadState.DOWNLOADED

        val saved = prefs.getString(KEY_DOWNLOAD_STATE, null)
        return try {
            if (saved != null) DownloadState.valueOf(saved) else DownloadState.NOT_DOWNLOADED
        } catch (_: IllegalArgumentException) {
            DownloadState.NOT_DOWNLOADED
        }
    }
}

/** Download lifecycle state. */
enum class DownloadState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED,
}
