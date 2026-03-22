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

    fun deleteDownloaded(context: Context, catalogId: String) {
        val entry = byId(catalogId) ?: return
        expectedFile(context, entry).takeIf { it.exists() }?.delete()
        File(localModelsDir(context), "${entry.fileName}.part").takeIf { it.exists() }?.delete()
    }
}
