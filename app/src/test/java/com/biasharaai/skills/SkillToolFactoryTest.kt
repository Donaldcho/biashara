package com.biasharaai.skills

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SkillToolFactoryTest {

    @Test
    fun buildToolProviders_onlyImplementedSkills() = runTest {
        val executor = mockk<SkillExecutor>()
        coEvery { executor.toolDefinitionsForLlm() } returns listOf(
            SkillToolDefinition("ping", "Ping", "{}", implemented = true),
            SkillToolDefinition("future", "Future", "{}", implemented = false),
        )
        val factory = SkillToolFactory(executor)
        assertEquals(1, factory.buildToolProviders().size)
    }
}
