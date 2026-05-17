package com.biasharaai.skills.builtin

import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.data.local.db.LedgerEntryDao
import com.biasharaai.skills.BiasharaSkill
import com.biasharaai.skills.SkillArgsParser
import com.biasharaai.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Phase 9 L8 — Ledger totals for a rolling day window. */
@Singleton
class QueryLedgerSkill @Inject constructor(
    private val ledgerEntryDao: LedgerEntryDao,
) : BiasharaSkill {
    override val id: String = ID
    override val displayName: String = "Query ledger"
    override val parameterSchemaJson: String =
        """{"type":"object","properties":{"days":{"type":"integer","minimum":1,"maximum":365}},"required":["days"]}"""

    override suspend fun execute(argumentsJson: String): SkillResult = withContext(Dispatchers.IO) {
        val args = SkillArgsParser.parseObject(argumentsJson).getOrElse {
            return@withContext SkillResult.Failure("INVALID_ARGS", it.message ?: "Invalid JSON")
        }
        val days = SkillArgsParser.intArg(args, "days", default = 7, min = 1, max = 365)
        val (start, endExclusive) = SkillArgsParser.periodBounds(days)

        val moneyIn = ledgerEntryDao.getTotalForDirection(
            LedgerDirection.MONEY_IN.name,
            start,
            endExclusive - 1,
        ) ?: 0.0
        val moneyOut = ledgerEntryDao.getTotalForDirection(
            LedgerDirection.MONEY_OUT.name,
            start,
            endExclusive - 1,
        ) ?: 0.0
        val balance = ledgerEntryDao.getCurrentBalance() ?: 0.0
        val pendingCredit = ledgerEntryDao.getPendingCreditTotal() ?: 0.0
        val breakdown = ledgerEntryDao.getBreakdownByType(start, endExclusive - 1)

        SkillResult.successMap(
            mapOf(
                "days" to days,
                "startMillis" to start,
                "endExclusiveMillis" to endExclusive,
                "moneyIn" to moneyIn,
                "moneyOut" to moneyOut,
                "net" to (moneyIn - moneyOut),
                "runningBalance" to balance,
                "pendingCredit" to pendingCredit,
                "breakdown" to breakdown.map { mapOf("type" to it.type, "total" to it.total) },
            ),
            summary = "Ledger ${days}d: in ${"%.0f".format(moneyIn)}, out ${"%.0f".format(moneyOut)}, balance ${"%.0f".format(balance)}",
        )
    }

    companion object {
        const val ID = "query_ledger"
    }
}
