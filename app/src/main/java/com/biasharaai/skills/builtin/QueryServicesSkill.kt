package com.biasharaai.skills.builtin

import com.biasharaai.data.local.db.AgentSetting
import com.biasharaai.data.local.db.AgentSettingDao
import com.biasharaai.data.local.db.ServiceDeliveryDao
import com.biasharaai.data.local.db.ServiceItemDao
import com.biasharaai.data.local.db.TransactionDao
import com.biasharaai.productline.ProductLineManager
import com.biasharaai.service.ServiceInsightsHelper
import com.biasharaai.skills.BiasharaSkill
import com.biasharaai.skills.SkillArgsParser
import com.biasharaai.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Returns service catalogue activity, deliveries, and revenue for a period (Pro). */
@Singleton
class QueryServicesSkill @Inject constructor(
    private val productLineManager: ProductLineManager,
    private val serviceItemDao: ServiceItemDao,
    private val serviceDeliveryDao: ServiceDeliveryDao,
    private val transactionDao: TransactionDao,
    private val agentSettingDao: AgentSettingDao,
) : BiasharaSkill {

    override val id: String = ID
    override val displayName: String = "Query services"
    override val parameterSchemaJson: String =
        """{"type":"object","properties":{"days":{"type":"integer","minimum":1,"maximum":365}},"required":["days"]}"""

    override suspend fun execute(argumentsJson: String): SkillResult = withContext(Dispatchers.IO) {
        if (!productLineManager.isProEnabled()) {
            return@withContext SkillResult.successMap(
                mapOf(
                    "proEnabled" to false,
                    "message" to "Service reporting requires a Pro licence.",
                ),
                summary = "Services not available on Shop edition",
            )
        }
        val args = SkillArgsParser.parseObject(argumentsJson).getOrElse {
            return@withContext SkillResult.Failure("INVALID_ARGS", it.message ?: "Invalid JSON")
        }
        val days = SkillArgsParser.intArg(args, "days", default = 7, min = 1, max = 365)
        val (start, endExclusive) = SkillArgsParser.periodBounds(days)
        val settings = agentSettingDao.getSettingsSync() ?: AgentSetting()

        val catalogue = serviceItemDao.getAllOnce()
        val stats = ServiceInsightsHelper.buildForPeriod(
            serviceDeliveryDao,
            serviceItemDao,
            settings,
            start,
            endExclusive,
        )
        val serviceSalesOnPos = transactionDao.sumServiceSubtotalBetween(start, endExclusive)

        SkillResult.successMap(
            mapOf(
                "proEnabled" to true,
                "days" to days,
                "activeServices" to catalogue.size,
                "serviceNames" to catalogue.map { it.name },
                "deliveryCount" to (stats?.deliveryCount ?: 0),
                "serviceRevenueFromDeliveries" to (stats?.serviceRevenue ?: 0.0),
                "serviceSalesOnPos" to serviceSalesOnPos,
                "topService" to (stats?.topServiceName ?: "—"),
                "topServiceVisits" to (stats?.topServiceCount ?: 0),
                "utilisationPct" to (stats?.utilisationPct ?: 0),
            ),
            summary = buildString {
                append("${catalogue.size} services; ")
                append("${stats?.deliveryCount ?: 0} visits in $days days; ")
                append("revenue %.0f".format(stats?.serviceRevenue ?: 0.0))
            },
        )
    }

    companion object {
        const val ID = "query_services"
    }
}
