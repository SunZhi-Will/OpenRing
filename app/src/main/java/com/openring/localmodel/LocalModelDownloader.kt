package com.openring.localmodel

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object LocalModelDownloader {
    private const val TAG = "OpenRing"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(10, TimeUnit.MINUTES)
        .build()

    /**
     * Downloads [entry] to [LocalModelCatalog.expectedFile]. Uses a `.part` temp file then rename.
     * [onProgress] receives 0f..1f when Content-Length is known, else null for indeterminate chunks.
     */
    fun download(
        context: android.content.Context,
        entry: LocalModelCatalogEntry,
        onProgress: (Float?) -> Unit,
    ): Result<File> {
        val target = LocalModelCatalog.expectedFile(context, entry)
        val partFile = File(target.parentFile, "${entry.fileName}.part")
        return runCatching {
            val request = Request.Builder().url(entry.downloadUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}")
                }
                val body = response.body ?: error("Empty body")
                val contentLength = body.contentLength().takeIf { it > 0 } ?: -1L
                partFile.parentFile?.mkdirs()
                if (partFile.exists()) partFile.delete()
                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var totalRead = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n <= 0) break
                            out.write(buffer, 0, n)
                            totalRead += n
                            if (contentLength > 0) {
                                onProgress((totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f))
                            } else {
                                onProgress(null)
                            }
                        }
                    }
                }
                if (target.exists()) target.delete()
                if (!partFile.renameTo(target)) {
                    error("Rename failed")
                }
                onProgress(1f)
                target
            }
        }.onFailure { e ->
            Log.e(TAG, "Local model download failed: ${entry.id}", e)
            partFile.takeIf { it.exists() }?.delete()
        }
    }
}
