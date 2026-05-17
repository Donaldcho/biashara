package com.biasharaai.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.biasharaai.data.local.db.AppSettings
import com.biasharaai.data.local.db.AppSettingsDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streaming **16 kHz mono PCM16** capture for the voice layer.
 *
 * - [startRecording] yields [AudioChunk]s (~every [CHUNK_MS]) until post-speech silence,
 *   [stopRecording], flow cancellation, or [maxDurationMs].
 * - [recordForDuration] keeps the legacy fixed-window behaviour used when silence detection
 *   should not apply (bounded capture length only).
 */
@Singleton
class AudioCaptureHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDao: AppSettingsDao,
) {

    private val recordingMutex = Mutex()

    @Volatile
    private var stopRequested: Boolean = false

    @Volatile
    private var activeRecorder: AudioRecord? = null

    fun startRecording(
        maxDurationMs: Long = MAX_STREAM_DURATION_MS,
    ): Flow<AudioChunk> = channelFlow {
        if (!recordingMutex.tryLock()) {
            Log.w(TAG, "startRecording: already in progress, ignoring duplicate request")
            return@channelFlow
        }
        try {
            stopRequested = false
            val settings = settingsDao.getSettingsSync() ?: AppSettings()
            val silenceDetector = SilenceDetector(
                silenceTimeoutMs = settings.silenceTimeoutMs.toLong(),
            )
            silenceDetector.reset()

            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            check(minBufferSize > 0) {
                "AudioRecord.getMinBufferSize returned $minBufferSize — audio recording not available"
            }

            val chunkSize = SAMPLE_RATE * CHUNK_MS / 1000
            val bufferSize = maxOf(minBufferSize, chunkSize * 2)

            @Suppress("MissingPermission")
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize,
            )
            check(recorder.state == AudioRecord.STATE_INITIALIZED) {
                "AudioRecord failed to initialise (state=${recorder.state})"
            }

            activeRecorder = recorder
            val wallDeadline = System.currentTimeMillis() + maxDurationMs.coerceIn(1_000L, MAX_STREAM_DURATION_MS)

            try {
                recorder.startRecording()
                Log.d(TAG, "Streaming recording started (chunk ${CHUNK_MS}ms, max ${maxDurationMs}ms)")
                val buffer = ShortArray(chunkSize)
                var sentFinal = false

                while (isActive && !stopRequested && System.currentTimeMillis() < wallDeadline) {
                    val read = recorder.read(buffer, 0, chunkSize)
                    if (read < 0) {
                        Log.w(TAG, "AudioRecord.read returned $read")
                        break
                    }
                    if (read == 0) continue

                    val pcm = buffer.copyOf(read)
                    val silenceEnded = silenceDetector.process(pcm)
                    send(AudioChunk(pcmData = pcm, sampleRate = SAMPLE_RATE, isFinal = silenceEnded))
                    if (silenceEnded) {
                        sentFinal = true
                        break
                    }
                }
                // Flush the downstream buffer when the loop exits by timeout or stop (no silence
                // detection fired), so transcribeStream doesn't drop the last partial batch.
                if (!sentFinal && isActive) {
                    send(AudioChunk(pcmData = ShortArray(0), sampleRate = SAMPLE_RATE, isFinal = true))
                }
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
                if (activeRecorder === recorder) activeRecorder = null
                Log.d(TAG, "Streaming recording stopped")
            }
        } finally {
            recordingMutex.unlock()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Signals [startRecording] to stop after the next read cycle; safe from any thread.
     */
    fun stopRecording() {
        stopRequested = true
        activeRecorder?.run { runCatching { stop() } }
    }

    /**
     * Record for a fixed duration (legacy helper). Does **not** use silence-based early stop.
     */
    suspend fun recordForDuration(durationMs: Long = DEFAULT_DURATION_MS): ByteArray = recordingMutex.withLock {
        withContext(Dispatchers.IO) {
            val clamped = durationMs.coerceIn(1_000L, MAX_DURATION_MS)
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            check(minBufferSize > 0) {
                "AudioRecord.getMinBufferSize returned $minBufferSize — audio recording not available"
            }
            val bufferSize = maxOf(minBufferSize, SAMPLE_RATE * 2)
            @Suppress("MissingPermission")
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize,
            )
            check(recorder.state == AudioRecord.STATE_INITIALIZED) {
                "AudioRecord failed to initialise (state=${recorder.state})"
            }
            val output = ByteArrayOutputStream()
            val readBuffer = ByteArray(minBufferSize)
            val endTime = System.currentTimeMillis() + clamped
            try {
                recorder.startRecording()
                while (System.currentTimeMillis() < endTime) {
                    val bytesRead = recorder.read(readBuffer, 0, readBuffer.size)
                    if (bytesRead > 0) output.write(readBuffer, 0, bytesRead)
                }
            } finally {
                recorder.stop()
                recorder.release()
            }
            output.toByteArray()
        }
    }

    companion object {
        private const val TAG = "AudioCaptureHelper"

        const val SAMPLE_RATE: Int = 16_000

        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        /** ~100 ms chunks at 16 kHz (Whisper / streaming STT). */
        private const val CHUNK_MS = 100

        const val DEFAULT_DURATION_MS: Long = 5_000L

        const val MAX_DURATION_MS: Long = 15_000L

        /** Hard cap so open-ended capture cannot run forever without speech + silence. */
        const val MAX_STREAM_DURATION_MS: Long = 120_000L

        fun hasRecordPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
    }
}
