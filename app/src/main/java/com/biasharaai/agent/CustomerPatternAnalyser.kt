package com.biasharaai.agent

import com.biasharaai.data.local.db.Customer
import com.biasharaai.data.local.db.CustomerDao
import com.biasharaai.data.local.db.TransactionDao
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

data class OverdueCustomer(
    val customer: Customer,
    val daysSinceLastVisit: Long,
    val avgGapDays: Double,
)

/**
 * A6 — Customers with **3+ distinct INCOME visit days** and a gap since [Customer.lastVisit] that
 * exceeds **1.5×** their historical average days between those visit days.
 */
@Singleton
class CustomerPatternAnalyser @Inject constructor(
    private val customerDao: CustomerDao,
    private val transactionDao: TransactionDao,
) {

    suspend fun findCustomersOverdueByVisitPattern(
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<OverdueCustomer> {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val out = ArrayList<OverdueCustomer>()
        for (c in customerDao.getCustomersList()) {
            if (c.lastVisit <= 0L) continue
            val datesRaw = transactionDao.getIncomeDatesForCustomer(c.id)
            if (datesRaw.isEmpty()) continue
            val visitDays = datesRaw
                .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                .distinct()
                .sorted()
            if (visitDays.size < 3) continue
            val gaps = visitDays.zipWithNext().map { (a, b) -> ChronoUnit.DAYS.between(a, b).toDouble() }
            val avgGap = gaps.average()
            if (avgGap <= 0.0) continue
            val lastDay = Instant.ofEpochMilli(c.lastVisit).atZone(zone).toLocalDate()
            val daysSince = ChronoUnit.DAYS.between(lastDay, today)
            if (daysSince > avgGap * 1.5) {
                out.add(OverdueCustomer(customer = c, daysSinceLastVisit = daysSince, avgGapDays = avgGap))
            }
        }
        return out
    }
}
