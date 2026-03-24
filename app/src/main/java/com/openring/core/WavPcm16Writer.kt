package com.openring.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 將 little-endian PCM16 原始位元組包成 WAV（供 Gemini 等使用）。 */
object WavPcm16Writer {

    fun build(pcmInterleavedLe: ByteArray, sampleRate: Int, numChannels: Int): ByteArray {
        require(numChannels >= 1) { "numChannels" }
        require(pcmInterleavedLe.size % (2 * numChannels) == 0) { "pcm length" }
        val dataSize = pcmInterleavedLe.size
        val bitsPerSample = 16
        val blockAlign = numChannels * bitsPerSample / 8
        val byteRate = sampleRate * blockAlign
        val out = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray(Charsets.US_ASCII))
        out.putInt(36 + dataSize)
        out.put("WAVE".toByteArray(Charsets.US_ASCII))
        out.put("fmt ".toByteArray(Charsets.US_ASCII))
        out.putInt(16)
        out.putShort(1)
        out.putShort(numChannels.toShort())
        out.putInt(sampleRate)
        out.putInt(byteRate)
        out.putShort(blockAlign.toShort())
        out.putShort(bitsPerSample.toShort())
        out.put("data".toByteArray(Charsets.US_ASCII))
        out.putInt(dataSize)
        out.put(pcmInterleavedLe)
        return out.array()
    }
}
