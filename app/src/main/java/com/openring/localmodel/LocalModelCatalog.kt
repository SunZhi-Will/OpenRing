package com.openring.localmodel

import android.content.Context
import java.io.File

/**
 * Curated on-device model files (e.g. GGUF). URLs point to public artifacts; replace or extend as needed.
 * [id] is stored in [com.openring.settings.ModelOption.model] when provider is `local`.
 */
data class LocalModelCatalogEntry(
    val id: String,
    val label: String,
    /** Final filename under app files dir (local_models/). */
    val fileName: String,
    val downloadUrl: String,
    /** Approximate size for UI hints (bytes). */
    val sizeBytesApprox: Long,
)

object LocalModelCatalog {
    val ENTRIES: List<LocalModelCatalogEntry> = listOf(
        LocalModelCatalogEntry(
            id = "tinyllama-1.1b-q4km",
            label = "TinyLlama 1.1B Chat (Q4_K_M)",
            fileName = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            sizeBytesApprox = 669_000_000L,
        ),
        LocalModelCatalogEntry(
            id = "qwen2.5-1.5b-instruct-q4km",
            label = "Qwen2.5 1.5B Instruct (Q4_K_M)",
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            sizeBytesApprox = 1_050_000_000L,
        ),
        LocalModelCatalogEntry(
            id = "phi-3.5-mini-instruct-q4km",
            label = "Phi-3.5 Mini Instruct (Q4_K_M)",
            fileName = "Phi-3.5-mini-instruct-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
            sizeBytesApprox = 2_200_000_000L,
        ),
        LocalModelCatalogEntry(
            id = "gemma-2-2b-it-q4km",
            label = "Gemma 2 2B IT (Q4_K_M)",
            fileName = "gemma-2-2b-it-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
            sizeBytesApprox = 1_650_000_000L,
        ),
        /**
         * Google Gemma 4 E2B Instruct（[模型資訊卡](https://ai.google.dev/gemma/docs/core/model_card_4?hl=zh-tw)）。
         * 僅文字；多模態／音訊需另接 mmproj，OpenRing 目前未支援。
         * 需較新 llama.cpp／JNI 才能載入；若載入失敗請升級 `llama-kotlin-android` 或等候上游支援。
         */
        LocalModelCatalogEntry(
            id = "gemma-4-e2b-it-q4km",
            label = "Gemma 4 E2B IT (Q4_K_M) · experimental",
            fileName = "google_gemma-4-E2B-it-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/bartowski/google_gemma-4-E2B-it-GGUF/resolve/main/google_gemma-4-E2B-it-Q4_K_M.gguf",
            sizeBytesApprox = 3_460_000_000L,
        ),
        /** Meta Llama 3.2 1B Instruct（單檔 GGUF；常見「地端輕量」推薦）。來源：bartowski 量化。 */
        LocalModelCatalogEntry(
            id = "llama-3.2-1b-instruct-q4km",
            label = "Llama 3.2 1B Instruct (Q4_K_M)",
            fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            sizeBytesApprox = 870_000_000L,
        ),
    )

    fun byId(id: String): LocalModelCatalogEntry? = ENTRIES.firstOrNull { it.id == id }

    fun localModelsDir(context: Context): File =
        File(context.filesDir, "local_models").also { it.mkdirs() }

    fun expectedFile(context: Context, entry: LocalModelCatalogEntry): File =
        File(localModelsDir(context), entry.fileName)

    fun isDownloaded(context: Context, catalogId: String): Boolean {
        val entry = byId(catalogId) ?: return false
        return expectedFile(context, entry).exists() && expectedFile(context, entry).length() > 0L
    }

    /**
     * 刪除已下載的 GGUF 與未完成的下載暫存（.part）。
     * [catalogId] 通常為目錄條目 id；若已無法對應目錄，可傳檔名（不含路徑）作為後備。
     */
    fun deleteDownloaded(context: Context, catalogId: String) {
        val dir = localModelsDir(context)
        val dirPath = dir.canonicalPath
        val entry = byId(catalogId)
        if (entry != null) {
            expectedFile(context, entry).takeIf { it.isFile }?.delete()
            File(dir, "${entry.fileName}.part").takeIf { it.exists() }?.delete()
            return
        }
        val name = catalogId.trim()
        if (name.isEmpty() || name.contains("..") || name.contains('/') || name.contains('\\')) return
        val f = File(dir, name).canonicalFile
        if (f.parentFile?.canonicalPath == dirPath && f.isFile) f.delete()
        File(dir, "${name}.part").canonicalFile
            .takeIf { it.parentFile?.canonicalPath == dirPath && it.isFile }
            ?.delete()
    }
}
