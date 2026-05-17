package com.biasharaai.skills.builtin

import com.biasharaai.ledger.intelligence.LedgerIntelligenceRepository
import com.biasharaai.skills.BiasharaSkill
import com.biasharaai.skills.SkillArgsParser
import com.biasharaai.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueryLedgerTrendSkill @Inject constructor(
    private val ledgerIntelligence: LedgerIntelligenceRepository,
) : BiasharaSkill {
    override val id: String = ID
    override val displayName: String = "Query ledger trend"
    override val parameterSchemaJson: String =
        """{"type":"object","properties":{"windowWeeks":{"type":"integer","minimum":4,"maximum":12},"timezone":{"type":"string"}}}"""

    override suspend fun execute(argumentsJson: String): SkillResult = withContext(Dispatchers.IO) {
        val args = SkillArgsParser.parseObject(argumentsJson).getOrElse {
            return@withContext SkillResult.Failure("INVALID_ARGS", it.message ?: "Invalid JSON")
        }
        val windowWeeks = SkillArgsParser.intArg(args, "windowWeeks", default = 12, min = 4, max = 12)
        val timezone = SkillArgsParser.stringArg(args, "timezone") ?: "Africa/Nairobi"
        val payload = ledgerIntelligence.queryTrend(windowWeeks, timezone)
        val trend = payload["netCashTrend"]?.toString() ?: "UNKNOWN"
        SkillResult.successMap(payload, summary = "Ledger trend ($windowWeeks wk): $trend")
    }

    companion object {
        const val ID = "query_ledger_trend"
    }
}
