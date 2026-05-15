package com.biasharaai.agent.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.biasharaai.agent.AgentDecisionEngine
import com.biasharaai.agent.AgentTypes
import com.biasharaai.agent.FraudRuleEngine
import com.biasharaai.data.local.db.AgentActionDao
import com.biasharaai.data.local.db.AgentSetting
import com.biasharaai.data.local.db.AgentSettingDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A3 — Fraud Sentinel: runs [FraudRuleEngine] on enqueue. Normally triggered by Room
 * [androidx.room.InvalidationTracker] (reactive); not on a periodic schedule.
 */
class FraudSentinelWorker(
    appContext: Context,
    params: WorkerParameters,
    private val fraudRuleEngine: FraudRuleEngine,
    private val agentActionDao: AgentActionDao,
    private val agentDecisionEngine: AgentDecisionEngine,
    private val agentSettingDao: AgentSettingDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = agentSettingDao.getSettingsSync() ?: AgentSetting()
        if (!settings.masterSwitch || !settings.fraudSentinelEnabled) {
            return@withContext Result.success()
        }

        val startTime = System.currentTimeMillis()
        val detected = fraudRuleEngine.detectAll(startTime)
        var actionsGenerated = 0
        val seenTransactionIds = mutableSetOf<Long>()

        for (action in detected) {
            val txId = action.relatedEntityId
            if (txId != null) {
                if (txId in seenTransactionIds) continue
                if (agentDecisionEngine.isDuplicateAction(AgentTypes.FRAUD_SENTINEL, txId)) continue
                seenTransactionIds.add(txId)
            } else {
                if (agentDecisionEngine.isDuplicatePendingHeadline(AgentTypes.FRAUD_SENTINEL, action.headline)) {
                    continue
                }
            }
            agentActionDao.insertAction(action)
            actionsGenerated++
        }

        agentDecisionEngine.buildRunLog(
            AgentTypes.FRAUD_SENTINEL,
            startTime,
            actionsGenerated,
            "SUCCESS",
        )
        Result.success()
    }
}
