package com.biasharaai.skills.builtin

import com.biasharaai.ledger.intelligence.LedgerIntelligenceRepository
import com.biasharaai.skills.BiasharaSkill
import com.biasharaai.skills.SkillArgsParser
import com.biasharaai.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ledger Intelligence v2 — versioned structured financial facts for agents.
 * Replaces ad-hoc [QueryLedgerSkill] for new agent prompts; v1 skill remains for compatibility.
 */
@Singleton
class QueryLedgerV2Skill @Inject constructor(
    private val ledgerIntelligence: LedgerIntelligenceRepository,
) : BiasharaSkill {
    override val id: String = ID
    override val displayName: String = "Query ledger (v2)"
    override val parameterSchemaJson: String = SCHEMA

    override suspend fun execute(argumentsJson: String): SkillResult = withContext(Dispatchers.IO) {
        val args = SkillArgsParser.parseObject(argumentsJson).getOrElse {
            return@withContext SkillResult.Failure("INVALID_ARGS", it.message ?: "Invalid JSON")
        }
        val period = SkillArgsParser.stringArg(args, "period") ?: "LAST_30_DAYS"
        val timezone = SkillArgsParser.stringArg(args, "timezone") ?: "Africa/Nairobi"
        val include = parseInclude(args["include"])

        val payload = ledgerIntelligence.queryV2(period, timezone, include)
        val summary = (payload["summary"] as? Map<*, *>)?.let { s ->
            val `in` = s["moneyIn"] as? Number ?: 0
            val out = s["moneyOut"] as? Number ?: 0
            val bal = s["runningBalance"] as? Number ?: 0
            "Ledger v2 $period: in ${`in`.toDouble()}, out ${out.toDouble()}, balance ${bal.toDouble()}"
        } ?: "Ledger v2 $period"

        SkillResult.successMap(payload, summary = summary)
    }

    private fun parseInclude(raw: Any?): Set<String> {
        if (raw == null) return emptySet()
        return when (raw) {
            is List<*> -> raw.mapNotNull { it?.toString()?.lowercase() }.toSet()
            is String -> raw.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
            else -> emptySet()
        }
    }

    companion object {
        const val ID = "query_ledger_v2"
        private val SCHEMA = """
            {
              "type":"object",
              "properties":{
                "period":{"type":"string","enum":["LAST_7_DAYS","LAST_30_DAYS","THIS_MONTH","LAST_MONTH","THIS_WEEK"]},
                "timezone":{"type":"string"},
                "include":{"type":"array","items":{"type":"string"}}
              },
              "required":["period"]
            }
        """.trimIndent()
    }
}
