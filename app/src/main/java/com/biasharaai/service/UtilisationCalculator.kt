package com.biasharaai.service

import com.biasharaai.data.local.db.AgentSetting
import com.biasharaai.data.local.db.ServiceDeliveryDao
import com.biasharaai.data.local.db.ServiceItemDao

object UtilisationCalculator {
    suspend fun calculatePct(
        serviceDeliveryDao: ServiceDeliveryDao,
        serviceItemDao: ServiceItemDao,
        settings: AgentSetting,
    ): Int {
        val workingMinutesPerDay = settings.workingHoursPerDay * 60
        val services = serviceItemDao.getAllOnce()
        if (services.isEmpty()) return 0
        val durations = services.map { it.durationMinutes }.filter { it > 0 }
        val avgDuration = if (durations.isEmpty()) 60 else durations.average().toInt().coerceAtLeast(1)
        val dailyCapacity = (workingMinutesPerDay / avgDuration).coerceAtLeast(1)
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 86_400_000L
        val deliveries = serviceDeliveryDao.getDeliveriesSince(sevenDaysAgo)
        val avgDaily = deliveries.size / 7.0
        return if (dailyCapacity > 0) (avgDaily / dailyCapacity * 100).toInt() else 0
    }
}
