package com.openring.settings

import android.net.Uri
import org.json.JSONObject

/**
 * Parses QR / clipboard payloads for OpenRing Cloud relay binding.
 * Supported: raw `ws://` / `wss://`, `openring://relay?url=…`, or JSON `{"url":"ws://…"}`.
 */
object RelayQrPayload {

    fun parse(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        if (t.startsWith("ws://", ignoreCase = true) || t.startsWith("wss://", ignoreCase = true)) {
            return t
        }
        val uri = Uri.parse(t)
        if (uri.scheme == "openring" && uri.host == "relay") {
            val u = uri.getQueryParameter("url")?.trim()
            if (!u.isNullOrEmpty() && isWsUrl(u)) return u
        }
        if (t.startsWith("{")) {
            try {
                val json = JSONObject(t)
                val u = json.optString("url", "").trim()
                if (isWsUrl(u)) return u
            } catch (_: Exception) {
                /* ignore */
            }
        }
        return null
    }

    private fun isWsUrl(s: String): Boolean =
        s.startsWith("ws://", ignoreCase = true) || s.startsWith("wss://", ignoreCase = true)
}
