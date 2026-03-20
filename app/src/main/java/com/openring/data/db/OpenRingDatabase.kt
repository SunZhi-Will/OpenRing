package com.openring.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.openring.data.dao.ExecutionHistoryDao
import com.openring.data.dao.ScriptDao
import com.openring.data.model.ExecutionRecord
import com.openring.data.model.Script

@Database(
    entities = [Script::class, ExecutionRecord::class],
    version = 1,
    exportSchema = false
)
abstract class OpenRingDatabase : RoomDatabase() {
    abstract fun scriptDao(): ScriptDao
    abstract fun executionHistoryDao(): ExecutionHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: OpenRingDatabase? = null

        fun getDatabase(context: Context): OpenRingDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    OpenRingDatabase::class.java,
                    "openring_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
