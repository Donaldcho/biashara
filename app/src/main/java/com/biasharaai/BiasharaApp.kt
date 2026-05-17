package com.biasharaai

import android.app.Application
import android.util.Log
import androidx.room.InvalidationTracker
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.biasharaai.agent.workers.LedgerAnomalyAgentWorker
import com.biasharaai.cash.workers.StorageWatchdogWorker
import com.biasharaai.ledger.workers.LedgerBackfillWorker
import com.biasharaai.ledger.workers.LedgerBalanceRecomputeWorker
import java.util.concurrent.TimeUnit
import com.biasharaai.agent.AgentOrchestrator
import com.biasharaai.agent.workers.FraudSentinelWorker
import com.biasharaai.ai.ModelRegistry
import com.biasharaai.skills.SkillRegistry
import com.biasharaai.skills.packs.SkillPackManager
import com.biasharaai.data.local.db.AppDatabase
import com.biasharaai.knowledge.KnowledgeIngestor
import com.biasharaai.di.WorkManagerEntryPoint
import com.biasharaai.loss.LossAlertScheduler
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BiasharaApp : Application(), Configuration.Provider {

    @Inject lateinit var lossAlertScheduler: LossAlertScheduler

    @Inject lateinit var agentOrchestrator: AgentOrchestrator

    @Inject lateinit var appDatabase: AppDatabase

    @Inject lateinit var modelRegistry: ModelRegistry

    @Inject lateinit var skillRegistry: SkillRegistry

    @Inject lateinit var skillPackManager: SkillPackManager

    @Inject lateinit var knowledgeIngestor: KnowledgeIngestor

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        runCatching { WorkManager.initialize(this, workManagerConfiguration) }
            .onFailure { Log.e(TAG, "WorkManager.initialize failed", it) }
        appScope.launch {
            runCatching { modelRegistry.bootstrap() }
                .onFailure { Log.e(TAG, "modelRegistry.bootstrap failed", it) }
            runCatching { skillRegistry.bootstrap() }
                .onFailure { Log.e(TAG, "skillRegistry.bootstrap failed", it) }
            runCatching { skillPackManager.bootstrap() }
                .onFailure { Log.e(TAG, "skillPackManager.bootstrap failed", it) }
            runCatching { knowledgeIngestor.ingestAll() }
                .onFailure { Log.e(TAG, "knowledgeIngestor.ingestAll failed", it) }
        }
        runCatching { lossAlertScheduler.schedule(this) }
            .onFailure { Log.e(TAG, "lossAlertScheduler.schedule failed", it) }
        runCatching { agentOrchestrator.scheduleAll() }
            .onFailure { Log.e(TAG, "agentOrchestrator.scheduleAll failed", it) }
        appScope.launch(Dispatchers.IO) {
            runCatching { scheduleLedgerWorkers() }
                .onFailure { Log.e(TAG, "scheduleLedgerWorkers failed", it) }
        }
        runCatching { registerFraudSentinelInvalidationObserver() }
            .onFailure { Log.e(TAG, "Fraud invalidation observer failed", it) }
    }

    private fun scheduleLedgerWorkers() {
        val wm = WorkManager.getInstance(this)
        val watchdog = PeriodicWorkRequestBuilder<StorageWatchdogWorker>(1, TimeUnit.DAYS).build()
        wm.enqueueUniquePeriodicWork(
            StorageWatchdogWorker.UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            watchdog,
        )
        val backfill = OneTimeWorkRequestBuilder<LedgerBackfillWorker>().build()
        wm.enqueueUniqueWork(
            LedgerBackfillWorker.UNIQUE_WORK,
            ExistingWorkPolicy.KEEP,
            backfill,
        )
        val recompute = PeriodicWorkRequestBuilder<LedgerBalanceRecomputeWorker>(1, TimeUnit.DAYS)
            .build()
        wm.enqueueUniquePeriodicWork(
            LedgerBalanceRecomputeWorker.UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            recompute,
        )
        val anomaly = PeriodicWorkRequestBuilder<LedgerAnomalyAgentWorker>(1, TimeUnit.DAYS)
            .build()
        wm.enqueueUniquePeriodicWork(
            LedgerAnomalyAgentWorker.UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            anomaly,
        )
    }

    private fun registerFraudSentinelInvalidationObserver() {
        appDatabase.invalidationTracker.addObserver(
            object : InvalidationTracker.Observer("transactions", "products") {
                override fun onInvalidated(tables: Set<String>) {
                    val fraudConstraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                    val request = OneTimeWorkRequestBuilder<FraudSentinelWorker>()
                        .setConstraints(fraudConstraints)
                        .build()
                    WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                        AgentOrchestrator.UNIQUE_FRAUD_REACTIVE,
                        ExistingWorkPolicy.KEEP,
                        request,
                    )
                }
            },
        )
    }

    override fun onTerminate() {
        super.onTerminate()
        appScope.cancel()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                EntryPointAccessors.fromApplication(this, WorkManagerEntryPoint::class.java)
                    .lossAlertWorkerFactory(),
            )
            .build()

    private companion object {
        private const val TAG = "BiasharaApp"
    }
}
