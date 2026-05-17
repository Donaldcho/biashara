package com.biasharaai.di

import com.biasharaai.loss.LossAlertWorkerFactory
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * WorkManager may read [androidx.work.Configuration] before field injection on [com.biasharaai.BiasharaApp]
 * finishes; resolve the factory via an entry point instead of a lateinit field.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkManagerEntryPoint {
    fun lossAlertWorkerFactory(): LossAlertWorkerFactory
}
