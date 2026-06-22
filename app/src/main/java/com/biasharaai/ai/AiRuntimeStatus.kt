package com.biasharaai.ai

data class AiRuntimeStatus(
    val state: AiRuntimeState,
    val detail: String? = null,
    val retryAfterMs: Long = 0L,
) {
    val isReady: Boolean get() = state == AiRuntimeState.READY
}

enum class AiRuntimeState {
    READY,
    MODEL_NOT_DOWNLOADED,
    DEVICE_UNSUPPORTED,
    RUNTIME_COOLING_DOWN,
}
