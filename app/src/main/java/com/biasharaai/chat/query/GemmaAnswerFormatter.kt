package com.biasharaai.chat.query

import android.util.Log
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.ai.GemmaService
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hybrid answer finish:
 * - **RULES_BASED / no model:** light deterministic variety on the factual string (same numbers).
 * - **PARTIAL_AI / FULL_AI + model:** strict rewrite pass — natural language only; facts stay fixed.
 *
 * Optional [toneHints] adds calendar / income snapshot lines so the model can contrast *only* using
 * numbers that also appear in the main Facts block (hints are supplementary).
 */
@Singleton
class GemmaAnswerFormatter @Inject constructor(
    private val gemmaService: GemmaService,
    private val capabilityTier: CapabilityTier,
) {

    suspend fun maybePolish(
        userQuestion: String,
        factualAnswer: String,
        languageDisplayName: String,
        toneHints: String? = null,
    ): String {
        if (factualAnswer.isBlank()) return factualAnswer

        if (capabilityTier == CapabilityTier.RULES_BASED || !gemmaService.isAvailable) {
            val varied = RulesBasedReplyVariator.variate(
                factualAnswer,
                seed = userQuestion,
                languageDisplayName = languageDisplayName,
            )
            Log.d(
                TAG,
                "structured_reply rules_tier=${capabilityTier.name} variate=${varied != factualAnswer} len=${varied.length}",
            )
            return varied
        }

        // Skip polishing for very short single-fact answers — already readable, and the
        // blocking generateResponse() call would be clearly perceptible to the user.
        if (factualAnswer.length < 120) {
            Log.d(TAG, "structured_polish_skip reason=too_short len=${factualAnswer.length}")
            return factualAnswer
        }
        if (factualAnswer.length > 4_000) {
            Log.d(TAG, "structured_polish_skip reason=too_long len=${factualAnswer.length}")
            return factualAnswer
        }

        return try {
            val prompt = buildString {
                append("You help a small shop owner. Reply in ")
                append(languageDisplayName)
                append(".\n\n")
                append("STRICT RULES:\n")
                append("- Use ONLY information from the Facts (and Tone_hints if present). Do NOT invent amounts, dates, product names, or customers.\n")
                append("- Every numeric amount that appears in Facts must appear unchanged in your answer (same digits and grouping as in Facts).\n")
                append("- You may shorten or clarify wording, add short natural connectors, and one sentence of interpretation ONLY if it restates relationships already implied by Facts (e.g. higher/lower than yesterday when both numbers are in Facts).\n")
                append("- If you cannot obey all rules, copy the Facts block verbatim.\n")
                append("- 2–6 short sentences.\n\n")
                if (!toneHints.isNullOrBlank()) {
                    append("Tone_hints (supplementary; Facts win if anything disagrees):\n")
                    append(toneHints.trim())
                    append("\n\n")
                }
                append("Facts:\n")
                append(factualAnswer.trim())
                append("\n\nQuestion: ")
                append(userQuestion.trim())
            }
            val polished = withTimeoutOrNull(POLISH_TIMEOUT_MS) {
                gemmaService.generateResponse(prompt).trim()
            } ?: run {
                Log.w(TAG, "structured_polish_skip reason=timeout ms=$POLISH_TIMEOUT_MS")
                return factualAnswer
            }
            val out = when {
                polished.isBlank() -> {
                    Log.d(TAG, "structured_polish_skip reason=blank_model_output")
                    factualAnswer
                }
                !digitMassPreserved(factualAnswer, polished) -> {
                    Log.w(TAG, "structured_polish_revert reason=digit_mass lenIn=${factualAnswer.length} lenOut=${polished.length}")
                    factualAnswer
                }
                else -> {
                    Log.d(TAG, "structured_polish_ok lenIn=${factualAnswer.length} lenOut=${polished.length}")
                    polished
                }
            }
            out
        } catch (e: Exception) {
            Log.w(TAG, "structured_polish_skip reason=exception", e)
            factualAnswer
        }
    }

    /**
     * Heuristic: model output dropped too many digits vs the factual string → likely hallucination or truncation.
     */
    private fun digitMassPreserved(factual: String, polished: String): Boolean {
        val dIn = factual.count { it.isDigit() }
        val dOut = polished.count { it.isDigit() }
        if (dIn <= 4) return true
        return dOut >= dIn * 3 / 4
    }

    companion object {
        private const val TAG = "GemmaAnswerFmt"
        private const val POLISH_TIMEOUT_MS = 8_000L
    }
}
