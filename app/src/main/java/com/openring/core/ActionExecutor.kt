package com.openring.core

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.openring.core.model.ActionResult
import com.openring.core.model.ErrorCode
import com.openring.core.model.ViewNode

/**
 * 執行點擊、滑動、長按、返回、Home
 * US-1.3, US-1.4, US-1.5
 */
@RequiresApi(Build.VERSION_CODES.N)
class ActionExecutor(private val service: AccessibilityService) {

    companion object {
        private const val GESTURE_DURATION_MS = 100L
        private const val DEFAULT_SWIPE_DISTANCE = 300
        private val PASTE_LABELS = listOf("貼上", "Paste", "PASTE", "粘貼", "붙여넣기")
    }

    /**
     * 依 nodeId 點擊節點
     */
    fun clickByNodeId(nodeId: String, nodeTree: ViewNode?): ActionResult {
        val node = findNodeById(nodeTree, nodeId) ?: return ActionResult.Error(ErrorCode.NODE_NOT_FOUND)
        return performClick(node.bounds.centerX(), node.bounds.centerY())
    }

    /**
     * 依 nodeId 對應到當前視窗的 AccessibilityNodeInfo，並填入文字。
     *
     * 注意：ViewTreeParser 的 nodeId 是每次掃描臨時生成，因此這裡用 bounds 來回找實際節點。
     */
    fun inputTextByNodeId(nodeId: String, text: String, nodeTree: ViewNode?): ActionResult {
        val target = findNodeById(nodeTree, nodeId) ?: return ActionResult.Error(ErrorCode.NODE_NOT_FOUND)
        val root = service.rootInActiveWindow ?: return ActionResult.Error(ErrorCode.ACTION_FAILED, "No active window")

        val nodeInfo = findNodeInfoByBounds(root, target.bounds)
            ?: return ActionResult.Error(ErrorCode.NODE_NOT_FOUND, "Cannot map nodeId=$nodeId to AccessibilityNodeInfo")
        return inputTextWithReactNativeStrategies(root, nodeInfo, text)
    }

    /**
     * 直接針對目前有輸入焦點的欄位輸入文字（避免動態 UI 造成 nodeId 不穩定）。
     */
    fun inputTextToFocused(text: String): ActionResult {
        val root = service.rootInActiveWindow ?: return ActionResult.Error(ErrorCode.ACTION_FAILED, "No active window")
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return ActionResult.Error(ErrorCode.NODE_NOT_FOUND, "No focused input")
        return inputTextWithReactNativeStrategies(root, focused, text)
    }

    private fun inputTextWithReactNativeStrategies(
        root: AccessibilityNodeInfo,
        seedNode: AccessibilityNodeInfo,
        text: String
    ): ActionResult {
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return ActionResult.Error(ErrorCode.ACTION_FAILED, "No ClipboardManager available")
        val oldClip = clipboard.primaryClip

        val candidates = linkedSetOf<AccessibilityNodeInfo>()
        candidates.add(seedNode)
        findTrueEditableNode(seedNode)?.let { candidates.add(it) }
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { focused ->
            candidates.add(focused)
            findTrueEditableNode(focused)?.let { candidates.add(it) }
        }

        var lastDetail = "No candidate nodes"
        for (node in candidates) {
            val r = tryInputOnNode(node, text, clipboard)
            if (r.ok) {
                oldClip?.let { clipboard.setPrimaryClip(it) }
                return ActionResult.Ok
            }
            lastDetail = r.detail
        }

        oldClip?.let { clipboard.setPrimaryClip(it) }
        return ActionResult.Error(ErrorCode.ACTION_FAILED, "Input blocked. $lastDetail")
    }

    private data class InputAttemptResult(val ok: Boolean, val detail: String)

    private fun tryInputOnNode(
        nodeInfo: AccessibilityNodeInfo,
        text: String,
        clipboard: ClipboardManager
    ): InputAttemptResult {
        // React Native often needs a "human rhythm": click -> short wait -> focus -> set/paste.
        val delays = intArrayOf(120, 300, 500)
        var lastDetail = ""
        for (delayMs in delays) {
            val okClick = nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            SystemClock.sleep(delayMs.toLong())
            val okFocus = nodeInfo.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val okSetText = nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (okSetText) {
                return InputAttemptResult(true, "set_text success")
            }

            clipboard.setPrimaryClip(ClipData.newPlainText("openring", text))
            SystemClock.sleep(80)
            val okPaste = nodeInfo.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            if (okPaste) {
                return InputAttemptResult(true, "paste success")
            }

            // Fallback for React Native / wrapped inputs:
            // long-press the target to open context menu, then click "Paste".
            val okLongPress = nodeInfo.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            if (okLongPress) {
                SystemClock.sleep(220)
                val clickedMenuPaste = clickPasteMenuItemFromWindow()
                if (clickedMenuPaste) {
                    return InputAttemptResult(true, "long_press_menu_paste success")
                }
            }

            val supported = nodeInfo.actionList.joinToString(separator = ",") { it.label?.toString() ?: it.id.toString() }
            lastDetail = "click=$okClick focus=$okFocus setText=$okSetText paste=$okPaste longPress=$okLongPress delayMs=$delayMs editable=${nodeInfo.isEditable} class=${nodeInfo.className} supportedActions=[$supported]"
        }
        return InputAttemptResult(false, lastDetail)
    }

    private fun clickPasteMenuItemFromWindow(): Boolean {
        val root = service.rootInActiveWindow ?: return false
        for (label in PASTE_LABELS) {
            val node = findNodeInfoByText(root, label) ?: continue
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            val r = Rect()
            node.getBoundsInScreen(r)
            if (!r.isEmpty) {
                return performClick(r.centerX(), r.centerY()) is ActionResult.Ok
            }
        }
        return false
    }

    private fun findTrueEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val className = node.className?.toString().orEmpty()
        val hasSetText = node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
        val hasPaste = node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_PASTE }
        if (node.isEditable || className.contains("EditText", ignoreCase = true) || hasSetText || hasPaste) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val hit = findTrueEditableNode(child)
            if (hit != null) return hit
        }
        return null
    }

    private fun findNodeInfoByText(node: AccessibilityNodeInfo?, target: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        if (text.equals(target, ignoreCase = true) || desc.equals(target, ignoreCase = true)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val hit = findNodeInfoByText(child, target)
            if (hit != null) return hit
        }
        return null
    }

    /**
     * 依 text 或 contentDesc 點擊（match: exact, contains）
     */
    fun clickByText(
        text: String,
        match: String,
        nodeTree: ViewNode?
    ): ActionResult {
        val node = findNodeByText(nodeTree, text, match)
            ?: return ActionResult.Error(ErrorCode.NODE_NOT_FOUND, "text='$text' match=$match")
        return performClick(node.bounds.centerX(), node.bounds.centerY())
    }

    private fun performClick(x: Int, y: Int): ActionResult {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, GESTURE_DURATION_MS)
        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
        return if (service.dispatchGesture(gesture, null, null)) {
            ActionResult.Ok
        } else {
            ActionResult.Error(ErrorCode.ACTION_FAILED)
        }
    }

    /**
     * 滑動 direction: up, down, left, right
     */
    fun swipe(direction: String, distance: Int = DEFAULT_SWIPE_DISTANCE): ActionResult {
        val root = service.rootInActiveWindow ?: return ActionResult.Error(ErrorCode.ACTION_FAILED)
        val rect = Rect()
        root.getBoundsInScreen(rect)
        val centerX = rect.centerX()
        val centerY = rect.centerY()

        val (startX, startY, endX, endY) = when (direction.lowercase()) {
            "up" -> floatArrayOf(centerX.toFloat(), centerY + distance / 2f, centerX.toFloat(), centerY - distance / 2f)
            "down" -> floatArrayOf(centerX.toFloat(), centerY - distance / 2f, centerX.toFloat(), centerY + distance / 2f)
            "left" -> floatArrayOf(centerX + distance / 2f, centerY.toFloat(), centerX - distance / 2f, centerY.toFloat())
            "right" -> floatArrayOf(centerX - distance / 2f, centerY.toFloat(), centerX + distance / 2f, centerY.toFloat())
            else -> return ActionResult.Error(ErrorCode.ACTION_FAILED, "Unknown direction: $direction")
        }

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 200)
        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
        return if (service.dispatchGesture(gesture, null, null)) {
            ActionResult.Ok
        } else {
            ActionResult.Error(ErrorCode.ACTION_FAILED)
        }
    }

    /**
     * 長按
     */
    fun longPress(nodeTree: ViewNode?, nodeId: String? = null, text: String? = null): ActionResult {
        val node = when {
            nodeId != null -> findNodeById(nodeTree, nodeId)
            text != null -> findNodeByText(nodeTree, text, "contains")
            else -> null
        } ?: return ActionResult.Error(ErrorCode.NODE_NOT_FOUND)
        return performLongPress(node.bounds.centerX(), node.bounds.centerY())
    }

    private fun performLongPress(x: Int, y: Int): ActionResult {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 600)
        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
        return if (service.dispatchGesture(gesture, null, null)) {
            ActionResult.Ok
        } else {
            ActionResult.Error(ErrorCode.ACTION_FAILED)
        }
    }

    /**
     * 返回鍵
     */
    fun back(): ActionResult {
        return if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
            ActionResult.Ok
        } else {
            ActionResult.Error(ErrorCode.ACTION_FAILED)
        }
    }

    /**
     * Home 鍵
     */
    fun home(): ActionResult {
        return if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) {
            ActionResult.Ok
        } else {
            ActionResult.Error(ErrorCode.ACTION_FAILED)
        }
    }

    private fun findNodeById(node: ViewNode?, id: String): ViewNode? {
        if (node == null) return null
        if (node.id == id) return node
        return node.children.firstNotNullOfOrNull { findNodeById(it, id) }
    }

    private fun findNodeInfoByBounds(node: AccessibilityNodeInfo, target: Rect): AccessibilityNodeInfo? {
        val r = Rect()
        node.getBoundsInScreen(r)

        val same = r.left == target.left && r.top == target.top && r.right == target.right && r.bottom == target.bottom
        val contains = r.contains(target)
        if ((same || contains) && node.isVisibleToUser) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val hit = findNodeInfoByBounds(child, target)
            if (hit != null) return hit
        }
        return null
    }

    private fun findNodeByText(node: ViewNode?, text: String, match: String): ViewNode? {
        if (node == null) return null
        val nodeText = node.text ?: node.contentDesc
        if (nodeText != null) {
            val matches = when (match.lowercase()) {
                "exact" -> nodeText.equals(text, ignoreCase = true)
                "contains" -> nodeText.contains(text, ignoreCase = true)
                else -> nodeText.contains(text, ignoreCase = true)
            }
            // Some apps (e.g. React Native) expose non-clickable text nodes inside clickable containers.
            // We still tap by bounds center to mimic user interaction.
            if (matches) return node
        }
        return node.children.firstNotNullOfOrNull { findNodeByText(it, text, match) }
    }

}
