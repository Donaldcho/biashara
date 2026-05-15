package com.biasharaai.agent

import com.biasharaai.data.local.db.AgentAction
import com.biasharaai.data.local.db.ProductDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-only low-stock evaluation for [StockGuardianWorker] — 7-day rolling average daily units
 * vs current [com.biasharaai.data.local.db.Product.stockQuantity].
 */
@Singleton
class StockGuardianRepository @Inject constructor(
    private val productDao: ProductDao,
) {

    suspend fun buildLowStockActions(
        nowMillis: Long,
        currencySymbol: String,
    ): List<AgentAction> {
        val periodStart = nowMillis - DAYS_7_MS
        val periodEndExclusive = nowMillis + 1
        val products = productDao.getProductsList()
        val actions = ArrayList<AgentAction>(products.size)
        for (product in products) {
            val unitsSold =
                productDao.sumUnitsSoldForProductInPeriod(product.id, periodStart, periodEndExclusive)
            val avgDaily = unitsSold / 7.0
            if (avgDaily <= 0.0) continue
            val daysRemaining = product.stockQuantity / avgDaily
            val urgency = when {
                daysRemaining < 1.0 -> "CRITICAL"
                daysRemaining < 2.0 -> "HIGH"
                daysRemaining < 5.0 -> "MEDIUM"
                daysRemaining < 7.0 -> "LOW"
                else -> null
            } ?: continue
            actions.add(
                AgentActionBuilder.stockAlert(
                    productId = product.id,
                    productName = product.name,
                    urgency = urgency,
                    daysRemaining = daysRemaining,
                    currencySymbol = currencySymbol,
                    nowMillis = nowMillis,
                ),
            )
        }
        return actions
    }

    companion object {
        private const val DAYS_7_MS = 7L * 24 * 60 * 60 * 1000
    }
}
