package com.openring.agent

/**
 * Classifies tools that change device state, exfiltrate media, run code, or alter schedules/settings.
 * Used when [com.openring.settings.AgentGovernanceStore] is in confirm-sensitive mode.
 */
object ToolRiskClassifier {

    fun isHighRisk(toolName: String): Boolean {
        val name = toolName.trim()
        if (name.isEmpty()) return false
        if (name.startsWith("skill_")) return true
        return when (name) {
            "call_skill",
            "create_skill",
            "install_skill",
            "install_official_skill",
            "set_system_prompt",
            "http_request",
            "launch_app",
            "find_and_click",
            "duolingo_match_pick",
            "click_send_button",
            "click_node",
            "swipe",
            "back",
            "home",
            "input_text",
            "input_text_focused",
            "verify_send_result",
            "describe_screen",
            "describe_ambient_audio",
            "update_script_schedule",
            "create_scheduled_script",
            "delete_scheduled_script",
            "memory_save_fact",
            "memory_delete_fact",
            "memory_set_session_summary",
            "memory_save_chunk",
            -> true
            else -> false
        }
    }
}
