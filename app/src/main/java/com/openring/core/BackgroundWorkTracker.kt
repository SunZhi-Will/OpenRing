package com.openring.core

import android.content.Context
import com.openring.domain.refreshOpenRingRuntimeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Counts in-flight background work (scheduled scripts, manual script runs, AI chat runs, etc.)
 * so status UI + notifications can show "processing" instead of only "schedule armed".
 */
object BackgroundWorkTracker {
    private val count = AtomicInteger(0)
    private val _activeCount = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = _activeCount.asStateFlow()

    fun currentCount(): Int = count.get()

    fun acquire(context: Context) {
        count.incrementAndGet()
        _activeCount.value = count.get()
        refreshOpenRingRuntimeStatus(context.applicationContext)
    }

    fun release(context: Context) {
        val v = count.decrementAndGet()
        if (v < 0) count.set(0)
        _activeCount.value = count.get()
        refreshOpenRingRuntimeStatus(context.applicationContext)
    }
}
