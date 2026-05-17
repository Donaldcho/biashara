package com.biasharaai.ai

/**
 * One chunk of raw **PCM16 mono** microphone audio (Whisper / STT path expects **16 kHz**).
 *
 * [isFinal] is `true` when this chunk ends the stream because **post-speech silence** was
 * detected (see [SilenceDetector]) or the caller stopped recording.
 */
data class AudioChunk(
    val pcmData: ShortArray,
    val sampleRate: Int = 16_000,
    val isFinal: Boolean = false,
)
