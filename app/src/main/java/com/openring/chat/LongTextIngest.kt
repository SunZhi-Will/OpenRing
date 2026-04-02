package com.openring.chat

import com.openring.gemini.model.Part

/**
 * Long-document handling: structured head/tail excerpt plus optional chunking for Gemini.
 */
object LongTextIngest {

    /** Above this, apply smart head/tail excerpt before chunking. */
    private const val SMART_EXCERPT_THRESHOLD = 96_000

    private const val HEAD_CHARS = 52_000
    private const val TAIL_CHARS = 28_000

    /** Max total characters injected into the local text model per attachment. */
    private const val LOCAL_MODEL_MAX_CHARS = 110_000

    /** Gemini text parts: stay under typical per-part JSON / memory limits on device. */
    private const val GEMINI_TEXT_PART_CHARS = 100_000

    /**
     * Smart excerpt: beginning + tail + explicit omission marker (line/char stats).
     */
    fun smartExcerpt(full: String): String {
        if (full.length <= SMART_EXCERPT_THRESHOLD) return full
        val head = full.take(HEAD_CHARS)
        val tail = full.takeLast(TAIL_CHARS)
        val omitted = full.length - head.length - tail.length
        val lines = full.count { it == '\n' } + 1
        return buildString {
            append(head)
            append("\n\n--- [Middle omitted: ")
            append(omitted.coerceAtLeast(0))
            append(" characters, ~")
            append(lines)
            append(" lines total; answer using beginning+end and ask if a missing section is needed] ---\n\n")
            append(tail)
        }
    }

    fun partsForGeminiText(full: String, label: String): List<Part> {
        val prepared = smartExcerpt(full)
        if (prepared.length <= GEMINI_TEXT_PART_CHARS) {
            return listOf(
                Part(text = "--- Attachment: $label ---\n$prepared")
            )
        }
        val chunks = prepared.chunked(GEMINI_TEXT_PART_CHARS)
        return chunks.mapIndexed { idx, chunk ->
            Part(text = "--- Attachment: $label (part ${idx + 1}/${chunks.size}) ---\n$chunk")
        }
    }

    fun flattenForLocalModel(full: String, label: String): String {
        val prepared = smartExcerpt(full).take(LOCAL_MODEL_MAX_CHARS)
        return "--- Attachment: $label ---\n$prepared"
    }
}
