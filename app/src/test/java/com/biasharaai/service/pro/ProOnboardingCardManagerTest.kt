package com.biasharaai.service.pro

import android.content.Context
import com.biasharaai.agent.AgentTypes
import com.biasharaai.data.local.db.AgentAction
import com.biasharaai.data.local.db.AgentActionDao
import com.biasharaai.data.local.db.AppSettings
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.productline.ProductLineManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ProOnboardingCardManagerTest {

    @Test
    fun firstProLaunch_insertsOnboardingAction() = runTest {
        val context = mockk<Context>(relaxed = true)
        val settingsDao = mockk<AppSettingsDao>()
        val agentDao = mockk<AgentActionDao>()
        val productLine = mockk<ProductLineManager>()
        every { productLine.isProEnabled() } returns true
        coEvery { settingsDao.getSettingsSync() } returns AppSettings(proOnboardingShown = false)
        coEvery { agentDao.insertAction(any()) } returns 1L
        coEvery { settingsDao.updateSettings(any()) } returns Unit

        val actionSlot = slot<AgentAction>()
        ProOnboardingCardManager(context, settingsDao, agentDao, productLine).checkAndShowIfNeeded()

        coVerify { agentDao.insertAction(capture(actionSlot)) }
        assertEquals(AgentTypes.PRO_ONBOARDING, actionSlot.captured.agentType)
    }

    @Test
    fun alreadyShown_skipsInsert() = runTest {
        val context = mockk<Context>(relaxed = true)
        val settingsDao = mockk<AppSettingsDao>()
        val agentDao = mockk<AgentActionDao>()
        val productLine = mockk<ProductLineManager>()
        every { productLine.isProEnabled() } returns true
        coEvery { settingsDao.getSettingsSync() } returns AppSettings(proOnboardingShown = true)

        ProOnboardingCardManager(context, settingsDao, agentDao, productLine).checkAndShowIfNeeded()

        coVerify(exactly = 0) { agentDao.insertAction(any()) }
    }
}
