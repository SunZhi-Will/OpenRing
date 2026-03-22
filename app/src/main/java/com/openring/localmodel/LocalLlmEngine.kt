package com.openring.localmodel

import android.content.Context
import android.util.Log
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.codeshipping.llamakotlin.LlamaModel
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
 */
object LocalLlmEngine {
    private const val TAG = "OpenRing"
    private val mutex = Mutex()

    @Volatile
    private var loadedCatalogId: String? = null

    @Volatile
    private var model: LlamaModel? = null

    @Volatile
    private var loadedParams: LocalInferenceParams? = null

    suspend fun unload() {
        mutex.withLock {
            runCatching { model?.close() }
            model = null
            loadedCatalogId = null
            loadedParams = null
        }
    }

    private suspend fun ensureLoaded(
        context: Context,
        catalogId: String,
        file: java.io.File,
        params: LocalInferenceParams,
    ): LlamaModel {
        if (loadedCatalogId != catalogId || model == null || !model!!.isLoaded || loadedParams != params) {
            runCatching { model?.close() }
            model = null
            loadedCatalogId = null
            loadedParams = null
            Log.d(
                TAG,
                "LocalLlmEngine loading catalogId=$catalogId path=${file.absolutePath} ctx=${params.contextSize} maxTok=${params.maxTokens}"
            )
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            val threads = min(cores, params.threadCap)
            val m = LlamaModel.load(file.absolutePath) {
                contextSize = params.contextSize
                batchSize = 256
                this.threads = threads
                threadsBatch = threads
                temperature = 0.25f
                topP = 0.9f
                topK = 40
                repeatPenalty = 1.08f
                maxTokens = params.maxTokens
                useMmap = true
                useMlock = false
                gpuLayers = 0
            }
            model = m
            loadedCatalogId = catalogId
            loadedParams = params
        }
        return model!!
    }

    private suspend fun prepare(
        context: Context,
        catalogId: String,
    ): Pair<LlamaModel, LocalInferenceParams>? {
        val entry = LocalModelCatalog.byId(catalogId) ?: return null
        val file = LocalModelCatalog.expectedFile(context, entry)
        if (!file.isFile || file.length() <= 0L) return null
        val params = localInferenceParamsForCatalog(catalogId)
        val m = ensureLoaded(context, catalogId, file, params)
        return m to params
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
        val prep = prepare(context, catalogId)
            ?: return LocalModelCatalog.byId(catalogId).let { e ->
                if (e == null) {
                    "錯誤：未知的本機模型 id「$catalogId」。"
                } else {
                    "錯誤：找不到 GGUF 檔案。請到設定下載「${e.label}」。"
                }
            }

        val (m, _) = prep
        return mutex.withLock {
            val modelRef = model ?: return@withLock "錯誤：模型載入失敗。"
            coroutineScope {
                val cancelWatch = launch {
                    while (isActive) {
                        delay(280)
                        if (isCancelled()) {
                            runCatching { modelRef.cancelGeneration() }
                            break
                        }
                    }
                }
                try {
                    if (isCancelled()) {
                        return@coroutineScope "已中斷。"
                    }
                    modelRef.generate(prompt)
                } catch (e: Exception) {
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
        val prep = prepare(context, catalogId)
            ?: return LocalModelCatalog.byId(catalogId).let { e ->
                if (e == null) {
                    "錯誤：未知的本機模型 id「$catalogId」。"
                } else {
                    "錯誤：找不到 GGUF 檔案。請到設定下載「${e.label}」。"
                }
            }

        val (m, _) = prep
        return mutex.withLock {
            val modelRef = model ?: return@withLock "錯誤：模型載入失敗。"
            val sb = StringBuilder()
            coroutineScope {
                val cancelWatch = launch {
                    while (isActive) {
                        delay(280)
                        if (isCancelled()) {
                            runCatching { modelRef.cancelGeneration() }
                            break
                        }
                    }
                }
                try {
                    if (isCancelled()) {
                        return@coroutineScope "已中斷。"
                    }
                    modelRef.generateStream(prompt).collect { token ->
                        if (isCancelled()) {
                            runCatching { modelRef.cancelGeneration() }
                            return@collect
                        }
                        sb.append(token)
                        onAccumulatedText(sb.toString())
                    }
                    sb.toString()
                } catch (e: Exception) {
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
