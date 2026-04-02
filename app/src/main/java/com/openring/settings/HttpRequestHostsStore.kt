package com.openring.settings

import android.content.Context

/**
 * 全域 `http_request` 工具可連線的 HTTPS 主機白名單（與 Skill `networkHosts` 規則相同：完整主機名或 `*.example.com`）。
 */
class HttpRequestHostsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAllowedHosts(): List<String> =
        prefs.getString(KEY_HOSTS, null)
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    fun setAllowedHosts(hosts: List<String>) {
        val text = hosts.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        prefs.edit().putString(KEY_HOSTS, text.ifEmpty { null }).apply()
    }

    fun addAllowedHost(host: String) {
        val h = host.trim()
        if (h.isEmpty()) return
        val current = getAllowedHosts().toMutableSet()
        current.add(h)
        setAllowedHosts(current.sorted())
    }

    fun removeAllowedHost(host: String) {
        val current = getAllowedHosts().filterNot { it == host.trim() }
        setAllowedHosts(current)
    }

    companion object {
        private const val PREFS_NAME = "openring_http_request_hosts_prefs"
        private const val KEY_HOSTS = "allowed_hosts"
    }
}
