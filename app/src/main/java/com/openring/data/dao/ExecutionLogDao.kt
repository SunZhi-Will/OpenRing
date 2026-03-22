package com.openring.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.openring.data.model.ExecutionLogEntryEntity

@Dao
interface ExecutionLogDao {

    @Query(
        "SELECT * FROM execution_log_entries WHERE sessionId = :sessionId ORDER BY createdAtMs ASC LIMIT :limit"
    )
    suspend fun getForSession(sessionId: String, limit: Int = 2000): List<ExecutionLogEntryEntity>

    @Insert
    suspend fun insert(entry: ExecutionLogEntryEntity): Long
}
