package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessProfileDao {

    @Query("SELECT * FROM business_profile WHERE id = 1 LIMIT 1")
    fun observe(): Flow<BusinessProfile?>

    @Query("SELECT * FROM business_profile WHERE id = 1 LIMIT 1")
    suspend fun get(): BusinessProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: BusinessProfile)

    @Query("SELECT onboarding_complete FROM business_profile WHERE id = 1 LIMIT 1")
    suspend fun isOnboardingComplete(): Boolean?
}
