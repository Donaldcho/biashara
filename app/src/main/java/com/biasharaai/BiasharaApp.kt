package com.biasharaai

import android.app.Application
import androidx.work.Configuration
import com.biasharaai.loss.LossAlertScheduler
import com.biasharaai.loss.LossAlertWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BiasharaApp : Application(), Configuration.Provider {

    @Inject lateinit var lossAlertWorkerFactory: LossAlertWorkerFactory

    @Inject lateinit var lossAlertScheduler: LossAlertScheduler

    override fun onCreate() {
        super.onCreate()
        lossAlertScheduler.schedule(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(lossAlertWorkerFactory)
            .build()
}
