package com.openring.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.openring.data.model.MemoryFactEntity

@Dao
interface MemoryFactDao {

    @Query("SELECT * FROM memory_facts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MemoryFactEntity?

    @Query(
        "SELECT * FROM memory_facts WHERE scope = :scope AND sessionId = :sessionId AND factKey = :factKey LIMIT 1"
    )
    suspend fun findByScopeSessionAndKey(
        scope: String,
        sessionId: String,
        factKey: String
    ): MemoryFactEntity?

    @Query(
        """SELECT * FROM memory_facts WHERE scope = :scope AND sessionId = :sessionId 
        ORDER BY updatedAtMs DESC LIMIT :limit"""
    )
    suspend fun listForScope(scope: String, sessionId: String, limit: Int): List<MemoryFactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MemoryFactEntity)

    @Query("DELETE FROM memory_facts WHERE id = :id")
    suspend fun deleteById(id: String)
}
