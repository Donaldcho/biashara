package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Temporary owner-review row for lazy money capture.
 *
 * Drafts let scan, SMS, and manual capture collect evidence first without mutating
 * the generated ledger until the owner approves the movement.
 */
@Entity(
    tableName = "money_drafts",
    indices = [
        Index(value = ["status", "created_at"], name = "index_money_drafts_status_created_at"),
        Index(value = ["direction"]),
        Index(value = ["ledger_entry_id"]),
        Index(value = ["parsed_reference"]),
    ],
)
data class MoneyDraft(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "direction")
    val direction: LedgerDirection,

    @ColumnInfo(name = "type")
    val type: LedgerEntryType,

    @ColumnInfo(name = "amount")
    val amount: Double,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "capture_method", defaultValue = "MANUAL")
    val captureMethod: CaptureMethod = CaptureMethod.MANUAL,

    @ColumnInfo(name = "proof_type", defaultValue = "UNKNOWN")
    val proofType: ProofType = ProofType.UNKNOWN,

    @ColumnInfo(name = "raw_text")
    val rawText: String? = null,

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

    @ColumnInfo(name = "status", defaultValue = "NEEDS_REVIEW")
    val status: MoneyDraftStatus = MoneyDraftStatus.NEEDS_REVIEW,

    @ColumnInfo(name = "thumbnail_bytes")
    val thumbnailBytes: ByteArray? = null,

    @ColumnInfo(name = "thumbnail_size_bytes", defaultValue = "0")
    val thumbnailSizeBytes: Int = 0,

    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "approved_at")
    val approvedAt: Long? = null,

    @ColumnInfo(name = "ledger_entry_id")
    val ledgerEntryId: Long? = null,

    @ColumnInfo(name = "device_id")
    val deviceId: String = "device",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MoneyDraft) return false
        return id == other.id &&
            direction == other.direction &&
            type == other.type &&
            amount == other.amount &&
            description == other.description &&
            notes == other.notes &&
            captureMethod == other.captureMethod &&
            proofType == other.proofType &&
            rawText == other.rawText &&
            parsedReference == other.parsedReference &&
            parsedCounterparty == other.parsedCounterparty &&
            parsedDate == other.parsedDate &&
            parserConfidence == other.parserConfidence &&
            parserEngine == other.parserEngine &&
            status == other.status &&
            thumbnailSizeBytes == other.thumbnailSizeBytes &&
            occurredAt == other.occurredAt &&
            createdAt == other.createdAt &&
            approvedAt == other.approvedAt &&
            ledgerEntryId == other.ledgerEntryId &&
            deviceId == other.deviceId
    }

    override fun hashCode(): Int = id.hashCode()
}

enum class MoneyDraftStatus { NEEDS_REVIEW, APPROVED, REJECTED }
