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
import java.io.File
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
        val modelBytes = File(modelPath).length()
        Log.i(
            TAG,
            "Building LiteRT-LM engine (tier=$tier, backend=$backend, " +
                "maxTokens=${spec.engineMaxTokens}, modelBytes=$modelBytes, path=$modelPath)",
        )
        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            visionBackend = null,
            audioBackend = null,
            maxNumTokens = spec.engineMaxTokens,
        )
        if (spec.userForcesCpu) {
            return createInitializedEngine(engineConfig)
        }
        return try {
            createInitializedEngine(engineConfig)
        } catch (ex: Throwable) {
            Log.w(TAG, "GPU backend failed, falling back to CPU", ex)
            createInitializedEngine(engineConfig.copy(backend = Backend.CPU()))
        }
    }

    private fun createInitializedEngine(config: EngineConfig): Engine {
        val startedAt = System.currentTimeMillis()
        val created = Engine(config)
        return try {
            created.initialize()
            Log.i(
                TAG,
                "LiteRT-LM engine initialized in ${System.currentTimeMillis() - startedAt}ms " +
                    "(backend=${config.backend}, maxTokens=${config.maxNumTokens})",
            )
            created
        } catch (t: Throwable) {
            Log.e(
                TAG,
                "LiteRT-LM engine initialization failed after " +
                    "${System.currentTimeMillis() - startedAt}ms (backend=${config.backend})",
                t,
            )
            try {
                created.close()
            } catch (_: Throwable) {
            }
            throw t
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
        val startedAt = System.currentTimeMillis()
        return try {
            ExperimentalFlags.enableConversationConstrainedDecoding =
                enableConversationConstrainedDecoding
            val conversation = engine.createConversation(
                ConversationConfig(
                    samplerConfig = sampler,
                    systemInstruction = Contents.of(systemInstruction),
                    tools = tools,
                    automaticToolCalling = automaticToolCalling,
                ),
            )
            Log.i(TAG, "LiteRT-LM conversation created in ${System.currentTimeMillis() - startedAt}ms")
            conversation
        } finally {
            ExperimentalFlags.enableConversationConstrainedDecoding = false
        }
    }
}
