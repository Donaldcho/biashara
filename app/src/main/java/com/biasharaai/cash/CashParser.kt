package com.biasharaai.cash

import android.util.Log
import com.biasharaai.agent.AgentMutex
import com.biasharaai.ai.ActiveModelStore
import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.data.local.db.ParserEngine
import com.biasharaai.data.local.db.ProofType
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 3-tier cash parser:
 *   Tier 1 — RegexParser (free, instant)
 *   Tier 2 — FunctionGemma 270M (acquire AgentMutex, conf < 0.75)
 *   Tier 3 — Full Gemma 4 E2B (acquire AgentMutex, conf < 0.40)
 */
@Singleton
class CashParser @Inject constructor(
    private val activeModelStore: ActiveModelStore,
    private val agentMutex: AgentMutex,
) {

    suspend fun parse(rawText: String, direction: LedgerDirection? = null): ParsedFields {
        if (rawText.isBlank()) return emptyFields()

        // Tier 1 — regex
        val regexResult = RegexParser.parse(rawText, direction)
        if (regexResult != null && regexResult.confidence >= TIER2_THRESHOLD) {
            Log.d(TAG, "Tier1 hit conf=${regexResult.confidence}")
            return regexResult
        }

        // Tier 2 — lightweight LLM (function-calling style prompt)
        if (activeModelStore.isAvailable) {
            val tier2 = agentMutex.mutex.withLock {
                runCatching { callLlm(rawText, direction, brief = true) }.getOrNull()
            }
            if (tier2 != null && tier2.confidence >= TIER3_THRESHOLD) {
                Log.d(TAG, "Tier2 hit conf=${tier2.confidence}")
                return tier2
            }

            // Tier 3 — full context LLM prompt
            val tier3 = agentMutex.mutex.withLock {
                runCatching { callLlm(rawText, direction, brief = false) }.getOrNull()
            }
            if (tier3 != null) {
                Log.d(TAG, "Tier3 hit conf=${tier3.confidence}")
                return tier3
            }
        }

        // Fallback — return whatever regex gave us, or an empty manual result
        return regexResult ?: emptyFields()
    }

    private suspend fun callLlm(
        text: String,
        direction: LedgerDirection?,
        brief: Boolean,
    ): ParsedFields? {
        val dirHint = direction?.name ?: "unknown"
        val prompt = if (brief) briefPrompt(text, dirHint) else fullPrompt(text, dirHint)
        val response = activeModelStore.generateResponse(prompt)
        return parseLlmResponse(response, direction)
    }

    private fun briefPrompt(text: String, dirHint: String) = """
        Extract JSON from this payment text. Direction hint: $dirHint.
        Return ONLY: {"amount":float,"reference":"","counterparty":"","date":"YYYY-MM-DD"}
        Text: ${text.take(500)}
    """.trimIndent()

    private fun fullPrompt(text: String, dirHint: String) = """
        You are a financial document parser for an East African small business app.
        Extract these fields from the text below. Direction: $dirHint.
        Return ONLY a JSON object: {"amount":float or null,"reference":"string or null",
        "counterparty":"string or null","date":"YYYY-MM-DD or null","proof_type":"MPESA_SMS|RECEIPT|INVOICE|SUPPLIER_BILL|BANK_SLIP|UTILITY_BILL|TILL_SLIP|UNKNOWN"}
        Text: ${text.take(1500)}
    """.trimIndent()

    private fun parseLlmResponse(response: String, direction: LedgerDirection?): ParsedFields? {
        val jsonStr = extractJson(response) ?: return null
        return runCatching {
            val obj = JSONObject(jsonStr)
            val amount = if (obj.has("amount") && !obj.isNull("amount")) obj.getDouble("amount") else null
            val reference = obj.optString("reference").takeIf { it.isNotBlank() }?.uppercase()
            val counterparty = obj.optString("counterparty").takeIf { it.isNotBlank() }
            val proofType = runCatching {
                ProofType.valueOf(obj.optString("proof_type", "UNKNOWN"))
            }.getOrDefault(ProofType.UNKNOWN)

            if (amount == null && reference == null) return null

            val confidence = when {
                amount != null && reference != null -> 0.85f
                amount != null -> 0.65f
                else -> 0.50f
            }

            ParsedFields(
                amount = amount,
                reference = reference,
                counterparty = counterparty,
                proofType = proofType,
                confidence = confidence,
                engine = ParserEngine.FULL_GEMMA,
                suggestedDirection = direction,
            )
        }.getOrNull()
    }

    private fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun emptyFields() = ParsedFields(engine = ParserEngine.MANUAL)

    companion object {
        private const val TAG = "CashParser"
        private const val TIER2_THRESHOLD = 0.75f
        private const val TIER3_THRESHOLD = 0.40f
    }
}
