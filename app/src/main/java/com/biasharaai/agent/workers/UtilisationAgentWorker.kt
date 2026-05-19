package com.biasharaai.agent.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.biasharaai.agent.AgentTypes
import com.biasharaai.data.local.db.AgentAction
import com.biasharaai.data.local.db.AgentActionDao
import com.biasharaai.data.local.db.AgentSetting
import com.biasharaai.data.local.db.AgentSettingDao
import com.biasharaai.data.local.db.ServiceDeliveryDao
import com.biasharaai.data.local.db.ServiceItemDao
import com.biasharaai.productline.ProductLineManager
import com.biasharaai.service.UtilisationCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UtilisationAgentWorker(
    appContext: Context,
    params: WorkerParameters,
    private val productLineManager: ProductLineManager,
    private val serviceDeliveryDao: ServiceDeliveryDao,
    private val serviceItemDao: ServiceItemDao,
    private val agentSettingDao: AgentSettingDao,
    private val agentActionDao: AgentActionDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!productLineManager.isProEnabled()) return@withContext Result.success()
        val settings = agentSettingDao.getSettingsSync() ?: AgentSetting()
        if (!settings.masterSwitch || !settings.utilisationAgentEnabled) {
            return@withContext Result.success()
        }

        val utilisationPct = UtilisationCalculator.calculatePct(
            serviceDeliveryDao,
            serviceItemDao,
            settings,
        )
        val activeServices = serviceItemDao.getAllOnce()
        if (activeServices.isEmpty()) return@withContext Result.success()
        val durations = activeServices.map { it.durationMinutes }.filter { it > 0 }
        val avgDuration = if (durations.isEmpty()) 60 else durations.average().toInt().coerceAtLeast(1)
        val dailyCapacity = (settings.workingHoursPerDay * 60 / avgDuration).coerceAtLeast(1)
        val avgDailyDeliveries = serviceDeliveryDao.getDeliveriesSince(
            System.currentTimeMillis() - 7 * 86_400_000L,
        ).size / 7.0

        val threshold = settings.utilisationAlertThresholdPct
        if (utilisationPct < threshold) {
            agentActionDao.insertAction(
                AgentAction(
                    agentType = AgentTypes.UTILISATION_AGENT,
                    urgency = if (utilisationPct < 40) "HIGH" else "MEDIUM",
                    executionType = "AUTO_EXECUTE",
                    headline = "You have spare capacity — $utilisationPct% utilised",
                    detail = "You can serve ~$dailyCapacity clients/day but averaged " +
                        "${avgDailyDeliveries.toInt()} last week. Consider a promotion on your quietest day.",
                    actionVerb = "VIEW_INSIGHTS",
                    status = "PENDING",
                    actionPayload = """{"utilisationPct":$utilisationPct,"dailyCapacity":$dailyCapacity}""",
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
        Result.success()
    }
}
