package com.biasharaai.ai

import android.content.Context
import android.content.SharedPreferences
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages LiteRT-LM model downloads.
 *
 * Downloads are stored as `<model>.tmp` until the byte count and optional SHA-256 pass
 * validation. Failed downloads keep that partial file so the next retry can resume with HTTP
 * range requests when the server supports them.
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

        private const val IO_BUFFER_BYTES = 1024 * 1024
        private const val PROGRESS_INTERVAL_BYTES = 4L * 1024L * 1024L
        private const val PROGRESS_INTERVAL_MS = 1_500L
        private const val MAX_DOWNLOAD_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1_500L
        private const val CONNECT_TIMEOUT_MS = 45_000
        private const val READ_TIMEOUT_MS = 180_000
        private const val FREE_SPACE_BUFFER_BYTES = 256L * 1024L * 1024L
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private const val SHA256_HEX_LENGTH = 64

        const val MODELS_DIR = "models"

        /** Legacy filename: matches [models_catalogue.json] primary entry. */
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
                "create a read token, then add it under AI Models -> Hugging Face access."

        const val HF_FORBIDDEN_MESSAGE =
            "Download returned HTTP 403 (access denied). A read token alone is not enough for " +
                "FunctionGemma: while logged in at huggingface.co, open " +
                "litert-community/functiongemma-270m-ft-mobile-actions and accept the Gemma " +
                "license, then retry. If you use a fine-grained token, grant read access to that " +
                "repo (or to all gated repos you can access)."

        /** @deprecated Use [approxPrimarySizeMb] on an injected instance. */
        const val APPROX_MODEL_SIZE_MB = LEGACY_APPROX_MODEL_SIZE_MB
    }

    data class DownloadProgress(
        val bytesDownloaded: Long = 0L,
        val totalBytes: Long = 0L,
        val percent: Int = 0,
        val modelId: String? = null,
        val resumedFromBytes: Long = 0L,
        val bytesPerSecond: Long = 0L,
        val estimatedSecondsRemaining: Long? = null,
    ) {
        val isResuming: Boolean get() = resumedFromBytes > 0L
    }

    private data class DownloadPlan(
        val modelId: String,
        val url: String,
        val targetFile: File,
        val partialFile: File,
        val catalogueBytes: Long,
        val sha256: String,
        val requiresHfAccess: Boolean,
    )

    private data class ContentRange(
        val start: Long,
        val end: Long,
        val totalBytes: Long,
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val downloadMutex = Mutex()

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

    fun approxPrimarySizeMb(): Long = modelRegistry.approxPrimarySizeMb()

    /** Download the current primary model from the catalogue. */
    suspend fun downloadModel() {
        downloadModel(modelRegistry.primaryModelId())
    }

    suspend fun downloadModel(modelId: String) = downloadMutex.withLock {
        if (_state.value == DownloadState.DOWNLOADING) return@withLock

        _state.value = DownloadState.DOWNLOADING
        _progress.value = DownloadProgress(modelId = modelId)
        persistState(DownloadState.DOWNLOADING, modelId)

        try {
            withContext(Dispatchers.IO) {
                downloadModelInternal(modelId)
            }
            _state.value = DownloadState.DOWNLOADED
            persistState(DownloadState.DOWNLOADED, modelId)
        } catch (cancelled: CancellationException) {
            markFailed(modelId)
            throw cancelled
        } catch (e: Exception) {
            val failure = normaliseFailure(e)
            markFailed(modelId)
            throw failure
        }
    }

    suspend fun deleteModel() {
        deleteModel(modelRegistry.primaryModelId())
    }

    suspend fun deleteModel(modelId: String) = downloadMutex.withLock {
        withContext(Dispatchers.IO) {
            val file = modelRegistry.modelFile(modelId)
            file.delete()
            partialFileFor(file).delete()
            metadataFileFor(file).delete()
            modelRegistry.clearDownloaded(modelId)
        }
        _progress.value = DownloadProgress()
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

    private suspend fun downloadModelInternal(modelId: String) {
        modelsDir.mkdirs()
        purgeLegacyModels(modelId)

        val plan = createPlan(modelId)
        if (plan.requiresHfAccess && !huggingFaceTokenStore.hasToken()) {
            throw ModelDownloadException(HF_TOKEN_REQUIRED_MESSAGE)
        }

        if (validateExistingTarget(plan)) return

        recoverInterruptedFinalFile(plan)
        discardImpossiblePartial(plan)
        publishExistingPartial(plan)
        ensureStorageAvailable(plan)

        val acceptedTotalBytes = downloadWithRetries(plan)
        validateCompleteDownload(plan, acceptedTotalBytes)
        finaliseDownload(plan)
        writeDownloadMetadata(plan, acceptedTotalBytes)
        modelRegistry.markDownloaded(modelId, plan.targetFile.absolutePath)

        publishProgress(
            bytesDownloaded = plan.targetFile.length(),
            totalBytes = acceptedTotalBytes,
            modelId = modelId,
        )
    }

    private fun createPlan(modelId: String): DownloadPlan {
        val entry = modelRegistry.catalogue().models.find { it.modelId == modelId }
            ?: throw ModelDownloadException("Unknown model '$modelId'. Refresh the model catalogue and try again.")
        val target = modelRegistry.modelFile(modelId)
        return DownloadPlan(
            modelId = modelId,
            url = entry.huggingFaceResolveUrl(),
            targetFile = target,
            partialFile = partialFileFor(target),
            catalogueBytes = entry.sizeBytes,
            sha256 = entry.sha256.trim(),
            requiresHfAccess = entry.requiresHfAccess,
        )
    }

    private suspend fun validateExistingTarget(plan: DownloadPlan): Boolean {
        val target = plan.targetFile
        if (!target.exists()) return false
        if (!hasAcceptableCompletedSize(target, plan.catalogueBytes, plan.modelId)) return false
        verifySha256IfPresent(target, plan.sha256)
        plan.partialFile.delete()
        modelRegistry.markDownloaded(plan.modelId, target.absolutePath)
        publishProgress(
            bytesDownloaded = target.length(),
            totalBytes = target.length(),
            modelId = plan.modelId,
        )
        return true
    }

    private suspend fun recoverInterruptedFinalFile(plan: DownloadPlan) {
        val target = plan.targetFile
        if (!target.exists()) return

        val targetBytes = target.length()
        if (targetBytes <= 0L) {
            target.delete()
            modelRegistry.clearDownloaded(plan.modelId)
            return
        }

        if (targetBytes < plan.catalogueBytes) {
            if (!plan.partialFile.exists() || plan.partialFile.length() < targetBytes) {
                target.copyTo(plan.partialFile, overwrite = true)
            }
            target.delete()
            modelRegistry.clearDownloaded(plan.modelId)
        }
    }

    private fun discardImpossiblePartial(plan: DownloadPlan) {
        val partial = plan.partialFile
        if (!partial.exists()) return
        if (partial.length() <= 0L) {
            partial.delete()
        }
    }

    private fun publishExistingPartial(plan: DownloadPlan) {
        val partialBytes = plan.partialFile.length()
        if (partialBytes <= 0L) return
        publishProgress(
            bytesDownloaded = partialBytes,
            totalBytes = plan.catalogueBytes,
            modelId = plan.modelId,
            resumedFromBytes = partialBytes,
        )
    }

    private fun ensureStorageAvailable(plan: DownloadPlan) {
        if (plan.catalogueBytes <= 0L) return
        val partialBytes = plan.partialFile.length().coerceAtMost(plan.catalogueBytes)
        val remainingBytes = (plan.catalogueBytes - partialBytes).coerceAtLeast(0L)
        val requiredBytes = remainingBytes + FREE_SPACE_BUFFER_BYTES
        val availableBytes = StatFs(modelsDir.absolutePath).availableBytes
        if (availableBytes < requiredBytes) {
            throw ModelDownloadException(
                "Not enough free storage. Need about ${formatMb(requiredBytes)} MB free; " +
                    "${formatMb(availableBytes)} MB is available.",
            )
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

    private suspend fun downloadWithRetries(plan: DownloadPlan): Long {
        var attempt = 1
        while (true) {
            try {
                return downloadFromUrl(plan)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (io: IOException) {
                if (attempt >= MAX_DOWNLOAD_ATTEMPTS) throw io
                publishExistingPartial(plan)
                delay(RETRY_DELAY_MS * attempt)
                attempt += 1
            }
        }
    }

    private fun downloadFromUrl(plan: DownloadPlan, allowResume: Boolean = true): Long {
        val resumeFromBytes = if (allowResume) plan.partialFile.length().coerceAtLeast(0L) else 0L
        val conn = openConnection(plan.url, resumeFromBytes)
        try {
            conn.connect()
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    if (resumeFromBytes > 0L) {
                        plan.partialFile.delete()
                    }
                    val serverTotalBytes = acceptedTotalBytes(conn.contentLengthLong, plan.catalogueBytes)
                    rejectImplausiblySmallServerFile(serverTotalBytes, plan.catalogueBytes, plan.partialFile)
                    streamToFile(
                        connection = conn,
                        destFile = plan.partialFile,
                        fallbackTotalBytes = serverTotalBytes,
                        modelId = plan.modelId,
                        append = false,
                        initialBytes = 0L,
                        resumedFromBytes = 0L,
                    )
                    return serverTotalBytes
                }

                HttpURLConnection.HTTP_PARTIAL -> {
                    val contentRange = parseContentRange(conn.getHeaderField("Content-Range"))
                    val rangeStart = contentRange?.start ?: resumeFromBytes
                    if (rangeStart != resumeFromBytes) {
                        plan.partialFile.delete()
                        conn.disconnect()
                        return downloadFromUrl(plan, allowResume = false)
                    }
                    val serverTotalBytes = acceptedTotalBytes(contentRange?.totalBytes ?: -1L, plan.catalogueBytes)
                    rejectImplausiblySmallServerFile(serverTotalBytes, plan.catalogueBytes, plan.partialFile)
                    streamToFile(
                        connection = conn,
                        destFile = plan.partialFile,
                        fallbackTotalBytes = serverTotalBytes,
                        modelId = plan.modelId,
                        append = resumeFromBytes > 0L,
                        initialBytes = resumeFromBytes,
                        resumedFromBytes = resumeFromBytes,
                    )
                    return serverTotalBytes
                }

                HTTP_RANGE_NOT_SATISFIABLE -> {
                    val serverTotalBytes = parseContentRangeTotal(conn.getHeaderField("Content-Range"))
                    if (serverTotalBytes > 0L && hasExpectedSize(plan.partialFile, serverTotalBytes)) {
                        return serverTotalBytes
                    }
                    plan.partialFile.delete()
                    conn.disconnect()
                    return downloadFromUrl(plan, allowResume = false)
                }

                HttpURLConnection.HTTP_UNAUTHORIZED -> throw ModelDownloadException(
                    if (huggingFaceTokenStore.hasToken()) {
                        "Download returned HTTP 401. Your Hugging Face token may be invalid, or you " +
                            "have not accepted the model license on huggingface.co yet."
                    } else {
                        HF_TOKEN_REQUIRED_MESSAGE
                    },
                )

                HttpURLConnection.HTTP_FORBIDDEN -> throw ModelDownloadException(
                    if (huggingFaceTokenStore.hasToken()) {
                        HF_FORBIDDEN_MESSAGE
                    } else {
                        HF_TOKEN_REQUIRED_MESSAGE
                    },
                )

                else -> throw ModelDownloadException(
                    "Download returned HTTP ${conn.responseCode}: ${conn.responseMessage.orEmpty()}".trim(),
                )
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun openConnection(url: String, resumeFromBytes: Long): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "BiasharaAI/1.0")
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Cache-Control", "no-transform")
            if (resumeFromBytes > 0L) {
                setRequestProperty("Range", "bytes=$resumeFromBytes-")
            }
            huggingFaceTokenStore.getToken()?.let { token ->
                setRequestProperty("Authorization", "Bearer $token")
            }
        }

    private fun streamToFile(
        connection: HttpURLConnection,
        destFile: File,
        fallbackTotalBytes: Long,
        modelId: String,
        append: Boolean,
        initialBytes: Long,
        resumedFromBytes: Long,
    ) {
        val responseBytes = connection.contentLengthLong
        val contentLength = when {
            fallbackTotalBytes > 0L -> fallbackTotalBytes
            responseBytes > 0L -> initialBytes + responseBytes
            else -> 0L
        }

        connection.inputStream.use { rawInput ->
            FileOutputStream(destFile, append).use { rawOutput ->
                val input = rawInput.buffered(IO_BUFFER_BYTES)
                val output = rawOutput.buffered(IO_BUFFER_BYTES)
                val buffer = ByteArray(IO_BUFFER_BYTES)
                var totalBytesRead = initialBytes
                var lastProgressUpdate = initialBytes
                var lastProgressAtMs = System.currentTimeMillis()
                val transferStartedAtMs = lastProgressAtMs
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    val now = System.currentTimeMillis()
                    if (
                        totalBytesRead - lastProgressUpdate >= PROGRESS_INTERVAL_BYTES ||
                        now - lastProgressAtMs >= PROGRESS_INTERVAL_MS
                    ) {
                        lastProgressUpdate = totalBytesRead
                        lastProgressAtMs = now
                        publishProgress(
                            bytesDownloaded = totalBytesRead,
                            totalBytes = contentLength,
                            modelId = modelId,
                            resumedFromBytes = resumedFromBytes,
                            bytesPerSecond = downloadRateBytesPerSecond(
                                downloadedBytes = totalBytesRead,
                                initialBytes = initialBytes,
                                startedAtMs = transferStartedAtMs,
                                nowMs = now,
                            ),
                        )
                    }
                }
                output.flush()

                publishProgress(
                    bytesDownloaded = totalBytesRead,
                    totalBytes = contentLength,
                    modelId = modelId,
                    resumedFromBytes = resumedFromBytes,
                    bytesPerSecond = downloadRateBytesPerSecond(
                        downloadedBytes = totalBytesRead,
                        initialBytes = initialBytes,
                        startedAtMs = transferStartedAtMs,
                        nowMs = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    private fun validateCompleteDownload(plan: DownloadPlan, expectedBytes: Long) {
        val partial = plan.partialFile
        if (!partial.exists()) {
            throw ModelDownloadException("Download did not produce a model file. Retry on a stable connection.")
        }
        if (!hasExpectedSize(partial, expectedBytes)) {
            val partialBytes = partial.length()
            if (partialBytes > expectedBytes) partial.delete()
            throw ModelDownloadException(
                "Download is incomplete ($partialBytes of $expectedBytes bytes). " +
                    "Retry to resume from the saved partial download.",
            )
        }
        verifySha256IfPresent(partial, plan.sha256)
    }

    private fun finaliseDownload(plan: DownloadPlan) {
        plan.targetFile.parentFile?.mkdirs()
        plan.targetFile.delete()
        if (!plan.partialFile.renameTo(plan.targetFile)) {
            plan.partialFile.copyTo(plan.targetFile, overwrite = true)
            plan.partialFile.delete()
        }
    }

    private fun writeDownloadMetadata(plan: DownloadPlan, downloadedBytes: Long) {
        val props = Properties().apply {
            setProperty("modelId", plan.modelId)
            setProperty("fileName", plan.targetFile.name)
            setProperty("sizeBytes", downloadedBytes.toString())
            setProperty("catalogueBytes", plan.catalogueBytes.toString())
            setProperty("sourceUrl", plan.url)
            setProperty("sha256", plan.sha256)
            setProperty("downloadedAt", System.currentTimeMillis().toString())
        }
        metadataFileFor(plan.targetFile).outputStream().use { output ->
            props.store(output, "BiasharaAI model download metadata")
        }
    }

    private fun acceptedTotalBytes(serverTotalBytes: Long, catalogueBytes: Long): Long =
        when {
            serverTotalBytes > 0L -> serverTotalBytes
            catalogueBytes > 0L -> catalogueBytes
            else -> 1L
        }

    private fun rejectImplausiblySmallServerFile(
        serverTotalBytes: Long,
        catalogueBytes: Long,
        partialFile: File,
    ) {
        if (serverTotalBytes <= 0L || catalogueBytes <= 0L) return
        val minimumReasonableBytes = catalogueBytes / 2L
        if (serverTotalBytes >= minimumReasonableBytes) return
        partialFile.delete()
        throw ModelDownloadException(
            "Server returned an unexpectedly small model file ($serverTotalBytes bytes). " +
                "Check model access and retry.",
        )
    }

    private fun verifySha256IfPresent(file: File, expectedSha256: String) {
        if (expectedSha256.isBlank()) return
        val normalised = expectedSha256.lowercase()
        if (normalised.length != SHA256_HEX_LENGTH) {
            throw ModelDownloadException("Invalid SHA-256 metadata for this model.")
        }

        val actual = sha256(file)
        if (actual != normalised) {
            file.delete()
            throw ModelDownloadException(
                "Downloaded model failed integrity check. Retry the download; the partial file was cleared.",
            )
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(IO_BUFFER_BYTES)
        FileInputStream(file).use { rawInput ->
            val input = rawInput.buffered(IO_BUFFER_BYTES)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun publishProgress(
        bytesDownloaded: Long,
        totalBytes: Long,
        modelId: String,
        resumedFromBytes: Long = 0L,
        bytesPerSecond: Long = 0L,
    ) {
        val percent = when {
            totalBytes <= 0L -> 0
            bytesDownloaded >= totalBytes -> 100
            else -> ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 99)
        }
        val estimatedSecondsRemaining = if (bytesPerSecond > 0L && totalBytes > bytesDownloaded) {
            ((totalBytes - bytesDownloaded) + bytesPerSecond - 1L) / bytesPerSecond
        } else {
            null
        }
        _progress.value = DownloadProgress(
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            percent = percent,
            modelId = modelId,
            resumedFromBytes = resumedFromBytes,
            bytesPerSecond = bytesPerSecond,
            estimatedSecondsRemaining = estimatedSecondsRemaining,
        )
    }

    private fun downloadRateBytesPerSecond(
        downloadedBytes: Long,
        initialBytes: Long,
        startedAtMs: Long,
        nowMs: Long,
    ): Long {
        val elapsedMs = (nowMs - startedAtMs).coerceAtLeast(1L)
        val transferredBytes = (downloadedBytes - initialBytes).coerceAtLeast(0L)
        return (transferredBytes * 1000L / elapsedMs).coerceAtLeast(0L)
    }

    private fun parseContentRange(header: String?): ContentRange? {
        if (header.isNullOrBlank()) return null
        val match = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""", RegexOption.IGNORE_CASE)
            .find(header)
            ?: return null
        val totalBytes = match.groupValues[3].takeUnless { it == "*" }?.toLongOrNull() ?: -1L
        return ContentRange(
            start = match.groupValues[1].toLong(),
            end = match.groupValues[2].toLong(),
            totalBytes = totalBytes,
        )
    }

    private fun parseContentRangeTotal(header: String?): Long {
        if (header.isNullOrBlank()) return -1L
        val match = Regex("""bytes\s+\*/(\d+)""", RegexOption.IGNORE_CASE)
            .find(header)
            ?: return -1L
        return match.groupValues[1].toLongOrNull() ?: -1L
    }

    private fun hasExpectedSize(file: File, expectedBytes: Long): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        return if (expectedBytes > 0L) file.length() == expectedBytes else true
    }

    private fun hasAcceptableCompletedSize(file: File, catalogueBytes: Long, modelId: String): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        val metadataBytes = readMetadataSizeBytes(file, expectedModelId = modelId)
        if (metadataBytes != null && metadataBytes == file.length()) return true
        return hasExpectedSize(file, catalogueBytes)
    }

    private fun readMetadataSizeBytes(file: File, expectedModelId: String?): Long? {
        val metadata = metadataFileFor(file)
        if (!metadata.exists()) return null
        return runCatching {
            val props = Properties().apply {
                metadata.inputStream().use(::load)
            }
            if (expectedModelId != null && props.getProperty("modelId") != expectedModelId) return@runCatching null
            if (props.getProperty("fileName") != file.name) return@runCatching null
            props.getProperty("sizeBytes")?.toLongOrNull()
        }.getOrNull()
    }

    private fun partialFileFor(target: File): File = File(target.parentFile, "${target.name}.tmp")

    private fun metadataFileFor(target: File): File = File(target.parentFile, "${target.name}.download.properties")

    private fun markFailed(modelId: String) {
        _state.value = DownloadState.FAILED
        persistState(DownloadState.FAILED, modelId)
    }

    private fun normaliseFailure(error: Exception): Exception =
        when (error) {
            is ModelDownloadException -> error
            is IOException -> ModelDownloadException(
                "Network or storage error. Retry to resume the saved partial download.",
                error,
            )
            else -> error
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
        val restored = try {
            if (saved != null) DownloadState.valueOf(saved) else DownloadState.NOT_DOWNLOADED
        } catch (_: IllegalArgumentException) {
            DownloadState.NOT_DOWNLOADED
        }

        if (restored != DownloadState.DOWNLOADING) return restored

        val interruptedModelId = prefs.getString(KEY_ACTIVE_DOWNLOAD_MODEL_ID, null)
            ?: modelRegistry.primaryModelId()
        val partial = partialFileFor(modelRegistry.modelFile(interruptedModelId))
        val restoredState = if (partial.exists() && partial.length() > 0L) {
            DownloadState.FAILED
        } else {
            DownloadState.NOT_DOWNLOADED
        }
        prefs.edit()
            .putString(KEY_DOWNLOAD_STATE, restoredState.name)
            .putString(KEY_ACTIVE_DOWNLOAD_MODEL_ID, interruptedModelId)
            .apply()
        return restoredState
    }

    private fun formatMb(bytes: Long): Long = (bytes / (1024L * 1024L)).coerceAtLeast(1L)
}

private class ModelDownloadException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

enum class DownloadState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED,
}
