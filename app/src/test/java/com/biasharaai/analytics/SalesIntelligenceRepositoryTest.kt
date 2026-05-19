package com.biasharaai.analytics

import com.biasharaai.data.local.db.SaleLineItemDao
import com.biasharaai.data.local.db.TransactionDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SalesIntelligenceRepositoryTest {

    private lateinit var transactionDao: TransactionDao
    private lateinit var saleLineItemDao: SaleLineItemDao
    private lateinit var repo: SalesIntelligenceRepository

    @Before
    fun setUp() {
        transactionDao = mockk()
        saleLineItemDao = mockk()
        repo = SalesIntelligenceRepository(transactionDao, saleLineItemDao)
    }

    @Test
    fun periodSummary_netsReturnsAgainstIncome() = runTest {
        coEvery { transactionDao.sumIncomeAmountBetween(0L, 1000L) } returns 500.0
        coEvery { transactionDao.sumReturnAmountBetween(0L, 1000L) } returns -120.0
        coEvery { transactionDao.countReturnTransactionsBetween(0L, 1000L) } returns 2L
        coEvery { transactionDao.sumProductSubtotalBetween(0L, 1000L) } returns 400.0
        coEvery { transactionDao.sumServiceSubtotalBetween(0L, 1000L) } returns 100.0

        val summary = repo.periodSummary(0L, 1000L)

        assertEquals(500.0, summary.grossIncome, 0.001)
        assertEquals(-120.0, summary.returnsTotal, 0.001)
        assertEquals(380.0, summary.netRevenue, 0.001)
        assertEquals(2L, summary.returnTransactionCount)
    }

    @Test
    fun netProductRanks_filtersByMinNetQty() = runTest {
        coEvery { saleLineItemDao.netProductSalesInPeriod(0L, 999L) } returns listOf(
            ProductNetSalesRow(1, "Milk", 5, 250.0),
            ProductNetSalesRow(2, "Returned item", 0, 0.0),
        )

        val ranks = repo.netProductRanksInPeriod(0L, 1000L, minNetQty = 1)

        assertEquals(1, ranks.size)
        assertEquals("Milk", ranks[0].name)
        assertEquals(5, ranks[0].netQty)
    }
}
