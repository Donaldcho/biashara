package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Singleton row — structured business identity for agent prompts and skills. */
@Entity(tableName = "business_profile")
data class BusinessProfile(
    @PrimaryKey val id: Int = 1,

    @ColumnInfo(name = "business_name")
    val businessName: String = "",

    @ColumnInfo(name = "owner_name")
    val ownerName: String = "",

    @ColumnInfo(name = "business_type")
    val businessType: String = "mixed",

    val description: String? = null,

    @ColumnInfo(name = "primary_products")
    val primaryProducts: String? = null,

    @ColumnInfo(name = "primary_services")
    val primaryServices: String? = null,

    val specialisation: String? = null,

    @ColumnInfo(name = "target_customer")
    val targetCustomer: String? = null,

    val location: String? = null,

    @ColumnInfo(name = "open_days")
    val openDays: String? = null,

    @ColumnInfo(name = "open_hours")
    val openHours: String? = null,

    @ColumnInfo(name = "staff_count")
    val staffCount: Int? = null,

    @ColumnInfo(name = "main_suppliers")
    val mainSuppliers: String? = null,

    @ColumnInfo(name = "payment_methods")
    val paymentMethods: String? = null,

    @ColumnInfo(name = "monthly_revenue_target")
    val monthlyRevenueTarget: Double? = null,

    @ColumnInfo(name = "business_goal")
    val businessGoal: String? = null,

    @ColumnInfo(name = "agent_tone")
    val agentTone: String? = null,

    @ColumnInfo(name = "last_updated_at")
    val lastUpdatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "onboarding_complete")
    val onboardingComplete: Boolean = false,

    @ColumnInfo(name = "onboarding_step")
    val onboardingStep: Int = 0,
) {
    fun hasIdentity(): Boolean = businessName.isNotBlank()

    fun whatTheyOffer(): String? =
        primaryServices?.takeIf { it.isNotBlank() }
            ?: primaryProducts?.takeIf { it.isNotBlank() }
}
