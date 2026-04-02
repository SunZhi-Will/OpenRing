package com.openring.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Reads SAF [Uri] into [ChatAttachmentPayload] for chat attachments.
 */
object ChatAttachmentLoader {

    private const val MAX_BYTES = 8 * 1024 * 1024

    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "markdown", "json", "jsonl", "xml", "html", "htm", "css", "js", "ts", "tsx", "jsx",
        "kt", "kts", "java", "gradle", "properties", "yaml", "yml", "toml", "ini", "cfg", "log",
        "csv", "sql", "sh", "bash", "zsh", "py", "rb", "go", "rs", "c", "h", "cpp", "hpp", "cs",
        "swift", "dart", "vue", "svelte", "scss", "less", "gitignore", "env"
    )

    private val IMAGE_MIME_PREFIX = "image/"
    private val TEXT_MIME_PREFIXES = listOf("text/", "application/json", "application/xml")

    suspend fun load(context: Context, uri: Uri): Result<ChatAttachmentPayload> = withContext(Dispatchers.IO) {
        runCatching {
            val cr = context.contentResolver
            val displayName = queryDisplayName(cr, uri) ?: "attachment"
            val mime = (cr.getType(uri) ?: guessMimeFromName(displayName)).ifBlank { "application/octet-stream" }
            val bytes = readAllBytesLimited(cr.openInputStream(uri) ?: throw IllegalStateException("Cannot open stream"))
            when {
                shouldTreatAsText(mime, displayName) -> {
                    val text = bytes.toString(Charsets.UTF_8)
                    ChatAttachmentPayload(displayName = displayName, mimeType = mime, textContent = text)
                }

                mime.startsWith(IMAGE_MIME_PREFIX) || mime == "application/pdf" -> {
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    ChatAttachmentPayload(displayName = displayName, mimeType = mime, base64Data = b64)
                }

                else -> {
                    val asText = bytes.toString(Charsets.UTF_8)
                    val badUtf = asText.count { it == '\uFFFD' }
                    if (badUtf == 0 || badUtf * 50 < asText.length) {
                        ChatAttachmentPayload(displayName = displayName, mimeType = mime, textContent = asText)
                    } else {
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        ChatAttachmentPayload(displayName = displayName, mimeType = mime, base64Data = b64)
                    }
                }
            }
        }
    }

    private fun shouldTreatAsText(mime: String, fileName: String): Boolean {
        if (TEXT_MIME_PREFIXES.any { mime.startsWith(it) || mime == it }) return true
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in TEXT_EXTENSIONS
    }

    private fun guessMimeFromName(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: ""
    }

    private fun queryDisplayName(cr: android.content.ContentResolver, uri: Uri): String? {
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) return c.getString(i)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun readAllBytesLimited(stream: InputStream): ByteArray {
        stream.use { s ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val r = s.read(buf)
                if (r <= 0) break
                total += r
                if (total > MAX_BYTES) throw IllegalStateException("FILE_TOO_LARGE")
                out.write(buf, 0, r)
            }
            return out.toByteArray()
        }
    }
}
