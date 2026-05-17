package com.biasharaai.voice

import android.util.Log
import com.biasharaai.ai.ActiveModelStore
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.ai.ModelCapability
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pattern-first voice intent routing; optional one-shot LLM disambiguation on PARTIAL_AI / FULL_AI
 * when a text model is available.
 */
@Singleton
class VoiceRouter @Inject constructor(
    private val activeModelStore: ActiveModelStore,
    private val capabilityTier: CapabilityTier,
) {

    private val dataEntryScreens = setOf(
        VoiceScreenContext.ADD_EDIT_PRODUCT,
        VoiceScreenContext.KIOSK_CATALOGUE,
    )

    private val queryPatterns = listOf(
        Regex(
            """^(what|how much|how many|show me|tell me|who|when|why|niambie|nini|ngapi)\b""",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            """^(mauzo|faida|hasara|stock|bidhaa|wateja)\b""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("""^(nawa|nawane)\b""", RegexOption.IGNORE_CASE),
    )

    /** More specific patterns first. */
    private val commandPatterns: List<Pair<Regex, String>> = listOf(
        Regex("""\b(home|nyumbani)\b""", RegexOption.IGNORE_CASE) to "HOME",
        Regex("""\b(alert|notification|arifa|taarifa)\b""", RegexOption.IGNORE_CASE) to "READ_ALERT",
        Regex("""\b(open\s+pos|pos\s+screen|fungua\s+pos|nenda\s+pos)\b""", RegexOption.IGNORE_CASE) to "OPEN_POS",
        Regex("""\b(inventory|stock|orodha|oricha|bidhaa\s*list)\b""", RegexOption.IGNORE_CASE) to "INVENTORY",
        Regex("""\b(sell|record sale|uza|sale)\b""", RegexOption.IGNORE_CASE) to "RECORD_SALE",
        Regex("""\b(open|go to|show|nenda|fungua)\b""", RegexOption.IGNORE_CASE) to "NAVIGATE",
    )

    private val recordSaleRegex = Regex(
        """(?:sell|record sale|uza)\s+(?:(\d+)\s*(?:x|\*)?\s*)?(.+)""",
        RegexOption.IGNORE_CASE,
    )

    suspend fun classify(
        result: TranscriptionResult,
        currentScreen: String,
    ): VoiceIntent {
        val text = result.text.trim()
        if (text.isEmpty()) return VoiceIntent.Unclassified("")

        if (currentScreen in dataEntryScreens) {
            return VoiceIntent.DataEntry(text)
        }

        for ((pattern, type) in commandPatterns) {
            if (pattern.containsMatchIn(text)) {
                return parseCommand(text.removePrefix(" ").trim(), type)
            }
        }

        if (queryPatterns.any { it.containsMatchIn(text) }) {
            return VoiceIntent.Query(text, result.language)
        }

        if (capabilityTier != CapabilityTier.RULES_BASED && activeModelStore.isAvailable) {
            runCatching {
                val classification = activeModelStore.sendPrompt(
                    """
                    Classify this voice input from a shop owner in ONE word.
                    Reply only with one of: QUERY, COMMAND, DATA.
                    Input: "${text.replace("\"", "'")}"
                    """.trimIndent(),
                    ModelCapability.TEXT_GENERATION,
                ).trim().uppercase()
                Log.d(TAG, "LLM voice classification: $classification")
                return when {
                    classification.contains("QUERY") -> VoiceIntent.Query(text, result.language)
                    classification.contains("COMMAND") -> VoiceIntent.Unclassified(text)
                    classification.contains("DATA") -> VoiceIntent.DataEntry(text)
                    else -> VoiceIntent.Unclassified(text)
                }
            }.onFailure { Log.w(TAG, "LLM classification failed", it) }
        }

        return VoiceIntent.Unclassified(text)
    }

    private fun parseCommand(text: String, type: String): VoiceIntent =
        when (type) {
            "HOME" -> VoiceIntent.Command.GoHome
            "INVENTORY" -> VoiceIntent.Command.OpenInventory
            "READ_ALERT" -> VoiceIntent.Command.ReadLastAlert
            "OPEN_POS" -> VoiceIntent.Command.OpenPOS(productHint = null)
            "RECORD_SALE" -> parseRecordSale(text)
            "NAVIGATE" -> VoiceIntent.Command.Navigate(extractNavigationTarget(text))
            else -> VoiceIntent.Unclassified(text)
        }

    private fun parseRecordSale(text: String): VoiceIntent {
        val m = recordSaleRegex.find(text) ?: return VoiceIntent.Unclassified(text)
        val qty = m.groupValues.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 } ?: 1
        val name = m.groupValues.getOrNull(2)?.trim().orEmpty()
        if (name.isBlank()) return VoiceIntent.Unclassified(text)
        return VoiceIntent.Command.RecordSale(productName = name, qty = qty)
    }

    private fun extractNavigationTarget(text: String): String =
        text.replace(
            Regex("""^(open|go to|show|nenda|fungua)\s+""", RegexOption.IGNORE_CASE),
            "",
        ).trim().ifBlank { text }

    companion object {
        private const val TAG = "VoiceRouter"
    }
}
