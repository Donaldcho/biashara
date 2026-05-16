package com.biasharaai.ai

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * Manages download lifecycle for on-device LiteRT-LM models (Phase 6 X2).
 *
 * Catalogue metadata and paths come from [ModelRegistry]; this class streams bytes from
 * HuggingFace and updates Room download state.
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRegistry: ModelRegistry,
    private val huggingFaceTokenStore: HuggingFaceTokenStore,
) {
    companion object {
        private const val PREFS_NAME = "model_download_prefs"
        private const val KEY_DOWNLOAD_STATE = "download_state"
        private const val KEY_ACTIVE_DOWNLOAD_MODEL_ID = "active_download_model_id"

        const val MODELS_DIR = "models"

        /** Legacy filename — matches [models_catalogue.json] primary entry. */
        const val LEGACY_MODEL_FILENAME = "gemma-4-E2B-it.litertlm"

        @Deprecated("Use catalogue via ModelRegistry", ReplaceWith("LEGACY_MODEL_FILENAME"))
        const val MODEL_FILENAME = LEGACY_MODEL_FILENAME

        @Deprecated("Resolved per model via ModelRegistry.resolveDownloadUrl")
        const val MODEL_DOWNLOAD_URL =
            "https://huggingface.co/litert-community/" +
                "gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"

        const val LEGACY_APPROX_MODEL_SIZE_MB = 2580L

        const val HF_TOKEN_REQUIRED_MESSAGE =
            "This model requires a Hugging Face access token. " +
                "Log in at huggingface.co, accept the Gemma license for the model repo, " +
                "create a read token, then add it under AI Models → Hugging Face access."

        const val HF_FORBIDDEN_MESSAGE =
            "Download returned HTTP 403 (access denied). A read token alone is not enough for " +
                "FunctionGemma: while logged in at huggingface.co, open " +
                "litert-community/functiongemma-270m-ft-mobile-actions and accept the Gemma " +
                "license, then retry. If you use a fine-grained token, grant read access to that " +
                "repo (or to all gated repos you can access)."

        /** @deprecated Use [approxPrimarySizeMb] on an injected instance. */
        const val APPROX_MODEL_SIZE_MB = LEGACY_APPROX_MODEL_SIZE_MB
    }

    fun approxPrimarySizeMb(): Long = modelRegistry.approxPrimarySizeMb()

    data class DownloadProgress(
        val bytesDownloaded: Long = 0L,
        val totalBytes: Long = 0L,
        val percent: Int = 0,
        val modelId: String? = null,
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadPersistedState())
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(DownloadProgress())
    val progress: StateFlow<DownloadProgress> = _progress.asStateFlow()

    val modelsDir: File
        get() = modelRegistry.modelsDir

    val modelFilePath: File
        get() = modelRegistry.primaryModelFile()

    val isModelDownloaded: Boolean
        get() = modelRegistry.isPrimaryDownloaded()

    val modelSizeBytes: Long
        get() = if (modelFilePath.exists()) modelFilePath.length() else 0L

    /** Download the current primary model from the catalogue. */
    suspend fun downloadModel() {
        downloadModel(modelRegistry.primaryModelId())
    }

    suspend fun downloadModel(modelId: String) {
        if (_state.value == DownloadState.DOWNLOADING) return

        _state.value = DownloadState.DOWNLOADING
        _progress.value = DownloadProgress(modelId = modelId)
        persistState(DownloadState.DOWNLOADING, modelId)

        try {
            withContext(Dispatchers.IO) {
                modelsDir.mkdirs()
                purgeLegacyModels(modelId)

                val target = modelRegistry.modelFile(modelId)
                val tempFile = File(modelsDir, "${target.name}.tmp")
                tempFile.delete()

                val url = modelRegistry.resolveDownloadUrl(modelId)
                val catalogueEntry = modelRegistry.catalogue().models.find { it.modelId == modelId }
                val expectedBytes = catalogueEntry?.sizeBytes
                    ?: (LEGACY_APPROX_MODEL_SIZE_MB * 1024 * 1024)

                if (catalogueEntry?.requiresHfAccess == true && !huggingFaceTokenStore.hasToken()) {
                    throw Exception(HF_TOKEN_REQUIRED_MESSAGE)
                }

                downloadFromUrl(url, tempFile, expectedBytes, modelId)

                val minBytes = (expectedBytes * 9) / 10
                if (tempFile.length() < minBytes) {
                    tempFile.delete()
                    throw Exception(
                        "Downloaded file too small (${tempFile.length()} bytes, expected ~$expectedBytes). " +
                            "Check network or HuggingFace availability.",
                    )
                }

                target.delete()
                if (!tempFile.renameTo(target)) {
                    tempFile.copyTo(target, overwrite = true)
                    tempFile.delete()
                }

                modelRegistry.markDownloaded(modelId, target.absolutePath)

                _state.value = DownloadState.DOWNLOADED
                persistState(DownloadState.DOWNLOADED, modelId)
            }
        } catch (e: Exception) {
            _state.value = DownloadState.FAILED
            persistState(DownloadState.FAILED, modelId)
            throw e
        }
    }

    suspend fun deleteModel() {
        deleteModel(modelRegistry.primaryModelId())
    }

    suspend fun deleteModel(modelId: String) = withContext(Dispatchers.IO) {
        val file = modelRegistry.modelFile(modelId)
        file.delete()
        File(modelsDir, "${file.name}.tmp").delete()
        modelRegistry.clearDownloaded(modelId)
        if (modelId == modelRegistry.primaryModelId()) {
            _state.value = DownloadState.NOT_DOWNLOADED
            persistState(DownloadState.NOT_DOWNLOADED, null)
        }
    }

    fun resetAfterFailure() {
        if (_state.value == DownloadState.FAILED) {
            _state.value = DownloadState.NOT_DOWNLOADED
            persistState(DownloadState.NOT_DOWNLOADED, null)
        }
    }

    /** Removes pre-catalogue filenames only; keeps other catalogue models on disk. */
    private fun purgeLegacyModels(activeModelId: String) {
        val catalogueNames = modelRegistry.catalogue().models.map { it.fileName }.toSet()
        val activeName = modelRegistry.modelFile(activeModelId).name
        val oldModel3n = File(modelsDir, "gemma-3n-E2B-it-int4.litertlm")
        if (oldModel3n.exists()) oldModel3n.delete()
        modelsDir.listFiles()?.forEach { file ->
            if (!file.isFile || !file.name.endsWith(".litertlm") || file.name == activeName) return@forEach
            if (file.name !in catalogueNames) file.delete()
        }
    }

    private fun downloadFromUrl(url: String, destFile: File, expectedBytes: Long, modelId: String) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 60_000
            readTimeout = 300_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "BiasharaAI/1.0")
            huggingFaceTokenStore.getToken()?.let { token ->
                setRequestProperty("Authorization", "Bearer $token")
            }
        }
        try {
            conn.connect()
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> Unit
                HttpURLConnection.HTTP_UNAUTHORIZED -> throw Exception(
                    if (huggingFaceTokenStore.hasToken()) {
                        "Download returned HTTP 401. Your Hugging Face token may be invalid, or you " +
                            "have not accepted the model license on huggingface.co yet."
                    } else {
                        HF_TOKEN_REQUIRED_MESSAGE
                    },
                )
                HttpURLConnection.HTTP_FORBIDDEN -> throw Exception(
                    if (huggingFaceTokenStore.hasToken()) {
                        HF_FORBIDDEN_MESSAGE
                    } else {
                        HF_TOKEN_REQUIRED_MESSAGE
                    },
                )
                else -> throw Exception("Download returned HTTP ${conn.responseCode}")
            }
            streamToFile(conn, destFile, expectedBytes, modelId)
        } finally {
            conn.disconnect()
        }
    }

    private fun streamToFile(
        connection: HttpURLConnection,
        destFile: File,
        fallbackTotalBytes: Long,
        modelId: String,
    ) {
        val contentLength = connection.contentLengthLong.let {
            if (it > 0) it else fallbackTotalBytes
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

                    if (totalBytesRead - lastProgressUpdate > 256_000) {
                        lastProgressUpdate = totalBytesRead
                        val pct = ((totalBytesRead * 100) / contentLength)
                            .toInt().coerceIn(0, 99)
                        _progress.value = DownloadProgress(
                            bytesDownloaded = totalBytesRead,
                            totalBytes = contentLength,
                            percent = pct,
                            modelId = modelId,
                        )
                    }
                }

                _progress.value = DownloadProgress(
                    bytesDownloaded = totalBytesRead,
                    totalBytes = contentLength,
                    percent = 100,
                    modelId = modelId,
                )
            }
        }
    }

    private fun persistState(state: DownloadState, modelId: String?) {
        prefs.edit()
            .putString(KEY_DOWNLOAD_STATE, state.name)
            .putString(KEY_ACTIVE_DOWNLOAD_MODEL_ID, modelId)
            .apply()
    }

    private fun loadPersistedState(): DownloadState {
        if (isModelDownloaded) return DownloadState.DOWNLOADED

        val saved = prefs.getString(KEY_DOWNLOAD_STATE, null)
        return try {
            if (saved != null) DownloadState.valueOf(saved) else DownloadState.NOT_DOWNLOADED
        } catch (_: IllegalArgumentException) {
            DownloadState.NOT_DOWNLOADED
        }
    }
}

enum class DownloadState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED,
}
