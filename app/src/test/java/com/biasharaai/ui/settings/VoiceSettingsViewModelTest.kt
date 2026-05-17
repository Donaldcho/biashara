package com.biasharaai.ui.settings

import android.content.Context
import com.biasharaai.R
import com.biasharaai.ai.VoiceInputPreferences
import com.biasharaai.data.local.db.AppSettings
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.voice.BiasharaTtsEngine
import com.biasharaai.voice.WhisperTranscriber
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceSettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var dao: AppSettingsDao
    private lateinit var prefs: VoiceInputPreferences
    private lateinit var whisper: WhisperTranscriber
    private lateinit var tts: BiasharaTtsEngine
    private lateinit var context: Context

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dao = mockk(relaxed = true)
        every { dao.getSettings() } returns flowOf(AppSettings())
        every { dao.getSettingsSync() } returns AppSettings(whisperModelId = "whisper-base")
        coEvery { dao.updateSettings(any()) } just runs

        prefs = mockk(relaxed = true)

        whisper = mockk(relaxed = true)
        every { whisper.release() } just runs
        coEvery { whisper.initialize(any()) } coAnswers { }

        tts = mockk(relaxed = true)
        coEvery { tts.speak(any(), any(), any()) } coAnswers { }

        context = mockk()
        every { context.getString(R.string.voice_settings_tts_test_phrase) } returns "Test phrase"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm() = VoiceSettingsViewModel(dao, prefs, whisper, tts, context)

    @Test
    fun setVoiceInputEnabled_updatesPreferencesAndDatabase() = runTest {
        val vm = newVm()
        vm.setVoiceInputEnabled(true)
        Thread.sleep(150)
        verify { prefs.setVoiceInputEnabled(true) }
        coVerify { dao.updateSettings(match { it.voiceInputEnabled }) }
    }

    @Test
    fun setWhisperModelId_releasesTranscriber() = runTest {
        val vm = newVm()
        vm.setWhisperModelId("whisper-tiny")
        Thread.sleep(150)
        verify { whisper.release() }
        coVerify { dao.updateSettings(match { it.whisperModelId == "whisper-tiny" }) }
    }

    @Test
    fun prepareWhisperModel_callsInitialize() = runTest {
        val vm = newVm()
        vm.prepareWhisperModel()
        Thread.sleep(150)
        coVerify { whisper.initialize(null) }
    }

    @Test
    fun playTestUtterance_callsTtsWithPhrase() = runTest {
        val vm = newVm()
        vm.playTestUtterance()
        Thread.sleep(150)
        coVerify { tts.speak("Test phrase", null, any()) }
    }
}
