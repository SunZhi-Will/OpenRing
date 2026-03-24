package com.openring.core

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * 透過 [MediaProjection] + [AudioPlaybackCaptureConfiguration] 錄製**裝置上其他 App 的播放混音**（手機聲音），非麥克風環境音。
 * 需 [android.Manifest.permission.RECORD_AUDIO]；目標 App 須允許擷取（未設 allowAudioPlaybackCapture=false）。
 */
object PlaybackAudioCapture {

    private const val TAG = "OpenRing"

    data class CaptureResult(
        val wavBase64: String,
        val sampleRate: Int,
        val channels: Int,
    )

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    fun recordWavBase64(projection: MediaProjection, durationMs: Long): CaptureResult? {
        if (durationMs <= 0L) return null
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .build()

        val rates = listOf(48000, 44100, 24000, 16000)
        val masks = listOf(AudioFormat.CHANNEL_IN_STEREO, AudioFormat.CHANNEL_IN_MONO)
        for (rate in rates) {
            for (mask in masks) {
                val minBuf = AudioRecord.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_16BIT)
                if (minBuf <= 0) continue
                val format = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(mask)
                    .build()
                val record = try {
                    AudioRecord.Builder()
                        .setAudioPlaybackCaptureConfig(config)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(minBuf * 2)
                        .build()
                } catch (e: Exception) {
                    Log.d(TAG, "AudioRecord.Builder failed rate=$rate mask=$mask", e)
                    continue
                }
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    continue
                }
                val pcm = try {
                    record.startRecording()
                    readPcmLittleEndian(record, durationMs, maxOf(minBuf, 256))
                } catch (e: Exception) {
                    Log.e(TAG, "playback capture read failed", e)
                    null
                } finally {
                    try {
                        record.stop()
                    } catch (_: Exception) {
                    }
                    record.release()
                }
                if (pcm == null || pcm.isEmpty()) continue
                val ch = when (mask) {
                    AudioFormat.CHANNEL_IN_MONO -> 1
                    AudioFormat.CHANNEL_IN_STEREO -> 2
                    else -> 1
                }
                if (pcm.size % (2 * ch) != 0) continue
                val wav = WavPcm16Writer.build(pcm, rate, ch)
                return CaptureResult(Base64.encodeToString(wav, Base64.NO_WRAP), rate, ch)
            }
        }
        return null
    }

    private fun readPcmLittleEndian(record: AudioRecord, durationMs: Long, minBytes: Int): ByteArray? {
        val endAt = System.currentTimeMillis() + durationMs
        val out = ByteArrayOutputBuffer()
        val samples = maxOf(minBytes / 2, 256)
        val buf = ShortArray(samples)
        while (System.currentTimeMillis() < endAt) {
            val n = record.read(buf, 0, buf.size)
            if (n <= 0) break
            for (i in 0 until n) {
                val s = buf[i].toInt()
                out.write(s and 0xff)
                out.write((s shr 8) and 0xff)
            }
        }
        return out.toByteArray()
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
