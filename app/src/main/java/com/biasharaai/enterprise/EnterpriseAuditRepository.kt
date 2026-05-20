package com.biasharaai.enterprise

import android.util.Log
import android.os.Build
import com.biasharaai.BuildConfig
import com.biasharaai.cloud.CloudAnalysisHttpClient
import com.biasharaai.cloud.CloudAnalysisSettingsStore
import com.biasharaai.cloud.EnterpriseEndpointPolicy
import com.biasharaai.data.local.db.EnterpriseAuditEvent
import com.biasharaai.data.local.db.EnterpriseAuditEventDao
import com.biasharaai.data.local.db.EnterpriseBranch
import com.biasharaai.data.local.db.EnterpriseBranchDao
import com.biasharaai.data.local.db.EnterpriseRegisteredDevice
import com.biasharaai.data.local.db.EnterpriseRegisteredDeviceDao
import com.biasharaai.data.local.db.EnterpriseSyncOutboxDao
import com.biasharaai.data.local.db.EnterpriseSyncOutboxItem
import com.biasharaai.device.DeviceIdProvider
import com.biasharaai.licence.LicenceValidator
import com.biasharaai.productline.ProductLineManager
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.flow.Flow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnterpriseAuditRepository @Inject constructor(
    private val auditEventDao: EnterpriseAuditEventDao,
    private val registeredDeviceDao: EnterpriseRegisteredDeviceDao,
    private val branchDao: EnterpriseBranchDao,
    private val syncOutboxDao: EnterpriseSyncOutboxDao,
    private val deviceIdProvider: DeviceIdProvider,
    private val licenceValidator: LicenceValidator,
    private val cloudAnalysisSettingsStore: CloudAnalysisSettingsStore,
    private val cloudAnalysisHttpClient: CloudAnalysisHttpClient,
    private val productLineManager: ProductLineManager,
) {
    private val gson = Gson()

    fun observeRegisteredDevices(): Flow<List<EnterpriseRegisteredDevice>> =
        registeredDeviceDao.observeActive()

    fun observeRecentAudit(limit: Int = 20): Flow<List<EnterpriseAuditEvent>> =
        auditEventDao.observeRecent(limit)

    fun observeBranches(): Flow<List<EnterpriseBranch>> =
        branchDao.observeActive()

    fun observePendingSyncCount(): Flow<Int> =
        syncOutboxDao.observeCount()

    suspend fun enqueueSyncPayload(
        payloadType: String,
        payloadEntityId: String,
        payload: Any,
    ) = runCatching {
        if (!productLineManager.isEnterprisePro()) return@runCatching
        registerCurrentDevice()
        val licence = licenceValidator.getStoredKey() ?: return@runCatching
        val branch = ensureDefaultBranch(licence.businessId)
        enqueue(
            businessId = licence.businessId,
            branchId = branch.id,
            deviceId = deviceIdProvider.get(),
            payloadType = payloadType,
            payloadEntityId = payloadEntityId,
            payload = payload,
        )
    }.onFailure { Log.w(TAG, "enqueueSyncPayload failed type=$payloadType", it) }

    suspend fun saveDefaultBranch(name: String, location: String?) {
        if (!productLineManager.isEnterprisePro()) return
        val licence = licenceValidator.getStoredKey() ?: return
        registerCurrentDevice()
        val current = ensureDefaultBranch(licence.businessId)
        val now = System.currentTimeMillis()
        val updated = current.copy(
            name = name.trim().take(MAX_BRANCH_NAME_CHARS),
            location = location?.trim()?.take(MAX_BRANCH_LOCATION_CHARS)?.takeIf { it.isNotBlank() },
            isDefault = true,
            isActive = true,
            updatedAt = now,
        )
        branchDao.upsert(updated)
        enqueue(
            businessId = licence.businessId,
            branchId = updated.id,
            deviceId = deviceIdProvider.get(),
            payloadType = "BRANCH_UPDATED",
            payloadEntityId = updated.id.toString(),
            payload = updated,
        )
        record(
            action = "BRANCH_UPDATED",
            entityType = "ENTERPRISE_BRANCH",
            entityId = updated.id.toString(),
            summary = "Default branch updated: ${updated.name}",
            metadata = "code=${updated.code}; locationConfigured=${!updated.location.isNullOrBlank()}",
        )
    }

    suspend fun flushPendingSync(limit: Int = 50): EnterpriseSyncResult = runCatching {
        if (!productLineManager.isEnterprisePro()) {
            return@runCatching EnterpriseSyncResult(skippedReason = SyncSkipReason.NOT_ENTERPRISE)
        }
        val settings = cloudAnalysisSettingsStore.load()
        val endpoint = settings.endpointUrl.trim()
        val key = cloudAnalysisSettingsStore.apiKeyOrNull()
        when {
            !settings.enabled -> return@runCatching EnterpriseSyncResult(skippedReason = SyncSkipReason.NOT_ENABLED)
            endpoint.isBlank() || !EnterpriseEndpointPolicy.isAllowed(endpoint, settings.deploymentMode.name) ->
                return@runCatching EnterpriseSyncResult(skippedReason = SyncSkipReason.MISSING_URL)
            key.isNullOrBlank() -> return@runCatching EnterpriseSyncResult(skippedReason = SyncSkipReason.MISSING_KEY)
        }
        val ready = syncOutboxDao.listReady(limit = limit)
        if (ready.isEmpty()) return@runCatching EnterpriseSyncResult()
        var sent = 0
        var failed = 0
        for (item in ready) {
            val envelope = EnterpriseSyncEnvelope(
                sentAtEpochMs = System.currentTimeMillis(),
                businessId = item.businessId,
                branchId = item.branchId,
                deviceId = item.deviceId,
                destinationMode = item.destinationMode,
                payloadType = item.payloadType,
                payloadEntityId = item.payloadEntityId,
                queuedAtEpochMs = item.createdAt,
                payload = parsePayload(item.payloadJson),
            )
            val result = cloudAnalysisHttpClient.postEnterpriseSyncItem(
                url = endpoint,
                jsonBody = gson.toJson(envelope),
                bearerToken = key,
                destinationMode = item.destinationMode,
                payloadType = item.payloadType,
            )
            result.fold(
                onSuccess = {
                    sent += 1
                    syncOutboxDao.markStatus(
                        id = item.id,
                        status = EnterpriseSyncOutboxItem.STATUS_SENT,
                    )
                },
                onFailure = { e ->
                    failed += 1
                    val attempts = item.attemptCount + 1
                    val finalFailure = attempts >= MAX_SYNC_ATTEMPTS
                    syncOutboxDao.markRetryState(
                        id = item.id,
                        status = if (finalFailure) {
                            EnterpriseSyncOutboxItem.STATUS_FAILED
                        } else {
                            EnterpriseSyncOutboxItem.STATUS_PENDING
                        },
                        attemptCount = attempts,
                        lastError = e.message?.take(MAX_SYNC_ERROR_CHARS),
                        nextAttemptAt = System.currentTimeMillis() + retryDelayMillis(attempts),
                    )
                },
            )
        }
        EnterpriseSyncResult(sent = sent, failed = failed)
    }.getOrElse {
        Log.w(TAG, "flushPendingSync failed", it)
        EnterpriseSyncResult(failed = 1)
    }

    suspend fun registerCurrentDevice() = runCatching {
        if (!productLineManager.isEnterprisePro()) return@runCatching Unit
        val licence = licenceValidator.getStoredKey() ?: return@runCatching Unit
        val deviceId = deviceIdProvider.get()
        val now = System.currentTimeMillis()
        val branch = ensureDefaultBranch(licence.businessId)
        val existing = registeredDeviceDao.getByDeviceId(deviceId)
        val displayName = buildDeviceDisplayName()
        val deploymentMode = cloudAnalysisSettingsStore.load().deploymentMode.name
        val device = EnterpriseRegisteredDevice(
            id = existing?.id ?: 0L,
            deviceId = deviceId,
            businessId = licence.businessId,
            displayName = displayName,
            deploymentMode = deploymentMode,
            appVersionName = BuildConfig.VERSION_NAME,
            maxDevicesSnapshot = licence.maxDevices,
            firstSeenAt = existing?.firstSeenAt ?: now,
            lastSeenAt = now,
            isActive = true,
        )
        val changed = existing == null ||
            existing.deploymentMode != deploymentMode ||
            existing.appVersionName != BuildConfig.VERSION_NAME ||
            existing.maxDevicesSnapshot != licence.maxDevices
        registeredDeviceDao.upsert(device)
        if (changed) {
            enqueue(
                businessId = licence.businessId,
                branchId = branch.id,
                deviceId = deviceId,
                payloadType = "DEVICE_REGISTRATION",
                payloadEntityId = deviceId,
                payload = device,
            )
        }
    }.onFailure { Log.w(TAG, "registerCurrentDevice failed", it) }

    suspend fun record(
        action: String,
        entityType: String,
        entityId: String? = null,
        summary: String,
        metadata: String? = null,
        actorStaffId: Long? = null,
        actorRole: String? = null,
    ) = runCatching {
        if (!productLineManager.isEnterprisePro()) return@runCatching Unit
        registerCurrentDevice()
        val licence = licenceValidator.getStoredKey() ?: return@runCatching Unit
        val branch = ensureDefaultBranch(licence.businessId)
        val event = EnterpriseAuditEvent(
            businessId = licence.businessId,
            deviceId = deviceIdProvider.get(),
            actorStaffId = actorStaffId,
            actorRole = actorRole,
            action = action.trim().uppercase(Locale.ROOT),
            entityType = entityType.trim().uppercase(Locale.ROOT),
            entityId = entityId?.trim()?.takeIf { it.isNotBlank() },
            summary = summary.trim().take(MAX_SUMMARY_CHARS),
            metadata = metadata?.trim()?.take(MAX_METADATA_CHARS)?.takeIf { it.isNotBlank() },
            deploymentMode = cloudAnalysisSettingsStore.load().deploymentMode.name,
        )
        val eventId = auditEventDao.insert(event)
        enqueue(
            businessId = licence.businessId,
            branchId = branch.id,
            deviceId = event.deviceId,
            payloadType = "AUDIT_EVENT",
            payloadEntityId = eventId.toString(),
            payload = event.copy(id = eventId),
        )
    }.onFailure { Log.w(TAG, "audit record failed action=$action", it) }

    private suspend fun ensureDefaultBranch(businessId: String): EnterpriseBranch {
        branchDao.getDefault(businessId)?.let { return it }
        branchDao.getByCode(businessId, DEFAULT_BRANCH_CODE)?.let { existing ->
            if (existing.isDefault && existing.isActive) return existing
            val updated = existing.copy(
                isDefault = true,
                isActive = true,
                updatedAt = System.currentTimeMillis(),
            )
            branchDao.upsert(updated)
            return updated
        }
        val now = System.currentTimeMillis()
        val branch = EnterpriseBranch(
            businessId = businessId,
            code = DEFAULT_BRANCH_CODE,
            name = "Main branch",
            isDefault = true,
            isActive = true,
            createdAt = now,
            updatedAt = now,
        )
        val id = branchDao.upsert(branch)
        return branch.copy(id = id)
    }

    private suspend fun enqueue(
        businessId: String,
        branchId: Long?,
        deviceId: String,
        payloadType: String,
        payloadEntityId: String,
        payload: Any,
    ) {
        val now = System.currentTimeMillis()
        syncOutboxDao.insert(
            EnterpriseSyncOutboxItem(
                businessId = businessId,
                branchId = branchId,
                deviceId = deviceId,
                destinationMode = cloudAnalysisSettingsStore.load().deploymentMode.name,
                payloadType = payloadType,
                payloadEntityId = payloadEntityId,
                payloadJson = gson.toJson(payload),
                createdAt = now,
                updatedAt = now,
                nextAttemptAt = now,
            ),
        )
    }

    private fun parsePayload(raw: String): JsonElement =
        runCatching { JsonParser.parseString(raw) }
            .getOrElse { JsonPrimitive(raw) }

    private fun retryDelayMillis(attemptCount: Int): Long {
        val minutes = when (attemptCount) {
            1 -> 5
            2 -> 15
            3 -> 60
            else -> 240
        }
        return minutes * 60_000L
    }

    private fun buildDeviceDisplayName(): String {
        val maker = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        return listOf(maker, model)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android device" }
            .take(MAX_DEVICE_NAME_CHARS)
    }

    private companion object {
        const val TAG = "EnterpriseAudit"
        const val DEFAULT_BRANCH_CODE = "MAIN"
        const val MAX_SYNC_ATTEMPTS = 5
        const val MAX_SYNC_ERROR_CHARS = 500
        const val MAX_BRANCH_NAME_CHARS = 80
        const val MAX_BRANCH_LOCATION_CHARS = 160
        const val MAX_DEVICE_NAME_CHARS = 80
        const val MAX_SUMMARY_CHARS = 240
        const val MAX_METADATA_CHARS = 1000
    }
}

data class EnterpriseSyncResult(
    val sent: Int = 0,
    val failed: Int = 0,
    val skippedReason: SyncSkipReason? = null,
)

enum class SyncSkipReason {
    NOT_ENTERPRISE,
    NOT_ENABLED,
    MISSING_URL,
    MISSING_KEY,
}

private data class EnterpriseSyncEnvelope(
    val schemaVersion: Int = 1,
    val exportKind: String = "enterprise_sync_outbox",
    val sentAtEpochMs: Long,
    val businessId: String,
    val branchId: Long?,
    val deviceId: String,
    val destinationMode: String,
    val payloadType: String,
    val payloadEntityId: String,
    val queuedAtEpochMs: Long,
    val payload: JsonElement,
)
