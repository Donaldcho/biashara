package com.biasharaai.skills

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class BiasharaSkillOpenApiToolTest {

    @Test
    fun getToolDescriptionJsonString_includesSkillId() {
        val tool = BiasharaSkillOpenApiTool(
            SkillToolDefinition("ping", "Health check", """{"type":"object"}""", true),
            mockk(),
        )
        val json = tool.getToolDescriptionJsonString()
        assertTrue(json.contains("\"name\"") && json.contains("ping"))
    }

    @Test
    fun execute_delegatesToSkillExecutor() = runTest {
        val executor = mockk<SkillExecutor>()
        coEvery { executor.execute("ping", "{}") } returns SkillResult.successText("ok")
        val tool = BiasharaSkillOpenApiTool(
            SkillToolDefinition("ping", "Health check", """{"type":"object"}""", true),
            executor,
        )
        val out = tool.execute("{}")
        assertTrue(out.contains("ok"))
    }
}
