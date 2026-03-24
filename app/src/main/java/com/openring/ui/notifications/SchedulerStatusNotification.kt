package com.openring.ui.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.openring.R
import com.openring.core.AlwaysOnSchedulerService
import com.openring.receiver.AlwaysOnSchedulerControlReceiver
import com.openring.ui.MainActivity

object SchedulerStatusNotification {
    /** Unified: share one app-wide notification slot. */
    private const val CHANNEL_ID = "openring_main_v1"
    private const val NOTIFICATION_ID = 1001

    fun update(
        context: Context,
        enabledScheduleCount: Int,
        hasAlwaysOnEnabled: Boolean,
        alwaysOnSuspended: Boolean,
        backgroundWorkCount: Int = 0
    ) {
        // User explicitly stopped always-on: keep notification hidden unless there is active background work.
        if (alwaysOnSuspended && backgroundWorkCount <= 0) {
            cancel(context)
            return
        }
        if (enabledScheduleCount <= 0 && backgroundWorkCount <= 0) {
            cancel(context)
            return
        }
        createChannel(context)
        // Android 13+: without this permission the system drops posts; user must allow (see Permissions screen).
        if (!hasPostNotificationPermission(context)) return

        val openIntent = PendingIntent.getActivity(
            context,
            1302,
            Intent(context, MainActivity::class.java).apply {
                setPackage(context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val scheduleShort = when {
            enabledScheduleCount <= 0 -> null
            hasAlwaysOnEnabled && !alwaysOnSuspended ->
                context.getString(R.string.notification_status_resident_count, enabledScheduleCount)
            hasAlwaysOnEnabled && alwaysOnSuspended ->
                context.getString(R.string.notification_status_schedule_suspended, enabledScheduleCount)
            else ->
                context.getString(R.string.notification_status_schedule_count, enabledScheduleCount)
        }

        val content = when {
            backgroundWorkCount > 0 && scheduleShort != null ->
                context.getString(R.string.notification_status_background_with_schedule, scheduleShort)
            backgroundWorkCount > 0 ->
                context.getString(R.string.notification_status_background_only)
            scheduleShort != null ->
                scheduleShort
            else -> ""
        }

        val builder = OpenRingNotificationStyle.apply(
            NotificationCompat.Builder(context, CHANNEL_ID),
            context
        )
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(content)
            .apply {
                if (content.length > 72) {
                    setStyle(NotificationCompat.BigTextStyle().bigText(content))
                }
            }
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(openIntent)

        if (hasAlwaysOnEnabled && !alwaysOnSuspended) {
            val terminateIntent = PendingIntent.getBroadcast(
                context,
                1303,
                Intent(context, AlwaysOnSchedulerControlReceiver::class.java).apply {
                    setPackage(context.packageName)
                    action = AlwaysOnSchedulerService.ACTION_TERMINATE_ALWAYS_ON
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_stat_stop,
                context.getString(R.string.notification_action_stop_resident),
                terminateIntent
            )
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    fun cancel(context: Context) {
        // Always clear our notification id; cancel does not require POST_NOTIFICATIONS.
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_status_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_status_description)
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
