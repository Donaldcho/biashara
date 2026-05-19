package com.biasharaai.ledger

import com.biasharaai.data.local.db.LedgerEntryDao
import com.biasharaai.data.local.db.LedgerEntryType
import com.biasharaai.data.local.db.SaleLineItemDao
import com.biasharaai.data.local.db.ServiceDeliveryDao
import com.biasharaai.productline.ProductLineManager
import javax.inject.Inject
import javax.inject.Singleton

data class IncomeBreakdown(
    val productSales: Double,
    val serviceSales: Double,
    val voucherSales: Double,
    val otherIncome: Double,
    val totalIncome: Double,
)

/**
 * P&L income streams: product revenue from sale lines; service revenue from deliveries
 * and pure service sales; mixed [SALE_MIXED] ledger rows use stored subtotals on transactions.
 */
@Singleton
class LedgerPnLCalculator @Inject constructor(
    private val saleLineItemDao: SaleLineItemDao,
    private val serviceDeliveryDao: ServiceDeliveryDao,
    private val ledgerEntryDao: LedgerEntryDao,
    private val productLineManager: ProductLineManager,
) {

    suspend fun incomeBreakdown(from: Long, to: Long): IncomeBreakdown {
        val productFromLines = saleLineItemDao.sumProductSalesInPeriod(from, to)
        val serviceFromDeliveries = if (productLineManager.isProEnabled()) {
            serviceDeliveryDao.sumChargedInPeriod(from, to)
        } else {
            0.0
        }
        val rows = ledgerEntryDao.getBreakdownByType(from, to)
        val totalsByType = rows.associate { it.type to it.total }
        val voucherSales = totalsByType[LedgerEntryType.VOUCHER_SALE.name] ?: 0.0
        val otherIncome = (totalsByType[LedgerEntryType.OTHER_INCOME.name] ?: 0.0) +
            (totalsByType[LedgerEntryType.DEBT_REPAID.name] ?: 0.0)

        val productSales = productFromLines
        val serviceSales = serviceFromDeliveries
        val totalIncome = productSales + serviceSales + voucherSales + otherIncome
        return IncomeBreakdown(
            productSales = productSales,
            serviceSales = serviceSales,
            voucherSales = voucherSales,
            otherIncome = otherIncome,
            totalIncome = totalIncome,
        )
    }
}
