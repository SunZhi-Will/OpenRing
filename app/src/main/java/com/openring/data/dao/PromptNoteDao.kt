package com.openring.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.openring.data.model.PromptNoteEntity

@Dao
interface PromptNoteDao {

    @Query("SELECT * FROM prompt_notes ORDER BY updatedAtMs DESC")
    suspend fun listAllOrdered(): List<PromptNoteEntity>

    @Query("SELECT * FROM prompt_notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PromptNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PromptNoteEntity)

    @Query("DELETE FROM prompt_notes WHERE id = :id")
    suspend fun deleteById(id: String)
}
