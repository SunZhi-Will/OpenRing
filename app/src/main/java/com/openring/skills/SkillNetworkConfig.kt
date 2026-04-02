package com.openring.skills

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

/**
 * 從 manifest.json 解析 Skill 是否允許網路，以及允許的 HTTPS 主機清單。
 */
data class SkillNetworkConfig(
    val networkEnabled: Boolean,
    /** 非空時，僅允許連線至清單內主機（見 [hostMatches]）。 */
    val allowedHosts: List<String>,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(manifestJson: String): SkillNetworkConfig {
            val root = runCatching { json.parseToJsonElement(manifestJson).jsonObject }
                .getOrElse { return SkillNetworkConfig(false, emptyList()) }
            val networkEnabled = permissionsRequestNetwork(root["permissions"])
            val hosts = root["networkHosts"]?.jsonArray?.mapNotNull { el ->
                (el as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }
            }.orEmpty()
            return SkillNetworkConfig(networkEnabled, hosts)
        }

        private fun permissionsRequestNetwork(permissions: kotlinx.serialization.json.JsonElement?): Boolean {
            if (permissions == null) return false
            return when (permissions) {
                is JsonArray ->
                    permissions.any { it is JsonPrimitive && it.content == "network" }
                is JsonObject -> {
                    when (val n = permissions["network"]) {
                        null -> false
                        is JsonPrimitive -> {
                            val c = n.content
                            when {
                                c.equals("true", ignoreCase = true) -> true
                                c.equals("false", ignoreCase = true) -> false
                                c.isEmpty() -> false
                                else -> true
                            }
                        }
                        is JsonObject -> true
                        else -> true
                    }
                }
                else -> false
            }
        }

        /**
         * 主機是否符合規則：完整比對，或規則為 `*.example.com` 時允許子網域。
         */
        fun hostMatches(host: String, rule: String): Boolean {
            val h = host.lowercase(Locale.US).trim()
            val r = rule.lowercase(Locale.US).trim()
            if (h.isEmpty() || r.isEmpty()) return false
            if (r == "*") return false
            if (r.startsWith("*.")) {
                val suffix = r.removePrefix("*.")
                return h == suffix || h.endsWith(".$suffix")
            }
            return h == r
        }

        fun isHostAllowed(host: String, rules: List<String>): Boolean =
            rules.any { hostMatches(host, it) }
    }
}
