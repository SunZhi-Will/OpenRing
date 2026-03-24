package com.openring.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.openring.agent.RunCancellationRegistry
import com.openring.core.OverlayService
import com.openring.ui.notifications.AiRunNotification
import com.openring.ui.notifications.SchedulerStatusNotification
import com.openring.ui.MainActivity

class AiRunControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val sessionId = intent?.getStringExtra(AiRunNotification.EXTRA_SESSION_ID)?.trim().orEmpty()
        RunCancellationRegistry.cancelAll()
        if (sessionId.isNotEmpty()) {
            RunCancellationRegistry.cancel(sessionId)
        }
        context.startService(
            Intent(context, OverlayService::class.java).apply {
                setPackage(context.packageName)
                action = OverlayService.ACTION_STOP_AI_RUN
            }
        )
        context.stopService(Intent(context, OverlayService::class.java))
        NotificationManagerCompat.from(context).cancel(AiRunNotification.NOTIFICATION_ID)
        SchedulerStatusNotification.cancel(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(openIntent)
    }
}
