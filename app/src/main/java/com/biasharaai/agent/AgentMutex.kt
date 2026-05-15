package com.biasharaai.agent

import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single [Mutex] for all code paths that call [com.biasharaai.ai.GemmaService] from background agents.
 * LiteRT-LM must not run concurrent inference — acquire with `mutex.withLock { ... }`.
 */
@Singleton
class AgentMutex @Inject constructor() {
    val mutex = Mutex()
}
