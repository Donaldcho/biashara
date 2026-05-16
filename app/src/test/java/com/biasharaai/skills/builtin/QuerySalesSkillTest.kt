package com.biasharaai.skills.builtin

import com.biasharaai.data.local.db.TransactionDao
import com.biasharaai.skills.SkillResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QuerySalesSkillTest {

    private lateinit var dao: TransactionDao
    private lateinit var skill: QuerySalesSkill

    @Before
    fun setUp() {
        dao = mockk()
        skill = QuerySalesSkill(dao)
        coEvery { dao.sumIncomeAmountBetween(any(), any()) } returns 1000.0
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
