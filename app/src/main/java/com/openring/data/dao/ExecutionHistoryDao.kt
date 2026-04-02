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

    /** 雲端中繼儀表板請求快照用（一次性查詢）。 */
    @Query("SELECT * FROM execution_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentHistoryLimited(limit: Int): List<ExecutionRecord>

    @Query("SELECT * FROM execution_history WHERE scriptId = :scriptId ORDER BY timestamp DESC LIMIT 20")
    fun getHistoryByScript(scriptId: String): Flow<List<ExecutionRecord>>

    @Insert
    suspend fun insert(record: ExecutionRecord)
}
