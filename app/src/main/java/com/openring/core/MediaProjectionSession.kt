package com.openring.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 保存使用者透過螢幕擷取意圖取得的 [MediaProjection]，供 [PlaybackAudioCapture] 擷取**他 App 播放音訊**。
 */
object MediaProjectionSession {

    private const val TAG = "OpenRing"

    @Volatile
    private var projection: MediaProjection? = null

    fun isActive(): Boolean = projection != null

    /**
     * 在 [Activity.onActivityResult] 成功後呼叫；會註冊 [MediaProjection.Callback] 並在系統收回時停止前台服務。
     */
    @Synchronized
    fun attachFromActivityResult(activity: Activity, resultCode: Int, data: Intent) {
        val previous = projection
        projection = null
        try {
            previous?.stop()
        } catch (_: Exception) {
        }
        val mpm = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = mpm.getMediaProjection(resultCode, data) ?: run {
            Log.w(TAG, "getMediaProjection returned null")
            MediaProjectionHostService.requestStop(activity.applicationContext)
            return
        }
        val appCtx = activity.applicationContext
        val handler = Handler(Looper.getMainLooper())
        mp.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    synchronized(MediaProjectionSession) {
                        if (projection === mp) {
                            projection = null
                        }
                    }
                    MediaProjectionHostService.requestStop(appCtx)
                }
            },
            handler,
        )
        projection = mp
        Log.d(TAG, "MediaProjection attached")
    }

    @Synchronized
    fun getProjection(): MediaProjection? = projection

    /**
     * 僅清除本機參考（projection 可能已由系統停止）。
     */
    @Synchronized
    fun clearProjectionOnly() {
        projection = null
    }

    /**
     * 使用者主動停止：停止 projection 並帶動前台服務結束。
     */
    fun releaseAndStopService(context: Context) {
        val mp = synchronized(this) {
            val p = projection
            projection = null
            p
        }
        try {
            mp?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "MediaProjection.stop", e)
        }
        MediaProjectionHostService.requestStop(context.applicationContext)
    }
}
