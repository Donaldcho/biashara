package com.biasharaai.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biasharaai.ai.BenchmarkRunner
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.ai.DownloadState
import com.biasharaai.ai.ModelCapability
import com.biasharaai.ai.ModelCatalogueEntry
import com.biasharaai.ai.HuggingFaceTokenStore
import com.biasharaai.ai.ModelDownloadManager
import com.biasharaai.ai.ModelRegistry
import com.biasharaai.data.local.db.ModelDescriptor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelRowUiState(
    val entry: ModelCatalogueEntry,
    val descriptor: ModelDescriptor?,
    val isDownloaded: Boolean,
    val isPrimary: Boolean,
    val isBenchmarking: Boolean = false,
)

data class CapabilityAssignmentUiState(
    val capability: ModelCapability,
    val assignedModelId: String?,
    val availableModelIds: List<String>,
)

@HiltViewModel
class ModelSettingsViewModel @Inject constructor(
    private val modelRegistry: ModelRegistry,
    private val modelDownloadManager: ModelDownloadManager,
    private val huggingFaceTokenStore: HuggingFaceTokenStore,
    private val benchmarkRunner: BenchmarkRunner,
    private val capabilityTier: CapabilityTier,
) : ViewModel() {

    private val _models = MutableStateFlow<List<ModelRowUiState>>(emptyList())
    val models: StateFlow<List<ModelRowUiState>> = _models.asStateFlow()

    private val _downloadState = MutableStateFlow(modelDownloadManager.state.value)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    val downloadProgress: StateFlow<ModelDownloadManager.DownloadProgress> = modelDownloadManager.progress

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val _isBenchmarking = MutableStateFlow(false)
    val isBenchmarking: StateFlow<Boolean> = _isBenchmarking.asStateFlow()

    private val benchmarkingModelIds = mutableSetOf<String>()

    val capabilityTierValue: CapabilityTier get() = capabilityTier

    fun hasHuggingFaceToken(): Boolean = huggingFaceTokenStore.hasToken()

    fun getHuggingFaceToken(): String? = huggingFaceTokenStore.getToken()

    fun saveHuggingFaceToken(token: String?) {
        huggingFaceTokenStore.setToken(token)
    }

    init {
        viewModelScope.launch {
            modelDownloadManager.state.collect { state ->
                _downloadState.value = state
                if (state == DownloadState.DOWNLOADED || state == DownloadState.NOT_DOWNLOADED) {
                    refreshModels()
                }
            }
        }
        refreshModels()
    }

    fun capabilityAssignments(): List<CapabilityAssignmentUiState> {
        val catalogue = modelRegistry.catalogue()
        val downloadedIds = catalogue.models.map { it.modelId }.filter { modelRegistry.isDownloaded(it) }
        return ModelCapability.entries.map { cap ->
            CapabilityAssignmentUiState(
                capability = cap,
                assignedModelId = modelRegistry.assignedModelForCapability(cap),
                availableModelIds = catalogue.models
                    .filter { cap.name in it.capabilities && modelRegistry.isDownloaded(it.modelId) }
                    .map { it.modelId },
            )
        }
    }

    fun assignCapability(capability: ModelCapability, modelId: String?) {
        modelRegistry.setCapabilityModelId(capability, modelId)
        refreshModels()
    }

    fun refreshModels() {
        viewModelScope.launch {
            val catalogue = modelRegistry.catalogue()
            val primaryId = modelRegistry.primaryModelId()
            val rows = catalogue.models.map { entry ->
                val descriptor = modelRegistry.getDescriptor(entry.modelId)
                ModelRowUiState(
                    entry = entry,
                    descriptor = descriptor,
                    isDownloaded = modelRegistry.isDownloaded(entry.modelId),
                    isPrimary = entry.modelId == primaryId,
                    isBenchmarking = benchmarkingModelIds.contains(entry.modelId),
                )
            }
            _models.value = rows
        }
    }

    fun setPrimary(modelId: String) {
        modelRegistry.setPrimaryModelId(modelId)
        refreshModels()
        viewModelScope.launch { _events.emit(Event.PrimaryModelChanged(modelId)) }
    }

    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            try {
                modelDownloadManager.downloadModel(modelId)
                _events.emit(Event.DownloadComplete(modelId))
            } catch (e: Exception) {
                Log.e("ModelSettingsVM", "Download failed for $modelId", e)
                _events.emit(Event.DownloadFailed(modelId, e.message ?: "Download failed"))
            }
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            modelDownloadManager.deleteModel(modelId)
            refreshModels()
        }
    }

    fun runBenchmark(modelId: String) {
        if (benchmarkingModelIds.contains(modelId)) return
        benchmarkingModelIds.add(modelId)
        _isBenchmarking.value = true
        refreshModels()
        viewModelScope.launch {
            try {
                val tps = benchmarkRunner.runBenchmark(modelId)
                benchmarkingModelIds.remove(modelId)
                _isBenchmarking.value = benchmarkingModelIds.isNotEmpty()
                refreshModels()
                if (tps != null) {
                    _events.emit(Event.BenchmarkComplete(modelId, tps))
                } else {
                    _events.emit(Event.BenchmarkFailed(modelId))
                }
            } catch (e: Exception) {
                benchmarkingModelIds.remove(modelId)
                _isBenchmarking.value = benchmarkingModelIds.isNotEmpty()
                refreshModels()
                _events.emit(Event.BenchmarkFailed(modelId))
            }
        }
    }

    sealed class Event {
        data class DownloadComplete(val modelId: String) : Event()
        data class DownloadFailed(val modelId: String, val message: String) : Event()
        data class PrimaryModelChanged(val modelId: String) : Event()
        data class BenchmarkComplete(val modelId: String, val tokensPerSec: Float) : Event()
        data class BenchmarkFailed(val modelId: String) : Event()
    }
}
