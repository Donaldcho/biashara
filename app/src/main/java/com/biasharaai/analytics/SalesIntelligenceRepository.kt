package com.biasharaai.analytics

import com.biasharaai.data.local.db.SaleLineItemDao
import com.biasharaai.data.local.db.TransactionDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for sales metrics used by agents, Chat, and skills.
 * All figures are **net of returns** unless explicitly named gross.
 */
@Singleton
class SalesIntelligenceRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val saleLineItemDao: SaleLineItemDao,
) {

    data class PeriodSalesSummary(
        val grossIncome: Double,
        val returnsTotal: Double,
        val netRevenue: Double,
        val returnTransactionCount: Long,
        val grossProductSubtotal: Double,
        val grossServiceSubtotal: Double,
    )

    data class ProductRank(
        val name: String,
        val netQty: Int,
        val netRevenue: Double,
    )

    suspend fun periodSummary(startMillis: Long, endExclusiveMillis: Long): PeriodSalesSummary {
        val grossIncome = transactionDao.sumIncomeAmountBetween(startMillis, endExclusiveMillis)
        val returnsTotal = transactionDao.sumReturnAmountBetween(startMillis, endExclusiveMillis)
        val netRevenue = grossIncome + returnsTotal
        val returnCount = transactionDao.countReturnTransactionsBetween(startMillis, endExclusiveMillis)
        return PeriodSalesSummary(
            grossIncome = grossIncome,
            returnsTotal = returnsTotal,
            netRevenue = netRevenue,
            returnTransactionCount = returnCount,
            grossProductSubtotal = transactionDao.sumProductSubtotalBetween(startMillis, endExclusiveMillis),
            grossServiceSubtotal = transactionDao.sumServiceSubtotalBetween(startMillis, endExclusiveMillis),
        )
    }

    suspend fun netProductRanksInPeriod(
        startMillis: Long,
        endExclusiveMillis: Long,
        minNetQty: Int = 1,
    ): List<ProductRank> {
        val endInclusive = endExclusiveMillis - 1L
        return saleLineItemDao.netProductSalesInPeriod(startMillis, endInclusive)
            .filter { it.netQty >= minNetQty }
            .map { ProductRank(it.productName, it.netQty, it.netRevenue) }
    }

    suspend fun topProductInPeriod(startMillis: Long, endExclusiveMillis: Long): ProductRank? =
        netProductRanksInPeriod(startMillis, endExclusiveMillis).maxByOrNull { it.netQty }

    suspend fun slowestProductInPeriod(
        startMillis: Long,
        endExclusiveMillis: Long,
        minNetQty: Int = 1,
    ): ProductRank? =
        netProductRanksInPeriod(startMillis, endExclusiveMillis, minNetQty)
            .filter { it.netQty > 0 }
            .minByOrNull { it.netQty }
}
