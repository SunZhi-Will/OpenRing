package com.openring.localmodel

/**
 * Must match CodeShipping `llama_context_wrapper.cpp` generation pre-check:
 *
 * ```
 * maxPromptTokens = llama_n_ctx - cfg.maxTokens - 16
 * if (promptTokens > maxPromptTokens && maxPromptTokens < 64) {
 *   error: "Context too small for generation. Need at least 64 tokens for prompt."
 * }
 * ```
 *
 * **Why Kotlin-side math still failed before:** native counts **tokenizer tokens**, not UTF-16 length.
 * ChatML / Llama-3 / Phi markers (`<|im_start|>`, `<|eot_id|>`, …) can consume far more tokens than
 * a naive `chars * 0.5` estimate. Also llama.cpp may **round `n_ctx` down** (e.g. divisibility),
 * so we subtract [PLANNING_N_CTX_SLACK] when budgeting caps and clipping — see upstream log
 * `n_ctx is not divisible by n_seq_max - rounding down`.
 *
 * [resolvedContextSizeForCatalog] is still what we pass to `LlamaConfig.contextSize` (max KV we request).
 * [planningNCtxForBudget] is a **pessimistic** budget for `maxTokens` and prompt **character** limits only.
 *
 * **Device evidence (Pixel 7, TinyLlama):** log showed `Tokenized prompt: 47` while
 * `Available prompt space (12)` → effective `llama_n_ctx ≈ 60` with `jniMaxTok=32`, even though load logged
 * requested `n_ctx=512`. Under memory pressure llama.cpp may use a much smaller **effective** context; the
 * wrapper still uses `llama_n_ctx` for the pre-check. Prefer **smaller requested ctx** for tiny models so
 * allocation is more likely to match reality.
 */
object LocalLlamaNativeConstraints {
    /** JNI reserves this many slots alongside the generation budget (see upstream cpp). */
    const val NATIVE_GEN_RESERVE: Int = 16

    /** Below this many prompt slots, native cannot run `smartTruncate` and will error if prompt is too long. */
    const val MIN_TRUNCATE_PROMPT_SLOTS: Int = 64

    /**
     * Conservative slack so planning math survives `n_ctx` rounding / KV quirks without seeing real
     * `llama_n_ctx` from Java.
     */
    const val PLANNING_N_CTX_SLACK_TINY: Int = 48
    const val PLANNING_N_CTX_SLACK_DEFAULT: Int = 384

    /**
     * Reserve tokens for BOS + chat template control strings; real prompt body must stay under
     * `maxPromptSlots - this` in the **worst case ~1 token per character** (CJK / odd Unicode).
     */
    const val TEMPLATE_TOKEN_RESERVE: Int = 192

    /** Hard ceiling on prompt characters for catalog models (SIGSEGV guard + JNI path). */
    const val CATALOG_PROMPT_CHAR_HARD_CAP: Int = 480

    /**
     * Maximum `cfg.maxTokens` such that `n_ctx - maxTokens - 16 >= MIN_TRUNCATE_PROMPT_SLOTS`
     * when `n_ctx` equals [nCtx].
     */
    fun maxGenerationTokensForTruncatePath(nCtx: Int): Int =
        (nCtx - NATIVE_GEN_RESERVE - MIN_TRUNCATE_PROMPT_SLOTS).coerceAtLeast(1)

    /** Matches cpp: `n_ctx - maxTokens - 16`. */
    fun maxPromptTokenSlots(nCtx: Int, maxGenerationTokens: Int): Int =
        nCtx - maxGenerationTokens - NATIVE_GEN_RESERVE

    /**
     * Load context for catalog models: bounded to reduce OOM while staying ≥ library minimum (128).
     */
    fun resolvedContextSizeForCatalog(params: LocalInferenceParams, catalogId: String): Int {
        val id = catalogId.lowercase()
        val cap = when {
            id.contains("tinyllama") -> 256
            else -> 1024
        }
        return params.contextSize.coerceIn(128, cap)
    }

    fun planningNCtxForBudget(resolvedContextSize: Int, catalogId: String): Int {
        val slack =
            if (catalogId.lowercase().contains("tinyllama")) {
                PLANNING_N_CTX_SLACK_TINY
            } else {
                PLANNING_N_CTX_SLACK_DEFAULT
            }
        return (resolvedContextSize - slack).coerceAtLeast(128)
    }

    /**
     * Safe UTF-16 length cap before JNI tokenize. When [slots] is below [MIN_TRUNCATE_PROMPT_SLOTS],
     * native cannot smart-truncate; stay within ~1 token/char.
     */
    fun maxSafePromptChars(slots: Int): Int {
        if (slots < MIN_TRUNCATE_PROMPT_SLOTS) {
            return (slots - 16).coerceAtLeast(1)
        }
        val usable = (slots - TEMPLATE_TOKEN_RESERVE).coerceAtLeast(1)
        return minOf(usable, CATALOG_PROMPT_CHAR_HARD_CAP)
    }
}
