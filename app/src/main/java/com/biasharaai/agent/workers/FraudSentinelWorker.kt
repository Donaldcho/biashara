package com.biasharaai.agent.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.biasharaai.agent.AgentAnomalySkillMapper
import com.biasharaai.agent.AgentDecisionEngine
import com.biasharaai.agent.AgentTypes
import com.biasharaai.data.local.db.AgentActionDao
import com.biasharaai.data.local.db.AgentSetting
import com.biasharaai.data.local.db.AgentSettingDao
import com.biasharaai.cash.CashAnomalySignal
import com.biasharaai.cash.CashEvidenceAnomalyDetector
import com.biasharaai.data.local.db.AgentAction
import com.biasharaai.skills.SkillExecutor
import com.biasharaai.skills.builtin.DetectAnomalySkill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A3 / X9 — Fraud Sentinel via [DetectAnomalySkill] + [CashEvidenceAnomalyDetector].
 * Normally triggered by Room [androidx.room.InvalidationTracker]; not on a periodic schedule.
 */
class FraudSentinelWorker(
    appContext: Context,
    params: WorkerParameters,
    private val skillExecutor: SkillExecutor,
    private val anomalyMapper: AgentAnomalySkillMapper,
    private val agentActionDao: AgentActionDao,
    private val agentDecisionEngine: AgentDecisionEngine,
    private val agentSettingDao: AgentSettingDao,
    private val cashEvidenceAnomalyDetector: CashEvidenceAnomalyDetector? = null,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = agentSettingDao.getSettingsSync() ?: AgentSetting()
        if (!settings.masterSwitch || !settings.fraudSentinelEnabled) {
            return@withContext Result.success()
        }

        val startTime = System.currentTimeMillis()
        val skillResult = skillExecutor.execute(DetectAnomalySkill.ID, "{}")
        val detected = anomalyMapper.actionsFromSkillResult(skillResult, startTime)
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

        // Cash evidence anomaly rules
        val cashSignals = runCatching { cashEvidenceAnomalyDetector?.detectAll() ?: emptyList() }.getOrDefault(emptyList())
        for (signal in cashSignals) {
            val headline = "[Cash] ${signal.rule}: ${signal.detail.take(120)}"
            if (!agentDecisionEngine.isDuplicatePendingHeadline(AgentTypes.FRAUD_SENTINEL, headline)) {
                agentActionDao.insertAction(
                    AgentAction(
                        agentType = AgentTypes.FRAUD_SENTINEL,
                        urgency = when (signal.severity) {
                            CashAnomalySignal.Severity.HIGH -> "CRITICAL"
                            CashAnomalySignal.Severity.MEDIUM -> "WARNING"
                            CashAnomalySignal.Severity.LOW -> "INFO"
                        },
                        headline = headline,
                        detail = signal.detail,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                actionsGenerated++
            }
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
