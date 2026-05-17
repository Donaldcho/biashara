package com.biasharaai.skills.builtin

import com.biasharaai.data.local.db.CustomerDao
import com.biasharaai.data.local.db.DebtDao
import com.biasharaai.skills.BiasharaSkill
import com.biasharaai.skills.SkillArgsParser
import com.biasharaai.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** X5 — Customer roster with optional overdue-credit filter. */
@Singleton
class QueryCustomersSkill @Inject constructor(
    private val customerDao: CustomerDao,
    private val debtDao: DebtDao,
) : BiasharaSkill {
    override val id: String = ID
    override val displayName: String = "Query customers"
    override val parameterSchemaJson: String =
        """{"type":"object","properties":{"overdueOnly":{"type":"boolean"}}}"""

    override suspend fun execute(argumentsJson: String): SkillResult = withContext(Dispatchers.IO) {
        val args = SkillArgsParser.parseObject(argumentsJson).getOrElse {
            return@withContext SkillResult.Failure("INVALID_ARGS", it.message ?: "Invalid JSON")
        }
        val overdueOnly = SkillArgsParser.boolArg(args, "overdueOnly", default = false)
        val now = System.currentTimeMillis()

        val rows = if (overdueOnly) {
            val overdue = debtDao.getOverdueDebtsBefore(now)
            val byCustomer = overdue.groupBy { it.customerId }
            byCustomer.mapNotNull { (customerId, debts) ->
                val customer = customerDao.getCustomerById(customerId) ?: return@mapNotNull null
                val totalOverdue = debts.sumOf { it.amount }
                mapOf(
                    "id" to customer.id,
                    "name" to customer.name,
                    "phone" to customer.phone,
                    "overdueAmount" to totalOverdue,
                    "overdueNoteCount" to debts.size,
                )
            }.take(MAX_ROWS)
        } else {
            customerDao.getCustomersList().take(MAX_ROWS).map { c ->
                mapOf(
                    "id" to c.id,
                    "name" to c.name,
                    "phone" to c.phone,
                    "lastVisit" to c.lastVisit,
                )
            }
        }

        SkillResult.successMap(
            mapOf(
                "overdueOnly" to overdueOnly,
                "count" to rows.size,
                "customers" to rows,
            ),
            summary = if (overdueOnly) {
                "${rows.size} customer(s) with overdue credit"
            } else {
                "${rows.size} customer(s)"
            },
        )
    }

    companion object {
        const val ID = "query_customers"
        private const val MAX_ROWS = 100
    }
}
