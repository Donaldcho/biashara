package com.biasharaai.skills.builtin

import android.content.Context
import com.biasharaai.agent.AgentMutex
import com.biasharaai.ai.VoiceInputProcessor
import com.biasharaai.skills.SkillResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TranscribeVoiceSkillTest {

    private lateinit var context: Context
    private lateinit var voiceInputProcessor: VoiceInputProcessor
    private lateinit var skill: TranscribeVoiceSkill

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        voiceInputProcessor = mockk()
        every { voiceInputProcessor.usesOnDeviceAi } returns false
        skill = TranscribeVoiceSkill(context, voiceInputProcessor, AgentMutex())
    }

    @Test
    fun execute_withoutMultimodalModel_returnsRequiresUi() = runTest {
        val result = skill.execute("{}")
        assertTrue(result is SkillResult.Failure)
        assertEquals("REQUIRES_UI", (result as SkillResult.Failure).code)
    }
}
