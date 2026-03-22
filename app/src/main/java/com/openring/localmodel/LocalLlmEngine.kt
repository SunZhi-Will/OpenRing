package com.openring.localmodel

import android.content.Context
import android.util.Log
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.codeshipping.llamakotlin.LlamaConfig
import java.util.concurrent.Executors
import kotlin.math.min

data class LocalInferenceParams(
    val contextSize: Int,
    val maxTokens: Int,
    val threadCap: Int,
)

/**
 * 依 catalog id 調整記憶體與輸出長度（過大 context 易 OOM，保守為主）。
 */
fun localInferenceParamsForCatalog(catalogId: String): LocalInferenceParams {
    val id = catalogId.lowercase()
    return when {
        id.contains("phi") -> LocalInferenceParams(contextSize = 4096, maxTokens = 640, threadCap = 6)
        id.contains("gemma") -> LocalInferenceParams(contextSize = 4096, maxTokens = 704, threadCap = 6)
        id.contains("qwen") -> LocalInferenceParams(contextSize = 4096, maxTokens = 768, threadCap = 6)
        id.contains("tinyllama") -> LocalInferenceParams(contextSize = 2048, maxTokens = 512, threadCap = 6)
        else -> LocalInferenceParams(contextSize = 2048, maxTokens = 608, threadCap = 6)
    }
}

/**
 * 單例載入／推論：同一時間僅一則請求；切換 catalog id 時會卸載重載。
 *
 * **重要**：`llama-kotlin-android` 0.1.0 的 [org.codeshipping.llamakotlin.LlamaModel] 在 **IO** 執行緒載入、在 **Default** 執行緒呼叫
 * `nativeGenerate`，與 llama JNI「同執行緒使用 context」不相容，會 SIGSEGV。此處改為在固定單執行緒直接呼叫
 * [LocalLlamaJni] 轉呼叫 JNI，不經過 [org.codeshipping.llamakotlin.LlamaModel]（該類會跨 IO/Default 執行緒）。
 */
object LocalLlmEngine {
    private const val TAG = "OpenRing"

    /** 遞增後會強制卸載重載 GGUF（例如 mmap／batch／執行緒策略變更）。 */
    private const val NATIVE_LOAD_SCHEME = 3
    private val mutex = Mutex()

    private val llamaExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "OpenRing-LocalLlm").apply { isDaemon = true }
    }
    private val llamaDispatcher = llamaExecutor.asCoroutineDispatcher()

    @Volatile
    private var loadedCatalogId: String? = null

    @Volatile
    private var nativeHandle: Long = 0L

    private var loadedLlamaConfig: LlamaConfig? = null

    @Volatile
    private var loadedParams: LocalInferenceParams? = null

    @Volatile
    private var loadedNativeScheme: Int = 0

    /**
     * 超長 prompt 會讓 llama 原生層崩潰或 OOM。以 (ctx - maxGen) 估算可用 token，再換算成保守字數上限並截尾。
     *
     * 實機曾於約 800+ tokenizer tokens 時於 nativeGenerate SIGSEGV，故整段 prompt 採 **嚴格字元上限**（約 ≤600 粗估 tokens），
     * 並避免在 ctx 不足時仍強制 ≥1536 字元。
     */
    internal fun clipPromptToContext(params: LocalInferenceParams, prompt: String): String {
        val tokenBudget = (params.contextSize - params.maxTokens).coerceAtLeast(64)
        val maxChars = (tokenBudget * 2).coerceAtMost(1400).coerceIn(256, 1400)
        if (prompt.length <= maxChars) return prompt
        val note = "(truncated: context limit)\n"
        val tailBudget = (maxChars - note.length).coerceAtLeast(128)
        Log.w(
            TAG,
            "Prompt clipped ${prompt.length} -> $maxChars chars (ctx=${params.contextSize} maxTok=${params.maxTokens} tokenBudget~$tokenBudget)"
        )
        return note + prompt.takeLast(tailBudget)
    }

    suspend fun unload() {
        withContext(llamaDispatcher) {
            mutex.withLock {
                closeNativeLocked()
            }
        }
    }

    private fun closeNativeLocked() {
        if (nativeHandle == 0L) return
        runCatching { LocalLlamaJni.nativeCancelGeneration(nativeHandle) }
        runCatching { LocalLlamaJni.nativeDestroyContext(nativeHandle) }
        nativeHandle = 0L
        loadedLlamaConfig = null
        loadedCatalogId = null
        loadedParams = null
        loadedNativeScheme = 0
    }

    private fun buildLlamaConfig(params: LocalInferenceParams): LlamaConfig {
        // 實機：單執行緒 + 小 batch 仍可能在 nativeGenerate SIGSEGV；Android 上 mmap 偶發與檔案對應／記憶體行為有關，改 no-mmap 較穩。
        val threads = 1
        val batch = min(64, params.contextSize / 16).coerceAtLeast(16)
        return LlamaConfig().apply {
            contextSize = params.contextSize
            batchSize = batch
            this.threads = threads
            threadsBatch = threads
            temperature = 0.25f
            topP = 0.9f
            topK = 40
            repeatPenalty = 1.08f
            maxTokens = params.maxTokens
            useMmap = false
            useMlock = false
            gpuLayers = 0
        }.also { it.validate() }
    }

    private fun ensureNativeModelLoaded(context: Context, catalogId: String, file: java.io.File, params: LocalInferenceParams) {
        LocalLlamaJni.ensureLoaded()
        if (loadedCatalogId == catalogId && nativeHandle != 0L &&
            loadedParams == params && loadedNativeScheme == NATIVE_LOAD_SCHEME &&
            LocalLlamaJni.nativeIsModelLoaded(nativeHandle)
        ) {
            return
        }
        closeNativeLocked()
        val config = buildLlamaConfig(params)
        val handle = LocalLlamaJni.nativeCreateContext()
        if (handle == 0L) {
            throw IllegalStateException("nativeCreateContext failed")
        }
        val ok = try {
            LocalLlamaJni.nativeLoadModel(handle, file.absolutePath, config)
        } catch (e: Exception) {
            LocalLlamaJni.nativeDestroyContext(handle)
            throw e
        }
        if (!ok) {
            val err = LocalLlamaJni.nativeGetLastError(handle)
            LocalLlamaJni.nativeDestroyContext(handle)
            throw IllegalStateException(err?.takeIf { it.isNotBlank() } ?: "nativeLoadModel failed")
        }
        nativeHandle = handle
        loadedLlamaConfig = config
        loadedCatalogId = catalogId
        loadedParams = params
        loadedNativeScheme = NATIVE_LOAD_SCHEME
        Log.d(
            TAG,
            "LocalLlmEngine native load catalogId=$catalogId path=${file.absolutePath} ctx=${params.contextSize} maxTok=${params.maxTokens} scheme=$NATIVE_LOAD_SCHEME threads=${config.threads} batch=${config.batchSize} mmap=${config.useMmap}"
        )
    }

    private fun prepareLocked(
        context: Context,
        catalogId: String,
    ): LocalInferenceParams? {
        val entry = LocalModelCatalog.byId(catalogId) ?: return null
        val file = LocalModelCatalog.expectedFile(context, entry)
        if (!file.isFile || file.length() <= 0L) return null
        val params = localInferenceParamsForCatalog(catalogId)
        ensureNativeModelLoaded(context, catalogId, file, params)
        return params
    }

    /**
     * 單次推論（非串流）。
     */
    suspend fun generate(
        context: Context,
        catalogId: String,
        prompt: String,
        isCancelled: () -> Boolean,
    ): String {
        return withContext(llamaDispatcher) {
            mutex.withLock {
                val params = try {
                    prepareLocked(context, catalogId)
                } catch (e: Exception) {
                    Log.e(TAG, "Local prepare/load failed catalogId=$catalogId", e)
                    return@withLock "本機推論失敗：${e.message?.take(400) ?: e.javaClass.simpleName}"
                } ?: return@withLock LocalModelCatalog.byId(catalogId).let { e ->
                    if (e == null) {
                        "錯誤：未知的本機模型 id「$catalogId」。"
                    } else {
                        "錯誤：找不到 GGUF 檔案。請到設定下載「${e.label}」。"
                    }
                }

                val handle = nativeHandle
                val cfg = loadedLlamaConfig
                if (handle == 0L || cfg == null) {
                    return@withLock "錯誤：模型載入失敗。"
                }
                val safePrompt = clipPromptToContext(params, prompt)
                Log.i(
                    TAG,
                    "LocalLlmEngine.generate jni catalogId=$catalogId promptChars=${safePrompt.length} ctx=${params.contextSize} maxTok=${params.maxTokens}"
                )
                coroutineScope {
                    val cancelWatch = launch {
                        while (isActive) {
                            delay(280)
                            if (isCancelled()) {
                                runCatching { LocalLlamaJni.nativeCancelGeneration(handle) }
                                break
                            }
                        }
                    }
                    try {
                        if (isCancelled()) {
                            return@coroutineScope "已中斷。"
                        }
                        LocalLlamaJni.nativeGenerate(handle, safePrompt, cfg)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Local generate failed catalogId=$catalogId", e)
                        val msg = e.message?.replace("\n", " ")?.take(400)
                        if (!msg.isNullOrBlank()) {
                            return@coroutineScope "本機推論失敗：$msg"
                        }
                        return@coroutineScope "本機推論失敗：${e.javaClass.simpleName}"
                    } finally {
                        cancelWatch.cancel()
                    }
                }
            }
        }
    }

    /**
     * 串流推論；[onAccumulatedText] 每次收到新 token 後的完整累積字串（於呼叫執行緒執行，請自行切 Main 更新 UI）。
     */
    suspend fun generateStreaming(
        context: Context,
        catalogId: String,
        prompt: String,
        isCancelled: () -> Boolean,
        onAccumulatedText: (String) -> Unit,
    ): String {
        return withContext(llamaDispatcher) {
            mutex.withLock {
                val params = try {
                    prepareLocked(context, catalogId)
                } catch (e: Exception) {
                    Log.e(TAG, "Local prepare/load failed catalogId=$catalogId", e)
                    return@withLock "本機推論失敗：${e.message?.take(400) ?: e.javaClass.simpleName}"
                } ?: return@withLock LocalModelCatalog.byId(catalogId).let { e ->
                    if (e == null) {
                        "錯誤：未知的本機模型 id「$catalogId」。"
                    } else {
                        "錯誤：找不到 GGUF 檔案。請到設定下載「${e.label}」。"
                    }
                }

                val handle = nativeHandle
                val cfg = loadedLlamaConfig
                if (handle == 0L || cfg == null) {
                    return@withLock "錯誤：模型載入失敗。"
                }
                val safePrompt = clipPromptToContext(params, prompt)
                Log.i(
                    TAG,
                    "LocalLlmEngine.generateStream jni catalogId=$catalogId promptChars=${safePrompt.length} ctx=${params.contextSize} maxTok=${params.maxTokens}"
                )
                val sb = StringBuilder()
                coroutineScope {
                    val cancelWatch = launch {
                        while (isActive) {
                            delay(280)
                            if (isCancelled()) {
                                runCatching { LocalLlamaJni.nativeCancelGeneration(handle) }
                                break
                            }
                        }
                    }
                    try {
                        if (isCancelled()) {
                            return@coroutineScope "已中斷。"
                        }
                        LocalLlamaJni.nativeGenerateStream(
                            handle,
                            safePrompt,
                            LocalLlamaJni.TokenSink { token ->
                                if (isCancelled()) return@TokenSink
                                sb.append(token)
                                onAccumulatedText(sb.toString())
                            },
                            cfg,
                        )
                        sb.toString()
                    } catch (e: Throwable) {
                        Log.e(TAG, "Local stream failed catalogId=$catalogId", e)
                        val msg = e.message?.replace("\n", " ")?.take(400)
                        if (!msg.isNullOrBlank()) {
                            return@coroutineScope "本機推論失敗：$msg"
                        }
                        return@coroutineScope "本機推論失敗：${e.javaClass.simpleName}"
                    } finally {
                        cancelWatch.cancel()
                    }
                }
            }
        }
    }
}
