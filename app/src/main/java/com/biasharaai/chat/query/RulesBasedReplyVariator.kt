package com.biasharaai.chat.query

import java.util.Locale

/**
 * Deterministic variety for [CapabilityTier.RULES_BASED] structured answers (no LLM).
 * Same facts, different short lead-in based on [seed] (e.g. question text).
 */
internal object RulesBasedReplyVariator {

    fun variate(factual: String, seed: String, languageDisplayName: String): String {
        if (factual.isBlank()) return factual
        val lang = languageDisplayName.lowercase(Locale.ROOT)
        val leads = when {
            lang.contains("swahili") || lang == "sw" -> swahiliLeads
            lang.contains("hausa") || lang == "ha" -> hausaLeads
            lang.contains("yoruba") || lang == "yo" -> yorubaLeads
            lang.contains("amharic") || lang == "am" -> amharicLeads
            else -> englishLeads
        }
        val idx = (seed.hashCode() and Int.MAX_VALUE) % leads.size
        return leads[idx] + factual
    }

    private val englishLeads = listOf(
        "",
        "From your saved records: ",
        "Here's what the numbers show: ",
        "Summary — ",
    )

    private val swahiliLeads = listOf(
        "",
        "Kutoka kwenye data yako: ",
        "Hii ndiyo hesabu iliyohifadhiwa: ",
        "Muhtasari — ",
    )

    private val hausaLeads = listOf(
        "",
        "Daga bayanan da aka adana: ",
        "Wannan shine sakamakon lissafin: ",
        "Taƙaitawa — ",
    )

    private val yorubaLeads = listOf(
        "",
        "Láti inú àwọn àkọọ́sílẹ̀ rẹ: ",
        "Èyí ni àbájáde àwọn nọ́mbà: ",
        "Àkójọpọ̀ — ",
    )

    private val amharicLeads = listOf(
        "",
        "ከተቀመጡ መዝገቦችዎ: ",
        "ቁጥሮቹ የሚከተለውን ያሳያሉ: ",
        "ማጠቃለያ — ",
    )
}
