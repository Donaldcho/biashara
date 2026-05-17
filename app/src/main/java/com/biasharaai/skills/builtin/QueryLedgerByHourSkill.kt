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
class QueryLedgerByHourSkill @Inject constructor(
    private val ledgerIntelligence: LedgerIntelligenceRepository,
) : BiasharaSkill {
    override val id: String = ID
    override val displayName: String = "Query ledger by hour"
    override val parameterSchemaJson: String =
        """{"type":"object","properties":{"windowDays":{"type":"integer","minimum":1,"maximum":90},"timezone":{"type":"string"}}}"""

    override suspend fun execute(argumentsJson: String): SkillResult = withContext(Dispatchers.IO) {
        val args = SkillArgsParser.parseObject(argumentsJson).getOrElse {
            return@withContext SkillResult.Failure("INVALID_ARGS", it.message ?: "Invalid JSON")
        }
        val windowDays = SkillArgsParser.intArg(args, "windowDays", default = 30, min = 1, max = 90)
        val timezone = SkillArgsParser.stringArg(args, "timezone") ?: "Africa/Nairobi"
        val payload = ledgerIntelligence.queryByHour(windowDays, timezone)
        SkillResult.successMap(payload, summary = "Ledger hourly pattern ($windowDays days)")
    }

    companion object {
        const val ID = "query_ledger_by_hour"
    }
}
