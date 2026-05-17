package com.biasharaai.ledger

import com.biasharaai.data.local.db.SaleLineItem

object LedgerDescriptionBuilder {

    fun productSale(items: List<SaleLineItem>, receiptNum: String?): String {
        val summary = items
            .filter { it.quantity > 0 }
            .take(2)
            .joinToString(", ") { "${it.productName} x${it.quantity}" }
        val more = items.count { it.quantity > 0 }.let { if (it > 2) " +${it - 2} more" else "" }
        val receipt = receiptNum?.takeIf { it.isNotBlank() }?.let { " [$it]" }.orEmpty()
        return "Sale: $summary$more$receipt"
    }

    fun serviceSale(serviceNames: List<String>): String {
        val names = serviceNames.joinToString(", ").ifBlank { "Service" }
        return "Service: $names"
    }

    fun mixedSale(productQty: Int, serviceCount: Int, receiptNum: String?): String {
        val receipt = receiptNum?.takeIf { it.isNotBlank() }?.let { " [$it]" }.orEmpty()
        return "Sale: $productQty product${if (productQty != 1) "s" else ""}, " +
            "$serviceCount service${if (serviceCount != 1) "s" else ""}$receipt"
    }
}
