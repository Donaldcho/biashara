package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "knowledge_chunks",
    indices = [
        Index(value = ["source_path"]),
        Index(value = ["language_code"]),
    ],
)
data class KnowledgeChunk(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "content_text") val contentText: String,
    @ColumnInfo(name = "source_path") val sourcePath: String,
    @ColumnInfo(name = "language_code") val languageCode: String,
    @ColumnInfo(name = "chunk_index") val chunkIndex: Int = 0,
    @ColumnInfo(name = "embedding_blob", typeAffinity = ColumnInfo.BLOB) val embeddingBlob: ByteArray? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KnowledgeChunk) return false
        return id == other.id &&
            contentText == other.contentText &&
            sourcePath == other.sourcePath &&
            languageCode == other.languageCode &&
            chunkIndex == other.chunkIndex &&
            embeddingBlob.contentEquals(other.embeddingBlob) &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + contentText.hashCode()
        result = 31 * result + sourcePath.hashCode()
        result = 31 * result + languageCode.hashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + (embeddingBlob?.contentHashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        return result
    }
}

private fun ByteArray?.contentEquals(other: ByteArray?): Boolean {
    if (this === other) return true
    if (this == null || other == null) return false
    return this.contentEquals(other)
}
