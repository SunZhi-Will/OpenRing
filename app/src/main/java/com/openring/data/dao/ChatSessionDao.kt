package com.openring.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.openring.data.model.ChatSession

@Dao
interface ChatSessionDao {

    @Query("SELECT * FROM chat_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ChatSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ChatSession)

    @Update
    suspend fun update(session: ChatSession)

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAtMs DESC LIMIT :limit")
    suspend fun listRecent(limit: Int): List<ChatSession>

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAll()
}
