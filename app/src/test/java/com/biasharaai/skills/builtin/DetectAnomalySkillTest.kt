package com.biasharaai.skills.builtin

import com.biasharaai.agent.FraudRuleEngine
import com.biasharaai.data.local.db.AgentAction
import com.biasharaai.skills.SkillResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DetectAnomalySkillTest {

    private lateinit var fraudRuleEngine: FraudRuleEngine
    private lateinit var skill: DetectAnomalySkill

    @Before
    fun setUp() {
        fraudRuleEngine = mockk()
        skill = DetectAnomalySkill(fraudRuleEngine)
        coEvery { fraudRuleEngine.detectAll(any()) } returns listOf(
            AgentAction(
                agentType = "FRAUD_SENTINEL",
                urgency = "CRITICAL",
                headline = "Test signal",
                detail = "Detail",
                createdAt = 0L,
            ),
        )
    }

    @Test
    fun execute_returnsSuccessWithAnomalies() = runTest {
        val result = skill.execute("{}")
        assertTrue(result is SkillResult.Success)
    }
}
