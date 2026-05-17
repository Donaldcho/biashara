package com.biasharaai.voice

import android.content.Context
import android.util.Log
import com.argmaxinc.whisperkit.ExperimentalWhisperKit
import com.argmaxinc.whisperkit.TranscriptionResult as WhisperKitTranscriptionResult
import com.argmaxinc.whisperkit.WhisperKit
import com.argmaxinc.whisperkit.WhisperKitException
import com.biasharaai.ai.AudioCaptureHelper
import com.biasharaai.ai.AudioChunk
import com.biasharaai.data.local.db.AppSettingsDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Streaming STT over Argmax WhisperKit. Transcribe calls are serialised by a dedicated
 * [transcribeMutex] so concurrent STT requests don't overlap without blocking agent workers.
 */
@Singleton
@OptIn(ExperimentalWhisperKit::class)
class WhisperTranscriber @Inject constructor(
    @ApplicationContext private val context: Context,
    private val whisperModelManager: WhisperModelManager,
    private val appSettingsDao: AppSettingsDao,
) {
    // Dedicated STT mutex — independent of AgentMutex so mic works while agent workers run.
    private val transcribeMutex = Mutex()

    private var whisperKit: WhisperKit? = null

    @Volatile
    private var initialised: Boolean = false

    private val pendingTextOut = AtomicReference<CompletableDeferred<WhisperKitTranscriptionResult>?>(null)

    /** Created on first [initialize] so WhisperKit JNI is not loaded at Hilt singleton construction. */
    private var textCallback: WhisperKit.TextOutputCallback? = null

    private fun textCallback(): WhisperKit.TextOutputCallback {
        textCallback?.let { return it }
        return WhisperKit.TextOutputCallback { what, result ->
            if (what == WhisperKit.TextOutputCallback.MSG_TEXT_OUT) {
                pendingTextOut.getAndSet(null)?.complete(result)
            }
        }.also { textCallback = it }
    }

    val isReady: Boolean
        get() = whisperKit != null && initialised

    /** Handbook alias — WhisperKit built and [initialize] completed successfully. */
    fun isAvailable(): Boolean = isReady

    /**
     * Build WhisperKit, download/load weights, and [WhisperKit.init] for 16 kHz mono streaming.
     *
     * @param modelIdOverride optional catalogue id (`whisper_model_id` in settings) — otherwise DB.
     */
    suspend fun initialize(modelIdOverride: String? = null): Unit = withContext(Dispatchers.IO) {
        val settingsId = modelIdOverride
            ?: appSettingsDao.getSettingsSync()?.whisperModelId
            ?: "whisper-tiny"
        val modelKey = whisperModelManager.resolveWhisperKitModelKey(settingsId)
        releaseInternal()
        try {
            val kit = WhisperKit.Builder()
                .setModel(modelKey)
                .setApplicationContext(context.applicationContext)
                .setCallback(textCallback())
                .build()
            whisperKit = kit
            kit.loadModel().collect { progress ->
                Log.d(TAG, "loadModel progress: $progress")
            }
            kit.init(
                frequency = AudioCaptureHelper.SAMPLE_RATE,
                channels = 1,
                duration = 0L,
            )
            initialised = true
            Log.i(TAG, "WhisperKit ready (model=$modelKey)")
            Unit
        } catch (e: WhisperKitException) {
            releaseInternal()
            throw e
        }
    }

    fun release() {
        releaseInternal()
    }

    private fun releaseInternal() {
        initialised = false
        pendingTextOut.getAndSet(null)?.cancel()
        whisperKit?.runCatching { deinitialize() }
        whisperKit = null
        textCallback = null
    }

    /**
     * Feeds [audioChunks] into WhisperKit. Buffers PCM until **≥ ~0.5s** of samples or [AudioChunk.isFinal].
     */
    fun transcribeStream(
        audioChunks: Flow<AudioChunk>,
        languageHint: String? = null,
    ): Flow<TranscriptionResult> = flow {
        val kit = whisperKit
        check(initialised && kit != null) { "WhisperKit not initialised — call initialize() first" }

        val buffer = ArrayList<Short>(16_000)
        val minSamplesBeforeFlush = SAMPLE_RATE / 2

        audioChunks.collect { chunk ->
            if (!coroutineContext.isActive) return@collect
            for (s in chunk.pcmData) buffer.add(s)
            val shouldFlush = buffer.size >= minSamplesBeforeFlush || chunk.isFinal
            if (!shouldFlush) return@collect

            val pcm = buffer.toShortArray()
            buffer.clear()
            if (pcm.isEmpty()) return@collect  // nothing to transcribe (e.g. empty final flush marker)

            val bytes = shortsToPcm16Le(pcm)
            val text = transcribeWithMutex(kit, bytes)
            emit(
                TranscriptionResult(
                    text = text,
                    language = languageHint ?: "auto",
                    confidence = if (chunk.isFinal) 1f else 0.85f,
                    isPartial = !chunk.isFinal,
                    engine = TranscriptionEngine.WHISPER,
                ),
            )
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun transcribeWithMutex(kit: WhisperKit, pcm: ByteArray): String =
        transcribeMutex.withLock {
            transcribeAwait(kit, pcm)
        }

    private suspend fun transcribeAwait(kit: WhisperKit, pcm: ByteArray): String {
        val d = CompletableDeferred<WhisperKitTranscriptionResult>()
        pendingTextOut.set(d)
        return try {
            val code = withContext(Dispatchers.IO) { kit.transcribe(pcm) }
            if (code < 0) {
                pendingTextOut.compareAndSet(d, null)
                throw WhisperKitException("transcribe returned $code")
            }
            val r = withTimeout(TRANSCRIBE_CALLBACK_TIMEOUT_MS) { d.await() }
            r.text.trim()
        } catch (e: CancellationException) {
            pendingTextOut.compareAndSet(d, null)
            throw e
        } catch (e: Throwable) {
            pendingTextOut.compareAndSet(d, null)
            throw e
        }
    }

    companion object {
        private const val TAG = "WhisperTranscriber"
        private const val SAMPLE_RATE = AudioCaptureHelper.SAMPLE_RATE
        private const val TRANSCRIBE_CALLBACK_TIMEOUT_MS = 12_000L
    }
}
