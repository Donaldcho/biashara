package com.biasharaai.agent

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.biasharaai.agent.workers.CashFlowSentinelWorker
import com.biasharaai.agent.workers.CustomerRelationWorker
import com.biasharaai.agent.workers.FraudSentinelWorker
import com.biasharaai.agent.workers.OpportunitySpotterWorker
import com.biasharaai.agent.workers.PricingAgentWorker
import com.biasharaai.agent.workers.StockGuardianWorker
import com.biasharaai.agent.workers.NoShowTrackerWorker
import com.biasharaai.agent.workers.ServicePricingAgentWorker
import com.biasharaai.agent.workers.UtilisationAgentWorker
import com.biasharaai.agent.workers.VoucherExpiryAgentWorker
import com.biasharaai.agent.workers.WeeklyReviewWorker
import com.biasharaai.data.local.db.AgentSetting
import com.biasharaai.productline.ProductLineManager
import com.biasharaai.data.local.db.AgentSettingDao
import com.biasharaai.ai.CapabilityTier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import java.time.Duration
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Master scheduler for Phase 4 agents — reads [AgentSetting] from Room and enqueues / cancels
 * [WorkManager] periodic jobs. **A3+** workers re-read settings inside `doWork` before side effects.
 */
@Singleton
class AgentOrchestrator @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val agentSettingDao: AgentSettingDao,
    private val capabilityTier: CapabilityTier,
    private val productLineManager: ProductLineManager,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val wm get() = WorkManager.getInstance(appContext)

    /**
     * Loads current [AgentSetting] (defaults if missing) and applies every agent schedule.
     * Safe to call from [android.app.Application.onCreate] — work is dispatched off the main thread.
     */
    fun scheduleAll() {
        scope.launch(Dispatchers.IO) {
            val settings = agentSettingDao.getSettingsSync() ?: AgentSetting()
            applySchedules(settings)
        }
    }

    private fun applySchedules(settings: AgentSetting) {
        scheduleStockGuardian(settings)
        schedulePricingAgent(settings)
        scheduleCashFlow(settings)
        scheduleCustomerRelation(settings)
        // Fraud Sentinel is reactive (Room InvalidationTracker → one-time work), not periodic.
        scheduleFraudSentinel(settings)
        scheduleWeeklyReview(settings)
        scheduleOpportunitySpotter(settings)
        scheduleVoucherExpiryAgent(settings)
        scheduleUtilisationAgent(settings)
        scheduleNoShowTracker(settings)
        scheduleServicePricingAgent(settings)
    }

    fun scheduleUtilisationAgent(settings: AgentSetting) {
        if (!settings.masterSwitch || !productLineManager.isProEnabled() || !settings.utilisationAgentEnabled) {
            wm.cancelUniqueWork(UNIQUE_UTILISATION_AGENT)
            return
        }
        val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()
        val request = PeriodicWorkRequestBuilder<UtilisationAgentWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(UNIQUE_UTILISATION_AGENT, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun scheduleNoShowTracker(settings: AgentSetting) {
        if (!settings.masterSwitch || !productLineManager.isProEnabled() || !settings.noShowTrackerEnabled) {
            wm.cancelUniqueWork(UNIQUE_NO_SHOW_TRACKER)
            return
        }
        val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()
        val request = PeriodicWorkRequestBuilder<NoShowTrackerWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setInitialDelay(nextDelayToLocalHourMillis(21), TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(UNIQUE_NO_SHOW_TRACKER, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun scheduleServicePricingAgent(settings: AgentSetting) {
        if (!settings.masterSwitch || !productLineManager.isProEnabled() || !settings.servicePricingAgentEnabled) {
            wm.cancelUniqueWork(UNIQUE_SERVICE_PRICING_AGENT)
            return
        }
        val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()
        val request = PeriodicWorkRequestBuilder<ServicePricingAgentWorker>(7, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(UNIQUE_SERVICE_PRICING_AGENT, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun scheduleVoucherExpiryAgent(settings: AgentSetting) {
        if (!settings.masterSwitch || !productLineManager.isProEnabled()) {
            wm.cancelUniqueWork(UNIQUE_VOUCHER_EXPIRY)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val request = PeriodicWorkRequestBuilder<VoucherExpiryAgentWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(
            UNIQUE_VOUCHER_EXPIRY,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun scheduleStockGuardian(settings: AgentSetting) {
        if (!settings.masterSwitch || !settings.stockGuardianEnabled) {
            wm.cancelUniqueWork(UNIQUE_STOCK_GUARDIAN)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val request = PeriodicWorkRequestBuilder<StockGuardianWorker>(4, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(
            UNIQUE_STOCK_GUARDIAN,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun schedulePricingAgent(settings: AgentSetting) {
        if (!settings.masterSwitch || !settings.pricingAgentEnabled) {
            wm.cancelUniqueWork(UNIQUE_PRICING_AGENT)
            return
        }
        val staggerHour = (settings.dailySummaryHour + 4) % 24
        val initialDelay = nextDelayToLocalHourMillis(staggerHour)
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val request = PeriodicWorkRequestBuilder<PricingAgentWorker>(1, TimeUnit.DAYS, 30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(UNIQUE_PRICING_AGENT, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun scheduleCashFlow(settings: AgentSetting) {
        if (!settings.masterSwitch || !settings.cashFlowEnabled) {
            wm.cancelUniqueWork(UNIQUE_CASH_FLOW)
            return
        }
        val initialDelay = nextDelayToLocalHourMillis(settings.dailySummaryHour)
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val request = PeriodicWorkRequestBuilder<CashFlowSentinelWorker>(1, TimeUnit.DAYS, 30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(UNIQUE_CASH_FLOW, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun scheduleCustomerRelation(settings: AgentSetting) {
        if (!settings.masterSwitch || !settings.customerRelationEnabled) {
            wm.cancelUniqueWork(UNIQUE_CUSTOMER_RELATION)
            return
        }
        val staggerHour = (settings.dailySummaryHour + 2) % 24
        val initialDelay = nextDelayToLocalHourMillis(staggerHour)
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val request = PeriodicWorkRequestBuilder<CustomerRelationWorker>(1, TimeUnit.DAYS, 30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(UNIQUE_CUSTOMER_RELATION, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Cancels legacy periodic fraud work; live installs use InvalidationTracker + one-time [UNIQUE_FRAUD_REACTIVE]. */
    fun scheduleFraudSentinel(settings: AgentSetting) {
        wm.cancelUniqueWork(UNIQUE_FRAUD_SENTINEL)
        if (!settings.masterSwitch || !settings.fraudSentinelEnabled) {
            wm.cancelUniqueWork(UNIQUE_FRAUD_REACTIVE)
        }
    }

    fun scheduleWeeklyReview(settings: AgentSetting) {
        if (!settings.masterSwitch || !settings.weeklyReviewEnabled || capabilityTier != CapabilityTier.FULL_AI) {
            wm.cancelUniqueWork(UNIQUE_WEEKLY_REVIEW)
            return
        }
        val zone = ZoneId.systemDefault()
        val weeklyDelay = nextDelayToIsoDayAndHourMillis(settings.weeklyReviewDayOfWeek, settings.dailySummaryHour, zone)
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val request = PeriodicWorkRequestBuilder<WeeklyReviewWorker>(7, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setInitialDelay(weeklyDelay, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(UNIQUE_WEEKLY_REVIEW, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun scheduleOpportunitySpotter(settings: AgentSetting) {
        if (!settings.masterSwitch || !settings.opportunitySpotterEnabled || capabilityTier != CapabilityTier.FULL_AI) {
            wm.cancelUniqueWork(UNIQUE_OPPORTUNITY_SPOTTER)
            return
        }
        val zone = ZoneId.systemDefault()
        val weeklyDelay = nextDelayToIsoDayAndHourMillis(settings.weeklyReviewDayOfWeek, settings.dailySummaryHour, zone)
        val oppDelay = weeklyDelay + TimeUnit.MINUTES.toMillis(30)
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val request = PeriodicWorkRequestBuilder<OpportunitySpotterWorker>(7, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setInitialDelay(oppDelay, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(UNIQUE_OPPORTUNITY_SPOTTER, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Pull-to-refresh on the agent feed: enqueue one-time runs for every **enabled** agent
     * (stub workers no-op until later prompts).
     */
    fun runAllNow() {
        scope.launch(Dispatchers.IO) {
            val s = agentSettingDao.getSettingsSync() ?: AgentSetting()
            if (!s.masterSwitch) return@launch
            val batteryOkNetworkFree = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
            val fraudReactive = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
            val weeklyHeavy = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresBatteryNotLow(true)
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
            if (s.stockGuardianEnabled) {
                wm.enqueueUniqueWork(
                    UNIQUE_RUN_NOW_STOCK,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<StockGuardianWorker>()
                        .setConstraints(batteryOkNetworkFree)
                        .build(),
                )
            }
            if (s.pricingAgentEnabled) {
                wm.enqueueUniqueWork(
                    UNIQUE_RUN_NOW_PRICING,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<PricingAgentWorker>()
                        .setConstraints(batteryOkNetworkFree)
                        .build(),
                )
            }
            if (s.cashFlowEnabled) {
                wm.enqueueUniqueWork(
                    UNIQUE_RUN_NOW_CASH_FLOW,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<CashFlowSentinelWorker>()
                        .setConstraints(batteryOkNetworkFree)
                        .build(),
                )
            }
            if (s.customerRelationEnabled) {
                wm.enqueueUniqueWork(
                    UNIQUE_RUN_NOW_CUSTOMER_RELATION,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<CustomerRelationWorker>()
                        .setConstraints(batteryOkNetworkFree)
                        .build(),
                )
            }
            if (s.fraudSentinelEnabled) {
                wm.enqueueUniqueWork(
                    UNIQUE_FRAUD_REACTIVE,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<FraudSentinelWorker>()
                        .setConstraints(fraudReactive)
                        .build(),
                )
            }
            if (s.weeklyReviewEnabled && capabilityTier == CapabilityTier.FULL_AI) {
                wm.enqueueUniqueWork(
                    UNIQUE_RUN_NOW_WEEKLY_REVIEW,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<WeeklyReviewWorker>()
                        .setConstraints(weeklyHeavy)
                        .build(),
                )
            }
            if (s.opportunitySpotterEnabled && capabilityTier == CapabilityTier.FULL_AI) {
                wm.enqueueUniqueWork(
                    UNIQUE_RUN_NOW_OPPORTUNITY,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<OpportunitySpotterWorker>()
                        .setConstraints(weeklyHeavy)
                        .build(),
                )
            }
        }
    }

    companion object {
        const val UNIQUE_STOCK_GUARDIAN = "stock_guardian"
        const val UNIQUE_PRICING_AGENT = "pricing_agent"
        const val UNIQUE_CASH_FLOW = "cash_flow"
        const val UNIQUE_CUSTOMER_RELATION = "customer_relation"
        const val UNIQUE_FRAUD_SENTINEL = "fraud_sentinel"
        const val UNIQUE_FRAUD_REACTIVE = "fraud_check"
        const val UNIQUE_WEEKLY_REVIEW = "weekly_review"
        const val UNIQUE_OPPORTUNITY_SPOTTER = "opportunity_spotter"
        const val UNIQUE_VOUCHER_EXPIRY = "voucher_expiry_agent"
        const val UNIQUE_UTILISATION_AGENT = "utilisation_agent"
        const val UNIQUE_NO_SHOW_TRACKER = "no_show_tracker"
        const val UNIQUE_SERVICE_PRICING_AGENT = "service_pricing_agent"

        internal const val UNIQUE_RUN_NOW_STOCK = "agent_run_now_stock"
        internal const val UNIQUE_RUN_NOW_PRICING = "agent_run_now_pricing"
        internal const val UNIQUE_RUN_NOW_CASH_FLOW = "agent_run_now_cash_flow"
        internal const val UNIQUE_RUN_NOW_CUSTOMER_RELATION = "agent_run_now_customer_relation"
        internal const val UNIQUE_RUN_NOW_WEEKLY_REVIEW = "agent_run_now_weekly_review"
        internal const val UNIQUE_RUN_NOW_OPPORTUNITY = "agent_run_now_opportunity"
    }
}

private fun nextDelayToLocalHourMillis(hourOfDay: Int): Long {
    val zone = ZoneId.systemDefault()
    val now = ZonedDateTime.now(zone)
    var target = now.withHour(hourOfDay).withMinute(0).withSecond(0).withNano(0)
    if (!target.isAfter(now)) {
        target = target.plusDays(1)
    }
    return Duration.between(now, target).toMillis()
}

/**
 * Next wall-clock hit of [dayOfWeekIso] (1 = Monday … 7 = Sunday, ISO-8601) at [hourOfDay], local zone.
 */
private fun nextDelayToIsoDayAndHourMillis(dayOfWeekIso: Int, hourOfDay: Int, zone: ZoneId): Long {
    val now = ZonedDateTime.now(zone)
    val dow = DayOfWeek.of(dayOfWeekIso.coerceIn(1, 7))
    var target = now.with(dow).withHour(hourOfDay).withMinute(0).withSecond(0).withNano(0)
    while (!target.isAfter(now)) {
        target = target.plusWeeks(1)
    }
    return Duration.between(now, target).toMillis()
}
