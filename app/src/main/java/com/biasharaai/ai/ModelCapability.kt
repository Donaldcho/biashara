package com.biasharaai.ai

/**
 * Which on-device capability a call targets — used by [ActiveModelStore] for routing once
 * multiple models are registered (Phase 6 X2/X3). For now only [TEXT_GENERATION] is used.
 */
enum class ModelCapability {
    TEXT_GENERATION,
    FUNCTION_CALLING,
    VISION,
    AUDIO,
}
