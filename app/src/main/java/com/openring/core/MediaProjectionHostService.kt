package com.openring.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.openring.R
import com.openring.ui.MainActivity
import com.openring.ui.notifications.OpenRingNotificationStyle

/**
 * Android 10+ 以 [android.media.projection.MediaProjection] 擷取他 App 音訊時，需有對應類型的前台服務（Android 14+ 為 [ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION]）。
 */
class MediaProjectionHostService : Service() {

    companion object {
        private const val TAG = "OpenRing"
        const val CHANNEL_ID = "openring_main_v1"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_PREPARE = "com.openring.mediaproj.PREPARE"
        const val ACTION_STOP = "com.openring.mediaproj.STOP"

        fun requestPrepare(context: Context) {
            val i = Intent(context, MediaProjectionHostService::class.java).apply { action = ACTION_PREPARE }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                @Suppress("DEPRECATION")
                context.startService(i)
            }
        }

        fun requestStop(context: Context) {
            context.startService(
                Intent(context, MediaProjectionHostService::class.java).apply { action = ACTION_STOP },
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREPARE -> {
                try {
                    startAsForeground()
                } catch (e: Exception) {
                    Log.e(TAG, "startForeground failed", e)
                }
            }
            ACTION_STOP -> {
                try {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } catch (_: Exception) {
                }
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_media_projection_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
            nm.createNotificationChannel(ch)
        }
    }

    private fun startAsForeground() {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply { setPackage(packageName) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPending = PendingIntent.getService(
            this,
            1,
            Intent(this, MediaProjectionHostService::class.java).apply {
                setPackage(packageName)
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = OpenRingNotificationStyle.apply(
            NotificationCompat.Builder(this, CHANNEL_ID),
            this,
        )
            .setContentTitle(getString(R.string.notification_media_projection_title))
            .setContentText(getString(R.string.notification_media_projection_text))
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                R.drawable.ic_stat_stop,
                getString(R.string.notification_action_stop_run),
                stopPending,
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
