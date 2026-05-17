package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface KnowledgeChunkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<KnowledgeChunk>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chunk: KnowledgeChunk): Long

    @Query(
        """
        SELECT * FROM knowledge_chunks
        WHERE language_code = :lang
        ORDER BY source_path, chunk_index
        """,
    )
    suspend fun getByLanguage(lang: String): List<KnowledgeChunk>

    @Query("SELECT * FROM knowledge_chunks WHERE embedding_blob IS NOT NULL")
    suspend fun getAllWithEmbeddings(): List<KnowledgeChunk>

    @Query("SELECT COUNT(*) FROM knowledge_chunks WHERE language_code = :lang")
    suspend fun countByLanguage(lang: String): Int

    @Query("DELETE FROM knowledge_chunks WHERE source_path = :sourcePath")
    suspend fun deleteBySourcePath(sourcePath: String)

    @Query("DELETE FROM knowledge_chunks")
    suspend fun deleteAll()

    @Query("UPDATE knowledge_chunks SET embedding_blob = :blob WHERE id = :id")
    suspend fun updateEmbedding(id: Long, blob: ByteArray)
}
