package com.openring.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.openring.data.model.Script
import kotlinx.coroutines.flow.Flow

@Dao
interface ScriptDao {

    @Query("SELECT * FROM scripts ORDER BY id")
    fun getAllScripts(): Flow<List<Script>>

    @Query("SELECT * FROM scripts ORDER BY id")
    suspend fun getAllScriptsOnce(): List<Script>

    @Query("SELECT * FROM scripts WHERE id = :id")
    suspend fun getScriptById(id: String): Script?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(script: Script)

    @Update
    suspend fun update(script: Script)

    @Delete
    suspend fun delete(script: Script)

    @Query("DELETE FROM scripts WHERE id = :id")
    suspend fun deleteById(id: String)
}
