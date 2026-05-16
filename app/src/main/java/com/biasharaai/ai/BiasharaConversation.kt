package com.biasharaai.ai

import com.google.ai.edge.litertlm.Conversation

/**
 * Thin façade over LiteRT-LM [Conversation] for multi-turn tool use / skills (Phase 6 X4+).
 * Phase 6 X1 introduces the type; agent loops will hold a session here instead of raw LiteRT types.
 */
class BiasharaConversation internal constructor(
    val liteRt: Conversation,
) {
    fun close() {
        try {
            liteRt.close()
        } catch (_: Throwable) {
        }
    }
}
