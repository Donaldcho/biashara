package com.biasharaai.ui.settings

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.biasharaai.ai.CapabilityResult
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.ai.DownloadState
import com.biasharaai.ai.GemmaService
import com.biasharaai.ai.InferenceSettingsStore
import com.biasharaai.ai.ModelDownloadManager
import com.biasharaai.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 *
 * Exposes device capability info and model download state.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    val capabilityResult: CapabilityResult,
    val modelDownloadManager: ModelDownloadManager,
    private val gemmaService: GemmaService,
    val inferenceSettingsStore: InferenceSettingsStore,
) : BaseViewModel() {

    val downloadState: StateFlow<DownloadState> = modelDownloadManager.state

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    val isAiCapable: Boolean
        get() = capabilityResult.tier != CapabilityTier.RULES_BASED

    fun downloadModel() {
        viewModelScope.launch {
            try {
                modelDownloadManager.downloadModel()
                _events.emit(Event.DownloadComplete)
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Model download failed", e)
                _events.emit(Event.DownloadFailed(e.message ?: "Download failed"))
            }
        }
    }

    fun deleteModel() {
        gemmaService.close()
        modelDownloadManager.deleteModel()
    }

    /** Call after saving Edge-style inference UI so the next chat run reloads the engine. */
    fun onInferenceSettingsSaved() {
        gemmaService.resetEngine()
    }

    fun retryDownload() {
        modelDownloadManager.resetAfterFailure()
        downloadModel()
    }

    private val _isBenchmarking = MutableStateFlow(false)
    val isBenchmarking: StateFlow<Boolean> = _isBenchmarking

    /**
     * Run a fixed short prompt and measure first-token latency + tokens/sec.
     * Mirrors Google AI Edge Gallery's benchmark feature.
     */
    fun runBenchmark() {
        if (_isBenchmarking.value) return
        if (!gemmaService.isAvailable) {
            viewModelScope.launch { _events.emit(Event.BenchmarkFailed("Model not available.")) }
            return
        }
        _isBenchmarking.value = true
        viewModelScope.launch {
            try {
                // LiteRT-LM applies the chat template internally; just send plain user text.
                val prompt = "Say one short sentence about your purpose."
                val start = System.currentTimeMillis()
                var firstTokenMs = 0L
                var chars = 0
                gemmaService.generateStreaming(prompt) { delta, _ ->
                    if (delta.isNotEmpty()) {
                        if (firstTokenMs == 0L) firstTokenMs = System.currentTimeMillis()
                        chars += delta.length
                    }
                }
                val totalMs = System.currentTimeMillis() - start
                val firstMs = if (firstTokenMs > 0L) firstTokenMs - start else totalMs
                val decodeMs = if (firstTokenMs > 0L) System.currentTimeMillis() - firstTokenMs else 0L
                val tokens = chars / 4
                val tps = if (decodeMs > 0) tokens * 1000.0 / decodeMs else 0.0
                _events.emit(
                    Event.BenchmarkResult(
                        firstTokenMs = firstMs,
                        decodeMs = decodeMs,
                        approxTokens = tokens,
                        tokensPerSecond = tps,
                    ),
                )
            } catch (e: Exception) {
                Log.w("SettingsViewModel", "benchmark failed", e)
                _events.emit(Event.BenchmarkFailed(e.message ?: "Unknown error"))
            } finally {
                _isBenchmarking.value = false
            }
        }
    }

    sealed class Event {
        data object DownloadComplete : Event()
        data class DownloadFailed(val message: String) : Event()
        data class BenchmarkResult(
            val firstTokenMs: Long,
            val decodeMs: Long,
            val approxTokens: Int,
            val tokensPerSecond: Double,
        ) : Event()
        data class BenchmarkFailed(val message: String) : Event()
    }
}
