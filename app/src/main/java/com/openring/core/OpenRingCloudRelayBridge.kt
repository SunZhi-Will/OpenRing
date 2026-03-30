package com.openring.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RelayPresenceClient(
    val role: String,
    val deviceName: String,
)

/**
 * UI-facing WebSocket relay state for [OpenRingCloudRelayService] (Compose collects this).
 */
object OpenRingCloudRelayBridge {

    enum class Phase {
        Idle,
        Connecting,
        Connected,
        Disconnected,
        Failed,
    }

    private val _phase = MutableStateFlow(Phase.Idle)
    private val _lastError = MutableStateFlow<String?>(null)
    private val _presenceClients = MutableStateFlow<List<RelayPresenceClient>>(emptyList())

    val phase: StateFlow<Phase> = _phase.asStateFlow()
    val lastError: StateFlow<String?> = _lastError.asStateFlow()
    val presenceClients: StateFlow<List<RelayPresenceClient>> = _presenceClients.asStateFlow()

    fun setPresenceClients(clients: List<RelayPresenceClient>) {
        _presenceClients.value = clients
    }

    private fun clearPresence() {
        _presenceClients.value = emptyList()
    }

    fun setIdle() {
        _phase.value = Phase.Idle
        _lastError.value = null
        clearPresence()
    }

    fun setConnecting() {
        _phase.value = Phase.Connecting
        _lastError.value = null
        clearPresence()
    }

    fun setConnected() {
        _phase.value = Phase.Connected
        _lastError.value = null
    }

    fun setDisconnected() {
        _phase.value = Phase.Disconnected
        clearPresence()
    }

    fun setFailed(message: String?) {
        _phase.value = Phase.Failed
        _lastError.value = message?.take(500)
        clearPresence()
    }
}
