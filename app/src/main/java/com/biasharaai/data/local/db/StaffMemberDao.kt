package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StaffMemberDao {
    @Query("SELECT * FROM staff_members WHERE is_active = 1 ORDER BY name COLLATE NOCASE ASC")
    fun getActive(): Flow<List<StaffMember>>

    @Query("SELECT * FROM staff_members WHERE is_active = 1 ORDER BY name COLLATE NOCASE ASC")
    suspend fun getActiveOnce(): List<StaffMember>

    @Query("SELECT COUNT(*) FROM staff_members WHERE is_active = 1")
    suspend fun getActiveCount(): Int

    @Query("SELECT * FROM staff_members WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): StaffMember?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(member: StaffMember): Long

    @Update
    suspend fun update(member: StaffMember)
}
