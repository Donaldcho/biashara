package com.biasharaai.cash

import android.util.Log
import androidx.room.withTransaction
import com.biasharaai.data.local.db.AppDatabase
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.CaptureMethod
import com.biasharaai.data.local.db.CashMovementEvidence
import com.biasharaai.data.local.db.CashMovementEvidenceDao
import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.data.local.db.LedgerEntry
import com.biasharaai.data.local.db.LedgerEntryDao
import com.biasharaai.data.local.db.LedgerEntryType
import com.biasharaai.data.local.db.MoneyDraft
import com.biasharaai.data.local.db.MoneyDraftDao
import com.biasharaai.data.local.db.MoneyDraftStatus
import com.biasharaai.data.local.db.ParserEngine
import com.biasharaai.data.local.db.ProofType
import com.biasharaai.data.local.db.ReviewStatus
import com.biasharaai.money.RegionalDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import android.graphics.Bitmap

data class CashMovementRequest(
    val direction: LedgerDirection,
    val type: LedgerEntryType,
    val amount: Double,
    val description: String,
    val notes: String? = null,
    val captureMethod: CaptureMethod = CaptureMethod.MANUAL,
    val proofType: ProofType = ProofType.UNKNOWN,
    val rawText: String? = null,
    val parsedReference: String? = null,
    val parsedCounterparty: String? = null,
    val parsedDate: Long? = null,
    val parserConfidence: Float = 0f,
    val parserEngine: ParserEngine = ParserEngine.MANUAL,
    val thumbnail: Bitmap? = null,
    val occurredAt: Long = System.currentTimeMillis(),
    val deviceId: String = "device",
)

data class CashMovementResult(
    val ledgerEntryId: Long,
    val evidenceId: Long,
)

data class CashMovementDraftResult(
    val draftId: Long,
)

@Singleton
class CashMovementRepository @Inject constructor(
    private val db: AppDatabase,
    private val ledgerEntryDao: LedgerEntryDao,
    private val evidenceDao: CashMovementEvidenceDao,
    private val moneyDraftDao: MoneyDraftDao,
    private val appSettingsDao: AppSettingsDao,
) {

    suspend fun saveCashMovement(request: CashMovementRequest): CashMovementResult =
        withContext(Dispatchers.IO) {
            val currency = appSettingsDao.getSettingsSync()?.currencyCode ?: RegionalDefaults.CURRENCY_CODE
            val thumbnailBytes = compressThumbnail(request.thumbnail)

            db.withTransaction {
                insertLedgerMovement(
                    request = request,
                    currency = currency,
                    thumbnailBytes = thumbnailBytes,
                    reviewStatus = ReviewStatus.CONFIRMED,
                )
            }
        }

    suspend fun saveDraft(request: CashMovementRequest): CashMovementDraftResult =
        withContext(Dispatchers.IO) {
            val thumbnailBytes = compressThumbnail(request.thumbnail)
            val draft = MoneyDraft(
                direction = request.direction,
                type = request.type,
                amount = request.amount,
                description = request.description,
                notes = request.notes,
                captureMethod = request.captureMethod,
                proofType = request.proofType,
                rawText = request.rawText?.take(2000),
                parsedReference = request.parsedReference,
                parsedCounterparty = request.parsedCounterparty,
                parsedDate = request.parsedDate,
                parserConfidence = request.parserConfidence,
                parserEngine = request.parserEngine,
                status = MoneyDraftStatus.NEEDS_REVIEW,
                thumbnailBytes = thumbnailBytes,
                thumbnailSizeBytes = thumbnailBytes?.size ?: 0,
                occurredAt = request.occurredAt,
                deviceId = request.deviceId,
            )
            CashMovementDraftResult(moneyDraftDao.insert(draft))
        }

    suspend fun approveDraft(
        draftId: Long,
        amount: Double? = null,
        description: String? = null,
        notes: String? = null,
    ): CashMovementResult =
        withContext(Dispatchers.IO) {
            val currency = appSettingsDao.getSettingsSync()?.currencyCode ?: RegionalDefaults.CURRENCY_CODE
            db.withTransaction {
                val draft = moneyDraftDao.getById(draftId) ?: error("Draft not found")
                if (draft.status == MoneyDraftStatus.APPROVED && draft.ledgerEntryId != null) {
                    return@withTransaction CashMovementResult(
                        ledgerEntryId = draft.ledgerEntryId,
                        evidenceId = evidenceDao.getForEntry(draft.ledgerEntryId)?.id ?: 0L,
                    )
                }
                check(draft.status == MoneyDraftStatus.NEEDS_REVIEW) { "Draft is already ${draft.status.name.lowercase()}" }

                val result = insertLedgerMovement(
                    request = draft.toRequest(
                        amountOverride = amount,
                        descriptionOverride = description,
                        notesOverride = notes,
                    ),
                    currency = currency,
                    thumbnailBytes = draft.thumbnailBytes,
                    reviewStatus = ReviewStatus.CONFIRMED,
                )
                moneyDraftDao.markApproved(
                    id = draft.id,
                    ledgerEntryId = result.ledgerEntryId,
                    approvedAt = System.currentTimeMillis(),
                )
                result
            }
        }

    suspend fun rejectDraft(draftId: Long) {
        withContext(Dispatchers.IO) {
            moneyDraftDao.markRejected(draftId)
        }
    }

    private suspend fun insertLedgerMovement(
        request: CashMovementRequest,
        currency: String,
        thumbnailBytes: ByteArray?,
        reviewStatus: ReviewStatus,
    ): CashMovementResult {
        // Balance read must be inside the transaction so concurrent saves serialise correctly.
        val currentBalance = ledgerEntryDao.getCurrentBalance() ?: 0.0
        val newBalance = when (request.direction) {
            LedgerDirection.MONEY_IN -> currentBalance + request.amount
            LedgerDirection.MONEY_OUT -> currentBalance - request.amount
            LedgerDirection.NEUTRAL -> currentBalance
        }

        val entry = LedgerEntry(
            occurredAt = request.occurredAt,
            type = request.type,
            direction = request.direction,
            amount = request.amount,
            currency = currency,
            description = request.description,
            notes = request.notes,
            runningBalance = newBalance,
            deviceId = request.deviceId,
        )
        val entryId = ledgerEntryDao.insert(entry)

        val evidence = CashMovementEvidence(
            ledgerEntryId = entryId,
            captureMethod = request.captureMethod,
            proofType = request.proofType,
            rawText = request.rawText?.take(2000),
            parsedAmount = request.amount,
            parsedReference = request.parsedReference,
            parsedCounterparty = request.parsedCounterparty,
            parsedDate = request.parsedDate,
            parserConfidence = request.parserConfidence,
            parserEngine = request.parserEngine,
            reviewStatus = reviewStatus,
            thumbnailBytes = thumbnailBytes,
            thumbnailSizeBytes = thumbnailBytes?.size ?: 0,
        )
        val evidenceId = evidenceDao.insert(evidence)

        checkFraudSignals(request, evidenceId)

        return CashMovementResult(entryId, evidenceId)
    }

    private suspend fun checkFraudSignals(request: CashMovementRequest, evidenceId: Long) {
        val ref = request.parsedReference ?: return
        val dupCount = evidenceDao.countByReference(ref, excludeId = evidenceId)
        if (dupCount > 0) {
            Log.w(TAG, "FraudSignal: duplicate reference $ref (existing count=$dupCount)")
        }
    }

    private fun compressThumbnail(thumbnail: Bitmap?): ByteArray? =
        thumbnail?.let { bmp ->
            runCatching {
                ByteArrayOutputStream().use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
                    val bytes = out.toByteArray()
                    if (bytes.size <= MAX_THUMBNAIL_BYTES) bytes else null
                }
            }.getOrNull()
        }

    private fun MoneyDraft.toRequest(
        amountOverride: Double?,
        descriptionOverride: String?,
        notesOverride: String?,
    ): CashMovementRequest =
        CashMovementRequest(
            direction = direction,
            type = type,
            amount = amountOverride?.takeIf { it > 0 } ?: amount,
            description = descriptionOverride?.trim()?.takeIf { it.isNotBlank() } ?: description,
            notes = notesOverride?.trim()?.takeIf { it.isNotBlank() } ?: notes,
            captureMethod = captureMethod,
            proofType = proofType,
            rawText = rawText,
            parsedReference = parsedReference,
            parsedCounterparty = parsedCounterparty,
            parsedDate = parsedDate,
            parserConfidence = parserConfidence,
            parserEngine = parserEngine,
            thumbnail = null,
            occurredAt = occurredAt,
            deviceId = deviceId,
        )

    companion object {
        private const val TAG = "CashMovementRepository"
        private const val MAX_THUMBNAIL_BYTES = 50 * 1024
    }
}
