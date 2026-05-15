package com.biasharaai

import android.app.Application
import androidx.room.InvalidationTracker
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.biasharaai.agent.AgentOrchestrator
import com.biasharaai.agent.workers.FraudSentinelWorker
import com.biasharaai.data.local.db.AppDatabase
import com.biasharaai.loss.LossAlertScheduler
import com.biasharaai.loss.LossAlertWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BiasharaApp : Application(), Configuration.Provider {

    @Inject lateinit var lossAlertWorkerFactory: LossAlertWorkerFactory

    @Inject lateinit var lossAlertScheduler: LossAlertScheduler

    @Inject lateinit var agentOrchestrator: AgentOrchestrator

    @Inject lateinit var appDatabase: AppDatabase

    override fun onCreate() {
        super.onCreate()
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
}
