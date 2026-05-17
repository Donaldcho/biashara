package com.biasharaai.agent

import com.biasharaai.ai.ActiveModelStore
import com.biasharaai.ai.FunctionGemmaRouter
import com.biasharaai.skills.SkillToolFactory
import android.util.Log
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 6 X8 — multi-step agent loop: LiteRT-LM conversation with registered Biashara skills.
 *
 * Acquires [AgentMutex] and delegates inference + automatic tool execution to [ActiveModelStore].
 * Workers will migrate here in X9.
 */
@Singleton
class AgentLoopRunner @Inject constructor(
    private val activeModelStore: ActiveModelStore,
    private val functionGemmaRouter: FunctionGemmaRouter,
    private val skillToolFactory: SkillToolFactory,
    private val agentMutex: AgentMutex,
) {

    suspend fun run(
        userMessage: String,
        systemInstruction: String = DEFAULT_AGENT_SYSTEM,
    ): AgentLoopResult = agentMutex.mutex.withLock {
        val route = functionGemmaRouter.routeForToolLoop()
        val toolLog = mutableListOf<AgentToolCallRecord>()
        val tools = skillToolFactory.buildToolProviders(toolLog)
        activeModelStore.runAgentLoop(
            userMessage = userMessage,
            systemInstruction = systemInstructionForRoute(systemInstruction, route),
            toolProviders = tools,
            toolCallsExecuted = toolLog,
            toolModelId = route.modelId,
        )
    }

    /**
     * X9 — Tries tool-enabled [run]; on failure or empty output falls back to [ActiveModelStore.sendPrompt]
     * (text-only models such as Gemma 4 E2B).
     */
    suspend fun runOrSendPrompt(
        userMessage: String,
        systemInstruction: String,
        legacyPrompt: String = userMessage,
    ): String = agentMutex.mutex.withLock {
        if (!activeModelStore.isAvailable) return@withLock ""
        val route = functionGemmaRouter.routeForToolLoop()
        val toolLog = mutableListOf<AgentToolCallRecord>()
        val tools = skillToolFactory.buildToolProviders(toolLog)
        val loop = activeModelStore.runAgentLoop(
            userMessage = userMessage,
            systemInstruction = systemInstructionForRoute(systemInstruction, route),
            toolProviders = tools,
            toolCallsExecuted = toolLog,
            toolModelId = route.modelId,
        )
        if (loop.success && loop.finalText.isNotBlank()) {
            return@withLock loop.finalText
        }
        runCatching { activeModelStore.sendPrompt(legacyPrompt).trim() }
            .getOrElse { "" }
    }

    companion object {
        const val DEFAULT_AGENT_SYSTEM =
            "You are Biashara AI, an assistant for a small shop owner in Africa. " +
                "You have tools to read and update shop data (sales, inventory, customers, receipts). " +
                "Call a tool when you need factual data or an approved action. " +
                "After tools return, use their JSON results — do not invent figures. " +
                "Reply in the user's language. Be concise and actionable. Stop after one final answer."

        private const val FUNCTION_GEMMA_PREFIX =
            "You are a model that can do function calling with the following functions. " +
                "Call the correct tool when you need shop data. " +
                "After tools return, summarize using their JSON only — do not invent figures. "
    }

    private fun systemInstructionForRoute(
        base: String,
        route: FunctionGemmaRouter.ToolLoopRoute,
    ): String =
        if (route.useFunctionFastPath) {
            FUNCTION_GEMMA_PREFIX + base
        } else {
            base
        }
}
