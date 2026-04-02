package com.openring.chat

import com.openring.gemini.model.InlineData
import com.openring.gemini.model.Part
import kotlinx.serialization.Serializable

/**
 * Serialized into [com.openring.data.model.ChatMessageEntity.attachmentsJson].
 * Either [textContent] (UTF-8) or [base64Data] (binary: PDF, images) is set.
 */
@Serializable
data class ChatAttachmentPayload(
    val displayName: String,
    val mimeType: String,
    val textContent: String? = null,
    val base64Data: String? = null,
)

object ChatAttachmentModelParts {

    fun toGeminiParts(payload: ChatAttachmentPayload): List<Part> {
        val text = payload.textContent
        if (text != null) {
            return LongTextIngest.partsForGeminiText(text, payload.displayName)
        }
        val b64 = payload.base64Data ?: return emptyList()
        val mime = payload.mimeType.ifBlank { "application/octet-stream" }
        return listOf(
            Part(text = "--- Attachment (binary): ${payload.displayName} ($mime) — data follows ---"),
            Part(inlineData = InlineData(mimeType = mime, data = b64))
        )
    }

    /** Single text block for local GGUF (no multimodal). */
    fun toLocalTextBlock(payload: ChatAttachmentPayload): String {
        val text = payload.textContent
        if (text != null) {
            return LongTextIngest.flattenForLocalModel(text, payload.displayName)
        }
        return "[Attachment: ${payload.displayName} — binary file (${payload.mimeType}) is not loaded into the local text model. Use a Gemini model to read this file.]"
    }
}
