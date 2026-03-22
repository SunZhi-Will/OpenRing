package com.openring.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.openring.agent.RunCancellationRegistry
import com.openring.ui.notifications.AiRunNotification

class AiRunControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val sessionId = intent?.getStringExtra(AiRunNotification.EXTRA_SESSION_ID)?.trim().orEmpty()
        if (sessionId.isEmpty()) return
        RunCancellationRegistry.cancel(sessionId)
        NotificationManagerCompat.from(context).cancel(AiRunNotification.NOTIFICATION_ID)
    }
}
