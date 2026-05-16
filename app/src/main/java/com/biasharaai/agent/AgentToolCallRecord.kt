package com.biasharaai.agent

/** One skill invocation performed during an [AgentLoopRunner] session. */
data class AgentToolCallRecord(
    val skillId: String,
    val argumentsJson: String,
    val success: Boolean,
    val outputJson: String,
)
