package com.biasharaai.ai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * @deprecated Phase 6 — inject [ActiveModelStore] instead. This class forwards every call to
 * [ActiveModelStore] so existing screens, workers, and tests keep compiling during migration.
 */
@Deprecated(
    message = "Use ActiveModelStore (Phase 6). GemmaService is a temporary façade.",
    replaceWith = ReplaceWith("ActiveModelStore", "com.biasharaai.ai.ActiveModelStore"),
)
@Singleton
class GemmaService @Inject constructor(
    private val activeModelStore: ActiveModelStore,
) {

    val isAvailable: Boolean
        get() = activeModelStore.isAvailable

    suspend fun generateStreaming(
        prompt: String,
        onPartial: (delta: String, done: Boolean) -> Unit,
    ) = activeModelStore.generateStreaming(prompt, onPartial)

    suspend fun generateResponse(prompt: String): String =
        activeModelStore.generateResponse(prompt)

    fun cancelGeneration() = activeModelStore.cancelGeneration()

    fun warmUp() = activeModelStore.warmUp()

    fun resetSession() = activeModelStore.resetSession()

    fun resetEngine() = activeModelStore.resetEngine()

    fun close() = activeModelStore.close()
}
