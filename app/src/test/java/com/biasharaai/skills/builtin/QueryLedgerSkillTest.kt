package com.biasharaai.skills.builtin

import com.biasharaai.data.local.db.LedgerEntryDao
import com.biasharaai.data.local.db.LedgerTypeTotal
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryLedgerSkillTest {

    @Test
    fun execute_returnsTotals() = runTest {
        val dao = mockk<LedgerEntryDao>()
        coEvery {
            dao.getTotalForDirection("MONEY_IN", any(), any())
        } returns 1000.0
        coEvery {
            dao.getTotalForDirection("MONEY_OUT", any(), any())
        } returns 400.0
        coEvery { dao.getCurrentBalance() } returns 600.0
        coEvery { dao.getPendingCreditTotal() } returns 200.0
        coEvery { dao.getBreakdownByType(any(), any()) } returns listOf(
            LedgerTypeTotal("SALE_PRODUCT", 800.0),
        )

        val result = QueryLedgerSkill(dao).execute("""{"days":7}""")

        assertTrue(result is com.biasharaai.skills.SkillResult.Success)
        val json = (result as com.biasharaai.skills.SkillResult.Success).outputJson
        assertTrue(json.contains("\"moneyIn\":1000"))
        assertTrue(json.contains("\"runningBalance\":600"))
    }
}
