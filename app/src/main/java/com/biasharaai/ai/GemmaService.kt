package com.biasharaai.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Singleton service wrapping the on-device LLM runtime.
 *
 * **Migrated from `com.google.mediapipe:tasks-genai` to `com.google.ai.edge.litertlm`** —
 * the same runtime Google AI Edge Gallery uses. Two reasons for the swap:
 *
 * 1. Our downloaded model is `.litertlm` format which `tasks-genai:0.10.33` can't load
 *    natively, leading to control-token leaks, hallucinated follow-up turns, and decoding
 *    loops.
 * 2. LiteRT-LM applies the model's chat template internally. We pass plain user text via
 *    [Content.Text]; the runtime injects `<start_of_turn>user`/`<end_of_turn>` itself and
 *    stops cleanly on the real EOS token.
 *
 * Lifecycle:
 * - [warmUp] eagerly builds the [Engine] + [Conversation] in the background so the first
 *   chat message doesn't pay the cold-start.
 * - [generateStreaming] sends a single user turn and streams the model's response.
 * - [resetSession] drops the conversation history (KV cache reset) without rebuilding the
 *   engine.
 * - [resetEngine] / [close] tear down the engine when settings change or the app shuts down.
 */
@Singleton
@OptIn(ExperimentalApi::class)
class GemmaService @Inject constructor(
    private val context: Context,
    private val modelDownloadManager: ModelDownloadManager,
    private val inferenceSettingsStore: InferenceSettingsStore,
) {
    companion object {
        private const val TAG = "GemmaService"

        /** Time we wait for engine / conversation initialization. */
        private const val INIT_TIMEOUT_MS = 180_000L

        /**
         * System instruction. LiteRT-LM bakes this into the conversation's first turn so it
         * persists across calls without us re-emitting it.
         */
        private const val SYSTEM_PROMPT =
            "You are Biashara AI, a helpful, friendly assistant for small business owners in " +
                "Africa. Reply in the language the user writes. Be concise (1-3 sentences), " +
                "direct, and actionable. When the user supplies business data with their " +
                "question, treat it as fact and use it to answer. Do not invent follow-up " +
                "questions, hypothetical conversations, or extra turns. Stop after one answer."
    }

    /** Single-thread executor so engine state changes don't race. */
    private val inferenceExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "BiasharaGemma").apply { isDaemon = true }
    }

    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null

    /** Serializes [generateStreaming] calls so two coroutines can't race on the engine. */
    private val generationMutex = Mutex()

    private fun effectiveTier(): CapabilityTier =
        DeviceCapabilityChecker.evaluate(
            context,
            modelPresentOnDisk = modelDownloadManager.isModelDownloaded,
        ).tier

    val isAvailable: Boolean
        get() = modelDownloadManager.isModelDownloaded && effectiveTier() != CapabilityTier.RULES_BASED

    // ── Engine / conversation lifecycle (must run on inferenceExecutor) ──

    private fun ensureEngineOnGateThread(): Engine {
        engine?.let { return it }
        check(isAvailable) {
            "GemmaService is not available (tier=${effectiveTier()}, " +
                "modelExists=${modelDownloadManager.isModelDownloaded})"
        }

        val tier = effectiveTier()
        val cfg = inferenceSettingsStore.load()
        val spec = InferenceRuntimeSpec.resolve(tier, cfg)
        val backend: Backend = if (spec.userForcesCpu) Backend.CPU() else Backend.GPU()

        Log.d(
            TAG,
            "Initialising LiteRT-LM engine (tier=$tier, backend=$backend, " +
                "maxTokens=${spec.engineMaxTokens})",
        )

        val engineConfig = EngineConfig(
            modelPath = modelDownloadManager.modelFilePath.absolutePath,
            backend = backend,
            visionBackend = null,
            audioBackend = null,
            maxNumTokens = spec.engineMaxTokens,
        )

        val e = try {
            Engine(engineConfig).also { it.initialize() }
        } catch (ex: Exception) {
            Log.w(TAG, "GPU/preferred backend failed, falling back to CPU", ex)
            // Try CPU as a safety net for devices where GPU init fails.
            Engine(engineConfig.copy(backend = Backend.CPU())).also { it.initialize() }
        }
        engine = e
        return e
    }

    private fun ensureConversationOnGateThread(): Conversation {
        conversation?.let { return it }
        val e = ensureEngineOnGateThread()
        val cfg = inferenceSettingsStore.load()
        val spec = InferenceRuntimeSpec.resolve(effectiveTier(), cfg)
        val sampler = SamplerConfig(
            topK = spec.sessionTopK,
            topP = spec.sessionTopP.toDouble(),
            temperature = spec.sessionTemperature.toDouble(),
        )
        val convo = e.createConversation(
            ConversationConfig(
                samplerConfig = sampler,
                systemInstruction = Contents.of(SYSTEM_PROMPT),
            ),
        )
        conversation = convo
        return convo
    }

    // ── Streaming generation ─────────────────────────────────────────────

    /**
     * Send one user turn and stream the model's response. [onPartial] is called once per
     * incoming chunk with the **delta** (not the cumulative text); the final call has
     * `done = true`.
     *
     * The runtime stops automatically when the model emits its real end-of-turn token, so
     * the caller doesn't need to scan for `<end_of_turn>` etc. — that's all handled
     * internally by LiteRT-LM.
     */
    suspend fun generateStreaming(
        prompt: String,
        onPartial: (delta: String, done: Boolean) -> Unit,
    ) = withContext(Dispatchers.IO) {
        generationMutex.withLock {
            val convo = inferenceExecutor.submit(Callable { ensureConversationOnGateThread() })
                .get(INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)

            suspendCancellableCoroutine<Unit> { cont ->
                val callback = object : MessageCallback {
                    override fun onMessage(message: Message) {
                        val delta = message.toString()
                        // LiteRT-LM emits control frames prefixed with `<ctrl` that we don't
                        // want surfaced to the UI (mirrors Gallery's LlmChatViewModel filter).
                        if (delta.startsWith("<ctrl")) return
                        try {
                            onPartial(delta, false)
                        } catch (t: Throwable) {
                            Log.w(TAG, "onPartial threw", t)
                        }
                    }

                    override fun onDone() {
                        try { onPartial("", true) } catch (_: Throwable) {}
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(throwable: Throwable) {
                        if (throwable is CancellationException) {
                            Log.i(TAG, "Inference cancelled")
                            if (cont.isActive) cont.resume(Unit)
                        } else {
                            Log.e(TAG, "Inference error", throwable)
                            if (cont.isActive) cont.resumeWithException(throwable)
                        }
                    }
                }

                cont.invokeOnCancellation {
                    try { convo.cancelProcess() } catch (_: Throwable) {}
                }

                try {
                    inferenceExecutor.submit {
                        try {
                            convo.sendMessageAsync(
                                Contents.of(Content.Text(prompt)),
                                callback,
                                emptyMap<String, String>(),
                            )
                        } catch (e: Throwable) {
                            if (cont.isActive) cont.resumeWithException(e)
                        }
                    }
                } catch (e: Throwable) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }
        }
    }

    /**
     * Convenience non-streaming variant; consumes [generateStreaming] internally.
     */
    suspend fun generateResponse(prompt: String): String {
        val sb = StringBuilder()
        generateStreaming(prompt) { delta, _ -> sb.append(delta) }
        return sb.toString()
    }

    /**
     * Cancel any in-flight generation. Safe to call from any thread; no-op if nothing is
     * running. LiteRT-LM's `cancelProcess` reports completion via the `MessageCallback` so
     * the suspended coroutine resumes cleanly.
     */
    fun cancelGeneration() {
        try { conversation?.cancelProcess() } catch (_: Throwable) {}
    }

    /**
     * Fire-and-forget background pre-load of the engine + conversation. Idempotent.
     */
    fun warmUp() {
        if (!isAvailable) return
        if (conversation != null) return
        try {
            inferenceExecutor.submit {
                if (conversation != null) return@submit
                try {
                    val t0 = System.currentTimeMillis()
                    ensureConversationOnGateThread()
                    Log.d(TAG, "Engine + conversation warmed up in ${System.currentTimeMillis() - t0}ms")
                } catch (e: Throwable) {
                    Log.w(TAG, "warmUp failed", e)
                }
            }
        } catch (_: Throwable) {
            // executor rejected (e.g. shutdown) — nothing to do
        }
    }

    /**
     * Drop just the conversation (history / KV cache) so the next message starts fresh.
     * Engine stays loaded.
     */
    fun resetSession() {
        try {
            inferenceExecutor.submit {
                try { conversation?.close() } catch (_: Throwable) {}
                conversation = null
                Log.d(TAG, "Conversation reset (new chat)")
            }.get(30, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "resetSession interrupted", e)
            conversation = null
        }
    }

    /**
     * Drop both the conversation and the engine. Used when inference settings change so the
     * next call rebuilds with the new params.
     */
    fun resetEngine() {
        try {
            inferenceExecutor.submit {
                try { conversation?.close() } catch (_: Throwable) {}
                conversation = null
                try { engine?.close() } catch (_: Throwable) {}
                engine = null
                Log.d(TAG, "Engine + conversation reset (settings changed)")
            }.get(60, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "resetEngine interrupted", e)
            conversation = null
            engine = null
        }
    }

    fun close() {
        try {
            inferenceExecutor.submit {
                try { conversation?.close() } catch (_: Throwable) {}
                conversation = null
                try { engine?.close() } catch (_: Throwable) {}
                engine = null
                Log.d(TAG, "GemmaService closed")
            }.get(30, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "close() could not run cleanly", e)
            conversation = null
            engine = null
        }
    }
}
