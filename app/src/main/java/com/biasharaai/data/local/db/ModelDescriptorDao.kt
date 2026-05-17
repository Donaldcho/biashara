package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ModelDescriptorDao {

    @Query("SELECT COUNT(*) FROM model_descriptors")
    suspend fun count(): Int

    @Query("SELECT * FROM model_descriptors ORDER BY displayName ASC")
    suspend fun getAll(): List<ModelDescriptor>

    @Query("SELECT * FROM model_descriptors WHERE modelId = :modelId LIMIT 1")
    suspend fun getById(modelId: String): ModelDescriptor?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(descriptor: ModelDescriptor)

    @Query(
        """
        UPDATE model_descriptors
        SET isDownloaded = :isDownloaded,
            downloadedAt = :downloadedAt,
            filePath = :filePath
        WHERE modelId = :modelId
        """,
    )
    suspend fun updateDownloadState(
        modelId: String,
        isDownloaded: Boolean,
        downloadedAt: Long?,
        filePath: String?,
    )

    @Query("UPDATE model_descriptors SET tokensPerSecGpu = :tps WHERE modelId = :modelId")
    suspend fun updateBenchmarkGpu(modelId: String, tps: Float)

    @Query("UPDATE model_descriptors SET tokensPerSecCpu = :tps WHERE modelId = :modelId")
    suspend fun updateBenchmarkCpu(modelId: String, tps: Float)
}
