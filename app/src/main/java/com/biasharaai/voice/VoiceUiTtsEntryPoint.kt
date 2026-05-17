package com.biasharaai.voice

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@EntryPoint
@InstallIn(ActivityComponent::class)
interface VoiceUiTtsEntryPoint {
    fun biasharaTtsEngine(): BiasharaTtsEngine
}
