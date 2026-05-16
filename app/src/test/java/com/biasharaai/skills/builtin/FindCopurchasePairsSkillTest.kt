package com.biasharaai.skills.builtin

import com.biasharaai.agent.CoPurchaseAnalyser
import com.biasharaai.data.local.db.CoPurchasePair
import com.biasharaai.skills.SkillResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FindCopurchasePairsSkillTest {

    private lateinit var analyser: CoPurchaseAnalyser
    private lateinit var skill: FindCopurchasePairsSkill

    @Before
    fun setUp() {
        analyser = mockk()
        skill = FindCopurchasePairsSkill(analyser)
        coEvery { analyser.topPairsSince(any(), any()) } returns listOf(
            CoPurchasePair(product1 = "Bread", product2 = "Milk", coCount = 5),
        )
    }

    @Test
    fun execute_returnsSuccessWithPairs() = runTest {
        val result = skill.execute("""{"days":30}""")
        assertTrue(result is SkillResult.Success)
    }
}
