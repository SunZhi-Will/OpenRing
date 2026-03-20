package com.openring.skills

import android.content.Context

/**
 * 允許 AI 從哪些 URL 安裝 Skill（白名單）。使用者需在設定中手動新增。
 */
class SkillAllowedSourcesStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAllowedUrls(): List<String> =
        prefs.getString(KEY_URLS, null)
            ?.split(SEP)
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    fun addAllowedUrl(url: String) {
        val current = getAllowedUrls().toMutableSet()
        current.add(url.trim())
        prefs.edit().putString(KEY_URLS, current.joinToString(SEP)).apply()
    }

    fun removeAllowedUrl(url: String) {
        val current = getAllowedUrls().toMutableSet()
        current.remove(url.trim())
        prefs.edit().putString(KEY_URLS, current.joinToString(SEP)).apply()
    }

    fun isAllowed(url: String): Boolean = getAllowedUrls().any { allowed ->
        url.startsWith(allowed, ignoreCase = true) || allowed == url.trim()
    }

    companion object {
        private const val PREFS_NAME = "openring_skill_sources_prefs"
        private const val KEY_URLS = "allowed_skill_urls"
        private const val SEP = ","
    }
}
