package com.openring.skills

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * Skill 沙盒內同步 HTTP（僅 HTTPS、主機白名單），由 QuickJS [com.whl.quickjs.wrapper.JSCallFunction] 呼叫。
 */
object SkillHttpFetch {
    private const val MAX_BODY_BYTES = 512 * 1024

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * @param requestJson 來自 JS 的 JSON 字串：
     * `{ "url", "method", "headers"?, "body"? }`
     * method 預設 GET；body 為字串或 null。
     */
    fun execute(requestJson: String, allowedHosts: List<String>): String {
        if (allowedHosts.isEmpty()) {
            return errorPayload("networkHosts is empty (misconfigured host)")
        }
        val root = runCatching { json.parseToJsonElement(requestJson).jsonObject }.getOrElse {
            return errorPayload("invalid JSON: ${it.message}")
        }
        val urlStr = root["url"]?.jsonPrimitive?.content?.trim()
            ?: return errorPayload("missing url")
        val httpUrl = urlStr.toHttpUrlOrNull()
            ?: return errorPayload("invalid url")
        if (httpUrl.scheme != "https") {
            return errorPayload("only https URLs are allowed")
        }
        val host = httpUrl.host
        if (!SkillNetworkConfig.isHostAllowed(host, allowedHosts)) {
            return errorPayload("host not allowed: $host")
        }
        val method = (root["method"]?.jsonPrimitive?.content ?: "GET").uppercase()
        if (method !in ALLOWED_METHODS) {
            return errorPayload("method not allowed: $method")
        }
        val headersObj = root["headers"]?.let {
            runCatching { it.jsonObject }.getOrNull()
        }
        val bodyStr = root["body"]?.jsonPrimitive?.content

        val reqBuilder = Request.Builder().url(httpUrl)
        headersObj?.forEach { (k, v) ->
            val value = v.jsonPrimitive.content
            if (k.isNotBlank()) reqBuilder.header(k, value)
        }
        when (method) {
            "GET", "HEAD" -> reqBuilder.method(method, null)
            else -> {
                val mediaType = (headersObj?.get("Content-Type")?.jsonPrimitive?.content
                    ?: "application/octet-stream").toMediaType()
                val bytes = (bodyStr ?: "").toByteArray(Charset.forName("UTF-8"))
                reqBuilder.method(method, bytes.toRequestBody(mediaType))
            }
        }

        return runCatching {
            client.newCall(reqBuilder.build()).execute().use { resp ->
                val rb = resp.body
                val charset = rb?.contentType()?.charset(Charset.forName("UTF-8"))
                    ?: Charset.forName("UTF-8")
                val bodyBytes = rb?.bytes() ?: ByteArray(0)
                val clipped = if (bodyBytes.size > MAX_BODY_BYTES) {
                    bodyBytes.copyOf(MAX_BODY_BYTES)
                } else {
                    bodyBytes
                }
                val text = String(clipped, charset)
                val headersJson = buildJsonObject {
                    for (i in 0 until resp.headers.size) {
                        val name = resp.headers.name(i)
                        val value = resp.headers.value(i)
                        put(name, value)
                    }
                }
                successPayload(resp.code, headersJson, text, clipped.size < bodyBytes.size)
            }
        }.getOrElse { e ->
            errorPayload(e.message ?: e.javaClass.simpleName)
        }
    }

    private val ALLOWED_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")

    private fun successPayload(
        status: Int,
        headers: JsonObject,
        body: String,
        truncated: Boolean,
    ): String = buildJsonObject {
        put("ok", true)
        put("status", status)
        put("headers", headers)
        put("body", body)
        put("truncated", truncated)
    }.let { json.encodeToString(JsonObject.serializer(), it) }

    private fun errorPayload(message: String): String = buildJsonObject {
        put("ok", false)
        put("error", message)
    }.let { json.encodeToString(JsonObject.serializer(), it) }
}
