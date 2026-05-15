package com.biasharaai.agent.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.biasharaai.agent.AgentDecisionEngine
import com.biasharaai.agent.AgentTypes
import com.biasharaai.agent.StockGuardianRepository
import com.biasharaai.data.local.db.AgentActionDao
import com.biasharaai.data.local.db.AgentSetting
import com.biasharaai.data.local.db.AgentSettingDao
import com.biasharaai.data.local.db.AppSettings
import com.biasharaai.data.local.db.AppSettingsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A3 — Stock Guardian: 7-day sales pace vs stock, Room-only (all device tiers). */
class StockGuardianWorker(
    appContext: Context,
    params: WorkerParameters,
    private val stockGuardianRepository: StockGuardianRepository,
    private val agentActionDao: AgentActionDao,
    private val agentDecisionEngine: AgentDecisionEngine,
    private val agentSettingDao: AgentSettingDao,
    private val appSettingsDao: AppSettingsDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = agentSettingDao.getSettingsSync() ?: AgentSetting()
        if (!settings.masterSwitch || !settings.stockGuardianEnabled) {
            return@withContext Result.success()
        }
        val appSettings = appSettingsDao.getSettingsSync() ?: AppSettings()
        val startTime = System.currentTimeMillis()
        var actionsGenerated = 0

        val candidates = stockGuardianRepository.buildLowStockActions(
            nowMillis = startTime,
            currencySymbol = appSettings.currencySymbol,
        )
        for (action in candidates) {
            val productId = action.relatedEntityId ?: continue
            if (agentDecisionEngine.isDuplicateAction(AgentTypes.STOCK_GUARDIAN, productId)) {
                continue
            }
            agentActionDao.insertAction(action)
            actionsGenerated++
        }

        agentDecisionEngine.buildRunLog(
            AgentTypes.STOCK_GUARDIAN,
            startTime,
            actionsGenerated,
            "SUCCESS",
        )
        Result.success()
    }
}
