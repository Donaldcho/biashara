package com.biasharaai.loss

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.ai.GemmaService
import com.biasharaai.data.local.db.AlertDao
import com.biasharaai.data.local.db.LossAlertEngine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LossAlertWorkerFactory @Inject constructor(
    private val lossAlertEngine: LossAlertEngine,
    private val alertDao: AlertDao,
    private val gemmaService: GemmaService,
    private val capabilityTier: CapabilityTier,
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
            else -> null
        }
}
