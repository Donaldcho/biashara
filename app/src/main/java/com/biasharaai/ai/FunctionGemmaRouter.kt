package com.biasharaai.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 6 X10 — Picks which on-device model runs tool/skill loops.
 *
 * When [FunctionGemmaRouter.FUNCTION_GEMMA_MODEL_ID] is downloaded, agent loops prefer it over
 * the large primary model if the primary lacks [ModelCapability.FUNCTION_CALLING] or benchmarks
 * show the small model is faster (latency routing).
 */
@Singleton
class FunctionGemmaRouter @Inject constructor(
    private val modelRegistry: ModelRegistry,
) {

    data class ToolLoopRoute(
        val modelId: String,
        /** True when a dedicated FUNCTION_CALLING model is used instead of the primary chat model. */
        val useFunctionFastPath: Boolean,
        val reason: String,
    )

    suspend fun routeForToolLoop(): ToolLoopRoute = withContext(Dispatchers.IO) {
        val primaryId = modelRegistry.primaryModelId()
        val functionId = modelRegistry.resolveModelForCapability(ModelCapability.FUNCTION_CALLING)

        if (functionId == null) {
            return@withContext ToolLoopRoute(primaryId, useFunctionFastPath = false, reason = "no_function_model")
        }

        if (functionId == primaryId) {
            return@withContext ToolLoopRoute(
                primaryId,
                useFunctionFastPath = false,
                reason = "primary_is_function_model",
            )
        }

        val primaryCaps = modelRegistry.capabilitiesForModel(primaryId)
        val fnTps = modelRegistry.bestTokensPerSec(functionId)
        val primaryTps = modelRegistry.bestTokensPerSec(primaryId)

        if (!primaryCaps.contains(ModelCapability.FUNCTION_CALLING)) {
            return@withContext ToolLoopRoute(
                functionId,
                useFunctionFastPath = true,
                reason = "primary_lacks_function_calling",
            )
        }

        val fasterFunction = when {
            fnTps != null && primaryTps != null -> fnTps >= primaryTps * FAST_PATH_MIN_SPEEDUP
            fnTps != null && primaryTps == null -> true
            else -> false
        }
        if (fasterFunction) {
            return@withContext ToolLoopRoute(
                functionId,
                useFunctionFastPath = true,
                reason = "latency_fast_path",
            )
        }

        ToolLoopRoute(primaryId, useFunctionFastPath = false, reason = "primary_preferred")
    }

    suspend fun isFunctionModelReady(): Boolean = withContext(Dispatchers.IO) {
        modelRegistry.isDownloaded(FUNCTION_GEMMA_MODEL_ID)
    }

    companion object {
        const val FUNCTION_GEMMA_MODEL_ID = "functiongemma-270m-it"

        /** Function model must be at least this much faster (benchmark t/s) to win routing. */
        private const val FAST_PATH_MIN_SPEEDUP = 1.15f
    }
}
