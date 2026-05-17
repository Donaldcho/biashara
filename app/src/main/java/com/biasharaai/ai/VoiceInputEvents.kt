package com.biasharaai.ai

import com.biasharaai.voice.TranscriptionResult

enum class VoiceSttEngine {
    WHISPER,
    GEMMA_3N,
    SPEECH_RECOGNIZER,
}

sealed class VoiceInputEvent {
    data object Listening : VoiceInputEvent()

    data class EngineSelected(val engine: VoiceSttEngine) : VoiceInputEvent()

    data class PartialTranscription(val text: String) : VoiceInputEvent()

    data class FinalTranscription(val result: TranscriptionResult) : VoiceInputEvent()

    data object UseSpeechRecognizerFallback : VoiceInputEvent()

    data class Error(val message: String) : VoiceInputEvent()
}
