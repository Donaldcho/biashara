package com.biasharaai.skills.builtin

import com.biasharaai.data.local.db.TransactionDao
import com.biasharaai.skills.BiasharaSkill
import com.biasharaai.skills.SkillArgsParser
import com.biasharaai.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** X5 — Sales totals for a rolling day window from [transactions]. */
@Singleton
class QuerySalesSkill @Inject constructor(
    private val transactionDao: TransactionDao,
) : BiasharaSkill {
    override val id: String = ID
    override val displayName: String = "Query sales"
    override val parameterSchemaJson: String =
        """{"type":"object","properties":{"days":{"type":"integer","minimum":1,"maximum":365}},"required":["days"]}"""

    override suspend fun execute(argumentsJson: String): SkillResult = withContext(Dispatchers.IO) {
        val args = SkillArgsParser.parseObject(argumentsJson).getOrElse {
            return@withContext SkillResult.Failure("INVALID_ARGS", it.message ?: "Invalid JSON")
        }
        val days = SkillArgsParser.intArg(args, "days", default = 7, min = 1, max = 365)
        val (start, endExclusive) = SkillArgsParser.periodBounds(days)

        val revenue = transactionDao.sumIncomeAmountBetween(start, endExclusive)
        val expenses = transactionDao.sumExpenseAmountBetween(start, endExclusive)
        val creditExtended = transactionDao.sumCreditIncomeAmountBetween(start, endExclusive)
        val saleCount = transactionDao.countIncomeTransactionsBetween(start, endExclusive)
        val net = revenue - expenses

        SkillResult.successMap(
            mapOf(
                "days" to days,
                "startMillis" to start,
                "endExclusiveMillis" to endExclusive,
                "revenue" to revenue,
                "expenses" to expenses,
                "net" to net,
                "creditExtended" to creditExtended,
                "saleCount" to saleCount,
            ),
            summary = "Revenue ${"%.0f".format(revenue)} / ${days}d, net ${"%.0f".format(net)}",
        )
    }

    companion object {
        const val ID = "query_sales"
    }
}
