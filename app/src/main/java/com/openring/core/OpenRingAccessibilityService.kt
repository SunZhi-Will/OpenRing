package com.openring.core

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.openring.core.model.ViewNode

/**
 * OpenRing 無障礙服務
 * US-1.1: 啟用後可取得當前畫面節點資訊
 * 整合 ViewTreeParser、ActionExecutor、SensitiveFilter
 */
class OpenRingAccessibilityService : AccessibilityService() {

    private lateinit var viewTreeParser: ViewTreeParser
    private lateinit var actionExecutor: ActionExecutor

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        viewTreeParser = ViewTreeParser(this)
        actionExecutor = ActionExecutor(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 事件由 ScriptExecutor 主動請求解析時處理
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
    }

    /**
     * 取得當前畫面的結構化節點樹（已過濾敏感節點）
     */
    fun getViewTree(): ViewNode? {
        val raw = viewTreeParser.parseFromWindow() ?: return null
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
        instance = null
        return super.onUnbind(intent)
    }
}
