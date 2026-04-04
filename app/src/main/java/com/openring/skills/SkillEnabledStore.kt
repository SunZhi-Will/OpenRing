package com.openring.skills

import android.content.Context

/**
 * Skill enable/disable 狀態（由 UI 勾選控制）。
 *
 * 「取消引用」的語意在此對應：此 skill 在 `call_skill` 時視為禁用。
 */
class SkillEnabledStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getEnabledIds(): List<String> =
        prefs.getString(KEY_LIST, null)
            ?.split(SEP)
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    fun isEnabled(skillId: String): Boolean = getEnabledIds().any { it.equals(skillId, ignoreCase = true) }

    fun setEnabled(skillId: String, enabled: Boolean) {
        val current = getEnabledIds().toMutableSet()
        val normalized = skillId.trim()
        if (normalized.isBlank()) return

        if (enabled) current.add(normalized) else current.removeAll { it.equals(normalized, ignoreCase = true) }

        prefs.edit().putString(KEY_LIST, current.joinToString(SEP)).apply()
    }

    /** Replaces the enabled set (fleet / relay sync). Preserves order of [ids]. */
    fun replaceEnabledIds(ids: List<String>) {
        val unique = LinkedHashSet<String>()
        for (s in ids) {
            val t = s.trim()
            if (t.isNotEmpty()) unique.add(t)
        }
        prefs.edit().putString(KEY_LIST, unique.joinToString(SEP)).apply()
    }

    companion object {
        private const val PREFS_NAME = "openring_enabled_skills_prefs"
        private const val KEY_LIST = "enabled_skill_ids"
        private const val SEP = ","
    }
}

