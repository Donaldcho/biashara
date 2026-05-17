package com.biasharaai.skills.builtin

import com.biasharaai.ledger.intelligence.LedgerIntelligenceRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryLedgerV2SkillTest {

    @Test
    fun execute_includesVersion2() = runTest {
        val repo = mockk<LedgerIntelligenceRepository>()
        coEvery {
            repo.queryV2(any(), any(), any())
        } returns mapOf(
            "version" to 2,
            "summary" to mapOf("moneyIn" to 100.0, "moneyOut" to 40.0, "net" to 60.0, "runningBalance" to 200.0),
        )

        val result = QueryLedgerV2Skill(repo).execute(
            """{"period":"LAST_30_DAYS","timezone":"Africa/Nairobi","include":["summary"]}""",
        )

        assertTrue(result is com.biasharaai.skills.SkillResult.Success)
        val json = (result as com.biasharaai.skills.SkillResult.Success).outputJson
        assertTrue(json.contains("\"version\":2"))
        assertTrue(json.contains("\"moneyIn\":100"))
    }
}
