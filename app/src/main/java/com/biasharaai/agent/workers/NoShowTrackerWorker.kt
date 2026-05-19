package com.biasharaai.agent.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.biasharaai.agent.AgentTypes
import com.biasharaai.data.local.db.AgentAction
import com.biasharaai.data.local.db.AgentActionDao
import com.biasharaai.data.local.db.Appointment
import com.biasharaai.data.local.db.AppointmentDao
import com.biasharaai.productline.ProductLineManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoShowTrackerWorker(
    appContext: Context,
    params: WorkerParameters,
    private val productLineManager: ProductLineManager,
    private val appointmentDao: AppointmentDao,
    private val agentActionDao: AgentActionDao,
) : CoroutineWorker(appContext, params) {

    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!productLineManager.isProEnabled()) return@withContext Result.success()

        val overdue = appointmentDao.getOverdue(
            before = System.currentTimeMillis(),
            status = Appointment.STATUS_BOOKED,
        )
        for (apt in overdue) {
            appointmentDao.updateStatus(apt.id, Appointment.STATUS_NO_SHOW)
            val customerId = apt.customerId ?: continue
            val noShowCount = appointmentDao.countNoShowsForCustomer(
                customerId = customerId,
                since = System.currentTimeMillis() - 60 * 86_400_000L,
            )
            val detail = buildString {
                append("Scheduled for ${dateFormat.format(Date(apt.scheduledAt))}. ")
                if (noShowCount >= 2) {
                    append("This is their $noShowCount no-show in 60 days. Consider requiring a deposit.")
                } else {
                    append("First missed appointment.")
                }
            }
            agentActionDao.insertAction(
                AgentAction(
                    agentType = AgentTypes.NO_SHOW_TRACKER,
                    urgency = if (noShowCount >= 2) "HIGH" else "MEDIUM",
                    executionType = "REQUIRES_APPROVAL",
                    headline = "${apt.customerName} missed their appointment",
                    detail = detail,
                    actionVerb = "VIEW_CUSTOMER",
                    status = "PENDING",
                    actionPayload = """{"customerId":$customerId,"appointmentId":${apt.id},"noShowCount":$noShowCount}""",
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
        Result.success()
    }
}
