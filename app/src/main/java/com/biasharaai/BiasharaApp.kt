package com.biasharaai

import android.app.Application
import android.util.Log
import androidx.room.InvalidationTracker
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.biasharaai.agent.AgentOrchestrator
import com.biasharaai.agent.workers.FraudSentinelWorker
import com.biasharaai.ai.ModelRegistry
import com.biasharaai.skills.SkillRegistry
import com.biasharaai.skills.packs.SkillPackManager
import com.biasharaai.data.local.db.AppDatabase
import com.biasharaai.loss.LossAlertScheduler
import com.biasharaai.loss.LossAlertWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BiasharaApp : Application(), Configuration.Provider {

    @Inject lateinit var lossAlertWorkerFactory: LossAlertWorkerFactory

    @Inject lateinit var lossAlertScheduler: LossAlertScheduler

    @Inject lateinit var agentOrchestrator: AgentOrchestrator

    @Inject lateinit var appDatabase: AppDatabase

    @Inject lateinit var modelRegistry: ModelRegistry

    @Inject lateinit var skillRegistry: SkillRegistry

    @Inject lateinit var skillPackManager: SkillPackManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            runCatching { modelRegistry.bootstrap() }
                .onFailure { Log.e(TAG, "modelRegistry.bootstrap failed", it) }
            runCatching { skillRegistry.bootstrap() }
                .onFailure { Log.e(TAG, "skillRegistry.bootstrap failed", it) }
            runCatching { skillPackManager.bootstrap() }
                .onFailure { Log.e(TAG, "skillPackManager.bootstrap failed", it) }
        }
        lossAlertScheduler.schedule(this)
        agentOrchestrator.scheduleAll()
        registerFraudSentinelInvalidationObserver()
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
                        ExistingWorkPolicy.REPLACE,
                        request,
                    )
                }
            },
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(lossAlertWorkerFactory)
            .build()

    private companion object {
        private const val TAG = "BiasharaApp"
    }
}
