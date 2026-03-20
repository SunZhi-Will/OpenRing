package com.openring.agent

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object RunCancellationRegistry {
    private val flags = ConcurrentHashMap<String, AtomicBoolean>()

    fun register(sessionId: String) {
        flags[sessionId] = AtomicBoolean(false)
    }

    fun cancel(sessionId: String) {
        flags[sessionId]?.set(true)
    }

    fun isCancelled(sessionId: String): Boolean {
        return flags[sessionId]?.get() == true
    }

    fun clear(sessionId: String) {
        flags.remove(sessionId)
    }
}
