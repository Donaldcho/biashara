package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Companion row to a [LedgerEntry] that records HOW a cash movement was captured.
 *
 * Images are NEVER stored. The [rawText] (OCR output or pasted SMS) is the permanent
 * proof record. [thumbnailBytes] is opt-in, capped at 50 KB, and purged when storage is low.
 */
@Entity(
    tableName = "cash_movement_evidence",
    foreignKeys = [
        ForeignKey(
            entity = LedgerEntry::class,
            parentColumns = ["id"],
            childColumns = ["ledger_entry_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["ledger_entry_id"]),
        Index(value = ["parsed_reference"]),
    ],
)
data class CashMovementEvidence(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "ledger_entry_id")
    val ledgerEntryId: Long,

    @ColumnInfo(name = "capture_method")
    val captureMethod: CaptureMethod,

    @ColumnInfo(name = "proof_type", defaultValue = "UNKNOWN")
    val proofType: ProofType = ProofType.UNKNOWN,

    /** Full OCR or pasted SMS text — capped at 2 000 chars. Never image bytes. */
    @ColumnInfo(name = "raw_text")
    val rawText: String? = null,

    @ColumnInfo(name = "parsed_amount")
    val parsedAmount: Double? = null,

    @ColumnInfo(name = "parsed_reference")
    val parsedReference: String? = null,

    @ColumnInfo(name = "parsed_counterparty")
    val parsedCounterparty: String? = null,

    @ColumnInfo(name = "parsed_date")
    val parsedDate: Long? = null,

    @ColumnInfo(name = "parser_confidence", defaultValue = "0.0")
    val parserConfidence: Float = 0f,

    @ColumnInfo(name = "parser_engine", defaultValue = "MANUAL")
    val parserEngine: ParserEngine = ParserEngine.MANUAL,

    @ColumnInfo(name = "review_status", defaultValue = "NEEDS_REVIEW")
    val reviewStatus: ReviewStatus = ReviewStatus.NEEDS_REVIEW,

    /** JPEG bytes ≤ 50 KB — only stored when owner opts in. Purged when storage < 100 MB. */
    @ColumnInfo(name = "thumbnail_bytes")
    val thumbnailBytes: ByteArray? = null,

    @ColumnInfo(name = "thumbnail_size_bytes", defaultValue = "0")
    val thumbnailSizeBytes: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CashMovementEvidence) return false
        return id == other.id &&
            ledgerEntryId == other.ledgerEntryId &&
            captureMethod == other.captureMethod &&
            proofType == other.proofType &&
            rawText == other.rawText &&
            parsedAmount == other.parsedAmount &&
            parsedReference == other.parsedReference &&
            parsedCounterparty == other.parsedCounterparty &&
            parsedDate == other.parsedDate &&
            parserConfidence == other.parserConfidence &&
            parserEngine == other.parserEngine &&
            reviewStatus == other.reviewStatus &&
            thumbnailSizeBytes == other.thumbnailSizeBytes &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int = id.hashCode()
}

enum class CaptureMethod { CAMERA_OCR, QR_CODE, SMS_IMPORT, MANUAL }

enum class ProofType {
    MPESA_SMS, RECEIPT, INVOICE, SUPPLIER_BILL,
    HANDWRITTEN_NOTE, BANK_SLIP, UTILITY_BILL,
    TILL_SLIP, QR_BIASHARA, UNKNOWN,
}

enum class ParserEngine { REGEX, FUNCTION_GEMMA, FULL_GEMMA, MANUAL }

enum class ReviewStatus { AUTO_ACCEPTED, CONFIRMED, NEEDS_REVIEW, REJECTED }
