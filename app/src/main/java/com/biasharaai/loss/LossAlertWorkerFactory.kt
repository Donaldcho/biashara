package com.biasharaai.loss

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.biasharaai.agent.AgentDecisionEngine
import com.biasharaai.agent.AgentMutex
import com.biasharaai.agent.CoPurchaseAnalyser
import com.biasharaai.agent.CustomerPatternAnalyser
import com.biasharaai.agent.FraudRuleEngine
import com.biasharaai.agent.PricingRuleEngine
import com.biasharaai.agent.StockGuardianRepository
import com.biasharaai.agent.WeeklyReviewBuilder
import com.biasharaai.agent.workers.CashFlowSentinelWorker
import com.biasharaai.agent.workers.CustomerRelationWorker
import com.biasharaai.agent.workers.FraudSentinelWorker
import com.biasharaai.agent.workers.OpportunitySpotterWorker
import com.biasharaai.agent.workers.PricingAgentWorker
import com.biasharaai.agent.workers.StockGuardianWorker
import com.biasharaai.agent.workers.WeeklyReviewWorker
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.ai.GemmaService
import com.biasharaai.data.local.db.AgentActionDao
import com.biasharaai.data.local.db.AgentSettingDao
import com.biasharaai.data.local.db.CustomerDao
import com.biasharaai.data.local.db.DebtRepository
import com.biasharaai.data.local.db.AlertDao
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.LossAlertEngine
import com.biasharaai.data.local.db.TransactionDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LossAlertWorkerFactory @Inject constructor(
    private val lossAlertEngine: LossAlertEngine,
    private val alertDao: AlertDao,
    private val gemmaService: GemmaService,
    private val capabilityTier: CapabilityTier,
    private val agentMutex: AgentMutex,
    private val transactionDao: TransactionDao,
    private val stockGuardianRepository: StockGuardianRepository,
    private val pricingRuleEngine: PricingRuleEngine,
    private val fraudRuleEngine: FraudRuleEngine,
    private val agentActionDao: AgentActionDao,
    private val agentDecisionEngine: AgentDecisionEngine,
    private val agentSettingDao: AgentSettingDao,
    private val appSettingsDao: AppSettingsDao,
    private val customerPatternAnalyser: CustomerPatternAnalyser,
    private val debtRepository: DebtRepository,
    private val customerDao: CustomerDao,
    private val weeklyReviewBuilder: WeeklyReviewBuilder,
    private val coPurchaseAnalyser: CoPurchaseAnalyser,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        when (workerClassName) {
            LossAlertWorker::class.java.name ->
                LossAlertWorker(
                    appContext,
                    workerParameters,
                    lossAlertEngine,
                    alertDao,
                    gemmaService,
                    capabilityTier,
                )
            StockGuardianWorker::class.java.name ->
                StockGuardianWorker(
                    appContext,
                    workerParameters,
                    stockGuardianRepository,
                    agentActionDao,
                    agentDecisionEngine,
                    agentSettingDao,
                    appSettingsDao,
                )
            PricingAgentWorker::class.java.name ->
                PricingAgentWorker(
                    appContext,
                    workerParameters,
                    pricingRuleEngine,
                    agentActionDao,
                    agentDecisionEngine,
                    agentSettingDao,
                    gemmaService,
                    capabilityTier,
                    agentMutex,
                )
            CashFlowSentinelWorker::class.java.name ->
                CashFlowSentinelWorker(
                    appContext,
                    workerParameters,
                    transactionDao,
                    agentActionDao,
                    agentDecisionEngine,
                    agentSettingDao,
                    appSettingsDao,
                    gemmaService,
                    capabilityTier,
                    agentMutex,
                )
            CustomerRelationWorker::class.java.name ->
                CustomerRelationWorker(
                    appContext,
                    workerParameters,
                    customerPatternAnalyser,
                    debtRepository,
                    customerDao,
                    agentActionDao,
                    agentDecisionEngine,
                    agentSettingDao,
                    appSettingsDao,
                    gemmaService,
                    capabilityTier,
                    agentMutex,
                )
            FraudSentinelWorker::class.java.name ->
                FraudSentinelWorker(
                    appContext,
                    workerParameters,
                    fraudRuleEngine,
                    agentActionDao,
                    agentDecisionEngine,
                    agentSettingDao,
                )
            WeeklyReviewWorker::class.java.name ->
                WeeklyReviewWorker(
                    appContext,
                    workerParameters,
                    weeklyReviewBuilder,
                    gemmaService,
                    capabilityTier,
                    agentMutex,
                    agentActionDao,
                    agentDecisionEngine,
                    agentSettingDao,
                )
            OpportunitySpotterWorker::class.java.name ->
                OpportunitySpotterWorker(
                    appContext,
                    workerParameters,
                    coPurchaseAnalyser,
                    weeklyReviewBuilder,
                    gemmaService,
                    capabilityTier,
                    agentMutex,
                    agentActionDao,
                    agentDecisionEngine,
                    agentSettingDao,
                )
            else -> null
        }
}
