package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentSettingDao {

    @Query("SELECT * FROM agent_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<AgentSetting?>

    @Query("SELECT * FROM agent_settings WHERE id = 1 LIMIT 1")
    fun getSettingsSync(): AgentSetting?

    @Update
    suspend fun updateSettings(settings: AgentSetting)
}
