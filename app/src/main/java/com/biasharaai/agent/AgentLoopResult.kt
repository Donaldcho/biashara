package com.biasharaai.agent

/** Outcome of [AgentLoopRunner.run]. */
data class AgentLoopResult(
    val finalText: String,
    val toolCalls: List<AgentToolCallRecord>,
    val success: Boolean,
    val errorMessage: String? = null,
)
