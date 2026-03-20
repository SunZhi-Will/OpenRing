package com.openring.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.openring.data.ScriptStore
import com.openring.data.db.OpenRingDatabase
import com.openring.domain.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AlarmManager 觸發入口：收到 alarm 後觸發腳本執行，並續排下一次。
 */
class ScriptAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scriptId = intent.getStringExtra(EXTRA_SCRIPT_ID) ?: return
        val appContext = context.applicationContext
        val scheduler = Scheduler(appContext)

        // Receiver 生命週期很短，DB/解析放到背景執行
        CoroutineScope(Dispatchers.IO).launch {
            val db = OpenRingDatabase.getDatabase(appContext)
            val script = db.scriptDao().getScriptById(scriptId) ?: return@launch
            val schedule = ScriptStore(db.scriptDao()).parseSchedule(script.scheduleJson)

            // 先續排，避免執行中發生例外導致漏排
            scheduler.scheduleScript(scriptId, schedule)
            scheduler.runOnce(scriptId)
        }
    }

    companion object {
        const val EXTRA_SCRIPT_ID = "scriptId"
    }
}

