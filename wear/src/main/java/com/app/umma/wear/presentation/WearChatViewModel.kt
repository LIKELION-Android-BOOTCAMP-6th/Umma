package com.app.umma.wear.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.app.umma.watchbridge.contract.WatchChatStatus
import com.app.umma.wear.domain.model.WearChatState
import com.app.umma.wear.domain.usecase.AttachWatchChatUseCase
import com.app.umma.wear.domain.usecase.DetachWatchChatUseCase
import com.app.umma.wear.domain.usecase.DebugReleasePhoneOwnerUseCase
import com.app.umma.wear.domain.usecase.ObserveWatchChatStateUseCase
import com.app.umma.wear.domain.usecase.PressPttUseCase
import com.app.umma.wear.domain.usecase.ReleasePttUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class WearChatViewModel(
    private val attachWatchChatUseCase: AttachWatchChatUseCase,
    private val detachWatchChatUseCase: DetachWatchChatUseCase,
    private val pressPttUseCase: PressPttUseCase,
    private val releasePttUseCase: ReleasePttUseCase,
    private val observeWatchChatStateUseCase: ObserveWatchChatStateUseCase,
    private val debugReleasePhoneOwnerUseCase: DebugReleasePhoneOwnerUseCase
) : ViewModel() {
    private val exitScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _uiState = MutableStateFlow(WearChatUiState())
    val uiState: StateFlow<WearChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeWatchChatStateUseCase().collectLatest { state ->
                _uiState.value = _uiState.value.copy(
                    chatStatus = state.chatStatus,
                    currentSessionId = state.currentSessionId,
                    errorCode = state.errorCode,
                    statusText = state.toStatusText(),
                    isRecording = state.isRecording,
                    isPlaying = state.isPlaying,
                    canStartPtt = state.canStartPtt,
                    canReleasePtt = state.canReleasePtt,
                    isSending = false
                )
            }
        }
    }

    fun onAttachClick() {
        _uiState.value = _uiState.value.copy(
            statusText = "휴대폰 채팅에 연결 중..",
            isSending = true
        )
        viewModelScope.launch {
            attachWatchChatUseCase()
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        statusText = "휴대폰 응답을 기다리는 중..",
                        isSending = false
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        statusText = "연결 요청 실패\n${error.message.orEmpty()}",
                        isSending = false
                    )
                }
        }
    }

    fun onPttClick() {
        if (_uiState.value.canReleasePtt) {
            onReleasePttClick()
        } else {
            onPressPttClick()
        }
    }

    fun onReleaseOwnerClick() {
        _uiState.value = _uiState.value.copy(
            statusText = "디버그 owner 해제 요청 중..",
            isSending = true
        )
        viewModelScope.launch {
            debugReleasePhoneOwnerUseCase()
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        statusText = "디버그 owner 해제 응답을 기다리는 중..",
                        isSending = false
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        statusText = "디버그 owner 해제 실패\n${error.message.orEmpty()}",
                        isSending = false
                    )
                }
        }
    }

    fun onMicPermissionDenied() {
        _uiState.value = _uiState.value.copy(
            statusText = "워치 마이크 권한이 필요합니다.",
            isSending = false,
            isRecording = false
        )
    }

    fun onAppFinishing() {
        exitScope.launch {
            detachWatchChatUseCase()
        }
    }

    private fun onPressPttClick() {
        _uiState.value = _uiState.value.copy(
            statusText = "워치 마이크를 여는 중..",
            isSending = true
        )
        viewModelScope.launch {
            pressPttUseCase()
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        statusText = "마이크 스트림 시작 응답을 기다리는 중..",
                        isSending = false
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        statusText = "PTT 시작 실패\n${error.message.orEmpty()}",
                        isSending = false
                    )
                }
        }
    }

    private fun onReleasePttClick() {
        _uiState.value = _uiState.value.copy(
            statusText = "질문을 전송하는 중..",
            isSending = true
        )
        viewModelScope.launch {
            releasePttUseCase()
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        statusText = "AI 응답을 기다리는 중..",
                        isSending = false
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        statusText = "PTT 종료 실패\n${error.message.orEmpty()}",
                        isSending = false
                    )
                }
        }
    }

    private fun WearChatState.toStatusText(): String {
        return when {
            errorCode != null && !errorMessage.isNullOrBlank() ->
                "$errorCode\n$errorMessage"

            errorCode != null ->
                errorCode.name

            isPlaying || chatStatus == WatchChatStatus.SPEAKING ->
                "Umma가 답변하는 중입니다."

            isRecording || chatStatus == WatchChatStatus.RECORDING ->
                "듣고 있어요. 버튼을 다시 누르면 전송합니다."

            chatStatus == WatchChatStatus.THINKING ->
                "Umma가 답변을 준비 중입니다."

            chatStatus == WatchChatStatus.READY ->
                "PTT를 눌러 바로 말할 수 있습니다."

            chatStatus == WatchChatStatus.RECONNECTING ->
                "연결을 복구하는 중입니다."

            chatStatus == WatchChatStatus.ERROR ->
                errorMessage ?: "워치 대화 중 오류가 발생했습니다."

            chatStatus == WatchChatStatus.UNAVAILABLE ->
                "휴대폰 세션을 먼저 준비해 주세요."

            else -> "대기 중입니다."
        }
    }

    class Factory(
        private val attachWatchChatUseCase: AttachWatchChatUseCase,
        private val detachWatchChatUseCase: DetachWatchChatUseCase,
        private val pressPttUseCase: PressPttUseCase,
        private val releasePttUseCase: ReleasePttUseCase,
        private val observeWatchChatStateUseCase: ObserveWatchChatStateUseCase,
        private val debugReleasePhoneOwnerUseCase: DebugReleasePhoneOwnerUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(WearChatViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return WearChatViewModel(
                attachWatchChatUseCase = attachWatchChatUseCase,
                detachWatchChatUseCase = detachWatchChatUseCase,
                pressPttUseCase = pressPttUseCase,
                releasePttUseCase = releasePttUseCase,
                observeWatchChatStateUseCase = observeWatchChatStateUseCase,
                debugReleasePhoneOwnerUseCase = debugReleasePhoneOwnerUseCase
            ) as T
        }
    }
}
