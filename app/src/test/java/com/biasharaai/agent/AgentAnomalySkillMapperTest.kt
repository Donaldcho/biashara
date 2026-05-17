package com.biasharaai.agent

import com.biasharaai.skills.SkillResult
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentAnomalySkillMapperTest {

  private val mapper = AgentAnomalySkillMapper()

  @Test
  fun actionsFromOutputJson_parsesAnomalies() {
    val json = """
      {"count":1,"anomalies":[{"headline":"Test","detail":"Detail","relatedTransactionId":42}]}
    """.trimIndent()
    val actions = mapper.actionsFromOutputJson(json, nowMillis = 1000L)
    assertEquals(1, actions.size)
    assertEquals("Test", actions[0].headline)
    assertEquals(42L, actions[0].relatedEntityId)
  }

  @Test
  fun actionsFromSkillResult_failureReturnsEmpty() {
    val actions = mapper.actionsFromSkillResult(
      SkillResult.Failure("X", "y"),
      nowMillis = 0L,
    )
    assertEquals(0, actions.size)
  }
}
