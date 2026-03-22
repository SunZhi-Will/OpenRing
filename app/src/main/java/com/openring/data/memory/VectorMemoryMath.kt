package com.openring.data.memory

import kotlin.math.sqrt

internal object VectorMemoryMath {

    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || a.size != b.size) return 0f
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if (na <= 0f || nb <= 0f) return 0f
        return dot / (sqrt(na) * sqrt(nb))
    }
}
