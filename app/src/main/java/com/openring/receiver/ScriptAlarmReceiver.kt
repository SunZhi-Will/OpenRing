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
 *
 * 必須 [goAsync]：若僅在 coroutine 內續排，[onReceive] 回傳後程序可能被結束，導致只執行到第一次 alarm。
 */
class ScriptAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scriptId = intent.getStringExtra(EXTRA_SCRIPT_ID) ?: return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val scheduler = Scheduler(appContext)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = OpenRingDatabase.getDatabase(appContext)
                val script = db.scriptDao().getScriptById(scriptId) ?: return@launch
                val schedule = ScriptStore(db.scriptDao()).parseSchedule(script.scheduleJson)

                // 先續排，避免執行中發生例外導致漏排
                scheduler.scheduleScript(scriptId, schedule)
                scheduler.runOnce(scriptId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_SCRIPT_ID = "scriptId"
    }
}

