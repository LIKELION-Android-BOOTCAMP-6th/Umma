package com.app.umma.wear.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.app.umma.watchbridge.contract.WatchChatStatus
import com.app.umma.wear.domain.model.WearChatState
import com.app.umma.wear.domain.usecase.AttachWatchChatUseCase
import com.app.umma.wear.domain.usecase.DebugReleasePhoneOwnerUseCase
import com.app.umma.wear.domain.usecase.DetachWatchChatUseCase
import com.app.umma.wear.domain.usecase.ObserveWatchChatStateUseCase
import com.app.umma.wear.domain.usecase.PressPttUseCase
import com.app.umma.wear.domain.usecase.RefreshPhoneTargetsUseCase
import com.app.umma.wear.domain.usecase.ReleasePttUseCase
import com.app.umma.wear.domain.usecase.SelectPhoneTargetUseCase
import com.app.umma.wear.domain.usecase.SetTargetChooserVisibleUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
    private val refreshPhoneTargetsUseCase: RefreshPhoneTargetsUseCase,
    private val selectPhoneTargetUseCase: SelectPhoneTargetUseCase,
    private val setTargetChooserVisibleUseCase: SetTargetChooserVisibleUseCase,
    @Suppress("UNUSED_PARAMETER")
    private val debugReleasePhoneOwnerUseCase: DebugReleasePhoneOwnerUseCase
) : ViewModel() {
    private val exitScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _uiState = MutableStateFlow(WearChatUiState())
    val uiState: StateFlow<WearChatUiState> = _uiState.asStateFlow()

    private var bannerJob: Job? = null
    private var userInitiatedDetach = false
    private var attachRequestPending = false

    init {
        viewModelScope.launch {
            observeWatchChatStateUseCase().collectLatest { state ->
                _uiState.value = state.toUiState(
                    isSending = false,
                    current = _uiState.value
                )
            }
        }
        viewModelScope.launch {
            refreshPhoneTargetsUseCase()
        }
    }

    fun onAttachClick() {
        bannerJob?.cancel()
        userInitiatedDetach = false
        attachRequestPending = true
        _uiState.value = _uiState.value.copy(
            isSending = true,
            isAttachPending = true,
            screenMode = WearChatScreenMode.PRE_CONNECT,
            hasAttemptedAttach = true,
            showTargetControls = true,
            transientBannerMessage = null
        )
        viewModelScope.launch {
            refreshPhoneTargetsUseCase()
            attachWatchChatUseCase()
                .onFailure {
                    attachRequestPending = false
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        isAttachPending = false
                    )
                }
        }
    }

    fun onDetachClick() {
        userInitiatedDetach = true
        attachRequestPending = false
        bannerJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isSending = true,
            isAttachPending = false,
            screenMode = WearChatScreenMode.PRE_CONNECT,
            connectionUiMode = WearConnectionUiMode.INITIAL_GUIDE,
            titleText = "휴대폰 연결",
            bodyText = "휴대폰에서 대화창을 먼저 여신 후\n연결 버튼을 눌러주세요.",
            isAttached = false,
            statusChipText = null,
            isRecording = false,
            isPlaying = false,
            canStartPtt = false,
            canReleasePtt = false,
            hasAttemptedAttach = false,
            showTargetControls = false,
            attachErrorMessage = null,
            isTargetChooserVisible = false,
            transientBannerMessage = null
        )
        viewModelScope.launch {
            detachWatchChatUseCase()
                .onFailure {
                    userInitiatedDetach = false
                    _uiState.value = _uiState.value.copy(isSending = false)
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

    fun onTargetChooserClick() {
        attachRequestPending = false
        _uiState.value = _uiState.value.copy(isAttachPending = false)
        viewModelScope.launch {
            refreshPhoneTargetsUseCase()
            setTargetChooserVisibleUseCase(true)
        }
    }

    fun onTargetChooserDismiss() {
        viewModelScope.launch {
            setTargetChooserVisibleUseCase(false)
        }
    }

    fun onTargetSelected(nodeId: String) {
        attachRequestPending = false
        _uiState.value = _uiState.value.copy(
            hasAttemptedAttach = true,
            showTargetControls = true,
            isAttachPending = false
        )
        viewModelScope.launch {
            selectPhoneTargetUseCase(nodeId)
        }
    }

    fun refreshTargets() {
        attachRequestPending = false
        _uiState.value = _uiState.value.copy(
            hasAttemptedAttach = true,
            showTargetControls = true,
            isAttachPending = false
        )
        viewModelScope.launch {
            refreshPhoneTargetsUseCase()
        }
    }

    fun onMicPermissionDenied() {
        _uiState.value = _uiState.value.copy(
            isSending = false,
            isAttachPending = false,
            screenMode = WearChatScreenMode.ERROR,
            titleText = "마이크 권한이 필요해요.",
            bodyText = "워치에서 말하려면 마이크 권한을 허용해주세요."
        )
    }

    fun onAppFinishing() {
        userInitiatedDetach = true
        attachRequestPending = false
        exitScope.launch {
            detachWatchChatUseCase()
        }
    }

    private fun onPressPttClick() {
        _uiState.value = _uiState.value.copy(
            isSending = true,
            titleText = "버튼을 눌러 말해보세요."
        )
        viewModelScope.launch {
            pressPttUseCase()
                .onFailure {
                    _uiState.value = _uiState.value.copy(isSending = false)
                }
        }
    }

    private fun onReleasePttClick() {
        _uiState.value = _uiState.value.copy(
            isSending = true,
            titleText = "듣고 있어요.\n정지 버튼을 누르면\nUmma가 답변해요."
        )
        viewModelScope.launch {
            releasePttUseCase()
                .onFailure {
                    _uiState.value = _uiState.value.copy(isSending = false)
                }
        }
    }

    private fun WearChatState.toUiState(
        isSending: Boolean,
        current: WearChatUiState
    ): WearChatUiState {
        val wasAttached = current.isAttached
        val detachedByTransition = wasAttached && !isAttached
        val shouldClearBanner = detachedByTransition && userInitiatedDetach
        val timedOutOrExternallyDetached =
            detachedByTransition && !userInitiatedDetach && attachErrorMessage == null

        if (timedOutOrExternallyDetached) {
            showTransientBanner("연결이 일정 시간 없어 종료되었어요.")
        } else if (shouldClearBanner) {
            bannerJob?.cancel()
        }

        if (isAttached || attachErrorMessage != null) {
            attachRequestPending = false
        }

        val resetToGuide = detachedByTransition
        val hasAttemptedAttach = if (resetToGuide) {
            false
        } else {
            current.hasAttemptedAttach || isAttached
        }
        val showTargetControls = if (isAttached) {
            true
        } else if (resetToGuide) {
            false
        } else {
            current.showTargetControls || attachErrorMessage != null
        }

        val screenMode = when {
            !isAttached && attachErrorMessage != null -> WearChatScreenMode.PRE_CONNECT
            !isAttached -> WearChatScreenMode.PRE_CONNECT
            isRecording || chatStatus == WatchChatStatus.RECORDING -> WearChatScreenMode.LISTENING
            isPlaying || chatStatus == WatchChatStatus.SPEAKING -> WearChatScreenMode.SPEAKING
            chatStatus == WatchChatStatus.THINKING -> WearChatScreenMode.THINKING
            chatStatus == WatchChatStatus.RECONNECTING -> WearChatScreenMode.RECONNECTING
            chatStatus == WatchChatStatus.ERROR -> WearChatScreenMode.ERROR
            else -> WearChatScreenMode.READY
        }

        val connectionUiMode = when {
            isAttached -> WearConnectionUiMode.ATTACHED
            showTargetControls -> WearConnectionUiMode.DISCOVERY_RESULT
            else -> WearConnectionUiMode.INITIAL_GUIDE
        }
        val isAttachPending = attachRequestPending && !isAttached && attachErrorMessage == null

        val titleText = when (screenMode) {
            WearChatScreenMode.PRE_CONNECT -> "휴대폰을 먼저 연결해주세요."
            WearChatScreenMode.CONNECTED -> "연결됨"
            WearChatScreenMode.READY -> "버튼을 눌러 말해보세요."
            WearChatScreenMode.LISTENING -> "듣고 있어요.\n정지 버튼을 누르면\nUmma가 답변해요."
            WearChatScreenMode.THINKING -> "Umma가 답변을\n준비하고 있어요."
            WearChatScreenMode.SPEAKING -> "Umma가 말하고 있어요."
            WearChatScreenMode.RECONNECTING -> "연결을 복구하고 있어요."
            WearChatScreenMode.ERROR -> "문제가 발생했어요."
        }

        val bodyText = when (screenMode) {
            WearChatScreenMode.PRE_CONNECT -> when {
                isAttachPending ->
                    "휴대폰과 연결하는 중이에요.\n잠시만 기다려주세요."
                attachErrorMessage != null ->
                    attachErrorMessage
                !showTargetControls ->
                    "휴대폰에서 대화창을 먼저 여신 후\n연결 버튼을 눌러주세요."
                availableTargets.size > 1 ->
                    "연결할 휴대폰을 선택한 뒤\n연결 버튼을 눌러주세요."
                availableTargets.isEmpty() ->
                    "휴대폰 연결 상태를 확인해주세요.\n연결 가능한 휴대폰을 찾지 못했어요."
                else ->
                    "휴대폰에서 대화창을 먼저 여신 후\n연결 버튼을 눌러주세요."
            }

            WearChatScreenMode.CONNECTED -> "워치에서 바로 대화를 이어갈 수 있어요."
            WearChatScreenMode.READY,
            WearChatScreenMode.LISTENING,
            WearChatScreenMode.THINKING,
            WearChatScreenMode.SPEAKING -> ""
            WearChatScreenMode.RECONNECTING -> "잠시만 기다려주세요."
            WearChatScreenMode.ERROR -> errorMessage ?: "워치 연결 상태를 복구하지 못했어요."
        }

        if (detachedByTransition) {
            userInitiatedDetach = false
            attachRequestPending = false
        }

        return current.copy(
            screenMode = screenMode,
            connectionUiMode = connectionUiMode,
            chatStatus = chatStatus,
            currentSessionId = currentSessionId,
            errorCode = errorCode,
            titleText = titleText,
            bodyText = bodyText,
            statusChipText = if (isAttached) "연결됨" else null,
            isAttached = isAttached,
            isSending = isSending,
            isAttachPending = isAttachPending,
            isRecording = isRecording,
            isPlaying = isPlaying,
            canStartPtt = canStartPtt,
            canReleasePtt = canReleasePtt,
            hasAttemptedAttach = hasAttemptedAttach,
            showTargetControls = showTargetControls,
            availableTargets = availableTargets,
            selectedTargetId = selectedTargetId,
            selectedTargetDisplayName = selectedTargetDisplayName,
            isTargetChooserVisible = isTargetChooserVisible,
            attachErrorMessage = attachErrorMessage,
            transientBannerMessage = if (shouldClearBanner) {
                null
            } else {
                current.transientBannerMessage
            }
        )
    }

    private fun showTransientBanner(message: String) {
        bannerJob?.cancel()
        _uiState.value = _uiState.value.copy(transientBannerMessage = message)
        bannerJob = viewModelScope.launch {
            delay(3_000L)
            _uiState.value = _uiState.value.copy(transientBannerMessage = null)
        }
    }

    class Factory(
        private val attachWatchChatUseCase: AttachWatchChatUseCase,
        private val detachWatchChatUseCase: DetachWatchChatUseCase,
        private val pressPttUseCase: PressPttUseCase,
        private val releasePttUseCase: ReleasePttUseCase,
        private val observeWatchChatStateUseCase: ObserveWatchChatStateUseCase,
        private val refreshPhoneTargetsUseCase: RefreshPhoneTargetsUseCase,
        private val selectPhoneTargetUseCase: SelectPhoneTargetUseCase,
        private val setTargetChooserVisibleUseCase: SetTargetChooserVisibleUseCase,
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
                refreshPhoneTargetsUseCase = refreshPhoneTargetsUseCase,
                selectPhoneTargetUseCase = selectPhoneTargetUseCase,
                setTargetChooserVisibleUseCase = setTargetChooserVisibleUseCase,
                debugReleasePhoneOwnerUseCase = debugReleasePhoneOwnerUseCase
            ) as T
        }
    }
}
