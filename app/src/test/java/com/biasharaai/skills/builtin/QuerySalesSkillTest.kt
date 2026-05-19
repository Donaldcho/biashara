package com.biasharaai.skills.builtin

import com.biasharaai.analytics.SalesIntelligenceRepository
import com.biasharaai.data.local.db.TransactionDao
import com.biasharaai.productline.ProductLineManager
import com.biasharaai.skills.SkillResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QuerySalesSkillTest {

    private lateinit var dao: TransactionDao
    private lateinit var salesIntelligence: SalesIntelligenceRepository
    private lateinit var productLineManager: ProductLineManager
    private lateinit var skill: QuerySalesSkill

    @Before
    fun setUp() {
        dao = mockk()
        salesIntelligence = mockk()
        productLineManager = mockk()
        skill = QuerySalesSkill(dao, salesIntelligence, productLineManager)
        every { productLineManager.isProEnabled() } returns false
        coEvery { salesIntelligence.periodSummary(any(), any()) } returns SalesIntelligenceRepository.PeriodSalesSummary(
            grossIncome = 1000.0,
            returnsTotal = -50.0,
            netRevenue = 950.0,
            returnTransactionCount = 1L,
            grossProductSubtotal = 900.0,
            grossServiceSubtotal = 100.0,
        )
        coEvery { dao.sumExpenseAmountBetween(any(), any()) } returns 200.0
        coEvery { dao.sumCreditIncomeAmountBetween(any(), any()) } returns 50.0
        coEvery { dao.countIncomeTransactionsBetween(any(), any()) } returns 12L
    }

    @Test
    fun execute_returnsSuccessWithTotals() = runTest {
        val result = skill.execute("""{"days":7}""")
        assertTrue(result is SkillResult.Success)
    }
}
