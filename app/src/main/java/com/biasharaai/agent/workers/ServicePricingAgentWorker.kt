package com.biasharaai.agent.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.biasharaai.agent.AgentTypes
import com.biasharaai.data.local.db.AgentAction
import com.biasharaai.data.local.db.AgentActionDao
import com.biasharaai.data.local.db.AgentSetting
import com.biasharaai.data.local.db.AgentSettingDao
import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.data.local.db.LedgerEntryDao
import com.biasharaai.data.local.db.ServiceItemDao
import com.biasharaai.productline.ProductLineManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ServicePricingAgentWorker(
    appContext: Context,
    params: WorkerParameters,
    private val productLineManager: ProductLineManager,
    private val serviceItemDao: ServiceItemDao,
    private val ledgerEntryDao: LedgerEntryDao,
    private val agentActionDao: AgentActionDao,
    private val agentSettingDao: AgentSettingDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!productLineManager.isProEnabled()) return@withContext Result.success()
        val settings = agentSettingDao.getSettingsSync() ?: AgentSetting()
        if (!settings.masterSwitch || !settings.servicePricingAgentEnabled) {
            return@withContext Result.success()
        }

        val ninetyDaysAgo = System.currentTimeMillis() - 90 * 86_400_000L
        val staleServices = serviceItemDao.getServicesNotUpdatedSince(ninetyDaysAgo)
        if (staleServices.isEmpty()) return@withContext Result.success()

        val sixMonthsAgo = System.currentTimeMillis() - 180 * 86_400_000L
        val threeMonthsAgo = System.currentTimeMillis() - 90 * 86_400_000L
        val now = System.currentTimeMillis()

        val recentExpenses = ledgerEntryDao.getTotalForDirection(
            LedgerDirection.MONEY_OUT.name,
            threeMonthsAgo,
            now,
        ) ?: 0.0
        val olderExpenses = ledgerEntryDao.getTotalForDirection(
            LedgerDirection.MONEY_OUT.name,
            sixMonthsAgo,
            threeMonthsAgo,
        ) ?: 0.0

        val expenseChange = if (olderExpenses > 0) {
            ((recentExpenses - olderExpenses) / olderExpenses * 100).toInt()
        } else {
            0
        }
        if (expenseChange < 10) return@withContext Result.success()

        for (service in staleServices) {
            agentActionDao.insertAction(
                AgentAction(
                    agentType = AgentTypes.SERVICE_PRICING_AGENT,
                    urgency = "MEDIUM",
                    executionType = "REQUIRES_APPROVAL",
                    headline = "${service.name} price not reviewed in 90+ days",
                    detail = "Your costs have risen ~$expenseChange% over 3 months. " +
                        "Consider reviewing ${service.name} (currently ${service.basePrice}).",
                    actionVerb = "EDIT_SERVICE",
                    status = "PENDING",
                    actionPayload = """{"serviceItemId":${service.id}}""",
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
        Result.success()
    }
}
