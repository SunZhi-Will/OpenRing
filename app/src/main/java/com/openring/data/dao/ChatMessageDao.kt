package com.openring.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.openring.data.model.ChatMessageEntity

@Dao
interface ChatMessageDao {

    @Query(
        "SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAtMs ASC"
    )
    suspend fun getMessagesForSession(sessionId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity)
}
