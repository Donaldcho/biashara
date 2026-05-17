package com.biasharaai.agent.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.biasharaai.agent.AgentActionBuilder
import com.biasharaai.agent.AgentDecisionEngine
import com.biasharaai.agent.AgentTypes
import com.biasharaai.data.local.db.AgentActionDao
import com.biasharaai.data.local.db.AgentSetting
import com.biasharaai.data.local.db.AgentSettingDao
import com.biasharaai.data.local.db.AppSettings
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.data.local.db.LedgerEntryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase 9 L8 — flags unusual ledger patterns (large single outflow, negative balance).
 */
class LedgerAnomalyAgentWorker(
    appContext: Context,
    params: WorkerParameters,
    private val ledgerEntryDao: LedgerEntryDao,
    private val agentActionDao: AgentActionDao,
    private val agentDecisionEngine: AgentDecisionEngine,
    private val agentSettingDao: AgentSettingDao,
    private val appSettingsDao: AppSettingsDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = agentSettingDao.getSettingsSync() ?: AgentSetting()
        if (!settings.masterSwitch || !settings.fraudSentinelEnabled) {
            return@withContext Result.success()
        }

        val startWall = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val balance = ledgerEntryDao.getCurrentBalance() ?: 0.0
        val moneyOut = ledgerEntryDao.getTotalForDirection(LedgerDirection.MONEY_OUT.name, start, end) ?: 0.0
        val moneyIn = ledgerEntryDao.getTotalForDirection(LedgerDirection.MONEY_IN.name, start, end) ?: 0.0
        val app = appSettingsDao.getSettingsSync() ?: AppSettings()
        val currency = app.currencySymbol

        val anomalies = mutableListOf<String>()
        if (balance < 0) {
            anomalies += "Running balance is negative ($currency${"%.0f".format(balance)})"
        }
        if (moneyOut > 0 && moneyIn > 0 && moneyOut > moneyIn * 2) {
            anomalies += "Today's money out (${"%.0f".format(moneyOut)}) is more than double money in"
        }
        if (moneyOut > 50_000 && moneyIn < moneyOut * 0.25) {
            anomalies += "Large outflow today without matching income"
        }

        if (anomalies.isEmpty()) {
            agentDecisionEngine.buildRunLog(AgentTypes.LEDGER_ANOMALY, startWall, 0, "NO_ANOMALY")
            return@withContext Result.success()
        }

        if (agentDecisionEngine.isDuplicateAction(AgentTypes.LEDGER_ANOMALY, start)) {
            agentDecisionEngine.buildRunLog(AgentTypes.LEDGER_ANOMALY, startWall, 0, "SKIPPED_DUPLICATE")
            return@withContext Result.success()
        }

        val detail = anomalies.joinToString(". ")
        val urgency = if (balance < 0) "HIGH" else "MEDIUM"
        val action = AgentActionBuilder.ledgerAnomaly(
            urgency = urgency,
            headline = "Ledger check: review today's cash movements",
            detail = detail,
            dayStartMillis = start,
            nowMillis = startWall,
        )
        agentActionDao.insertAction(action)
        agentDecisionEngine.buildRunLog(AgentTypes.LEDGER_ANOMALY, startWall, 1, "SUCCESS")
        Result.success()
    }

    companion object {
        const val UNIQUE_WORK = "ledger_anomaly_agent"
    }
}
