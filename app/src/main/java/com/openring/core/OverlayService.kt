package com.openring.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.util.Log
import androidx.core.app.NotificationCompat
import com.openring.R
import com.openring.ui.notifications.OpenRingNotificationStyle
import com.openring.agent.RunCancellationRegistry
import com.openring.ui.MainActivity

/**
 * 執行時顯示鸚鵡圖示懸浮窗
 * US-1.7: 執行中顯示圖示、可點擊收起/展開
 */
class OverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "openring_overlay_v2"
        const val NOTIFICATION_ID = 1001
        private const val OVERLAY_SIZE_DP = 56
        const val ACTION_START_AI_RUN = "com.openring.overlay.START_AI_RUN"
        const val ACTION_STOP_AI_RUN = "com.openring.overlay.STOP_AI_RUN"
        const val EXTRA_SESSION_ID = "session_id"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialWindowX = 0
    private var initialWindowY = 0
    private var activeSessionId: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_AI_RUN -> {
                activeSessionId = null
                updateOverlayUiState()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_AI_RUN -> {
                activeSessionId = intent.getStringExtra(EXTRA_SESSION_ID)?.trim().takeIf { !it.isNullOrBlank() }
            }
        }
        try {
            startForeground(NOTIFICATION_ID, createNotification())
            showOverlay()
            updateOverlayUiState()
        } catch (e: Exception) {
            Log.e("OverlayService", "onStartCommand 錯誤", e)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OpenRing 執行中",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { setPackage(packageName) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return OpenRingNotificationStyle.apply(
            NotificationCompat.Builder(this, CHANNEL_ID),
            this
        )
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_overlay_running))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun showOverlay() {
        if (overlayView != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(this)) {
            Log.w("OverlayService", "無懸浮窗權限，略過顯示")
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_parrot, null)

        val params = WindowManager.LayoutParams(
            (OVERLAY_SIZE_DP * resources.displayMetrics.density).toInt(),
            (OVERLAY_SIZE_DP * resources.displayMetrics.density).toInt(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        overlayView?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialWindowX = params.x
                    initialWindowY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialWindowX + (event.rawX - initialTouchX).toInt()
                    params.y = initialWindowY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(overlayView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = kotlin.math.abs(event.rawX - initialTouchX)
                    val dy = kotlin.math.abs(event.rawY - initialTouchY)
                    if (dx < 10 && dy < 10) {
                        requestStopAiRun()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun requestStopAiRun() {
        val sid = activeSessionId
        if (!sid.isNullOrBlank()) {
            RunCancellationRegistry.cancel(sid)
        }
        activeSessionId = null
        updateOverlayUiState()
        stopSelf()
    }

    private fun updateOverlayUiState() {
        val root = overlayView ?: return
        val icon = root.findViewById<ImageView>(R.id.overlay_icon) ?: return
        val aiRunning = !activeSessionId.isNullOrBlank()
        icon.alpha = if (aiRunning) 1f else 0.92f
        icon.contentDescription = if (aiRunning) "OpenRing AI 執行中，點擊可中斷" else "OpenRing 執行中"
    }

    private fun hideOverlay() {
        overlayView?.let { view ->
            try {
                // 僅在 View 已附加到 Window 時才移除，避免 IllegalArgumentException
                if (view.isAttachedToWindow) {
                    windowManager?.removeView(view)
                }
            } catch (e: IllegalArgumentException) {
                // View 可能已被系統移除或從未附加
                Log.w("OverlayService", "hideOverlay: view 未附加，略過", e)
            } catch (e: Exception) {
                Log.w("OverlayService", "hideOverlay 失敗", e)
            }
            overlayView = null
        }
    }
}
