package com.biasharaai.ai

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ToolProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds LiteRT-LM [Engine] instances for a given on-disk `.litertlm` path.
 *
 * Phase 6 X1: extracted from the former monolithic [GemmaService] so [ActiveModelStore] and
 * future multi-model flows can share construction logic (X2 will add caching / swap).
 */
@Singleton
@OptIn(ExperimentalApi::class)
class ModelLoader @Inject constructor() {

    companion object {
        private const val TAG = "ModelLoader"
    }

    /**
     * Creates and initializes an [Engine] for [modelPath]. Prefers GPU unless [InferenceRuntimeSpec]
     * forces CPU; falls back to CPU if GPU init fails.
     *
     * Caller owns lifecycle — must [Engine.close] when done.
     */
    fun buildEngine(
        modelPath: String,
        tier: CapabilityTier,
        cfg: InferenceUiConfig,
        forFunctionToolModel: Boolean = false,
    ): Engine {
        val spec = InferenceRuntimeSpec.resolve(tier, cfg, forFunctionToolModel)
        val backend: Backend = if (spec.userForcesCpu) Backend.CPU() else Backend.GPU()
        Log.d(
            TAG,
            "Building LiteRT-LM engine (tier=$tier, backend=$backend, maxTokens=${spec.engineMaxTokens})",
        )
        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            visionBackend = null,
            audioBackend = null,
            maxNumTokens = spec.engineMaxTokens,
        )
        return try {
            Engine(engineConfig).also { it.initialize() }
        } catch (ex: Exception) {
            Log.w(TAG, "GPU/preferred backend failed, falling back to CPU", ex)
            Engine(engineConfig.copy(backend = Backend.CPU())).also { it.initialize() }
        }
    }

    fun createConversation(
        engine: Engine,
        tier: CapabilityTier,
        cfg: InferenceUiConfig,
        systemInstruction: String,
        tools: List<ToolProvider> = emptyList(),
        automaticToolCalling: Boolean = true,
        enableConversationConstrainedDecoding: Boolean = false,
    ): Conversation {
        val spec = InferenceRuntimeSpec.resolve(tier, cfg)
        val sampler = SamplerConfig(
            topK = spec.sessionTopK,
            topP = spec.sessionTopP.toDouble(),
            temperature = spec.sessionTemperature.toDouble(),
        )
        return try {
            ExperimentalFlags.enableConversationConstrainedDecoding =
                enableConversationConstrainedDecoding
            engine.createConversation(
                ConversationConfig(
                    samplerConfig = sampler,
                    systemInstruction = Contents.of(systemInstruction),
                    tools = tools,
                    automaticToolCalling = automaticToolCalling,
                ),
            )
        } finally {
            ExperimentalFlags.enableConversationConstrainedDecoding = false
        }
    }
}
