package com.openring.core

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Delivers cloud relay text to [com.openring.ui.screens.ChatScreen] (same path as [startRun]).
 * Uses only [tryEmit] (never blocking [emit]) so a foreground service cannot deadlock before
 * [android.app.Activity.startActivity].
 */
object CloudRelayTaskBus {
    private const val TAG = "CloudRelayTask"

    private val _tasks = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val tasks: SharedFlow<String> = _tasks.asSharedFlow()

    /**
     * Non-blocking enqueue from [com.openring.ui.navigation.OpenRingNavHost] after reading
     * [com.openring.ui.MainActivity.EXTRA_RELAY_TASK_TEXT].
     */
    fun tryEnqueueFromRelay(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return true
        val ok = _tasks.tryEmit(t)
        Log.d(TAG, "tryEnqueueFromRelay len=${t.length} ok=$ok")
        if (!ok) {
            Log.e(TAG, "Relay task dropped: SharedFlow buffer full (max 32 pending)")
        }
        return ok
    }
}
