package com.biasharaai.ui.inventory

import com.biasharaai.receipt.ReceiptLineItem
import kotlin.math.roundToInt

/**
 * Editable row on [ReceiptReviewFragment] — Prompt U4.
 */
data class ReceiptDraftLine(
    val id: String,
    var name: String,
    var quantityText: String,
    var costText: String,
    val parsedHadNulls: Boolean,
) {
    fun isRowBlank(): Boolean =
        name.isBlank() && quantityText.isBlank() && costText.isBlank()

    /** Amber until the row is complete; empty manual-fallback row stays highlighted until filled. */
    fun needsAmberHighlight(): Boolean =
        !isCompleteForSave() && (parsedHadNulls || !isRowBlank())

    fun isCompleteForSave(): Boolean {
        if (name.isBlank()) return false
        val qty = quantityText.toDoubleOrNull() ?: return false
        if (qty <= 0.0) return false
        val cost = costText.toDoubleOrNull() ?: return false
        return cost >= 0.0
    }

    companion object {
        fun fromParsed(item: ReceiptLineItem): ReceiptDraftLine =
            ReceiptDraftLine(
                id = java.util.UUID.randomUUID().toString(),
                name = item.name?.trim().orEmpty(),
                quantityText = formatQuantity(item.quantity),
                costText = item.cost?.let { formatCost(it) }.orEmpty(),
                parsedHadNulls =
                    item.name.isNullOrBlank() || item.quantity == null || item.cost == null,
            )

        fun emptyRow(): ReceiptDraftLine =
            ReceiptDraftLine(
                id = java.util.UUID.randomUUID().toString(),
                name = "",
                quantityText = "",
                costText = "",
                parsedHadNulls = true,
            )

        private fun formatQuantity(q: Double?): String {
            if (q == null) return ""
            return if (q % 1.0 == 0.0) q.roundToInt().toString() else q.toString()
        }

        private fun formatCost(c: Double): String =
            if (c % 1.0 == 0.0) c.roundToInt().toString() else String.format(java.util.Locale.US, "%.2f", c)
    }
}
