package com.biasharaai.ai

import android.content.Context
import android.content.SharedPreferences
import com.biasharaai.data.local.db.ModelDescriptor
import com.biasharaai.data.local.db.ModelDescriptorDao
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 6 X2 — authoritative view of known models: catalogue → Room, disk reconciliation,
 * and primary model selection for [ActiveModelStore] / [ModelDownloadManager].
 */
@Singleton
class ModelRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDescriptorDao: ModelDescriptorDao,
    private val catalogueLoader: ModelCatalogueLoader,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val gson = Gson()

    @Volatile
    private var cachedCatalogue: ModelCatalogue? = null

    val modelsDir: File
        get() = File(context.filesDir, ModelDownloadManager.MODELS_DIR).also { it.mkdirs() }

    fun catalogue(): ModelCatalogue = cachedCatalogue ?: catalogueLoader.load().also { cachedCatalogue = it }

    fun primaryModelId(): String {
        val saved = prefs.getString(KEY_PRIMARY_MODEL_ID, null)
        if (saved != null && saved.isNotBlank()) return saved
        return catalogue().defaultPrimaryModelId
    }

    fun setPrimaryModelId(modelId: String) {
        prefs.edit().putString(KEY_PRIMARY_MODEL_ID, modelId).apply()
    }

    fun modelFile(modelId: String): File {
        val name = catalogue().models.find { it.modelId == modelId }?.fileName
            ?: ModelDownloadManager.LEGACY_MODEL_FILENAME
        return File(modelsDir, name)
    }

    fun primaryModelFile(): File = modelFile(primaryModelId())

    fun isDownloaded(modelId: String): Boolean {
        val file = modelFile(modelId)
        return file.exists() && file.length() > 0
    }

    fun isPrimaryDownloaded(): Boolean = isDownloaded(primaryModelId())

    fun approxPrimarySizeMb(): Long {
        val bytes = catalogue().models.find { it.modelId == primaryModelId() }?.sizeBytes
            ?: catalogue().models.firstOrNull()?.sizeBytes
            ?: (ModelDownloadManager.LEGACY_APPROX_MODEL_SIZE_MB * 1024 * 1024)
        return (bytes / (1024 * 1024)).coerceAtLeast(1)
    }

    fun resolveDownloadUrl(modelId: String): String {
        val entry = catalogue().models.find { it.modelId == modelId }
            ?: error("Unknown modelId in catalogue: $modelId")
        return entry.huggingFaceResolveUrl()
    }

    suspend fun bootstrap() = withContext(Dispatchers.IO) {
        syncFromCatalogue()
        reconcileDiskState()
    }

    suspend fun syncFromCatalogue() = withContext(Dispatchers.IO) {
        val cat = catalogueLoader.load().also { cachedCatalogue = it }
        for (entry in cat.models) {
            val existing = modelDescriptorDao.getById(entry.modelId)
            modelDescriptorDao.upsert(entry.toDescriptor(existing))
        }
        if (prefs.getString(KEY_PRIMARY_MODEL_ID, null).isNullOrBlank()) {
            setPrimaryModelId(cat.defaultPrimaryModelId)
        }
    }

    suspend fun reconcileDiskState() = withContext(Dispatchers.IO) {
        val all = modelDescriptorDao.getAll()
        for (descriptor in all) {
            val file = fileForDescriptor(descriptor)
            val onDisk = file.exists() && file.length() > 0
            val path = if (onDisk) file.absolutePath else null
            val downloadedAt = if (onDisk) descriptor.downloadedAt ?: System.currentTimeMillis() else null
            if (descriptor.isDownloaded != onDisk || descriptor.filePath != path) {
                modelDescriptorDao.updateDownloadState(
                    modelId = descriptor.modelId,
                    isDownloaded = onDisk,
                    downloadedAt = downloadedAt,
                    filePath = path,
                )
            }
        }
        // Legacy install: file on disk before registry existed
        val legacyFile = File(modelsDir, ModelDownloadManager.LEGACY_MODEL_FILENAME)
        if (legacyFile.exists() && legacyFile.length() > 0) {
            val primary = primaryModelId()
            val row = modelDescriptorDao.getById(primary)
            if (row != null && !row.isDownloaded) {
                modelDescriptorDao.updateDownloadState(
                    modelId = primary,
                    isDownloaded = true,
                    downloadedAt = System.currentTimeMillis(),
                    filePath = legacyFile.absolutePath,
                )
            }
        }
    }

    suspend fun markDownloaded(modelId: String, absolutePath: String) = withContext(Dispatchers.IO) {
        modelDescriptorDao.updateDownloadState(
            modelId = modelId,
            isDownloaded = true,
            downloadedAt = System.currentTimeMillis(),
            filePath = absolutePath,
        )
    }

    suspend fun clearDownloaded(modelId: String) = withContext(Dispatchers.IO) {
        modelDescriptorDao.updateDownloadState(
            modelId = modelId,
            isDownloaded = false,
            downloadedAt = null,
            filePath = null,
        )
    }

    suspend fun getDescriptor(modelId: String): ModelDescriptor? =
        withContext(Dispatchers.IO) { modelDescriptorDao.getById(modelId) }

    suspend fun getPrimaryDescriptor(): ModelDescriptor? = getDescriptor(primaryModelId())

    fun capabilitiesForModel(modelId: String): Set<ModelCapability> {
        val entry = catalogue().models.find { it.modelId == modelId } ?: return emptySet()
        return entry.capabilities.mapNotNull { tag ->
            runCatching { ModelCapability.valueOf(tag) }.getOrNull()
        }.toSet()
    }

    fun setCapabilityModelId(capability: ModelCapability, modelId: String?) {
        prefs.edit().apply {
            if (modelId.isNullOrBlank()) {
                remove(capabilityPrefKey(capability))
            } else {
                putString(capabilityPrefKey(capability), modelId)
            }
        }.apply()
    }

    fun assignedModelForCapability(capability: ModelCapability): String? =
        prefs.getString(capabilityPrefKey(capability), null)?.takeIf { it.isNotBlank() }

    /**
     * Best downloaded model for [capability]: explicit assignment, else highest benchmark t/s.
     */
    suspend fun resolveModelForCapability(capability: ModelCapability): String? =
        withContext(Dispatchers.IO) {
            val assigned = assignedModelForCapability(capability)
            if (assigned != null && isDownloaded(assigned) && capabilitiesForModel(assigned).contains(capability)) {
                return@withContext assigned
            }
            catalogue().models
                .filter { capability.name in it.capabilities && isDownloaded(it.modelId) }
                .maxByOrNull { bestTokensPerSec(it.modelId) ?: 0f }
                ?.modelId
        }

    suspend fun bestTokensPerSec(modelId: String): Float? = withContext(Dispatchers.IO) {
        val row = getDescriptor(modelId) ?: return@withContext null
        row.tokensPerSecGpu ?: row.tokensPerSecCpu
    }

    private fun parseCapabilities(json: String): Set<ModelCapability> {
        val type = object : TypeToken<List<String>>() {}.type
        val tags: List<String> = runCatching {
            gson.fromJson<List<String>>(json, type)
        }.getOrNull() ?: emptyList()
        return tags.mapNotNull { tag ->
            runCatching { ModelCapability.valueOf(tag) }.getOrNull()
        }.toSet()
    }

    private fun capabilityPrefKey(capability: ModelCapability): String =
        "$KEY_CAPABILITY_PREFIX${capability.name}"

    private fun fileForDescriptor(descriptor: ModelDescriptor): File {
        if (!descriptor.filePath.isNullOrBlank()) {
            val explicit = File(descriptor.filePath)
            if (explicit.exists()) return explicit
        }
        return File(modelsDir, descriptor.fileName)
    }

    private fun ModelCatalogueEntry.toDescriptor(existing: ModelDescriptor?): ModelDescriptor =
        ModelDescriptor(
            modelId = modelId,
            displayName = displayName,
            huggingFaceRepo = huggingFaceRepo,
            fileName = fileName,
            sizeBytes = sizeBytes,
            sha256 = sha256,
            capabilitiesJson = capabilitiesJson(),
            minTier = minTier,
            isDownloaded = existing?.isDownloaded ?: false,
            downloadedAt = existing?.downloadedAt,
            filePath = existing?.filePath,
            tokensPerSecGpu = existing?.tokensPerSecGpu,
            tokensPerSecCpu = existing?.tokensPerSecCpu,
        )

    companion object {
        private const val PREFS_NAME = "model_registry_prefs"
        private const val KEY_PRIMARY_MODEL_ID = "primary_model_id"
        private const val KEY_CAPABILITY_PREFIX = "capability_model_"
    }
}
