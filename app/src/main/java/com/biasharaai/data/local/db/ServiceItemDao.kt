package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceItemDao {
    @Query("SELECT * FROM service_items ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ServiceItem>>

    @Query("SELECT * FROM service_items ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllOnce(): List<ServiceItem>

    @Query("SELECT * FROM service_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ServiceItem?

    @Query("SELECT * FROM service_items WHERE catalogue_token = :token LIMIT 1")
    suspend fun getByCatalogueToken(token: String): ServiceItem?

    @Query("SELECT COUNT(*) FROM service_items")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: ServiceItem): Long

    @Update
    suspend fun update(item: ServiceItem)

    @Query("DELETE FROM service_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM service_items WHERE updated_at < :since")
    suspend fun getServicesNotUpdatedSince(since: Long): List<ServiceItem>
}
