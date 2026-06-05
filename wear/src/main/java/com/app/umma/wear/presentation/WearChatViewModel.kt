package com.app.umma.wear.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.app.umma.watchbridge.contract.WatchBridgeCommand
import com.app.umma.watchbridge.contract.WatchBridgeEvent
import com.app.umma.wear.data.WearBridgeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WearChatViewModel(
    private val repository: WearBridgeRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(WearChatUiState())
    val uiState: StateFlow<WearChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeEvents().collect { event ->
                _uiState.update {
                    it.copy(
                        statusMessage = event.toUserMessage(),
                        isSending = false
                    )
                }
            }
        }
    }

    fun onAttachClick() {
        sendCommand(
            command = WatchBridgeCommand.StartWatchChatSession,
            pendingMessage = "Sending attach request to phone..."
        )
    }

    fun onReleaseOwnerClick() {
        sendCommand(
            command = WatchBridgeCommand.DebugReleasePhoneOwner,
            pendingMessage = "Sending release-owner request to phone..."
        )
    }

    private fun sendCommand(command: WatchBridgeCommand, pendingMessage: String) {
        _uiState.update {
            it.copy(
                statusMessage = pendingMessage,
                isSending = true
            )
        }

        viewModelScope.launch {
            repository.sendCommand(command)
                .onSuccess { node ->
                    _uiState.update {
                        it.copy(
                            statusMessage = "Request sent.\nnode=${node.displayName}",
                            isSending = true
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            statusMessage = "Request failed.\n${error.message.orEmpty()}",
                            isSending = false
                        )
                    }
                }
        }
    }

    private fun WatchBridgeEvent.toUserMessage(): String {
        return when (this) {
            is WatchBridgeEvent.Ack ->
                "Ack received.\nstatus=$status\nsessionId=${activeSessionId ?: "-"}"

            is WatchBridgeEvent.Snapshot ->
                buildString {
                    appendLine("Snapshot received.")
                    appendLine("status=$status")
                    appendLine("sessionId=${activeSessionId ?: "-"}")
                    appendLine("lang=${currentLanguageCode ?: "-"}")
                    if (errorCode != null) {
                        appendLine("errorCode=$errorCode")
                    }
                    if (!errorMessage.isNullOrBlank()) {
                        appendLine("message=$errorMessage")
                    }
                }.trim()
        }
    }

    class Factory(
        private val repository: WearBridgeRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(WearChatViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return WearChatViewModel(repository) as T
        }
    }
}
