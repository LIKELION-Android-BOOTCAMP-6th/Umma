package com.app.umma.watchbridge

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class ActiveWatchConnectionStore @Inject constructor() {
    private val _state = MutableStateFlow(ActiveWatchConnectionState())
    val state: StateFlow<ActiveWatchConnectionState> = _state.asStateFlow()

    fun registerNode(nodeId: String) {
        _state.update {
            it.copy(
                nodeId = nodeId,
                lastActivityAtMs = System.currentTimeMillis()
            )
        }
    }

    fun clearNode(expectedNodeId: String? = null): Boolean {
        var cleared = false
        _state.update { current ->
            if (expectedNodeId != null && current.nodeId != expectedNodeId) {
                current
            } else {
                cleared = current.nodeId != null || current.inputChannelOpen
                ActiveWatchConnectionState()
            }
        }
        return cleared
    }

    fun noteActivity() {
        _state.update { it.copy(lastActivityAtMs = System.currentTimeMillis()) }
    }

    fun setInputChannelOpen(isOpen: Boolean) {
        val now = System.currentTimeMillis()
        _state.update {
            it.copy(
                inputChannelOpen = isOpen,
                lastActivityAtMs = now,
                lastInputChannelClosedAtMs = if (isOpen) it.lastInputChannelClosedAtMs else now
            )
        }
    }
}

data class ActiveWatchConnectionState(
    val nodeId: String? = null,
    val inputChannelOpen: Boolean = false,
    val lastActivityAtMs: Long = 0L,
    val lastInputChannelClosedAtMs: Long = 0L
)
