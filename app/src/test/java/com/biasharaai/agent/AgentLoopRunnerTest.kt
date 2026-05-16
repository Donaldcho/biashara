package com.biasharaai.agent

import com.biasharaai.ai.ActiveModelStore
import com.biasharaai.ai.FunctionGemmaRouter
import com.biasharaai.skills.SkillToolFactory
import com.google.ai.edge.litertlm.ToolProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AgentLoopRunnerTest {

    private lateinit var activeModelStore: ActiveModelStore
    private lateinit var functionGemmaRouter: FunctionGemmaRouter
    private lateinit var skillToolFactory: SkillToolFactory
    private lateinit var runner: AgentLoopRunner

    @Before
    fun setUp() {
        activeModelStore = mockk()
        functionGemmaRouter = mockk()
        skillToolFactory = mockk()
        coEvery { functionGemmaRouter.routeForToolLoop() } returns FunctionGemmaRouter.ToolLoopRoute(
            modelId = "gemma-4-e2b-it",
            useFunctionFastPath = false,
            reason = "test",
        )
        coEvery { activeModelStore.isAvailable } returns true
        coEvery { skillToolFactory.buildToolProviders(any()) } returns emptyList()
        coEvery {
            activeModelStore.runAgentLoop(any(), any(), any(), any(), any())
        } returns AgentLoopResult(
            finalText = "Done",
            toolCalls = emptyList(),
            success = true,
        )
        runner = AgentLoopRunner(activeModelStore, functionGemmaRouter, skillToolFactory, AgentMutex())
    }

    @Test
    fun run_returnsStoreResult() = runTest {
        val result = runner.run("What were sales this week?")
        assertTrue(result.success)
        assertEquals("Done", result.finalText)
    }

    @Test
    fun runOrSendPrompt_fallsBackToSendPrompt() = runTest {
        coEvery {
            activeModelStore.runAgentLoop(any(), any(), any(), any(), any())
        } returns AgentLoopResult("", emptyList(), success = false, errorMessage = "no tools")
        coEvery { activeModelStore.isAvailable } returns true
        coEvery { activeModelStore.sendPrompt(any()) } returns "Legacy reply"

        val text = runner.runOrSendPrompt("user", "system", "legacy")
        assertEquals("Legacy reply", text)
    }
}
