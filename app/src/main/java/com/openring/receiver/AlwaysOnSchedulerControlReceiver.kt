package com.openring.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.openring.core.AlwaysOnRunGate
import com.openring.core.AlwaysOnSchedulerService
import com.openring.domain.Scheduler

class AlwaysOnSchedulerControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != AlwaysOnSchedulerService.ACTION_TERMINATE_ALWAYS_ON) return
        AlwaysOnRunGate.suspendUntilNextAppLaunch(context)
        context.stopService(Intent(context, AlwaysOnSchedulerService::class.java))
        Scheduler(context.applicationContext).refreshAlwaysOnServiceState()
        Toast.makeText(
            context,
            "已停止常駐排程，將於下次開啟 App 恢復",
            Toast.LENGTH_SHORT
        ).show()
    }
}
