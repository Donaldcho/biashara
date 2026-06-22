package com.biasharaai.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.ToolProvider
import com.biasharaai.agent.AgentLoopResult
import com.biasharaai.agent.AgentToolCallRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException as FutureCancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Phase 6 — canonical on-device LLM runtime for **new** code paths.
 *
 * Owns LiteRT-LM [Engine] + [Conversation] for the **primary** downloaded model (same behaviour
 * as pre–Phase 6 [GemmaService]). [GemmaService] is now a deprecated façade over this class so
 * existing UI and tests keep working until callers migrate.
 */
@Singleton
@OptIn(ExperimentalApi::class)
class ActiveModelStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDownloadManager: ModelDownloadManager,
    private val modelRegistry: ModelRegistry,
    private val inferenceSettingsStore: InferenceSettingsStore,
    private val modelLoader: ModelLoader,
) {
    companion object {
        private const val TAG = "ActiveModelStore"

        private const val INIT_TIMEOUT_MS = 30_000L
        private const val GENERATION_TIMEOUT_MS = 90_000L
        private const val RUNTIME_FAILURE_COOLDOWN_MS = 30_000L

        private const val SYSTEM_PROMPT =
            "You are Biashara AI, a helpful, friendly assistant for small business owners in " +
                "Africa. Reply in the language the user writes. Be concise (1-3 sentences), " +
                "direct, and actionable. When the user supplies business data with their " +
                "question, treat it as fact and use it to answer. The user message may also " +
                "include saved owner notes or a short chat recap — use them for preferences and " +
                "continuity, but never override explicit database figures with guesses. " +
                "Do not invent follow-up questions, hypothetical conversations, or extra turns. " +
                "Stop after one answer."
    }

    private val inferenceExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "BiasharaActiveModel").apply { isDaemon = true }
    }

    @Volatile
    private var engine: Engine? = null

    @Volatile
    private var conversation: Conversation? = null

    @Volatile
    private var runtimeDisabledReason: String? = null

    @Volatile
    private var runtimeDisabledUntilMs: Long = 0L

    private val generationMutex = Mutex()

    private fun effectiveTier(): CapabilityTier =
        DeviceCapabilityChecker.evaluate(
            context,
            modelPresentOnDisk = hasUsablePrimaryModel(),
        ).tier

    val isAvailable: Boolean
        get() = runtimeStatus().isReady

    fun runtimeStatus(): AiRuntimeStatus {
        runtimeCooldownReasonOrNull()?.let { reason ->
            return AiRuntimeStatus(
                state = AiRuntimeState.RUNTIME_COOLING_DOWN,
                detail = reason,
                retryAfterMs = (runtimeDisabledUntilMs - System.currentTimeMillis()).coerceAtLeast(0L),
            )
        }
        if (!hasUsablePrimaryModel()) {
            return AiRuntimeStatus(AiRuntimeState.MODEL_NOT_DOWNLOADED)
        }
        if (effectiveTier() == CapabilityTier.RULES_BASED) {
            return AiRuntimeStatus(AiRuntimeState.DEVICE_UNSUPPORTED)
        }
        return AiRuntimeStatus(AiRuntimeState.READY)
    }

    /** Returns true if the current inference config forces CPU backend. */
    fun isForcedCpu(): Boolean = inferenceSettingsStore.load().preferCpu

    private fun hasUsablePrimaryModel(): Boolean = hasUsableModel(modelRegistry.primaryModelId())

    private fun hasUsableModel(modelId: String): Boolean {
        val file = modelRegistry.modelFile(modelId)
        if (!file.exists() || file.length() <= 0L) return false
        if (!modelRegistry.isDownloaded(modelId)) {
            val expectedBytes = modelRegistry.catalogue().models
                .firstOrNull { it.modelId == modelId }
                ?.sizeBytes
            val expectedLabel = expectedBytes?.toString() ?: "unknown"
            Log.w(
                TAG,
                "Model is incomplete: modelId=$modelId, bytes=${file.length()}, expected=$expectedLabel",
            )
            return false
        }
        return true
    }

    private fun ensureEngineOnGateThread(): Engine {
        engine?.let { return it }
        runtimeCooldownReasonOrNull()?.let { reason ->
            error("On-device AI is cooling down after runtime failure: $reason")
        }
        check(isAvailable) {
            "ActiveModelStore is not available (tier=${effectiveTier()}, " +
                "modelExists=${hasUsablePrimaryModel()})"
        }
        val tier = effectiveTier()
        val cfg = inferenceSettingsStore.load()
        val path = modelDownloadManager.modelFilePath.absolutePath
        val e = modelLoader.buildEngine(path, tier, cfg)
        engine = e
        return e
    }

    private fun ensureConversationOnGateThread(): Conversation {
        conversation?.let { return it }
        val e = ensureEngineOnGateThread()
        val tier = effectiveTier()
        val cfg = inferenceSettingsStore.load()
        val convo = modelLoader.createConversation(e, tier, cfg, systemPromptFor(cfg))
        conversation = convo
        return convo
    }

    private fun systemPromptFor(cfg: InferenceUiConfig): String {
        val answerOnly =
            "Return only the final user-facing answer. Do not include thought channels, " +
                "reasoning traces, or control tokens in the answer."
        val base = "$SYSTEM_PROMPT $answerOnly"
        return if (cfg.enableThinking) {
            "<|think|>\n$base"
        } else {
            base
        }
    }

    /**
     * Preferred entry for agents (Phase 6): one-shot text generation.
     * [ModelCapability] is reserved for multi-model routing in X2/X3; only [ModelCapability.TEXT_GENERATION] is active today.
     */
    suspend fun sendPrompt(
        prompt: String,
        capability: ModelCapability = ModelCapability.TEXT_GENERATION,
    ): String {
        @Suppress("UNUSED_VARIABLE")
        val _routing = capability // future: engineForCapability(capability)
        return generateResponse(prompt)
    }

    suspend fun generateStreaming(
        prompt: String,
        onPartial: (delta: String, done: Boolean) -> Unit,
    ) = withContext(Dispatchers.IO) {
        generationMutex.withLock {
            try {
                val conversationFuture = inferenceExecutor.submit(Callable { ensureConversationOnGateThread() })
                val convo = try {
                    conversationFuture.get(INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                } catch (t: Throwable) {
                    conversationFuture.cancel(true)
                    throw t
                }

                withTimeout(GENERATION_TIMEOUT_MS) {
                    suspendCancellableCoroutine<Unit> { cont ->
                        val callback = object : MessageCallback {
                            override fun onMessage(message: Message) {
                                val delta = extractText(message)
                                if (delta.isBlank() || delta.startsWith("<ctrl")) return
                                try {
                                    onPartial(delta, false)
                                } catch (t: Throwable) {
                                    Log.w(TAG, "onPartial threw", t)
                                }
                            }

                            override fun onDone() {
                                try {
                                    onPartial("", true)
                                } catch (_: Throwable) {
                                }
                                if (cont.isActive) cont.resume(Unit)
                            }

                            override fun onError(throwable: Throwable) {
                                if (throwable is CancellationException ||
                                    throwable is FutureCancellationException
                                ) {
                                    Log.i(TAG, "Inference cancelled")
                                    if (cont.isActive) cont.resume(Unit)
                                } else {
                                    Log.e(TAG, "Inference error", throwable)
                                    if (cont.isActive) cont.resumeWithException(throwable)
                                }
                            }
                        }

                        cont.invokeOnCancellation {
                            try {
                                convo.cancelProcess()
                            } catch (_: Throwable) {
                            }
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
            } catch (timeout: TimeoutCancellationException) {
                handleRuntimeFailure("generateStreaming timeout", timeout)
                throw ModelRuntimeException("On-device AI timed out; using local fallback.", timeout)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                handleRuntimeFailure("generateStreaming", t)
                throw ModelRuntimeException("On-device AI failed; using local fallback.", rootCause(t))
            }
        }
    }

    suspend fun generateResponse(prompt: String): String {
        val sb = StringBuilder()
        generateStreaming(prompt) { delta, _ -> sb.append(delta) }
        return GemmaOutputSanitizer.finalAnswer(sb.toString())
    }

    private fun handleRuntimeFailure(operation: String, throwable: Throwable) {
        val root = rootCause(throwable)
        if (root is CancellationException && root !is TimeoutCancellationException) return
        if (root is FutureCancellationException) return
        val reason = root.message?.take(240) ?: root.javaClass.simpleName
        runtimeDisabledReason = reason
        runtimeDisabledUntilMs = System.currentTimeMillis() + RUNTIME_FAILURE_COOLDOWN_MS
        Log.e(TAG, "$operation failed; cooling down on-device AI before retry", root)
        val staleConversation = conversation
        val staleEngine = engine
        conversation = null
        engine = null
        try {
            inferenceExecutor.execute {
                try {
                    staleConversation?.close()
                } catch (_: Throwable) {
                }
                try {
                    staleEngine?.close()
                } catch (_: Throwable) {
                }
            }
        } catch (closeError: Throwable) {
            Log.w(TAG, "Could not schedule failed runtime cleanup", closeError)
        }
    }

    private tailrec fun rootCause(throwable: Throwable): Throwable {
        val cause = throwable.cause ?: return throwable
        return if (throwable is java.util.concurrent.ExecutionException) {
            rootCause(cause)
        } else {
            throwable
        }
    }

    private fun isRuntimeCoolingDown(): Boolean = runtimeCooldownReasonOrNull() != null

    private fun runtimeCooldownReasonOrNull(): String? {
        val reason = runtimeDisabledReason ?: return null
        if (runtimeDisabledUntilMs > System.currentTimeMillis()) return reason
        runtimeDisabledReason = null
        runtimeDisabledUntilMs = 0L
        return null
    }

    private fun extractText(message: Message): String =
        message.contents.contents
            .mapNotNull { content -> (content as? Content.Text)?.text }
            .joinToString(separator = "")

    /**
     * X8 — Ephemeral tool-enabled conversation for agent workers.
     * Does not mutate the singleton chat [conversation]; opens and closes a dedicated session.
     */
    suspend fun runAgentLoop(
        userMessage: String,
        systemInstruction: String,
        toolProviders: List<ToolProvider>,
        toolCallsExecuted: List<AgentToolCallRecord>,
        toolModelId: String? = null,
    ): AgentLoopResult = withContext(Dispatchers.IO) {
        runtimeCooldownReasonOrNull()?.let { reason ->
            return@withContext AgentLoopResult(
                finalText = "",
                toolCalls = toolCallsExecuted,
                success = false,
                errorMessage = "On-device AI is temporarily disabled: $reason",
            )
        }
        if (effectiveTier() == CapabilityTier.RULES_BASED) {
            return@withContext AgentLoopResult(
                finalText = "",
                toolCalls = toolCallsExecuted,
                success = false,
                errorMessage = "Device tier does not support on-device models.",
            )
        }
        val loopModelId = toolModelId?.takeIf { it.isNotBlank() } ?: modelRegistry.primaryModelId()
        if (!hasUsableModel(loopModelId)) {
            return@withContext AgentLoopResult(
                finalText = "",
                toolCalls = toolCallsExecuted,
                success = false,
                errorMessage = "Model '$loopModelId' is not downloaded or is incomplete.",
            )
        }
        if (toolProviders.isEmpty()) {
            return@withContext AgentLoopResult(
                finalText = "",
                toolCalls = toolCallsExecuted,
                success = false,
                errorMessage = "No skills registered for tool calling.",
            )
        }

        val useEphemeralEngine = loopModelId != modelRegistry.primaryModelId()
        val useFunctionGemma =
            loopModelId == FunctionGemmaRouter.FUNCTION_GEMMA_MODEL_ID ||
                (
                    modelRegistry.capabilitiesForModel(loopModelId)
                        .contains(ModelCapability.FUNCTION_CALLING) &&
                        !modelRegistry.capabilitiesForModel(modelRegistry.primaryModelId())
                            .contains(ModelCapability.FUNCTION_CALLING)
                    )

        return@withContext generationMutex.withLock {
            try {
                val loopFuture = inferenceExecutor.submit(
                    Callable {
                        val tier = effectiveTier()
                        val cfg = inferenceSettingsStore.load()
                        val path = modelRegistry.modelFile(loopModelId).absolutePath
                        val loopEngine = if (useEphemeralEngine) {
                            Log.d(TAG, "Agent loop using ephemeral engine: $loopModelId")
                            modelLoader.buildEngine(
                                path,
                                tier,
                                cfg,
                                forFunctionToolModel = useFunctionGemma,
                            )
                        } else {
                            ensureEngineOnGateThread()
                        }
                        val agentConvo = modelLoader.createConversation(
                            engine = loopEngine,
                            tier = tier,
                            cfg = cfg,
                            systemInstruction = systemInstruction,
                            tools = toolProviders,
                            automaticToolCalling = true,
                            enableConversationConstrainedDecoding = useFunctionGemma,
                        )
                        try {
                            val sb = StringBuilder()
                            runBlocking {
                                suspendCancellableCoroutine { cont ->
                                    val callback = object : MessageCallback {
                                        override fun onMessage(message: Message) {
                                            val chunk = extractText(message)
                                            if (chunk.isNotBlank() && !chunk.startsWith("<ctrl")) {
                                                sb.append(chunk)
                                            }
                                        }

                                        override fun onDone() {
                                            if (cont.isActive) {
                                                cont.resume(
                                                    AgentLoopResult(
                                                        finalText = sb.toString().trim(),
                                                        toolCalls = toolCallsExecuted,
                                                        success = true,
                                                    ),
                                                )
                                            }
                                        }

                                        override fun onError(throwable: Throwable) {
                                            if (throwable is CancellationException ||
                                                throwable is FutureCancellationException
                                            ) {
                                                if (cont.isActive) {
                                                    cont.resume(
                                                        AgentLoopResult(
                                                            finalText = sb.toString().trim(),
                                                            toolCalls = toolCallsExecuted,
                                                            success = sb.isNotEmpty(),
                                                        ),
                                                    )
                                                }
                                            } else if (cont.isActive) {
                                                cont.resume(
                                                    AgentLoopResult(
                                                        finalText = sb.toString().trim(),
                                                        toolCalls = toolCallsExecuted,
                                                        success = false,
                                                        errorMessage = throwable.message
                                                            ?: "Agent loop failed",
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                    cont.invokeOnCancellation {
                                        try {
                                            agentConvo.cancelProcess()
                                        } catch (_: Throwable) {
                                        }
                                    }
                                    try {
                                        agentConvo.sendMessageAsync(
                                            Contents.of(Content.Text(userMessage)),
                                            callback,
                                            emptyMap(),
                                        )
                                    } catch (e: Throwable) {
                                        if (cont.isActive) {
                                            cont.resume(
                                                AgentLoopResult(
                                                    finalText = "",
                                                    toolCalls = toolCallsExecuted,
                                                    success = false,
                                                    errorMessage = e.message,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        } finally {
                            try {
                                agentConvo.close()
                            } catch (_: Throwable) {
                            }
                            if (useEphemeralEngine) {
                                try {
                                    loopEngine.close()
                                } catch (_: Throwable) {
                                }
                            }
                        }
                    },
                )
                try {
                    loopFuture.get(INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                } catch (t: Throwable) {
                    loopFuture.cancel(true)
                    throw t
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                handleRuntimeFailure("runAgentLoop", t)
                AgentLoopResult(
                    finalText = "",
                    toolCalls = toolCallsExecuted,
                    success = false,
                    errorMessage = rootCause(t).message ?: "Agent loop failed",
                )
            }
        }
    }

    fun cancelGeneration() {
        try {
            conversation?.cancelProcess()
        } catch (_: Throwable) {
        }
    }

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
                    handleRuntimeFailure("warmUp", e)
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun resetSession() {
        val staleConversation = conversation
        conversation = null
        try {
            inferenceExecutor.execute {
                try {
                    staleConversation?.close()
                } catch (_: Throwable) {
                }
                Log.d(TAG, "Conversation reset (new chat)")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "resetSession could not schedule cleanup", e)
        }
    }

    fun resetEngine() {
        runtimeDisabledReason = null
        runtimeDisabledUntilMs = 0L
        val staleConversation = conversation
        val staleEngine = engine
        conversation = null
        engine = null
        try {
            inferenceExecutor.execute {
                try {
                    staleConversation?.close()
                } catch (_: Throwable) {
                }
                try {
                    staleEngine?.close()
                } catch (_: Throwable) {
                }
                Log.d(TAG, "Engine + conversation reset (settings changed)")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "resetEngine could not schedule cleanup", e)
        }
    }

    fun close() {
        runtimeDisabledReason = null
        runtimeDisabledUntilMs = 0L
        val staleConversation = conversation
        val staleEngine = engine
        conversation = null
        engine = null
        try {
            inferenceExecutor.execute {
                try {
                    staleConversation?.close()
                } catch (_: Throwable) {
                }
                try {
                    staleEngine?.close()
                } catch (_: Throwable) {
                }
                Log.d(TAG, "ActiveModelStore closed")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "close() could not schedule cleanup", e)
        }
    }
}

private class ModelRuntimeException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)
