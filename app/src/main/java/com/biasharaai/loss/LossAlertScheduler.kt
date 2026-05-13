package com.biasharaai.loss

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LossAlertScheduler @Inject constructor() {

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<LossAlertWorker>(24, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        const val WORK_NAME = "loss_alert_scan"
    }
}
