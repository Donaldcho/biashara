package com.biasharaai.voice

/**
 * Normalised STT output for the voice layer (not to be confused with
 * [com.argmaxinc.whisperkit.TranscriptionResult]).
 */
data class TranscriptionResult(
    val text: String,
    val language: String,
    val confidence: Float,
    val isPartial: Boolean,
    val engine: TranscriptionEngine,
)

enum class TranscriptionEngine {
    WHISPER,
    GEMMA_3N,
    SPEECH_RECOGNIZER,
}
