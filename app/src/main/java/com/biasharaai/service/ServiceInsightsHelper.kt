package com.biasharaai.service

import com.biasharaai.data.local.db.AgentSetting
import com.biasharaai.data.local.db.ServiceDeliveryDao
import com.biasharaai.data.local.db.ServiceItemDao

/** Aggregates service delivery data for agents, weekly review, and skills. */
object ServiceInsightsHelper {

    data class PeriodServiceStats(
        val activeServiceCount: Int,
        val deliveryCount: Int,
        val serviceRevenue: Double,
        val topServiceName: String,
        val topServiceCount: Int,
        val topServiceRevenue: Double,
        val utilisationPct: Int,
    ) {
        fun hasActivity(): Boolean = deliveryCount > 0 || serviceRevenue > 0.0
    }

    suspend fun buildForPeriod(
        serviceDeliveryDao: ServiceDeliveryDao,
        serviceItemDao: ServiceItemDao,
        settings: AgentSetting,
        startMillis: Long,
        endExclusiveMillis: Long,
    ): PeriodServiceStats? {
        val services = serviceItemDao.getAllOnce()
        if (services.isEmpty()) return null

        val endInclusive = endExclusiveMillis - 1L
        val deliveries = serviceDeliveryDao.getDeliveriesSince(startMillis)
            .filter { it.deliveredAt <= endInclusive }

        val byServiceId = deliveries.groupBy { it.serviceItemId }
        val topEntry = byServiceId.maxByOrNull { it.value.size }
        val topService = topEntry?.key?.let { id -> services.find { it.id == id } }
        val topCount = topEntry?.value?.size ?: 0
        val topRev = topEntry?.value?.sumOf { it.chargedAmount } ?: 0.0

        val utilPct = UtilisationCalculator.calculatePct(serviceDeliveryDao, serviceItemDao, settings)

        return PeriodServiceStats(
            activeServiceCount = services.size,
            deliveryCount = deliveries.size,
            serviceRevenue = deliveries.sumOf { it.chargedAmount },
            topServiceName = topService?.name ?: "—",
            topServiceCount = topCount,
            topServiceRevenue = topRev,
            utilisationPct = utilPct,
        )
    }

    fun formatForAgentPrompt(stats: PeriodServiceStats, currencySymbol: String): String {
        val money = { v: Double -> String.format(java.util.Locale.US, "%.2f", v) }
        return buildString {
            appendLine("Services (${stats.activeServiceCount} in catalogue):")
            appendLine("- Deliveries completed: ${stats.deliveryCount}")
            appendLine("- Service revenue (charged): $currencySymbol${money(stats.serviceRevenue)}")
            appendLine("- Top service: ${stats.topServiceName} (${stats.topServiceCount} visits, $currencySymbol${money(stats.topServiceRevenue)})")
            appendLine("- Capacity utilisation (7-day): ${stats.utilisationPct}%")
        }.trimEnd()
    }
}
