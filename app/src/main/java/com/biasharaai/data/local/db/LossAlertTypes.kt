package com.biasharaai.data.local.db

/** Prefix and types for loss-prevention alerts (Prompt U5). */
object LossAlertTypes {
    const val PREFIX = "LOSS_"
    const val SHRINKAGE = "LOSS_SHRINKAGE"
    const val SALES_GAP = "LOSS_SALES_GAP"
    const val LOW_PRICE = "LOSS_LOW_PRICE"
    const val HIGH_EXPENSE = "LOSS_HIGH_EXPENSE"

    fun isLossDashboardType(alertType: String): Boolean = alertType.startsWith(PREFIX)
}
