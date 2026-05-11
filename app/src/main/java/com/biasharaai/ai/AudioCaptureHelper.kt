package com.biasharaai.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Captures microphone audio as raw PCM mono 16-bit data.
 *
 * Produces a [ByteArray] suitable for passing to
 * [LlmInferenceSession.addAudio] for Gemma 3n multimodal transcription.
 *
 * Audio format: 16 kHz, mono, 16-bit PCM (signed little-endian).
 */
object AudioCaptureHelper {

    private const val TAG = "AudioCaptureHelper"

    /** Sample rate matching Gemma 3n audio input requirements. */
    const val SAMPLE_RATE = 16_000

    /** Mono channel. */
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO

    /** 16-bit PCM encoding. */
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    /** Default recording duration in milliseconds (5 seconds). */
    const val DEFAULT_DURATION_MS = 5_000L

    /** Maximum recording duration in milliseconds (15 seconds). */
    const val MAX_DURATION_MS = 15_000L

    /**
     * Check whether the RECORD_AUDIO permission is granted.
     */
    fun hasRecordPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Record audio from the microphone for [durationMs] milliseconds.
     *
     * Must be called from a coroutine — runs on [Dispatchers.IO].
     *
     * @param durationMs Recording duration (clamped to [MAX_DURATION_MS]).
     * @return Raw PCM [ByteArray] (16 kHz, mono, 16-bit LE).
     * @throws SecurityException if RECORD_AUDIO permission is not granted.
     * @throws IllegalStateException if AudioRecord cannot be initialised.
     */
    suspend fun recordAudio(
        durationMs: Long = DEFAULT_DURATION_MS,
    ): ByteArray = withContext(Dispatchers.IO) {
        val clampedDuration = durationMs.coerceIn(1_000L, MAX_DURATION_MS)
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

        check(minBufferSize > 0) {
            "AudioRecord.getMinBufferSize returned $minBufferSize — audio recording not available"
        }

        val bufferSize = maxOf(minBufferSize, SAMPLE_RATE * 2) // at least 1 second buffer

        @Suppress("MissingPermission") // Caller must ensure permission
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
        val endTime = System.currentTimeMillis() + clampedDuration

        try {
            recorder.startRecording()
            Log.d(TAG, "Recording started (${clampedDuration}ms)")

            while (System.currentTimeMillis() < endTime) {
                val bytesRead = recorder.read(readBuffer, 0, readBuffer.size)
                if (bytesRead > 0) {
                    output.write(readBuffer, 0, bytesRead)
                }
            }

            Log.d(TAG, "Recording complete: ${output.size()} bytes")
        } finally {
            recorder.stop()
            recorder.release()
        }

        output.toByteArray()
    }
}
