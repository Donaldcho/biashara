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
import com.biasharaai.cloud.EnterpriseDeploymentMode
import com.biasharaai.cloud.EnterpriseEndpointPolicy
import com.biasharaai.data.local.db.AppSettings
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.StaffMember
import com.biasharaai.data.local.db.StaffMemberDao
import com.biasharaai.enterprise.EnterpriseAuditRepository
import com.biasharaai.enterprise.EnterpriseLanDiscoveryClient
import com.biasharaai.enterprise.EnterpriseOperatorStore
import com.biasharaai.enterprise.EnterprisePinHasher
import com.biasharaai.enterprise.EnterpriseRolePermissions
import com.biasharaai.enterprise.SyncSkipReason
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
    private val enterpriseAuditRepository: EnterpriseAuditRepository,
    private val enterpriseLanDiscoveryClient: EnterpriseLanDiscoveryClient,
    private val staffMemberDao: StaffMemberDao,
    private val enterpriseOperatorStore: EnterpriseOperatorStore,
) : BaseViewModel() {

    private val _licenceKey = MutableStateFlow(licenceValidator.getStoredKey())
    val licenceKey: StateFlow<LicenceKey?> = _licenceKey.asStateFlow()

    val isProEnabled: Boolean get() = productLineManager.isProEnabled()

    val isEnterprisePro: Boolean get() = productLineManager.isEnterprisePro()

    private val _currentEnterpriseOperator = MutableStateFlow<StaffMember?>(null)
    val currentEnterpriseOperator: StateFlow<StaffMember?> = _currentEnterpriseOperator.asStateFlow()

    val enterpriseStaff: StateFlow<List<StaffMember>> = staffMemberDao.getActive()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Singleton POS / shop settings row (currency, tax, …). */
    val shopSettings: StateFlow<AppSettings?> = appSettingsDao.getSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val downloadState: StateFlow<DownloadState> = modelDownloadManager.state

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    val isAiCapable: Boolean
        get() = capabilityResult.tier != CapabilityTier.RULES_BASED

    init {
        refreshEnterpriseOperator()
    }

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
        refreshEnterpriseOperator()
    }

    fun applyLicenceKey(keyString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = licenceValidator.storeLicenceKey(keyString.trim())
            result.fold(
                onSuccess = {
                    _licenceKey.value = it
                    enterpriseAuditRepository.record(
                        action = "LICENCE_APPLIED",
                        entityType = "LICENCE",
                        entityId = it.businessId,
                        summary = "${it.productLine.name} ${it.edition.name} licence applied for up to ${it.maxDevices} devices",
                    )
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

    val enterpriseDevices = enterpriseAuditRepository.observeRegisteredDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val enterpriseAuditEvents = enterpriseAuditRepository.observeRecentAudit(limit = 5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val enterpriseBranches = enterpriseAuditRepository.observeBranches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingEnterpriseSyncCount = enterpriseAuditRepository.observePendingSyncCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _isCloudUploading = MutableStateFlow(false)
    val isCloudUploading: StateFlow<Boolean> = _isCloudUploading.asStateFlow()

    private val _isEnterpriseSyncing = MutableStateFlow(false)
    val isEnterpriseSyncing: StateFlow<Boolean> = _isEnterpriseSyncing.asStateFlow()

    private val _isEnterpriseDiscovering = MutableStateFlow(false)
    val isEnterpriseDiscovering: StateFlow<Boolean> = _isEnterpriseDiscovering.asStateFlow()

    fun saveCloudAnalysis(enabled: Boolean, endpointUrl: String, newApiKeyIfNonBlank: String?) {
        saveCloudAnalysis(
            enabled = enabled,
            deploymentMode = _cloudSettings.value.deploymentMode,
            endpointUrl = endpointUrl,
            newApiKeyIfNonBlank = newApiKeyIfNonBlank,
        )
    }

    fun saveCloudAnalysis(
        enabled: Boolean,
        deploymentMode: EnterpriseDeploymentMode,
        endpointUrl: String,
        newApiKeyIfNonBlank: String?,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val effectiveMode = if (productLineManager.isEnterprisePro()) {
                deploymentMode
            } else {
                EnterpriseDeploymentMode.CLOUD
            }
            cloudAnalysisSettingsStore.save(
                enabled = enabled,
                deploymentMode = effectiveMode,
                endpointUrl = endpointUrl,
                newApiKeyIfNonBlank = newApiKeyIfNonBlank,
            )
            _cloudSettings.value = cloudAnalysisSettingsStore.load()
            enterpriseAuditRepository.record(
                action = "DEPLOYMENT_SAVED",
                entityType = "ENTERPRISE_DEPLOYMENT",
                entityId = effectiveMode.name,
                summary = "Enterprise deployment saved as ${effectiveMode.name}",
                metadata = "uploadsEnabled=$enabled; endpointConfigured=${endpointUrl.trim().isNotBlank()}",
            )
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
                url.isBlank() || !EnterpriseEndpointPolicy.isAllowed(url, cfg.deploymentMode.name) -> {
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
                    cloudAnalysisHttpClient.postJson(
                        url = url,
                        jsonBody = json,
                        bearerToken = key,
                        destinationMode = cfg.deploymentMode.name,
                    )
                }
                result.fold(
                    onSuccess = {
                        enterpriseAuditRepository.record(
                            action = "ANALYTICS_JSON_UPLOADED",
                            entityType = "ENTERPRISE_EXPORT",
                            entityId = cfg.deploymentMode.name,
                            summary = "Business analytics JSON uploaded to ${cfg.deploymentMode.name}",
                        )
                        _events.emit(Event.CloudUploadSucceeded)
                    },
                    onFailure = { e ->
                        Log.w("SettingsViewModel", "cloud json upload failed", e)
                        enterpriseAuditRepository.record(
                            action = "ANALYTICS_JSON_UPLOAD_FAILED",
                            entityType = "ENTERPRISE_EXPORT",
                            entityId = cfg.deploymentMode.name,
                            summary = "Business analytics JSON upload failed",
                            metadata = e.message,
                        )
                        _events.emit(Event.CloudUploadFailed(e.message ?: "Unknown error"))
                    },
                )
            } finally {
                _isCloudUploading.value = false
            }
        }
    }

    fun syncEnterpriseQueue() {
        if (_isEnterpriseSyncing.value) return
        viewModelScope.launch {
            _isEnterpriseSyncing.value = true
            try {
                val result = withContext(Dispatchers.IO) { enterpriseAuditRepository.flushPendingSync() }
                val skipped = result.skippedReason
                if (skipped != null) {
                    _events.emit(
                        Event.EnterpriseSyncFailed(
                            when (skipped) {
                                SyncSkipReason.NOT_ENABLED -> CLOUD_ERR_NOT_ENABLED
                                SyncSkipReason.MISSING_URL -> CLOUD_ERR_MISSING_URL
                                SyncSkipReason.MISSING_KEY -> CLOUD_ERR_MISSING_KEY
                                SyncSkipReason.NOT_ENTERPRISE ->
                                    "Enterprise sync is not available for this licence."
                            },
                        ),
                    )
                } else {
                    _events.emit(Event.EnterpriseSyncComplete(result.sent, result.failed))
                }
            } catch (e: Exception) {
                Log.w("SettingsViewModel", "enterprise sync failed", e)
                _events.emit(Event.EnterpriseSyncFailed(e.message ?: "Unknown error"))
            } finally {
                _isEnterpriseSyncing.value = false
            }
        }
    }

    fun discoverEnterpriseService() {
        if (_isEnterpriseDiscovering.value) return
        viewModelScope.launch {
            _isEnterpriseDiscovering.value = true
            try {
                val result = enterpriseLanDiscoveryClient.discover()
                result.fold(
                    onSuccess = { service ->
                        _cloudSettings.value = _cloudSettings.value.copy(
                            deploymentMode = EnterpriseDeploymentMode.ON_PREMISE,
                            endpointUrl = service.endpointUrl,
                        )
                        _events.emit(Event.EnterpriseServiceDiscovered(service.endpointUrl))
                    },
                    onFailure = { e ->
                        Log.w("SettingsViewModel", "enterprise discovery failed", e)
                        _events.emit(
                            Event.EnterpriseDiscoveryFailed(
                                e.message ?: "No Enterprise sync service found.",
                            ),
                        )
                    },
                )
            } finally {
                _isEnterpriseDiscovering.value = false
            }
        }
    }

    fun requestEnterpriseAction(action: RestrictedAction) {
        viewModelScope.launch(Dispatchers.IO) {
            val permission = permissionFor(action)
            val operator = loadCurrentEnterpriseOperator()
            if (
                productLineManager.isEnterprisePro() &&
                operator != null &&
                !EnterpriseRolePermissions.can(operator.role, permission)
            ) {
                enterpriseAuditRepository.record(
                    action = "PERMISSION_DENIED",
                    entityType = "ENTERPRISE_PERMISSION",
                    entityId = permission,
                    summary = "${operator.name} was blocked from ${action.name}",
                    metadata = "role=${operator.role}; permission=$permission",
                    actorStaffId = operator.id,
                    actorRole = operator.role,
                )
                _events.emit(
                    Event.EnterprisePermissionDenied(
                        operatorName = operator.name,
                        operatorRole = operator.role,
                    ),
                )
                return@launch
            }
            _events.emit(Event.EnterpriseActionAllowed(action))
        }
    }

    fun selectEnterpriseOperator(member: StaffMember?, pin: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            if (member == null) {
                val previous = _currentEnterpriseOperator.value
                enterpriseOperatorStore.clear()
                _currentEnterpriseOperator.value = null
                enterpriseAuditRepository.record(
                    action = "DEVICE_OPERATOR_CLEARED",
                    entityType = "STAFF_MEMBER",
                    entityId = previous?.id?.toString(),
                    summary = "Device operator cleared from Settings",
                    metadata = previous?.let { "previous=${it.name}; role=${it.role}" },
                    actorStaffId = previous?.id,
                    actorRole = previous?.role,
                )
            } else {
                val fresh = staffMemberDao.getById(member.id)?.takeIf { it.isActive } ?: return@launch
                if (fresh.pinHash.isNullOrBlank() || fresh.pinSalt.isNullOrBlank()) {
                    _events.emit(Event.EnterpriseOperatorPinRequired)
                    return@launch
                }
                val verified = withContext(Dispatchers.Default) {
                    EnterprisePinHasher.verify(pin.orEmpty(), fresh.pinSalt, fresh.pinHash)
                }
                if (!verified) {
                    enterpriseAuditRepository.record(
                        action = "OPERATOR_PIN_FAILED",
                        entityType = "STAFF_MEMBER",
                        entityId = fresh.id.toString(),
                        summary = "Invalid operator PIN from Settings for ${fresh.name}",
                        metadata = "role=${fresh.role}",
                        actorStaffId = fresh.id,
                        actorRole = fresh.role,
                    )
                    _events.emit(Event.EnterpriseOperatorPinInvalid)
                    return@launch
                }
                enterpriseOperatorStore.selectStaff(fresh.id)
                _currentEnterpriseOperator.value = fresh
                enterpriseAuditRepository.record(
                    action = "DEVICE_OPERATOR_SELECTED",
                    entityType = "STAFF_MEMBER",
                    entityId = fresh.id.toString(),
                    summary = "Device operator selected from Settings: ${fresh.name}",
                    metadata = "role=${fresh.role}; pinVerified=true",
                    actorStaffId = fresh.id,
                    actorRole = fresh.role,
                )
            }
            _events.emit(Event.EnterpriseOperatorChanged)
        }
    }

    private fun refreshEnterpriseOperator() {
        viewModelScope.launch(Dispatchers.IO) {
            loadCurrentEnterpriseOperator()
        }
    }

    private suspend fun loadCurrentEnterpriseOperator(): StaffMember? {
        val selectedId = enterpriseOperatorStore.selectedStaffId()
        val member = selectedId?.let { staffMemberDao.getById(it) }?.takeIf { it.isActive }
        if (selectedId != null && member == null) {
            enterpriseOperatorStore.clear()
        }
        _currentEnterpriseOperator.value = member
        return member
    }

    private fun permissionFor(action: RestrictedAction): String = when (action) {
        RestrictedAction.OPEN_LEDGER -> EnterpriseRolePermissions.PERMISSION_EDIT_LEDGER
        RestrictedAction.OPEN_STAFF_SETTINGS -> EnterpriseRolePermissions.PERMISSION_MANAGE_STAFF
        RestrictedAction.SAVE_CLOUD_SETTINGS,
        RestrictedAction.UPLOAD_ANALYTICS_JSON,
        RestrictedAction.UPLOAD_SQLITE_DATABASE,
        RestrictedAction.SYNC_ENTERPRISE_QUEUE,
        -> EnterpriseRolePermissions.PERMISSION_EXPORT_DATA
    }

    fun saveDefaultEnterpriseBranch(name: String, location: String?) {
        val trimmed = name.trim()
        if (trimmed.length < 2) {
            viewModelScope.launch { _events.emit(Event.EnterpriseBranchInvalid) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            enterpriseAuditRepository.saveDefaultBranch(trimmed, location)
            _events.emit(Event.EnterpriseBranchSaved)
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
                url.isBlank() || !EnterpriseEndpointPolicy.isAllowed(url, cfg.deploymentMode.name) -> {
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
                    cloudAnalysisHttpClient.postSqliteFile(
                        url = url,
                        dbFile = file,
                        bearerToken = key,
                        destinationMode = cfg.deploymentMode.name,
                    )
                }
                result.fold(
                    onSuccess = {
                        enterpriseAuditRepository.record(
                            action = "SQLITE_DATABASE_UPLOADED",
                            entityType = "ENTERPRISE_EXPORT",
                            entityId = cfg.deploymentMode.name,
                            summary = "Full SQLite database uploaded to ${cfg.deploymentMode.name}",
                        )
                        _events.emit(Event.CloudUploadSucceeded)
                    },
                    onFailure = { e ->
                        Log.w("SettingsViewModel", "cloud sqlite upload failed", e)
                        enterpriseAuditRepository.record(
                            action = "SQLITE_DATABASE_UPLOAD_FAILED",
                            entityType = "ENTERPRISE_EXPORT",
                            entityId = cfg.deploymentMode.name,
                            summary = "Full SQLite database upload failed",
                            metadata = e.message,
                        )
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
        data class EnterpriseSyncComplete(val sent: Int, val failed: Int) : Event()
        data class EnterpriseSyncFailed(val message: String) : Event()
        data class EnterpriseServiceDiscovered(val endpointUrl: String) : Event()
        data class EnterpriseDiscoveryFailed(val message: String) : Event()
        data object EnterpriseBranchSaved : Event()
        data object EnterpriseBranchInvalid : Event()
        data class EnterpriseActionAllowed(val action: RestrictedAction) : Event()
        data class EnterprisePermissionDenied(
            val operatorName: String,
            val operatorRole: String,
        ) : Event()
        data object EnterpriseOperatorChanged : Event()
        data object EnterpriseOperatorPinRequired : Event()
        data object EnterpriseOperatorPinInvalid : Event()
        data class LicenceApplied(val proEnabled: Boolean) : Event()
        data class LicenceInvalid(val message: String) : Event()
    }

    enum class RestrictedAction {
        OPEN_LEDGER,
        OPEN_STAFF_SETTINGS,
        SAVE_CLOUD_SETTINGS,
        UPLOAD_ANALYTICS_JSON,
        UPLOAD_SQLITE_DATABASE,
        SYNC_ENTERPRISE_QUEUE,
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
