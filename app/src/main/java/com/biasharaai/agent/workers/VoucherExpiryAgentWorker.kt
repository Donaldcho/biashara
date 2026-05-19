package com.biasharaai.agent.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.biasharaai.agent.AgentActionBuilder
import com.biasharaai.agent.AgentDecisionEngine
import com.biasharaai.agent.AgentTypes
import com.biasharaai.data.local.db.AgentActionDao
import com.biasharaai.data.local.db.AgentSetting
import com.biasharaai.data.local.db.AgentSettingDao
import com.biasharaai.data.local.db.CustomerDao
import com.biasharaai.data.local.db.ServiceItemDao
import com.biasharaai.data.local.db.ServiceVoucherDao
import com.biasharaai.productline.ProductLineManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Pro — alerts when prepaid vouchers expire within 7 days with uses remaining. */
class VoucherExpiryAgentWorker(
    appContext: Context,
    params: WorkerParameters,
    private val productLineManager: ProductLineManager,
    private val serviceVoucherDao: ServiceVoucherDao,
    private val serviceItemDao: ServiceItemDao,
    private val customerDao: CustomerDao,
    private val agentActionDao: AgentActionDao,
    private val agentDecisionEngine: AgentDecisionEngine,
    private val agentSettingDao: AgentSettingDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!productLineManager.isProEnabled()) return@withContext Result.success()
        val settings = agentSettingDao.getSettingsSync() ?: AgentSetting()
        if (!settings.masterSwitch) return@withContext Result.success()

        val now = System.currentTimeMillis()
        val within = now + TimeUnit.DAYS.toMillis(7)
        val expiring = serviceVoucherDao.getExpiringSoon(now, within)
        if (expiring.isEmpty()) return@withContext Result.success()

        for (voucher in expiring.take(5)) {
            val service = serviceItemDao.getById(voucher.serviceItemId) ?: continue
            val customerName = voucher.customerId?.let { customerDao.getCustomerById(it)?.name }
                ?: "your customer"
            val expiresAt = voucher.expiresAt ?: within
            val daysLeft = ((expiresAt - now) / 86_400_000L).toInt().coerceAtLeast(0)
            val headline = "$customerName has ${voucher.remainingUses} sessions expiring in $daysLeft days"
            if (agentDecisionEngine.isDuplicatePendingHeadline(AgentTypes.VOUCHER_EXPIRY, headline)) continue
            val detail = "Voucher for ${service.name} expires ${java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(expiresAt))}."
            agentActionDao.insertAction(
                AgentActionBuilder.voucherExpiryReminder(
                    serviceName = service.name,
                    remainingUses = voucher.remainingUses,
                    expiresAt = expiresAt,
                    dedupeKey = voucher.voucherId,
                    headlineOverride = headline,
                    detailOverride = detail,
                ),
            )
        }
        Result.success()
    }
}
