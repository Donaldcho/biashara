package com.biasharaai.service

import com.biasharaai.data.local.db.AgentSetting
import com.biasharaai.data.local.db.ServiceDelivery
import com.biasharaai.data.local.db.ServiceDeliveryDao
import com.biasharaai.data.local.db.ServiceItem
import com.biasharaai.data.local.db.ServiceItemDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilisationCalculatorTest {

    @Test
    fun lowDeliveries_returnsLowUtilisation() = runTest {
        val deliveryDao = mockk<ServiceDeliveryDao>()
        val itemDao = mockk<ServiceItemDao>()
        coEvery { itemDao.getAllOnce() } returns listOf(
            ServiceItem(id = 1L, name = "Cut", basePrice = 100.0, durationMinutes = 60, catalogueToken = "BSVC:1"),
        )
        coEvery { deliveryDao.getDeliveriesSince(any()) } returns listOf(
            mockk<ServiceDelivery>(),
            mockk<ServiceDelivery>(),
            mockk<ServiceDelivery>(),
        )
        val pct = UtilisationCalculator.calculatePct(deliveryDao, itemDao, AgentSetting(workingHoursPerDay = 8))
        assertTrue(pct < 60)
    }
}
