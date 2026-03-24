package com.openring.core

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log

/**
 * 短時間以**麥克風**錄製環境音並輸出 **WAV** Base64（喇叭漏音＋環境聲）。
 * 需已授予 [android.Manifest.permission.RECORD_AUDIO]。
 * 若要錄**手機內部播放**，請使用 [PlaybackAudioCapture] + [MediaProjectionSession]。
 */
object AmbientAudioCapture {

    private const val TAG = "OpenRing"
    private const val SAMPLE_RATE = 16_000
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    /**
     * @param durationMs 實際錄製長度（上限由呼叫端限制）
     * @return Base64（NO_WRAP）或失敗時 null
     */
    @SuppressLint("MissingPermission")
    fun recordWavBase64(durationMs: Long): String? {
        if (durationMs <= 0L) return null
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuf <= 0) {
            Log.w(TAG, "recordWavBase64: invalid minBufferSize=$minBuf")
            return null
        }
        val bufferSize = minBuf * 2
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "recordWavBase64: AudioRecord not initialized")
            record.release()
            return null
        }
        val pcm = try {
            record.startRecording()
            val endAt = System.currentTimeMillis() + durationMs
            val out = ByteArrayOutputBuffer()
            val buf = ShortArray(maxOf(minBuf / 2, 256))
            while (System.currentTimeMillis() < endAt) {
                val n = record.read(buf, 0, buf.size)
                if (n <= 0) break
                for (i in 0 until n) {
                    val s = buf[i].toInt()
                    out.write(s and 0xff)
                    out.write((s shr 8) and 0xff)
                }
            }
            out.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "recordWavBase64 failed", e)
            null
        } finally {
            try {
                record.stop()
            } catch (_: Exception) {
            }
            record.release()
        }
        if (pcm == null || pcm.isEmpty()) return null
        val wav = WavPcm16Writer.build(pcm, SAMPLE_RATE, 1)
        return Base64.encodeToString(wav, Base64.NO_WRAP)
    }

    private class ByteArrayOutputBuffer {
        private var buf = ByteArray(256)
        private var size = 0

        fun write(b: Int) {
            if (size >= buf.size) {
                buf = buf.copyOf((buf.size * 2).coerceAtLeast(size + 1))
            }
            buf[size++] = b.toByte()
        }

        fun toByteArray(): ByteArray = buf.copyOf(size)
    }
}
