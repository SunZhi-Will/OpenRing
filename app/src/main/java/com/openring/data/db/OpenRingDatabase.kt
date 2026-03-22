package com.openring.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.openring.data.dao.ChatMessageDao
import com.openring.data.dao.ChatSessionDao
import com.openring.data.dao.ExecutionHistoryDao
import com.openring.data.dao.ExecutionLogDao
import com.openring.data.dao.MemoryFactDao
import com.openring.data.dao.MemoryVectorDao
import com.openring.data.dao.ScriptDao
import com.openring.data.model.ChatMessageEntity
import com.openring.data.model.ChatSession
import com.openring.data.model.ExecutionLogEntryEntity
import com.openring.data.model.ExecutionRecord
import com.openring.data.model.MemoryFactEntity
import com.openring.data.model.MemoryVectorChunkEntity
import com.openring.data.model.Script

@Database(
    entities = [
        Script::class,
        ExecutionRecord::class,
        ChatSession::class,
        ChatMessageEntity::class,
        ExecutionLogEntryEntity::class,
        MemoryFactEntity::class,
        MemoryVectorChunkEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class OpenRingDatabase : RoomDatabase() {
    abstract fun scriptDao(): ScriptDao
    abstract fun executionHistoryDao(): ExecutionHistoryDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun executionLogDao(): ExecutionLogDao
    abstract fun memoryFactDao(): MemoryFactDao
    abstract fun memoryVectorDao(): MemoryVectorDao

    companion object {
        @Volatile
        private var INSTANCE: OpenRingDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_sessions` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_messages` (
                        `id` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`sessionId`) REFERENCES `chat_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId_createdAtMs` ON `chat_messages` (`sessionId`, `createdAtMs`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `execution_log_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `payload` TEXT NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        FOREIGN KEY(`sessionId`) REFERENCES `chat_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_execution_log_entries_sessionId_createdAtMs` ON `execution_log_entries` (`sessionId`, `createdAtMs`)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE chat_sessions ADD COLUMN summary TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `memory_facts` (
                        `id` TEXT NOT NULL,
                        `scope` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `factKey` TEXT NOT NULL,
                        `factValue` TEXT NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_memory_facts_scope_sessionId_factKey` ON `memory_facts` (`scope`, `sessionId`, `factKey`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `memory_vector_chunks` (
                        `id` TEXT NOT NULL,
                        `scope` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `embeddingJson` TEXT NOT NULL,
                        `embeddingModel` TEXT NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_memory_vector_chunks_scope_sessionId_createdAtMs` ON `memory_vector_chunks` (`scope`, `sessionId`, `createdAtMs`)"
                )
            }
        }

        fun getDatabase(context: Context): OpenRingDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    OpenRingDatabase::class.java,
                    "openring_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
