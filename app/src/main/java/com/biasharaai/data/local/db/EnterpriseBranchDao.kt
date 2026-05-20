package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EnterpriseBranchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(branch: EnterpriseBranch): Long

    @Query("SELECT * FROM enterprise_branches WHERE business_id = :businessId AND code = :code LIMIT 1")
    suspend fun getByCode(businessId: String, code: String): EnterpriseBranch?

    @Query("SELECT * FROM enterprise_branches WHERE business_id = :businessId AND is_default = 1 LIMIT 1")
    suspend fun getDefault(businessId: String): EnterpriseBranch?

    @Query("SELECT * FROM enterprise_branches WHERE is_active = 1 ORDER BY is_default DESC, name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<EnterpriseBranch>>

    @Query("SELECT * FROM enterprise_branches WHERE is_active = 1 ORDER BY is_default DESC, name COLLATE NOCASE ASC")
    suspend fun listActive(): List<EnterpriseBranch>
}
