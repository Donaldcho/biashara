package com.biasharaai.loss

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.biasharaai.agent.AgentAnomalySkillMapper
import com.biasharaai.agent.AgentDecisionEngine
import com.biasharaai.agent.AgentLoopRunner
import com.biasharaai.agent.AgentPromptComposer
import com.biasharaai.agent.CoPurchaseAnalyser
import com.biasharaai.agent.CustomerPatternAnalyser
import com.biasharaai.agent.PricingRuleEngine
import com.biasharaai.agent.StockGuardianRepository
import com.biasharaai.agent.WeeklyReviewBuilder
import com.biasharaai.analytics.SalesIntelligenceRepository
import com.biasharaai.agent.workers.CashFlowSentinelWorker
import com.biasharaai.agent.workers.CustomerRelationWorker
import com.biasharaai.agent.workers.FraudSentinelWorker
import com.biasharaai.agent.workers.OpportunitySpotterWorker
import com.biasharaai.agent.workers.PricingAgentWorker
import com.biasharaai.agent.workers.StockGuardianWorker
import com.biasharaai.agent.workers.LedgerAnomalyAgentWorker
import com.biasharaai.agent.workers.NoShowTrackerWorker
import com.biasharaai.agent.workers.ServicePricingAgentWorker
import com.biasharaai.agent.workers.UtilisationAgentWorker
import com.biasharaai.agent.workers.VoucherExpiryAgentWorker
import com.biasharaai.agent.workers.WeeklyReviewWorker
import com.biasharaai.data.local.db.AppointmentDao
import com.biasharaai.data.local.db.ServiceDeliveryDao
import com.biasharaai.data.local.db.ServiceItemDao
import com.biasharaai.data.local.db.ServiceVoucherDao
import com.biasharaai.productline.ProductLineManager
import com.biasharaai.cash.workers.StorageWatchdogWorker
import com.biasharaai.data.local.db.CashMovementEvidenceDao
import com.biasharaai.data.local.db.LedgerEntryDao
import com.biasharaai.ledger.LedgerBackfillRunner
import com.biasharaai.ledger.LedgerBalanceRecomputer
import com.biasharaai.ledger.intelligence.LedgerIntelligenceRepository
import com.biasharaai.ledger.workers.LedgerBackfillWorker
import com.biasharaai.ledger.workers.LedgerBalanceRecomputeWorker
import com.biasharaai.ai.ActiveModelStore
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.data.local.db.AgentActionDao
import com.biasharaai.data.local.db.AgentSettingDao
import com.biasharaai.data.local.db.CustomerDao
import com.biasharaai.data.local.db.DebtRepository
import com.biasharaai.data.local.db.AlertDao
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.LossAlertEngine
import com.biasharaai.data.local.db.TransactionDao
import com.biasharaai.ai.ForecastCalibrationResolver
import com.biasharaai.cash.CashEvidenceAnomalyDetector
import com.biasharaai.data.local.db.BusinessKpiSnapshotDao
import com.biasharaai.enterprise.EnterpriseAuditRepository
import com.biasharaai.enterprise.EnterpriseSyncWorker
import com.biasharaai.knowledge.BusinessMemoryExtractor
import com.biasharaai.skills.SkillExecutor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LossAlertWorkerFactory @Inject constructor(
    private val lossAlertEngine: LossAlertEngine,
    private val alertDao: AlertDao,
    private val agentLoopRunner: AgentLoopRunner,
    private val agentPromptComposer: AgentPromptComposer,
    private val capabilityTier: CapabilityTier,
    private val transactionDao: TransactionDao,
    private val salesIntelligence: SalesIntelligenceRepository,
    private val stockGuardianRepository: StockGuardianRepository,
    private val pricingRuleEngine: PricingRuleEngine,
    private val skillExecutor: SkillExecutor,
    private val anomalyMapper: AgentAnomalySkillMapper,
    private val agentActionDao: AgentActionDao,
    private val agentDecisionEngine: AgentDecisionEngine,
    private val agentSettingDao: AgentSettingDao,
    private val appSettingsDao: AppSettingsDao,
    private val customerPatternAnalyser: CustomerPatternAnalyser,
    private val debtRepository: DebtRepository,
    private val customerDao: CustomerDao,
    private val weeklyReviewBuilder: WeeklyReviewBuilder,
    private val coPurchaseAnalyser: CoPurchaseAnalyser,
    private val activeModelStore: ActiveModelStore,
    private val ledgerBackfillRunner: LedgerBackfillRunner,
    private val ledgerBalanceRecomputer: LedgerBalanceRecomputer,
    private val ledgerEntryDao: LedgerEntryDao,
    private val ledgerIntelligenceRepository: LedgerIntelligenceRepository,
    private val cashMovementEvidenceDao: CashMovementEvidenceDao,
    private val cashEvidenceAnomalyDetector: CashEvidenceAnomalyDetector,
    private val productLineManager: ProductLineManager,
    private val serviceVoucherDao: ServiceVoucherDao,
    private val serviceItemDao: ServiceItemDao,
    private val serviceDeliveryDao: ServiceDeliveryDao,
    private val appointmentDao: AppointmentDao,
    private val kpiSnapshotDao: BusinessKpiSnapshotDao,
    private val businessMemoryExtractor: BusinessMemoryExtractor,
    private val forecastCalibrationResolver: ForecastCalibrationResolver,
    private val enterpriseAuditRepository: EnterpriseAuditRepository,
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
                    agentLoopRunner,
                    capabilityTier,
                    agentPromptComposer,
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
                    agentLoopRunner,
                    capabilityTier,
                    agentPromptComposer,
                )
            CashFlowSentinelWorker::class.java.name ->
                CashFlowSentinelWorker(
                    appContext,
                    workerParameters,
                    transactionDao,
                    salesIntelligence,
                    agentActionDao,
                    agentDecisionEngine,
                    agentSettingDao,
                    appSettingsDao,
                    agentLoopRunner,
                    capabilityTier,
                    ledgerIntelligenceRepository,
                    agentPromptComposer,
                    productLineManager,
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
                    agentLoopRunner,
                    capabilityTier,
                    agentPromptComposer,
                )
            FraudSentinelWorker::class.java.name ->
                FraudSentinelWorker(
                    appContext,
                    workerParameters,
                    skillExecutor,
                    anomalyMapper,
                    agentActionDao,
                    agentDecisionEngine,
                    agentSettingDao,
                    cashEvidenceAnomalyDetector,
                )
            WeeklyReviewWorker::class.java.name ->
                WeeklyReviewWorker(
                    appContext,
                    workerParameters,
                    weeklyReviewBuilder,
                    activeModelStore,
                    agentLoopRunner,
                    capabilityTier,
                    agentActionDao,
                    agentDecisionEngine,
                    agentSettingDao,
                    productLineManager,
                    serviceDeliveryDao,
                    serviceItemDao,
                    agentPromptComposer,
                    kpiSnapshotDao,
                    businessMemoryExtractor,
                    forecastCalibrationResolver,
                )
            OpportunitySpotterWorker::class.java.name ->
                OpportunitySpotterWorker(
                    appContext,
                    workerParameters,
                    coPurchaseAnalyser,
                    weeklyReviewBuilder,
                    activeModelStore,
                    agentLoopRunner,
                    capabilityTier,
                    agentActionDao,
                    agentDecisionEngine,
                    agentSettingDao,
                    agentPromptComposer,
                )
            LedgerBackfillWorker::class.java.name ->
                LedgerBackfillWorker(
                    appContext,
                    workerParameters,
                    ledgerBackfillRunner,
                )
            LedgerBalanceRecomputeWorker::class.java.name ->
                LedgerBalanceRecomputeWorker(
                    appContext,
                    workerParameters,
                    ledgerBalanceRecomputer,
                )
            LedgerAnomalyAgentWorker::class.java.name ->
                LedgerAnomalyAgentWorker(
                    appContext,
                    workerParameters,
                    ledgerEntryDao,
                    agentActionDao,
                    agentDecisionEngine,
                    agentSettingDao,
                    appSettingsDao,
                )
            StorageWatchdogWorker::class.java.name ->
                StorageWatchdogWorker(
                    appContext,
                    workerParameters,
                    cashMovementEvidenceDao,
                )
            VoucherExpiryAgentWorker::class.java.name ->
                VoucherExpiryAgentWorker(
                    appContext,
                    workerParameters,
                    productLineManager,
                    serviceVoucherDao,
                    serviceItemDao,
                    customerDao,
                    agentActionDao,
                    agentDecisionEngine,
                    agentSettingDao,
                )
            UtilisationAgentWorker::class.java.name ->
                UtilisationAgentWorker(
                    appContext,
                    workerParameters,
                    productLineManager,
                    serviceDeliveryDao,
                    serviceItemDao,
                    agentSettingDao,
                    agentActionDao,
                )
            NoShowTrackerWorker::class.java.name ->
                NoShowTrackerWorker(
                    appContext,
                    workerParameters,
                    productLineManager,
                    appointmentDao,
                    agentActionDao,
                )
            ServicePricingAgentWorker::class.java.name ->
                ServicePricingAgentWorker(
                    appContext,
                    workerParameters,
                    productLineManager,
                    serviceItemDao,
                    ledgerEntryDao,
                    agentActionDao,
                    agentSettingDao,
                )
            EnterpriseSyncWorker::class.java.name ->
                EnterpriseSyncWorker(
                    appContext,
                    workerParameters,
                    enterpriseAuditRepository,
                )
            else -> null
        }
}
