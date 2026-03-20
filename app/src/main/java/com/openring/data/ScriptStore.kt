package com.openring.data

import com.openring.data.dao.ScriptDao
import com.openring.data.model.Schedule
import com.openring.data.model.Script
import com.openring.data.model.ScriptStep
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * 腳本 CRUD 本地儲存
 * US-2.1: 支援腳本 CRUD、可列出所有腳本
 */
class ScriptStore(private val scriptDao: ScriptDao) {

    private val json = Json { ignoreUnknownKeys = true }

    val allScripts: Flow<List<Script>> = scriptDao.getAllScripts()

    suspend fun getScript(id: String) = scriptDao.getScriptById(id)

    suspend fun insertScript(
        name: String,
        steps: List<ScriptStep>,
        schedule: Schedule
    ): Script {
        val script = Script(
            id = UUID.randomUUID().toString(),
            name = name,
            version = 1,
            stepsJson = json.encodeToString(steps),
            scheduleJson = json.encodeToString(schedule)
        )
        scriptDao.insert(script)
        return script
    }

    suspend fun updateScript(script: Script) {
        scriptDao.update(script)
    }

    suspend fun deleteScript(id: String) {
        scriptDao.deleteById(id)
    }

    fun parseSteps(stepsJson: String): List<ScriptStep> {
        return try {
            json.decodeFromString<List<ScriptStep>>(stepsJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseSchedule(scheduleJson: String): Schedule {
        return try {
            json.decodeFromString<Schedule>(scheduleJson)
        } catch (e: Exception) {
            Schedule()
        }
    }
}
