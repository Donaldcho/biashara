package com.biasharaai.skills.builtin

import android.content.Context
import com.biasharaai.agent.AgentMutex
import com.biasharaai.ai.ActiveModelStore
import com.biasharaai.skills.SkillResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DraftMessageSkillTest {

    private lateinit var context: Context
    private lateinit var activeModelStore: ActiveModelStore
    private lateinit var skill: DraftMessageSkill

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        activeModelStore = mockk()
        every { activeModelStore.isAvailable } returns false
        skill = DraftMessageSkill(context, activeModelStore, AgentMutex())
    }

    @Test
    fun execute_withoutAi_usesTemplate() = runTest {
        val result = skill.execute(
            """{"purpose":"debt reminder","customerName":"Amina"}""",
        )

        assertTrue(result is SkillResult.Success)
    }
}
