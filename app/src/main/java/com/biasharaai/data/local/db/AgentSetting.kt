package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agent_settings")
data class AgentSetting(
    @PrimaryKey val id: Long = 1L,
    @ColumnInfo(name = "master_switch") val masterSwitch: Boolean = true,
    @ColumnInfo(name = "stock_guardian_enabled") val stockGuardianEnabled: Boolean = true,
    @ColumnInfo(name = "pricing_agent_enabled") val pricingAgentEnabled: Boolean = true,
    @ColumnInfo(name = "cash_flow_enabled") val cashFlowEnabled: Boolean = true,
    @ColumnInfo(name = "customer_relation_enabled") val customerRelationEnabled: Boolean = true,
    @ColumnInfo(name = "fraud_sentinel_enabled") val fraudSentinelEnabled: Boolean = true,
    @ColumnInfo(name = "weekly_review_enabled") val weeklyReviewEnabled: Boolean = true,
    @ColumnInfo(name = "opportunity_spotter_enabled") val opportunitySpotterEnabled: Boolean = true,
    @ColumnInfo(name = "stock_alert_threshold_days") val stockAlertThresholdDays: Int = 2,
    @ColumnInfo(name = "daily_summary_hour") val dailySummaryHour: Int = 20,
    /**
     * Day the weekly review job should run, **ISO-8601**: `1` = Monday … `7` = Sunday
     * (matches [java.time.DayOfWeek.getValue]).
     */
    @ColumnInfo(name = "weekly_review_day_of_week") val weeklyReviewDayOfWeek: Int = 1,
    @ColumnInfo(name = "quiet_hours_start") val quietHoursStart: Int = 22,
    @ColumnInfo(name = "quiet_hours_end") val quietHoursEnd: Int = 7,
    @ColumnInfo(name = "auto_approve_low_dismiss") val autoApproveLowDismiss: Boolean = true,
)
