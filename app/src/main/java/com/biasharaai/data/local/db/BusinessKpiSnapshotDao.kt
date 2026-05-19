package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessKpiSnapshotDao {

    /** Insert or overwrite the snapshot for this week (unique on week_start_millis). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: BusinessKpiSnapshot)

    @Query("SELECT * FROM business_kpi_snapshots WHERE week_start_millis = :weekStartMillis LIMIT 1")
    suspend fun getByWeek(weekStartMillis: Long): BusinessKpiSnapshot?

    /** Most-recent [limit] weeks descending — used for trend context building. */
    @Query("SELECT * FROM business_kpi_snapshots ORDER BY week_start_millis DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 12): List<BusinessKpiSnapshot>

    @Query("SELECT * FROM business_kpi_snapshots ORDER BY week_start_millis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 4): Flow<List<BusinessKpiSnapshot>>

    @Query("SELECT * FROM business_kpi_snapshots ORDER BY week_start_millis DESC")
    suspend fun getAll(): List<BusinessKpiSnapshot>

    /** Total number of weeks stored. */
    @Query("SELECT COUNT(*) FROM business_kpi_snapshots")
    suspend fun count(): Int
}
