package com.biasharaai.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.biasharaai.MainActivity
import com.biasharaai.R
import com.biasharaai.agent.AgentDecisionEngine
import com.biasharaai.data.local.db.AgentSetting
import com.biasharaai.data.local.db.AgentSettingDao
import com.biasharaai.data.local.db.PendingNotification
import com.biasharaai.data.local.db.PendingNotificationDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry for agent-related system notifications. Honors [AgentSetting] quiet hours by
 * persisting to [PendingNotification] and delivering after the next allowed window (including
 * CRITICAL alerts — nothing is dropped when the master switch is on).
 */
@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
    private val agentSettingDao: AgentSettingDao,
    private val pendingNotificationDao: PendingNotificationDao,
    private val agentDecisionEngine: AgentDecisionEngine,
) {

    private val ephemeralNotificationId = AtomicInteger(NOTIFICATION_ID_EPHEMERAL_BASE)

    suspend fun notifyOrQueue(title: String, body: String, urgency: String = "NORMAL") {
        withContext(Dispatchers.IO) {
            val settings = agentSettingDao.getSettingsSync() ?: AgentSetting()
            if (!settings.masterSwitch) {
                Log.d(TAG, "Skipping notification (master switch off): $title")
                return@withContext
            }
            val now = System.currentTimeMillis()
            when {
                agentDecisionEngine.shouldSuppressNotification(settings, now) -> {
                    val fireAt = firstMillisNotificationsAllowed(settings, now + 60_000L)
                    pendingNotificationDao.insert(
                        PendingNotification(
                            title = title,
                            body = body,
                            urgency = urgency,
                            fireAt = fireAt,
                            createdAt = now,
                        ),
                    )
                    Log.d(TAG, "Queued notification until quiet hours end ($fireAt): $title")
                }
                !canPostNotifications() -> {
                    pendingNotificationDao.insert(
                        PendingNotification(
                            title = title,
                            body = body,
                            urgency = urgency,
                            fireAt = now + POST_PERMISSION_RETRY_MS,
                            createdAt = now,
                        ),
                    )
                    Log.d(TAG, "Queued notification (POST_NOTIFICATIONS not granted): $title")
                }
                else -> {
                    deliverRaw(title, body, urgency, notificationId = nextEphemeralNotificationId())
                }
            }
        }
    }

    suspend fun flushPendingDue() {
        withContext(Dispatchers.IO) {
            ensureChannel()
            val settings = agentSettingDao.getSettingsSync() ?: AgentSetting()
            if (!settings.masterSwitch) {
                pendingNotificationDao.deleteAll()
                return@withContext
            }
            val now = System.currentTimeMillis()
            for (p in pendingNotificationDao.getDue(now)) {
                if (agentDecisionEngine.shouldSuppressNotification(settings, now)) {
                    pendingNotificationDao.updateFireAt(
                        p.id,
                        firstMillisNotificationsAllowed(settings, now + 60_000L),
                    )
                    continue
                }
                if (!canPostNotifications()) {
                    pendingNotificationDao.updateFireAt(p.id, now + POST_PERMISSION_RETRY_MS)
                    continue
                }
                deliverPending(p)
                pendingNotificationDao.deleteById(p.id)
            }
        }
    }

    private fun deliverPending(p: PendingNotification) {
        val id = (NOTIFICATION_ID_PENDING_BASE + (p.id % 50_000L)).toInt()
        deliverRaw(p.title, p.body, p.urgency, notificationId = id)
    }

    private fun deliverRaw(title: String, body: String, urgency: String, notificationId: Int) {
        ensureChannel()
        val openApp = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val priority = when (urgency.uppercase()) {
            "CRITICAL", "HIGH" -> NotificationCompat.PRIORITY_HIGH
            else -> NotificationCompat.PRIORITY_DEFAULT
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_loss_alert)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        try {
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "notify blocked", e)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_agent_alerts),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_agent_alerts_desc)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun firstMillisNotificationsAllowed(settings: AgentSetting, afterMillis: Long): Long {
        var t = afterMillis
        repeat(24 * 60) {
            if (!agentDecisionEngine.shouldSuppressNotification(settings, t)) return t
            t += 60_000L
        }
        return afterMillis + 3_600_000L
    }

    private fun nextEphemeralNotificationId(): Int = ephemeralNotificationId.getAndIncrement()

    companion object {
        private const val TAG = "NotificationScheduler"
        private const val CHANNEL_ID = "agent_alerts"
        private const val NOTIFICATION_ID_EPHEMERAL_BASE = 91_000
        private const val NOTIFICATION_ID_PENDING_BASE = 96_000
        private const val POST_PERMISSION_RETRY_MS = 5 * 60_000L
    }
}
