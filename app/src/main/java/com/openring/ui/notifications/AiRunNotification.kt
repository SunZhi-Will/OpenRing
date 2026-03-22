package com.openring.ui.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.openring.receiver.AiRunControlReceiver
import com.openring.ui.MainActivity

object AiRunNotification {
    const val CHANNEL_ID = "openring_ai_run"
    const val NOTIFICATION_ID = 1201
    const val EXTRA_SESSION_ID = "session_id"

    fun show(context: Context, sessionId: String) {
        createChannel(context)
        if (!hasPostNotificationPermission(context)) return

        val openIntent = PendingIntent.getActivity(
            context,
            100,
            Intent().apply {
                component = ComponentName(context, MainActivity::class.java)
                setPackage(context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getBroadcast(
            context,
            101,
            Intent().apply {
                component = ComponentName(context, AiRunControlReceiver::class.java)
                setPackage(context.packageName)
                putExtra(EXTRA_SESSION_ID, sessionId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("OpenRing AI 執行中")
            .setContentText("可從這裡中斷本次執行")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                "中斷",
                stopIntent
            )
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notif)
    }

    fun cancel(context: Context) {
        if (!hasPostNotificationPermission(context)) return
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "OpenRing AI Run",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "AI 執行中通知，提供中斷按鈕"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun hasPostNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
