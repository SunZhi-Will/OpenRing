package com.openring.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.openring.data.model.ExecutionRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ExecutionHistoryDao {

    @Query("SELECT * FROM execution_history ORDER BY timestamp DESC LIMIT 100")
    fun getRecentHistory(): Flow<List<ExecutionRecord>>

    @Query("SELECT * FROM execution_history WHERE scriptId = :scriptId ORDER BY timestamp DESC LIMIT 20")
    fun getHistoryByScript(scriptId: String): Flow<List<ExecutionRecord>>

    @Insert
    suspend fun insert(record: ExecutionRecord)
}
