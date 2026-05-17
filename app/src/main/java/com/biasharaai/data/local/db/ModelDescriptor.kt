package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Phase 6 — Model Registry: one row per known / downloadable LiteRT-LM model.
 * Populated from catalogue (Prompt X2); benchmark fields updated from UI (Prompt X3).
 */
@Entity(tableName = "model_descriptors")
data class ModelDescriptor(
    @PrimaryKey
    @ColumnInfo(name = "modelId")
    val modelId: String,
    @ColumnInfo(name = "displayName")
    val displayName: String,
    @ColumnInfo(name = "huggingFaceRepo")
    val huggingFaceRepo: String,
    @ColumnInfo(name = "fileName")
    val fileName: String,
    @ColumnInfo(name = "sizeBytes")
    val sizeBytes: Long,
    @ColumnInfo(name = "sha256")
    val sha256: String,
    /** JSON array of capability tags, e.g. `["TEXT_GENERATION","FUNCTION_CALLING"]`. */
    @ColumnInfo(name = "capabilitiesJson")
    val capabilitiesJson: String,
    @ColumnInfo(name = "minTier")
    val minTier: String,
    @ColumnInfo(name = "isDownloaded")
    val isDownloaded: Boolean = false,
    @ColumnInfo(name = "downloadedAt")
    val downloadedAt: Long? = null,
    @ColumnInfo(name = "filePath")
    val filePath: String? = null,
    @ColumnInfo(name = "tokensPerSecGpu")
    val tokensPerSecGpu: Float? = null,
    @ColumnInfo(name = "tokensPerSecCpu")
    val tokensPerSecCpu: Float? = null,
)
