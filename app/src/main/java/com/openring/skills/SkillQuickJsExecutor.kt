package com.openring.skills

import android.util.Log
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * 在裝置上以 QuickJS 執行 Skill 的 `script.js`（需定義同步 `run(input)`）。
 *
 * 沙盒為純 JS：無 DOM、無 Node。若 manifest 宣告網路權限且提供 [networkHosts]，
 * 會注入同步函式 `__openring_fetch(jsonString)`（僅 HTTPS、主機須符合清單），見 [SkillHttpFetch]。
 */
object SkillQuickJsExecutor {

    private const val TAG = "SkillQuickJs"
    private const val MEMORY_LIMIT_BYTES = 8 * 1024 * 1024

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var loaderInitialized = false

    @Synchronized
    fun ensureLoaderInitialized() {
        if (!loaderInitialized) {
            QuickJSLoader.init()
            loaderInitialized = true
        }
    }

    fun execute(
        scriptSource: String,
        input: JsonObject,
        /** 非空時註冊 `__openring_fetch`（僅允許連線至此清單內主機）。 */
        networkHosts: List<String> = emptyList(),
    ): Result<JsonObject> {
        ensureLoaderInitialized()
        val inputStr = json.encodeToString(JsonObject.serializer(), input)
        val ctx = QuickJSContext.create()
        return try {
            ctx.setMemoryLimit(MEMORY_LIMIT_BYTES)
            val global = ctx.globalObject
            val parsed = ctx.parseJSON(inputStr)
            ctx.setProperty(global, "__openring_input", parsed)
            if (networkHosts.isNotEmpty()) {
                val hosts = networkHosts
                ctx.setProperty(
                    global,
                    "__openring_fetch",
                    JSCallFunction { args ->
                        val req = args.getOrNull(0)?.toString()
                            ?: """{"ok":false,"error":"missing request json"}"""
                        SkillHttpFetch.execute(req, hosts)
                    }
                )
            }
            val normalized = stripExportKeywords(scriptSource)
            val bundle = """
                $normalized
                (function(){
                  if (typeof run !== 'function') throw new Error('Skill must define function run(input)');
                  return JSON.stringify(run(__openring_input));
                })();
            """.trimIndent()
            val out = ctx.evaluate(bundle)
            val text = when (out) {
                is String -> out
                null -> "{}"
                else -> out.toString()
            }
            val element: JsonElement = json.parseToJsonElement(text.trim())
            val obj = element as? JsonObject
                ?: throw IllegalStateException("Skill run(input) must return a JSON object")
            Result.success(obj)
        } catch (e: QuickJSException) {
            Log.e(TAG, "QuickJS skill error", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Skill execution failed", e)
            Result.failure(e)
        } finally {
            try {
                ctx.destroy()
            } catch (_: Throwable) {
            }
        }
    }

    private fun stripExportKeywords(source: String): String {
        var s = source.trim()
        if (s.startsWith("export default ")) {
            s = s.removePrefix("export default ").trim()
        }
        s = s.replaceFirst(Regex("^export\\s+function\\s+"), "function ")
        s = s.replaceFirst(Regex("^export\\s+async\\s+function\\s+"), "async function ")
        return s
    }
}
