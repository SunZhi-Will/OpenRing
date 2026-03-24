package com.openring.core

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.openring.core.model.ViewNode

/**
 * OpenRing 無障礙服務
 * US-1.1: 啟用後可取得當前畫面節點資訊
 * 整合 ViewTreeParser、ActionExecutor、SensitiveFilter
 */
class OpenRingAccessibilityService : AccessibilityService() {

    private lateinit var viewTreeParser: ViewTreeParser
    private lateinit var actionExecutor: ActionExecutor
    private var lastKnownRoot: AccessibilityNodeInfo? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        viewTreeParser = ViewTreeParser(this)
        actionExecutor = ActionExecutor(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val source = event?.source ?: return
        var cursor: AccessibilityNodeInfo? = source
        var top: AccessibilityNodeInfo? = null
        while (cursor != null) {
            top = cursor
            cursor = cursor.parent
        }
        if (top != null) {
            lastKnownRoot?.recycle()
            lastKnownRoot = AccessibilityNodeInfo.obtain(top)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        lastKnownRoot?.recycle()
        lastKnownRoot = null
        super.onDestroy()
    }

    /**
     * 取得當前畫面的結構化節點樹（已過濾敏感節點）
     */
    fun getViewTree(): ViewNode? {
        val raw = getViewTreeWithRetry() ?: return null
        return SensitiveFilter.filter(raw)
    }

    fun getViewTreeParser(): ViewTreeParser = viewTreeParser
    fun getActionExecutor(): ActionExecutor = actionExecutor

    companion object {
        private var instance: OpenRingAccessibilityService? = null

        fun getInstance(): OpenRingAccessibilityService? = instance

        fun isEnabled(): Boolean = instance != null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        lastKnownRoot?.recycle()
        lastKnownRoot = null
        instance = null
        return super.onUnbind(intent)
    }

    private fun getViewTreeWithRetry(
        attempts: Int = 6,
        sleepMs: Long = 90L
    ): ViewNode? {
        repeat(attempts) { idx ->
            viewTreeParser.parseFromWindow()?.let { return it }
            val cached = lastKnownRoot
            if (cached != null) {
                viewTreeParser.parse(AccessibilityNodeInfo.obtain(cached))?.let { return it }
            }
            if (idx < attempts - 1) {
                SystemClock.sleep(sleepMs)
            }
        }
        return null
    }
}
