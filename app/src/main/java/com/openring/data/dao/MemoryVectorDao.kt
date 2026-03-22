package com.openring.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.openring.data.model.MemoryVectorChunkEntity

@Dao
interface MemoryVectorDao {

    @Query(
        """SELECT * FROM memory_vector_chunks 
        WHERE scope = 'global' OR (scope = 'session' AND sessionId = :sessionId) 
        ORDER BY createdAtMs DESC LIMIT :limit"""
    )
    suspend fun listForRecall(sessionId: String, limit: Int): List<MemoryVectorChunkEntity>

    @Insert
    suspend fun insert(entity: MemoryVectorChunkEntity)

    @Query("DELETE FROM memory_vector_chunks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM memory_vector_chunks")
    suspend fun countAll(): Long

    @Query("DELETE FROM memory_vector_chunks WHERE scope = 'session' AND sessionId = :sessionId")
    suspend fun deleteAllForSessionScope(sessionId: String)
}
