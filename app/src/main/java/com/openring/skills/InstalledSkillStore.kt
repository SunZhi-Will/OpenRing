package com.openring.skills

import android.content.Context

/**
 * 已安裝的 Skill 清單（id）。安裝時寫入，call_skill 時依此解析。
 */
class InstalledSkillStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getInstalledIds(): List<String> =
        prefs.getString(KEY_LIST, null)
            ?.split(SEP)
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    fun getSkillDir(context: Context, skillId: String): java.io.File? {
        val dir = java.io.File(context.filesDir, "skills/$skillId")
        return if (dir.isDirectory) dir else null
    }

    fun addInstalled(skillId: String) {
        val current = getInstalledIds().toMutableSet()
        current.add(skillId)
        prefs.edit().putString(KEY_LIST, current.joinToString(SEP)).apply()
    }

    fun removeInstalled(skillId: String) {
        val current = getInstalledIds().toMutableSet()
        current.remove(skillId)
        prefs.edit().putString(KEY_LIST, current.joinToString(SEP)).apply()
    }

    companion object {
        private const val PREFS_NAME = "openring_installed_skills_prefs"
        private const val KEY_LIST = "installed_skill_ids"
        private const val SEP = ","
    }
}
