package com.biasharaai.skills.builtin

import com.biasharaai.data.local.db.SaleLineItemDao
import com.biasharaai.data.local.db.TransactionDao
import com.biasharaai.skills.BiasharaSkill
import com.biasharaai.skills.SkillArgsParser
import com.biasharaai.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** X5 — Gross profit from POS line items vs product cost in a day window. */
@Singleton
class CalculateProfitSkill @Inject constructor(
    private val saleLineItemDao: SaleLineItemDao,
    private val transactionDao: TransactionDao,
) : BiasharaSkill {
    override val id: String = ID
    override val displayName: String = "Calculate profit"
    override val parameterSchemaJson: String =
        """{"type":"object","properties":{"days":{"type":"integer","minimum":1,"maximum":365}},"required":["days"]}"""

    override suspend fun execute(argumentsJson: String): SkillResult = withContext(Dispatchers.IO) {
        val args = SkillArgsParser.parseObject(argumentsJson).getOrElse {
            return@withContext SkillResult.Failure("INVALID_ARGS", it.message ?: "Invalid JSON")
        }
        val days = SkillArgsParser.intArg(args, "days", default = 7, min = 1, max = 365)
        val (start, endExclusive) = SkillArgsParser.periodBounds(days)

        val posRevenue = saleLineItemDao.sumPosRevenueBetween(start, endExclusive)
        val grossProfit = saleLineItemDao.sumGrossProfitBetween(start, endExclusive)
        val expenses = transactionDao.sumExpenseAmountBetween(start, endExclusive)
        val netAfterExpenses = grossProfit - expenses
        val marginPct = if (posRevenue > 0) (grossProfit / posRevenue) * 100.0 else 0.0

        SkillResult.successMap(
            mapOf(
                "days" to days,
                "posRevenue" to posRevenue,
                "grossProfit" to grossProfit,
                "grossMarginPercent" to marginPct,
                "expenses" to expenses,
                "netAfterExpenses" to netAfterExpenses,
            ),
            summary = "Gross profit ${"%.0f".format(grossProfit)} (${"%.0f".format(marginPct)}% margin) over $days days",
        )
    }

    companion object {
        const val ID = "calculate_profit"
    }
}
