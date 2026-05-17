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
import com.biasharaai.data.local.db.ParserEngine
import com.biasharaai.data.local.db.ProofType
import com.biasharaai.data.local.db.ReviewStatus
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

@Singleton
class CashMovementRepository @Inject constructor(
    private val db: AppDatabase,
    private val ledgerEntryDao: LedgerEntryDao,
    private val evidenceDao: CashMovementEvidenceDao,
    private val appSettingsDao: AppSettingsDao,
) {

    suspend fun saveCashMovement(request: CashMovementRequest): CashMovementResult =
        withContext(Dispatchers.IO) {
            val currency = appSettingsDao.getSettingsSync()?.currencyCode ?: "KES"

            // Compress thumbnail outside the transaction (pure CPU work).
            val thumbnailBytes = request.thumbnail?.let { bmp ->
                runCatching {
                    ByteArrayOutputStream().use { out ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
                        val bytes = out.toByteArray()
                        if (bytes.size <= MAX_THUMBNAIL_BYTES) bytes else null
                    }
                }.getOrNull()
            }

            db.withTransaction {
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

                val reviewStatus = if (request.parserConfidence >= 0.85f)
                    ReviewStatus.AUTO_ACCEPTED else ReviewStatus.NEEDS_REVIEW

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

                CashMovementResult(entryId, evidenceId)
            }
        }

    private suspend fun checkFraudSignals(request: CashMovementRequest, evidenceId: Long) {
        val ref = request.parsedReference ?: return
        val dupCount = evidenceDao.countByReference(ref, excludeId = evidenceId)
        if (dupCount > 0) {
            Log.w(TAG, "FraudSignal: duplicate reference $ref (existing count=$dupCount)")
        }
    }

    companion object {
        private const val TAG = "CashMovementRepository"
        private const val MAX_THUMBNAIL_BYTES = 50 * 1024
    }
}
