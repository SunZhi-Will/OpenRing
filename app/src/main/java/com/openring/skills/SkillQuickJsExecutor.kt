package com.openring.skills

import android.util.Log
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * 在裝置上以 QuickJS 執行 Skill 的 `script.js`（需定義同步 `run(input)`）。
 *
 * 沙盒為純 JS：無 DOM、無 Node、`manifest.json` 的 `permissions` 目前**不**授予網路或儲存等 host API。
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

    fun execute(scriptSource: String, input: JsonObject): Result<JsonObject> {
        ensureLoaderInitialized()
        val inputStr = json.encodeToString(JsonObject.serializer(), input)
        val ctx = QuickJSContext.create()
        return try {
            ctx.setMemoryLimit(MEMORY_LIMIT_BYTES)
            val global = ctx.globalObject
            val parsed = ctx.parseJSON(inputStr)
            ctx.setProperty(global, "__openring_input", parsed)
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
            val element = json.parseToJsonElement(text.trim())
            val obj = element.jsonObject
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
