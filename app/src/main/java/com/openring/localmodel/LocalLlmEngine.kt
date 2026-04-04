package com.openring.localmodel

import android.content.Context
import android.app.ActivityManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.codeshipping.llamakotlin.LlamaConfig
import java.util.concurrent.Executors

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
        // 與 [LocalLlamaNativeConstraints.resolvedContextSizeForCatalog] 上限對齊；避免請求過大 ctx 造成載入／KV 不穩。
        // 實機 log：請求 512 時 llama_n_ctx 在推論端可縮到 ~60（47 prompt tokens vs maxPrompt=12）。
        // 改請求較小 ctx，較易整段配置成功；maxGen 一併降低。
        id.contains("tinyllama") ->
            LocalInferenceParams(contextSize = 256, maxTokens = 16, threadCap = 2)
        id.contains("phi") -> LocalInferenceParams(contextSize = 1024, maxTokens = 256, threadCap = 4)
        id.contains("gemma-4") || id.contains("gemma4") ->
            LocalInferenceParams(contextSize = 1024, maxTokens = 256, threadCap = 4)
        id.contains("gemma") -> LocalInferenceParams(contextSize = 1024, maxTokens = 256, threadCap = 4)
        id.contains("qwen") -> LocalInferenceParams(contextSize = 1024, maxTokens = 256, threadCap = 4)
        id.contains("llama-3.2") || id.contains("llama3.2") ->
            LocalInferenceParams(contextSize = 1024, maxTokens = 256, threadCap = 4)
        else -> LocalInferenceParams(contextSize = 1024, maxTokens = 256, threadCap = 4)
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

    private fun isLocalCatalogModel(catalogId: String?): Boolean =
        catalogId != null && LocalModelCatalog.byId(catalogId) != null

    /**
     * 與 [buildLlamaConfig] 使用同一個「有效 ctx」計算 maxTokens，並對齊
     * [LocalLlamaNativeConstraints.maxGenerationTokensForTruncatePath]（native smartTruncate 門檻）。
     */
    private fun effectiveContextSize(params: LocalInferenceParams, catalogId: String?): Int =
        if (catalogId != null && isLocalCatalogModel(catalogId)) {
            LocalLlamaNativeConstraints.resolvedContextSizeForCatalog(params, catalogId)
        } else {
            params.contextSize
        }

    /**
     * 用於 **maxTokens 上限** 與 **prompt 字數裁剪**：扣 [LocalLlamaNativeConstraints] 內 slack，
     * 對齊 llama.cpp 可能下修 `n_ctx`、以及我們在 Java 端看不到真實 `llama_n_ctx` 的情況。
     */
    private fun planningContextSize(params: LocalInferenceParams, catalogId: String?): Int {
        val resolved = effectiveContextSize(params, catalogId)
        if (catalogId.isNullOrBlank() || !isLocalCatalogModel(catalogId)) return resolved
        return LocalLlamaNativeConstraints.planningNCtxForBudget(resolved, catalogId)
    }

    private fun effectiveMaxTokens(params: LocalInferenceParams, catalogId: String?): Int {
        val planCtx = planningContextSize(params, catalogId)
        val cap = LocalLlamaNativeConstraints.maxGenerationTokensForTruncatePath(planCtx)
        return params.maxTokens.coerceAtMost(cap)
    }

    /** Logcat：`adb logcat -s OpenRing:I | grep LocalLlmDiag` */
    private fun logBudgetTrace(
        phase: String,
        catalogId: String?,
        resolvedCtx: Int,
        planCtx: Int,
        reqCtx: Int,
        reqMaxTok: Int,
        effMaxTok: Int,
        slotsPlan: Int,
        slotsIfRealCtxResolved: Int,
        maxChars: Int,
        rawLen: Int,
        clippedLen: Int,
    ) {
        Log.i(
            TAG,
            "LocalLlmDiag BUDGET|$phase|catalogId=${catalogId ?: "?"} " +
                "resolvedCtx=$resolvedCtx planCtx=$planCtx reqCtx=$reqCtx reqMaxTok=$reqMaxTok effMaxTok=$effMaxTok " +
                "slotsPlan=$slotsPlan slotsIfLoadedCtx=resolved=>$slotsIfRealCtxResolved " +
                "minTrunc=${LocalLlamaNativeConstraints.MIN_TRUNCATE_PROMPT_SLOTS} " +
                "tplReserve=${LocalLlamaNativeConstraints.TEMPLATE_TOKEN_RESERVE} " +
                "charCap=${LocalLlamaNativeConstraints.CATALOG_PROMPT_CHAR_HARD_CAP} " +
                "maxChars=$maxChars rawLen=$rawLen clippedLen=$clippedLen",
        )
    }

    private fun logLongPromptSnippet(phase: String, catalogId: String, label: String, text: String) {
        // 避免單行 log 塞滿整段 prompt（logcat 難讀、像「怪回應」）；只打長度與頭尾摘要。
        val head = text.take(400).replace("\n", "\\n")
        val tail = text.takeLast(400).replace("\n", "\\n")
        Log.d(
            TAG,
            "LocalLlmDiag PROMPT_SNIP|$phase|$catalogId|$label len=${text.length} " +
                "HEAD=$head … TAIL=$tail",
        )
    }

    /**
     * 原生閃退時對照用：若進程直接 SIGSEGV，logcat 最後一則 `LocalLlmDiag` 可標示卡在哪個階段。
     */
    /** Log line for matching user-visible「本機推論失敗」／錯誤字串與 logcat。 */
    private fun logUserVisibleOutcome(
        level: Int,
        phase: String,
        catalogId: String,
        streaming: Boolean?,
        userVisible: String,
    ) {
        val stream = when (streaming) {
            true -> "streaming=true"
            false -> "streaming=false"
            null -> "streaming=n/a"
        }
        val msg =
            "LocalLlmDiag user_outcome phase=$phase catalogId=$catalogId $stream " +
                "userVisible=${userVisible.replace("\n", " ").take(500)}"
        when (level) {
            Log.ERROR -> Log.e(TAG, msg)
            Log.WARN -> Log.w(TAG, msg)
            else -> Log.i(TAG, msg)
        }
    }

    private fun logLocalLlmDiag(
        context: Context,
        phase: String,
        catalogId: String,
        extra: String = "",
    ) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val availMb = memInfo.availMem / (1024 * 1024)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        Log.i(
            TAG,
            "LocalLlmDiag phase=$phase catalogId=$catalogId thread=${Thread.currentThread().name} " +
                "device=${Build.MANUFACTURER}/${Build.DEVICE}/${Build.MODEL} sdk=${Build.VERSION.SDK_INT} " +
                "abis64=${Build.SUPPORTED_64_BIT_ABIS.joinToString()} nativeHandle=$nativeHandle " +
                "memAvailMb=$availMb memTotalMb=$totalMb lowMem=${memInfo.lowMemory} $extra".trimEnd()
        )
    }

    /** 遞增後會強制卸載重載 GGUF（例如 mmap／batch／執行緒策略變更）。 */
    private const val NATIVE_LOAD_SCHEME = 13
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
     * JNI 以 **tokenizer token 數** 判斷是否超長；Chat 模板會吃掉大量 token，不能用 `chars*2` 估算。
     * 目錄模型改用 [planningContextSize] + [LocalLlamaNativeConstraints.maxSafePromptChars]（近似 1 char ≤ 1 token）。
     */
    internal fun clipPromptToContext(
        params: LocalInferenceParams,
        prompt: String,
        catalogId: String? = null,
    ): String {
        val resolvedCtx = effectiveContextSize(params, catalogId)
        val planCtx = planningContextSize(params, catalogId)
        val maxGen = effectiveMaxTokens(params, catalogId)
        val slotsPlan = LocalLlamaNativeConstraints.maxPromptTokenSlots(planCtx, maxGen)
        val slotsResolved = LocalLlamaNativeConstraints.maxPromptTokenSlots(resolvedCtx, maxGen)
        val maxChars =
            if (catalogId != null && isLocalCatalogModel(catalogId)) {
                LocalLlamaNativeConstraints.maxSafePromptChars(slotsPlan)
            } else {
                val s = LocalLlamaNativeConstraints.maxPromptTokenSlots(resolvedCtx, maxGen)
                when {
                    s < LocalLlamaNativeConstraints.MIN_TRUNCATE_PROMPT_SLOTS ->
                        (s - 8).coerceAtLeast(1)
                    else ->
                        (s * 2).coerceAtMost(1400).coerceIn(256, 1400)
                }
            }
        logBudgetTrace(
            phase = "clip_compute",
            catalogId = catalogId,
            resolvedCtx = resolvedCtx,
            planCtx = planCtx,
            reqCtx = params.contextSize,
            reqMaxTok = params.maxTokens,
            effMaxTok = maxGen,
            slotsPlan = slotsPlan,
            slotsIfRealCtxResolved = slotsResolved,
            maxChars = maxChars,
            rawLen = prompt.length,
            clippedLen = prompt.length.coerceAtMost(maxChars),
        )
        if (catalogId != null && isLocalCatalogModel(catalogId)) {
            logLongPromptSnippet("pre_clip", catalogId, "raw", prompt)
        }
        if (prompt.length <= maxChars) return prompt
        val note = "(truncated: context limit)\n"
        val tailBudget = (maxChars - note.length).coerceAtLeast(1)
        val clipped = note + prompt.takeLast(tailBudget)
        Log.w(
            TAG,
            "LocalLlmDiag prompt_clipped catalogId=${catalogId ?: "?"} " +
                "${prompt.length} -> ${clipped.length} chars (resolvedCtx=$resolvedCtx planCtx=$planCtx effMaxTok=$maxGen " +
                "slotsPlan=$slotsPlan maxChars=$maxChars)",
        )
        logBudgetTrace(
            phase = "clip_done",
            catalogId = catalogId,
            resolvedCtx = resolvedCtx,
            planCtx = planCtx,
            reqCtx = params.contextSize,
            reqMaxTok = params.maxTokens,
            effMaxTok = maxGen,
            slotsPlan = slotsPlan,
            slotsIfRealCtxResolved = slotsResolved,
            maxChars = maxChars,
            rawLen = prompt.length,
            clippedLen = clipped.length,
        )
        if (catalogId != null && isLocalCatalogModel(catalogId)) {
            logLongPromptSnippet("post_clip", catalogId, "safe", clipped)
        }
        return clipped
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
        val h = nativeHandle
        Log.d(TAG, "LocalLlmDiag native_close_before handle=$h loadedId=$loadedCatalogId")
        runCatching { LocalLlamaJni.nativeCancelGeneration(h) }
        runCatching { LocalLlamaJni.nativeDestroyContext(h) }
        nativeHandle = 0L
        loadedLlamaConfig = null
        loadedCatalogId = null
        loadedParams = null
        loadedNativeScheme = 0
        Log.d(TAG, "LocalLlmDiag native_close_after")
    }

    private fun buildLlamaConfig(params: LocalInferenceParams, catalogId: String): LlamaConfig {
        val threads = 1
        val effMax = effectiveMaxTokens(params, catalogId)
        val ctx = effectiveContextSize(params, catalogId)
        val batch = when {
            isLocalCatalogModel(catalogId) -> minOf(ctx, 512).coerceAtLeast(128).let { c ->
                when {
                    c <= 512 -> 4
                    c <= 1024 -> 8
                    else -> 12
                }
            }
            ctx <= 1024 -> 8
            ctx <= 1536 -> 12
            else -> 16
        }
        return LlamaConfig().apply {
            contextSize = ctx
            batchSize = batch
            this.threads = threads
            threadsBatch = threads
            temperature = 0.25f
            topP = 0.9f
            topK = 40
            repeatPenalty = 1.08f
            maxTokens = effMax
            useMmap = isLocalCatalogModel(catalogId)
            useMlock = false
            gpuLayers = 0
        }.also { it.validate() }
    }

    private fun assertMemoryBudget(context: Context, modelFile: java.io.File, catalogId: String) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return
        val memInfo = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val modelBytes = modelFile.length().coerceAtLeast(0L)
        if (modelBytes <= 0L) return

        // Model-size aware budgets: keep protection for larger GGUFs while allowing tiny models.
        val isTinyClass = catalogId.lowercase().contains("tinyllama") || modelBytes <= 800L * 1024L * 1024L
        val availMultiplier = if (isTinyClass) 1.15 else 1.6
        val totalMultiplier = if (isTinyClass) 1.3 else 1.9
        val minAvailBytes = (modelBytes * availMultiplier).toLong()
        val minTotalBytes = (modelBytes * totalMultiplier).toLong()
        val isUnderBudget = memInfo.availMem < minAvailBytes || memInfo.totalMem < minTotalBytes
        if (!isUnderBudget) {
            Log.d(
                TAG,
                "LocalLlmDiag memory_check_ok catalogId=$catalogId modelBytes=$modelBytes tiny=$isTinyClass " +
                    "avail=${memInfo.availMem} minAvail=$minAvailBytes"
            )
            return
        }

        val modelMb = modelBytes / (1024 * 1024)
        val availMb = memInfo.availMem / (1024 * 1024)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        val minAvailMb = minAvailBytes / (1024 * 1024)
        val minTotalMb = minTotalBytes / (1024 * 1024)
        if (isTinyClass) {
            Log.w(
                TAG,
                "Low-memory allow for tiny model ($catalogId, model=${modelMb}MB, avail=${availMb}MB, total=${totalMb}MB, needAvail>=${minAvailMb}MB, needTotal>=${minTotalMb}MB)"
            )
            return
        }
        Log.e(
            TAG,
            "LocalLlmDiag memory_check_fail catalogId=$catalogId modelMb=$modelMb availMb=$availMb totalMb=$totalMb " +
                "minAvailMb=$minAvailMb minTotalMb=$minTotalMb"
        )
        throw IllegalStateException(
            "裝置記憶體不足以穩定載入此本機模型（$catalogId, model=${modelMb}MB, avail=${availMb}MB, total=${totalMb}MB, needAvail>=${minAvailMb}MB, needTotal>=${minTotalMb}MB）。請改用較小模型或先釋放記憶體。"
        )
    }

    private fun ensureNativeModelLoaded(context: Context, catalogId: String, file: java.io.File, params: LocalInferenceParams) {
        LocalLlamaJni.ensureLoaded()
        if (loadedCatalogId == catalogId && nativeHandle != 0L &&
            loadedParams == params && loadedNativeScheme == NATIVE_LOAD_SCHEME &&
            LocalLlamaJni.nativeIsModelLoaded(nativeHandle)
        ) {
            logLocalLlmDiag(context, "load_skip_already_loaded", catalogId, "path=${file.absolutePath}")
            return
        }
        logLocalLlmDiag(
            context,
            "load_start",
            catalogId,
            "path=${file.absolutePath} fileBytes=${file.length()} ctx=${params.contextSize} maxTok=${params.maxTokens} scheme=$NATIVE_LOAD_SCHEME"
        )
        val loadT0 = SystemClock.elapsedRealtime()
        assertMemoryBudget(context, file, catalogId)
        closeNativeLocked()
        val config = buildLlamaConfig(params, catalogId)
        logLocalLlmDiag(context, "native_create_context_before", catalogId, "threads=${config.threads} batch=${config.batchSize}")
        val handle = LocalLlamaJni.nativeCreateContext()
        if (handle == 0L) {
            Log.e(TAG, "LocalLlmDiag native_create_context_failed catalogId=$catalogId")
            throw IllegalStateException("nativeCreateContext failed")
        }
        logLocalLlmDiag(context, "native_load_model_before", catalogId, "handle=$handle path=${file.absolutePath}")
        val ok = try {
            LocalLlamaJni.nativeLoadModel(handle, file.absolutePath, config)
        } catch (e: Exception) {
            Log.e(TAG, "LocalLlmDiag native_load_model_exception catalogId=$catalogId", e)
            LocalLlamaJni.nativeDestroyContext(handle)
            throw e
        }
        if (!ok) {
            val err = LocalLlamaJni.nativeGetLastError(handle)
            Log.e(TAG, "LocalLlmDiag native_load_model_failed catalogId=$catalogId err=$err")
            LocalLlamaJni.nativeDestroyContext(handle)
            throw IllegalStateException(err?.takeIf { it.isNotBlank() } ?: "nativeLoadModel failed")
        }
        nativeHandle = handle
        loadedLlamaConfig = config
        loadedCatalogId = catalogId
        loadedParams = params
        loadedNativeScheme = NATIVE_LOAD_SCHEME
        val loadMs = SystemClock.elapsedRealtime() - loadT0
        val nativeVer = LocalLlamaJni.getNativeLibraryVersion() ?: "unknown"
        val resolved = config.contextSize
        val plan = planningContextSize(params, catalogId)
        val eff = config.maxTokens
        val sp = LocalLlamaNativeConstraints.maxPromptTokenSlots(plan, eff)
        val sr = LocalLlamaNativeConstraints.maxPromptTokenSlots(resolved, eff)
        Log.i(
            TAG,
            "LocalLlmDiag load_ok catalogId=$catalogId path=${file.absolutePath} " +
                "reqCtx=${params.contextSize} reqMaxTok=${params.maxTokens} scheme=$NATIVE_LOAD_SCHEME " +
                "jniCtx=$resolved jniMaxTok=$eff nativeLib=$nativeVer " +
                "planCtx=$plan slotsPlan=$sp slotsIfRealCtx=$sr threads=${config.threads} batch=${config.batchSize} " +
                "mmap=${config.useMmap} loadElapsedMs=$loadMs",
        )
    }

    private fun prepareLocked(
        context: Context,
        catalogId: String,
    ): LocalInferenceParams? {
        val entry = LocalModelCatalog.byId(catalogId) ?: run {
            Log.w(TAG, "LocalLlmDiag prepare_abort reason=unknown_catalog_id catalogId=$catalogId")
            return null
        }
        val file = LocalModelCatalog.expectedFile(context, entry)
        if (!file.isFile || file.length() <= 0L) {
            Log.w(
                TAG,
                "LocalLlmDiag prepare_abort reason=missing_or_empty_gguf catalogId=$catalogId label=${entry.label} " +
                    "path=${file.absolutePath} exists=${file.exists()} isFile=${file.isFile} len=${file.length()}"
            )
            return null
        }
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
                logLocalLlmDiag(context, "generate_request_enter", catalogId, "mode=oneshot streaming=false")
                val params = try {
                    prepareLocked(context, catalogId)
                } catch (e: Exception) {
                    val userMsg = "本機推論失敗：${e.message?.take(400) ?: e.javaClass.simpleName}"
                    Log.e(TAG, "LocalLlmDiag prepare_failed catalogId=$catalogId userVisible=${userMsg.take(500)}", e)
                    return@withLock userMsg
                } ?: run {
                    val msg = LocalModelCatalog.byId(catalogId).let { e ->
                        if (e == null) {
                            "錯誤：未知的本機模型 id「$catalogId」。"
                        } else {
                            "錯誤：找不到 GGUF 檔案。請到設定下載「${e.label}」。"
                        }
                    }
                    logUserVisibleOutcome(Log.WARN, "prepare_unsatisfied", catalogId, false, msg)
                    return@withLock msg
                }

                val handle = nativeHandle
                val cfg = loadedLlamaConfig
                if (handle == 0L || cfg == null) {
                    val msg = "錯誤：模型載入失敗。"
                    Log.e(
                        TAG,
                        "LocalLlmDiag invariant_broken catalogId=$catalogId handle=$handle cfgNull=${cfg == null} " +
                            "loadedCatalogId=$loadedCatalogId userVisible=${msg.take(500)}"
                    )
                    return@withLock msg
                }
                val safePrompt = clipPromptToContext(params, prompt, catalogId)
                val genT0 = SystemClock.elapsedRealtime()
                logLocalLlmDiag(
                    context,
                    "generate_stream_before",
                    catalogId,
                    "promptChars=${safePrompt.length} rawPromptChars=${prompt.length} ctx=${params.contextSize} " +
                        "reqMaxTok=${params.maxTokens} jniMaxTok=${cfg.maxTokens}"
                )
                try {
                    coroutineScope {
                        try {
                            if (isCancelled()) {
                                Log.i(TAG, "LocalLlmDiag generate_cancelled catalogId=$catalogId phase=before_native")
                                return@coroutineScope "已中斷。"
                            }
                            // nativeGenerate has repeatedly SIGSEGV on some devices;
                            // use stream API and accumulate to avoid that JNI path.
                            val sb = StringBuilder()
                            LocalLlamaJni.nativeGenerateStream(
                                handle,
                                safePrompt,
                                LocalLlamaJni.TokenSink { token ->
                                    if (isCancelled()) return@TokenSink
                                    sb.append(token)
                                },
                                cfg,
                            )
                            if (isCancelled()) {
                                Log.i(TAG, "LocalLlmDiag generate_cancelled catalogId=$catalogId phase=after_native")
                                "已中斷。"
                            } else {
                                sb.toString().also {
                                    val elapsed = SystemClock.elapsedRealtime() - genT0
                                    val preview = it.trim().replace("\n", " ").take(160)
                                    Log.i(
                                        TAG,
                                        "LocalLlmDiag generate_stream_after catalogId=$catalogId outChars=${it.length} " +
                                            "genElapsedMs=$elapsed streaming=false preview=$preview"
                                    )
                                }
                            }
                        } catch (e: Throwable) {
                            val userMsg = when {
                                !e.message.isNullOrBlank() ->
                                    "本機推論失敗：${e.message!!.replace("\n", " ").take(400)}"
                                else -> "本機推論失敗：${e.javaClass.simpleName}"
                            }
                            Log.e(TAG, "LocalLlmDiag generate_stream_failed catalogId=$catalogId streaming=false " +
                                "userVisible=${userMsg.take(500)}", e)
                            return@coroutineScope userMsg
                        }
                    }
                } finally {
                    // Empirical workaround for Android crashes: do not reuse context across requests.
                    closeNativeLocked()
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
                logLocalLlmDiag(context, "generate_request_enter", catalogId, "mode=stream streaming=true")
                val params = try {
                    prepareLocked(context, catalogId)
                } catch (e: Exception) {
                    val userMsg = "本機推論失敗：${e.message?.take(400) ?: e.javaClass.simpleName}"
                    Log.e(TAG, "LocalLlmDiag prepare_failed catalogId=$catalogId userVisible=${userMsg.take(500)}", e)
                    return@withLock userMsg
                } ?: run {
                    val msg = LocalModelCatalog.byId(catalogId).let { e ->
                        if (e == null) {
                            "錯誤：未知的本機模型 id「$catalogId」。"
                        } else {
                            "錯誤：找不到 GGUF 檔案。請到設定下載「${e.label}」。"
                        }
                    }
                    logUserVisibleOutcome(Log.WARN, "prepare_unsatisfied", catalogId, true, msg)
                    return@withLock msg
                }

                val handle = nativeHandle
                val cfg = loadedLlamaConfig
                if (handle == 0L || cfg == null) {
                    val msg = "錯誤：模型載入失敗。"
                    Log.e(
                        TAG,
                        "LocalLlmDiag invariant_broken catalogId=$catalogId handle=$handle cfgNull=${cfg == null} " +
                            "loadedCatalogId=$loadedCatalogId userVisible=${msg.take(500)}"
                    )
                    return@withLock msg
                }
                val safePrompt = clipPromptToContext(params, prompt, catalogId)
                val genT0 = SystemClock.elapsedRealtime()
                logLocalLlmDiag(
                    context,
                    "generate_stream_before",
                    catalogId,
                    "promptChars=${safePrompt.length} rawPromptChars=${prompt.length} ctx=${params.contextSize} " +
                        "reqMaxTok=${params.maxTokens} jniMaxTok=${cfg.maxTokens} streaming=true"
                )
                val sb = StringBuilder()
                try {
                    coroutineScope {
                        var cancelIssued = false
                        try {
                            if (isCancelled()) {
                                Log.i(TAG, "LocalLlmDiag generate_cancelled catalogId=$catalogId phase=before_native streaming=true")
                                return@coroutineScope "已中斷。"
                            }
                            LocalLlamaJni.nativeGenerateStream(
                                handle,
                                safePrompt,
                                LocalLlamaJni.TokenSink { token ->
                                    if (isCancelled()) {
                                        if (!cancelIssued) {
                                            cancelIssued = true
                                            runCatching { LocalLlamaJni.nativeCancelGeneration(handle) }
                                        }
                                        return@TokenSink
                                    }
                                    sb.append(token)
                                    onAccumulatedText(sb.toString())
                                },
                                cfg,
                            )
                            sb.toString().also { out ->
                                val elapsed = SystemClock.elapsedRealtime() - genT0
                                Log.i(
                                    TAG,
                                    "LocalLlmDiag generate_stream_after catalogId=$catalogId outChars=${out.length} " +
                                        "genElapsedMs=$elapsed streaming=true"
                                )
                            }
                        } catch (e: Throwable) {
                            val userMsg = when {
                                !e.message.isNullOrBlank() ->
                                    "本機推論失敗：${e.message!!.replace("\n", " ").take(400)}"
                                else -> "本機推論失敗：${e.javaClass.simpleName}"
                            }
                            Log.e(TAG, "LocalLlmDiag generate_stream_failed catalogId=$catalogId streaming=true " +
                                "userVisible=${userMsg.take(500)}", e)
                            return@coroutineScope userMsg
                        }
                    }
                } finally {
                    closeNativeLocked()
                }
            }
        }
    }
}
