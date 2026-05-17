package com.biasharaai.agent

import com.biasharaai.ai.ActiveModelStore
import com.biasharaai.ai.FunctionGemmaRouter
import com.biasharaai.skills.SkillToolFactory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * X12 — Phase 6 agent-loop acceptance criteria.
 *
 * Acceptance conditions:
 *  AC-1  Successful loop result propagates finalText to caller.
 *  AC-2  Failed loop result propagates errorMessage without throwing.
 *  AC-3  FunctionGemma fast-path prepends the function-calling prefix to the system instruction.
 *  AC-4  The route's modelId is forwarded to [ActiveModelStore.runAgentLoop] as toolModelId.
 *  AC-5  [runOrSendPrompt] returns empty string when the store is unavailable.
 *  AC-6  [runOrSendPrompt] falls back to [ActiveModelStore.sendPrompt] when the loop fails.
 *  AC-7  [runOrSendPrompt] returns the loop result directly when the loop succeeds.
 *  AC-8  [runOrSendPrompt] returns empty string when both loop and sendPrompt fail.
 */
class Phase6AcceptanceCriteriaTest {

    private lateinit var activeModelStore: ActiveModelStore
    private lateinit var functionGemmaRouter: FunctionGemmaRouter
    private lateinit var skillToolFactory: SkillToolFactory
    private lateinit var runner: AgentLoopRunner

    @Before
    fun setUp() {
        activeModelStore = mockk()
        functionGemmaRouter = mockk()
        skillToolFactory = mockk()
        // Happy-path defaults
        coEvery { functionGemmaRouter.routeForToolLoop() } returns FunctionGemmaRouter.ToolLoopRoute(
            modelId = "gemma-4-e2b-it",
            useFunctionFastPath = false,
            reason = "test",
        )
        every { activeModelStore.isAvailable } returns true
        coEvery { skillToolFactory.buildToolProviders(any()) } returns emptyList()
        coEvery {
            activeModelStore.runAgentLoop(any(), any(), any(), any(), any())
        } returns AgentLoopResult(finalText = "Done", toolCalls = emptyList(), success = true)
        runner = AgentLoopRunner(activeModelStore, functionGemmaRouter, skillToolFactory, AgentMutex())
    }

    // ── AC-1: Successful result propagated ────────────────────────────────────

    @Test
    fun ac1_run_successResult_propagatesFinalText() = runTest {
        val result = runner.run("What are today's sales?")
        assertTrue(result.success)
        assertEquals("Done", result.finalText)
    }

    // ── AC-2: Failed result propagated without throw ───────────────────────────

    @Test
    fun ac2_run_failedResult_propagatesErrorMessageWithoutThrowing() = runTest {
        coEvery {
            activeModelStore.runAgentLoop(any(), any(), any(), any(), any())
        } returns AgentLoopResult(
            finalText = "",
            toolCalls = emptyList(),
            success = false,
            errorMessage = "Model not downloaded.",
        )

        val result = runner.run("Query")

        assertFalse(result.success)
        assertEquals("Model not downloaded.", result.errorMessage)
    }

    // ── AC-3: FunctionGemma fast-path prepends prefix ─────────────────────────

    @Test
    fun ac3_run_functionFastPath_prefixesSystemInstruction() = runTest {
        coEvery { functionGemmaRouter.routeForToolLoop() } returns FunctionGemmaRouter.ToolLoopRoute(
            modelId = FunctionGemmaRouter.FUNCTION_GEMMA_MODEL_ID,
            useFunctionFastPath = true,
            reason = "primary_lacks_function_calling",
        )
        val capturedInstruction = slot<String>()
        coEvery {
            activeModelStore.runAgentLoop(any(), capture(capturedInstruction), any(), any(), any())
        } returns AgentLoopResult(finalText = "OK", toolCalls = emptyList(), success = true)

        runner.run("Query", systemInstruction = "base instruction")

        assertTrue(
            "Fast-path instruction must contain function-calling prefix",
            capturedInstruction.captured.contains("function calling"),
        )
        assertTrue(
            "Fast-path instruction must also contain the base instruction",
            capturedInstruction.captured.contains("base instruction"),
        )
    }

    @Test
    fun ac3_run_noFastPath_usesBaseInstructionUnchanged() = runTest {
        val capturedInstruction = slot<String>()
        coEvery {
            activeModelStore.runAgentLoop(any(), capture(capturedInstruction), any(), any(), any())
        } returns AgentLoopResult(finalText = "OK", toolCalls = emptyList(), success = true)

        runner.run("Query", systemInstruction = "base instruction")

        assertEquals("base instruction", capturedInstruction.captured)
    }

    // ── AC-4: Route modelId forwarded as toolModelId ───────────────────────────

    @Test
    fun ac4_run_passesRouteModelIdAsToolModelId() = runTest {
        coEvery { functionGemmaRouter.routeForToolLoop() } returns FunctionGemmaRouter.ToolLoopRoute(
            modelId = FunctionGemmaRouter.FUNCTION_GEMMA_MODEL_ID,
            useFunctionFastPath = true,
            reason = "latency_fast_path",
        )
        var capturedModelId: String? = null
        coEvery {
            activeModelStore.runAgentLoop(any(), any(), any(), any(), any())
        } answers {
            @Suppress("UNCHECKED_CAST")
            capturedModelId = arg<Any?>(4) as? String
            AgentLoopResult(finalText = "OK", toolCalls = emptyList(), success = true)
        }

        runner.run("Query")

        assertEquals(FunctionGemmaRouter.FUNCTION_GEMMA_MODEL_ID, capturedModelId)
    }

    // ── AC-5: runOrSendPrompt returns empty when store unavailable ─────────────

    @Test
    fun ac5_runOrSendPrompt_storeUnavailable_returnsEmpty() = runTest {
        every { activeModelStore.isAvailable } returns false

        val result = runner.runOrSendPrompt("user message", "system", "legacy prompt")

        assertEquals("", result)
    }

    // ── AC-6: runOrSendPrompt falls back to sendPrompt on loop failure ─────────

    @Test
    fun ac6_runOrSendPrompt_loopFails_fallsBackToSendPrompt() = runTest {
        coEvery {
            activeModelStore.runAgentLoop(any(), any(), any(), any(), any())
        } returns AgentLoopResult(
            finalText = "",
            toolCalls = emptyList(),
            success = false,
            errorMessage = "no tools",
        )
        coEvery { activeModelStore.sendPrompt(any()) } returns "Legacy answer"

        val result = runner.runOrSendPrompt("user", "system", "legacy prompt")

        assertEquals("Legacy answer", result)
    }

    // ── AC-7: runOrSendPrompt returns loop result when loop succeeds ───────────

    @Test
    fun ac7_runOrSendPrompt_loopSucceeds_returnsLoopFinalText() = runTest {
        coEvery {
            activeModelStore.runAgentLoop(any(), any(), any(), any(), any())
        } returns AgentLoopResult(
            finalText = "Loop answer from tools",
            toolCalls = emptyList(),
            success = true,
        )

        val result = runner.runOrSendPrompt("user", "system", "legacy prompt")

        assertEquals("Loop answer from tools", result)
    }

    // ── AC-8: runOrSendPrompt returns empty when both paths fail ──────────────

    @Test
    fun ac8_runOrSendPrompt_bothPathsFail_returnsEmpty() = runTest {
        coEvery {
            activeModelStore.runAgentLoop(any(), any(), any(), any(), any())
        } returns AgentLoopResult(finalText = "", toolCalls = emptyList(), success = false)
        coEvery { activeModelStore.sendPrompt(any()) } throws RuntimeException("inference error")

        val result = runner.runOrSendPrompt("user", "system", "legacy")

        assertEquals("", result)
    }

    // ── Tool call records propagated ───────────────────────────────────────────

    @Test
    fun run_toolCallsRecorded_propagatedInResult() = runTest {
        val toolRecord = AgentToolCallRecord(
            skillId = "query_sales",
            argumentsJson = "{}",
            success = true,
            outputJson = "{\"revenue\":1000}",
        )
        coEvery {
            activeModelStore.runAgentLoop(any(), any(), any(), any(), any())
        } returns AgentLoopResult(
            finalText = "Revenue is 1000",
            toolCalls = listOf(toolRecord),
            success = true,
        )

        val result = runner.run("What is revenue?")

        assertEquals(1, result.toolCalls.size)
        val call = result.toolCalls.first()
        assertEquals("query_sales", call.skillId)
        assertTrue(call.outputJson.isNotBlank())
    }
}
