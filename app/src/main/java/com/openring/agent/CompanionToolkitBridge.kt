package com.openring.agent

import kotlinx.serialization.json.JsonObject

/**
 * Optional hook for a **desktop / relay companion** to expose extra tools (e.g. MCP-style)
 * without bloating the APK. Default is null (no remote tools).
 *
 * Implementations should be thread-safe; callers invoke from agent loops on IO dispatcher.
 */
fun interface CompanionToolkitBridge {
    suspend fun invokeRemoteTool(name: String, args: JsonObject): ToolDispatcher.ToolResult?
}

object CompanionToolkitRegistry {
    @Volatile
    var bridge: CompanionToolkitBridge? = null
}
