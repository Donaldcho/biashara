package com.biasharaai.ui.settings

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.biasharaai.ai.CapabilityResult
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.ai.DownloadState
import com.biasharaai.ai.GemmaService
import com.biasharaai.ai.InferenceSettingsStore
import com.biasharaai.ai.ModelDownloadManager
import com.biasharaai.cloud.BusinessAnalyticsJsonExporter
import com.biasharaai.cloud.CloudAnalysisHttpClient
import com.biasharaai.cloud.CloudAnalysisSettings
import com.biasharaai.cloud.CloudAnalysisSettingsStore
import com.biasharaai.data.local.db.AppSettings
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.licence.Edition
import com.biasharaai.licence.LicenceKey
import com.biasharaai.licence.LicenceValidator
import com.biasharaai.licence.ProductLine
import com.biasharaai.productline.ProductLineManager
import com.biasharaai.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Currency
import java.util.Locale
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
    private val appSettingsDao: AppSettingsDao,
    private val cloudAnalysisSettingsStore: CloudAnalysisSettingsStore,
    private val businessAnalyticsJsonExporter: BusinessAnalyticsJsonExporter,
    private val cloudAnalysisHttpClient: CloudAnalysisHttpClient,
    private val licenceValidator: LicenceValidator,
    private val productLineManager: ProductLineManager,
) : BaseViewModel() {

    private val _licenceKey = MutableStateFlow(licenceValidator.getStoredKey())
    val licenceKey: StateFlow<LicenceKey?> = _licenceKey.asStateFlow()

    val isProEnabled: Boolean get() = productLineManager.isProEnabled()

    /** Singleton POS / shop settings row (currency, tax, …). */
    val shopSettings: StateFlow<AppSettings?> = appSettingsDao.getSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
        viewModelScope.launch {
            gemmaService.close()
            modelDownloadManager.deleteModel()
        }
    }

    /** Call after saving Edge-style inference UI so the next chat run reloads the engine. */
    fun onInferenceSettingsSaved() {
        gemmaService.resetEngine()
    }

    /** Persists ISO 4217 code and a display symbol for receipts and prompts. */
    fun setShopCurrency(isoCode: String) {
        viewModelScope.launch {
            val code = isoCode.trim().uppercase(Locale.ROOT)
            val currency = runCatching { Currency.getInstance(code) }.getOrNull() ?: return@launch
            // Room forbids synchronous DAO reads on the main thread (no allowMainThreadQueries).
            withContext(Dispatchers.IO) {
                val row = appSettingsDao.getSettingsSync() ?: AppSettings()
                appSettingsDao.updateSettings(
                    row.copy(
                        currencyCode = code,
                        currencySymbol = currency.getSymbol(Locale.getDefault()),
                    ),
                )
            }
        }
    }

    fun refreshLicenceState() {
        _licenceKey.value = licenceValidator.getStoredKey()
    }

    fun applyLicenceKey(keyString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = licenceValidator.storeLicenceKey(keyString.trim())
            result.fold(
                onSuccess = {
                    _licenceKey.value = it
                    _events.emit(Event.LicenceApplied(productLineManager.isProEnabled()))
                },
                onFailure = {
                    _events.emit(Event.LicenceInvalid(it.message ?: "Invalid licence"))
                },
            )
        }
    }

    fun retryDownload() {
        modelDownloadManager.resetAfterFailure()
        downloadModel()
    }

    fun redownloadModel() {
        viewModelScope.launch {
            try {
                gemmaService.close()
                modelDownloadManager.deleteModel()
                modelDownloadManager.downloadModel()
                _events.emit(Event.DownloadComplete)
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Model re-download failed", e)
                _events.emit(Event.DownloadFailed(e.message ?: "Download failed"))
            }
        }
    }

    private val _isBenchmarking = MutableStateFlow(false)
    val isBenchmarking: StateFlow<Boolean> = _isBenchmarking

    private val _cloudSettings = MutableStateFlow(cloudAnalysisSettingsStore.load())
    val cloudSettings: StateFlow<CloudAnalysisSettings> = _cloudSettings.asStateFlow()

    private val _isCloudUploading = MutableStateFlow(false)
    val isCloudUploading: StateFlow<Boolean> = _isCloudUploading.asStateFlow()

    fun saveCloudAnalysis(enabled: Boolean, endpointUrl: String, newApiKeyIfNonBlank: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            cloudAnalysisSettingsStore.save(enabled, endpointUrl, newApiKeyIfNonBlank)
            _cloudSettings.value = cloudAnalysisSettingsStore.load()
            _events.emit(Event.CloudSettingsSaved)
        }
    }

    fun uploadCloudAnalyticsJson() {
        if (_isCloudUploading.value) return
        viewModelScope.launch {
            val cfg = cloudAnalysisSettingsStore.load()
            val url = cfg.endpointUrl.trim()
            val key = cloudAnalysisSettingsStore.apiKeyOrNull()
            when {
                !cfg.enabled -> {
                    _events.emit(Event.CloudUploadFailed(CLOUD_ERR_NOT_ENABLED))
                    return@launch
                }
                url.isBlank() || !url.startsWith("https://", ignoreCase = true) -> {
                    _events.emit(Event.CloudUploadFailed(CLOUD_ERR_MISSING_URL))
                    return@launch
                }
                key.isNullOrBlank() -> {
                    _events.emit(Event.CloudUploadFailed(CLOUD_ERR_MISSING_KEY))
                    return@launch
                }
            }
            _isCloudUploading.value = true
            try {
                val json = withContext(Dispatchers.IO) { businessAnalyticsJsonExporter.buildJson() }
                val result = withContext(Dispatchers.IO) {
                    cloudAnalysisHttpClient.postJson(url, json, key)
                }
                result.fold(
                    onSuccess = { _events.emit(Event.CloudUploadSucceeded) },
                    onFailure = { e ->
                        Log.w("SettingsViewModel", "cloud json upload failed", e)
                        _events.emit(Event.CloudUploadFailed(e.message ?: "Unknown error"))
                    },
                )
            } finally {
                _isCloudUploading.value = false
            }
        }
    }

    fun uploadCloudSqliteDatabase() {
        if (_isCloudUploading.value) return
        viewModelScope.launch {
            val cfg = cloudAnalysisSettingsStore.load()
            val url = cfg.endpointUrl.trim()
            val key = cloudAnalysisSettingsStore.apiKeyOrNull()
            when {
                !cfg.enabled -> {
                    _events.emit(Event.CloudUploadFailed(CLOUD_ERR_NOT_ENABLED))
                    return@launch
                }
                url.isBlank() || !url.startsWith("https://", ignoreCase = true) -> {
                    _events.emit(Event.CloudUploadFailed(CLOUD_ERR_MISSING_URL))
                    return@launch
                }
                key.isNullOrBlank() -> {
                    _events.emit(Event.CloudUploadFailed(CLOUD_ERR_MISSING_KEY))
                    return@launch
                }
            }
            _isCloudUploading.value = true
            var temp: java.io.File? = null
            try {
                temp = withContext(Dispatchers.IO) { businessAnalyticsJsonExporter.copyCheckpointedDatabaseToCache() }
                val file = temp!!
                val result = withContext(Dispatchers.IO) {
                    cloudAnalysisHttpClient.postSqliteFile(url, file, key)
                }
                result.fold(
                    onSuccess = { _events.emit(Event.CloudUploadSucceeded) },
                    onFailure = { e ->
                        Log.w("SettingsViewModel", "cloud sqlite upload failed", e)
                        _events.emit(Event.CloudUploadFailed(e.message ?: "Unknown error"))
                    },
                )
            } finally {
                temp?.delete()
                _isCloudUploading.value = false
            }
        }
    }

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
        data object CloudSettingsSaved : Event()
        data object CloudUploadSucceeded : Event()
        data class CloudUploadFailed(val message: String) : Event()
        data class LicenceApplied(val proEnabled: Boolean) : Event()
        data class LicenceInvalid(val message: String) : Event()
    }

    fun productLineNameRes(productLine: ProductLine): Int = when (productLine) {
        ProductLine.SHOP -> com.biasharaai.R.string.settings_product_line_shop
        ProductLine.PRO -> com.biasharaai.R.string.settings_product_line_pro
    }

    fun editionNameRes(edition: Edition): Int = when (edition) {
        Edition.PRIVATE -> com.biasharaai.R.string.settings_edition_private
        Edition.SME -> com.biasharaai.R.string.settings_edition_sme
        Edition.ENTERPRISE -> com.biasharaai.R.string.settings_edition_enterprise
    }

    companion object {
        const val CLOUD_ERR_NOT_ENABLED = "__cloud_not_enabled__"
        const val CLOUD_ERR_MISSING_URL = "__cloud_missing_url__"
        const val CLOUD_ERR_MISSING_KEY = "__cloud_missing_key__"
    }
}

