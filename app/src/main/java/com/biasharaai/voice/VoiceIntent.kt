package com.biasharaai.voice

/**
 * Result of classifying owner speech — drives query vs command vs field fill-in.
 */
sealed class VoiceIntent {

    /** Business question → conversational query / [com.biasharaai.agent.AgentLoopRunner] path. */
    data class Query(val text: String, val language: String) : VoiceIntent()

    sealed class Command : VoiceIntent() {
        data class Navigate(val destination: String) : Command()

        data class OpenPOS(val productHint: String?) : Command()

        data class RecordSale(val productName: String, val qty: Int) : Command()

        data object GoHome : Command()

        data object OpenInventory : Command()

        data object ReadLastAlert : Command()
    }

    /** Dictation into the focused field. */
    data class DataEntry(val text: String) : VoiceIntent()

    /** Router could not map utterance to a known intent — surface raw text. */
    data class Unclassified(val rawText: String) : VoiceIntent()
}
