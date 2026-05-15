package com.biasharaai.agent

import com.biasharaai.data.local.db.AgentAction
import com.biasharaai.data.local.db.TransactionDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure Room-backed fraud heuristics — no on-device LLM. Each hit becomes a [AgentAction] with
 * [AgentActionBuilder.fraudSignal] (**CRITICAL** urgency).
 */
@Singleton
class FraudRuleEngine @Inject constructor(
    private val transactionDao: TransactionDao,
) {

    suspend fun detectAll(nowMillis: Long): List<AgentAction> {
        val since7d = nowMillis - DAYS_7_MS
        val since24h = nowMillis - HOURS_24_MS
        val out = ArrayList<AgentAction>()

        for (id in transactionDao.transactionIdsWithBelowHalfCostSaleLinesSince(since7d)) {
            out.add(
                AgentActionBuilder.fraudSignal(
                    headline = "Below-cost sale suspected",
                    detail = "At least one sale line was recorded below half of current product cost. Review the sale and cost data.",
                    relatedTransactionId = id,
                    nowMillis = nowMillis,
                ),
            )
        }

        val returnCount = transactionDao.countReturnsSince(since24h)
        if (returnCount > HIGH_RETURN_COUNT_THRESHOLD) {
            out.add(
                AgentActionBuilder.fraudSignal(
                    headline = HEADLINE_ELEVATED_RETURNS,
                    detail = "Recorded $returnCount return transactions in the last 24 hours. Review for policy abuse or operational issues.",
                    relatedTransactionId = null,
                    nowMillis = nowMillis,
                ),
            )
        }

        for (id in transactionDao.incomeTransactionIdsWithNonPositiveAmountSince(since7d)) {
            out.add(
                AgentActionBuilder.fraudSignal(
                    headline = "Non-positive sale total",
                    detail = "An income transaction has a non-positive total amount in the recent window.",
                    relatedTransactionId = id,
                    nowMillis = nowMillis,
                ),
            )
        }

        for (id in transactionDao.transactionIdsWithDuplicateReceiptNumberSince(since7d)) {
            out.add(
                AgentActionBuilder.fraudSignal(
                    headline = "Duplicate receipt number",
                    detail = "The same receipt number appears on more than one transaction in the last 7 days.",
                    relatedTransactionId = id,
                    nowMillis = nowMillis,
                ),
            )
        }

        return out
    }

    companion object {
        const val HEADLINE_ELEVATED_RETURNS = "Elevated return volume (24h)"

        private const val DAYS_7_MS = 7L * 24 * 60 * 60 * 1000
        private const val HOURS_24_MS = 24L * 60 * 60 * 1000
        private const val HIGH_RETURN_COUNT_THRESHOLD = 8L
    }
}
