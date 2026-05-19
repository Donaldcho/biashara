package com.biasharaai.skills.builtin

import com.biasharaai.analytics.SalesIntelligenceRepository
import com.biasharaai.data.local.db.TransactionDao
import com.biasharaai.productline.ProductLineManager
import com.biasharaai.skills.BiasharaSkill
import com.biasharaai.skills.SkillArgsParser
import com.biasharaai.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** X5 — Sales totals for a rolling day window from [transactions] (net of returns). */
@Singleton
class QuerySalesSkill @Inject constructor(
    private val transactionDao: TransactionDao,
    private val salesIntelligence: SalesIntelligenceRepository,
    private val productLineManager: ProductLineManager,
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

        val summary = salesIntelligence.periodSummary(start, endExclusive)
        val expenses = transactionDao.sumExpenseAmountBetween(start, endExclusive)
        val creditExtended = transactionDao.sumCreditIncomeAmountBetween(start, endExclusive)
        val saleCount = transactionDao.countIncomeTransactionsBetween(start, endExclusive)
        val net = summary.netRevenue - expenses
        val serviceRevenue = if (productLineManager.isProEnabled()) {
            summary.grossServiceSubtotal
        } else {
            0.0
        }

        SkillResult.successMap(
            mapOf(
                "days" to days,
                "startMillis" to start,
                "endExclusiveMillis" to endExclusive,
                "revenue" to summary.netRevenue,
                "grossRevenue" to summary.grossIncome,
                "returnsTotal" to summary.returnsTotal,
                "returnCount" to summary.returnTransactionCount,
                "productRevenue" to summary.grossProductSubtotal,
                "serviceRevenue" to serviceRevenue,
                "expenses" to expenses,
                "net" to net,
                "creditExtended" to creditExtended,
                "saleCount" to saleCount,
            ),
            summary = "Net revenue ${"%.0f".format(summary.netRevenue)} (products ${"%.0f".format(summary.grossProductSubtotal)}, services ${"%.0f".format(serviceRevenue)}) / ${days}d",
        )
    }

    companion object {
        const val ID = "query_sales"
    }
}
