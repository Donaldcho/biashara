package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EnterpriseRegisteredDeviceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: EnterpriseRegisteredDevice): Long

    @Query("SELECT * FROM enterprise_registered_devices WHERE device_id = :deviceId LIMIT 1")
    suspend fun getByDeviceId(deviceId: String): EnterpriseRegisteredDevice?

    @Query("SELECT * FROM enterprise_registered_devices WHERE is_active = 1 ORDER BY last_seen_at DESC")
    fun observeActive(): Flow<List<EnterpriseRegisteredDevice>>

    @Query("SELECT * FROM enterprise_registered_devices WHERE is_active = 1 ORDER BY last_seen_at DESC")
    suspend fun listActive(): List<EnterpriseRegisteredDevice>

    @Query("SELECT COUNT(*) FROM enterprise_registered_devices WHERE is_active = 1")
    suspend fun activeCount(): Int

    @Query("UPDATE enterprise_registered_devices SET is_active = 0 WHERE device_id = :deviceId")
    suspend fun deactivate(deviceId: String)
}
