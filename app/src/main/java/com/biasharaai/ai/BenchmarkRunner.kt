package com.biasharaai.ai

import android.util.Log
import com.biasharaai.data.local.db.ModelDescriptorDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

/**
 * Phase 6 X3 — runs a short warm-up generation and measures tokens/sec for a given model.
 * Results are persisted to [ModelDescriptorDao] so [ModelSettingsFragment] can display them.
 */
@Singleton
class BenchmarkRunner @Inject constructor(
    private val activeModelStore: ActiveModelStore,
    private val modelDescriptorDao: ModelDescriptorDao,
) {
    companion object {
        private const val TAG = "BenchmarkRunner"

        private val BENCHMARK_PROMPT =
            "List five ways a small shop owner in Nairobi can increase daily sales. " +
                "Be specific and brief."
    }

    /**
     * Runs generation for [modelId] and persists measured tokens/sec.
     * Only the primary model (already loaded in [ActiveModelStore]) can be benchmarked in-process;
     * multi-model support will extend this in X9+.
     *
     * @return measured tokens/sec, or null on failure.
     */
    suspend fun runBenchmark(modelId: String): Float? = withContext(Dispatchers.IO) {
        if (!activeModelStore.isAvailable) {
            Log.w(TAG, "Benchmark skipped — model not available")
            return@withContext null
        }
        return@withContext try {
            // Warm the session first so we measure steady-state throughput.
            activeModelStore.resetSession()
            var outputText = ""
            val elapsedMs = measureTimeMillis {
                outputText = activeModelStore.sendPrompt(BENCHMARK_PROMPT)
            }
            val tokens = estimateTokenCount(outputText)
            val tokensPerSec = if (elapsedMs > 0) tokens * 1000f / elapsedMs else 0f
            Log.d(TAG, "Benchmark modelId=$modelId: ${tokens}t / ${elapsedMs}ms = $tokensPerSec t/s")

            val isGpu = !activeModelStore.isForcedCpu()
            if (isGpu) {
                modelDescriptorDao.updateBenchmarkGpu(modelId, tokensPerSec)
            } else {
                modelDescriptorDao.updateBenchmarkCpu(modelId, tokensPerSec)
            }
            activeModelStore.resetSession()
            tokensPerSec
        } catch (e: Exception) {
            Log.e(TAG, "Benchmark failed for $modelId", e)
            null
        }
    }

    /** Rough token count: ~4 chars per token for English text. */
    private fun estimateTokenCount(text: String): Int = (text.length / 4).coerceAtLeast(1)
}
