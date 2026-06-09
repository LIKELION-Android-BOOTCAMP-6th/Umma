package com.app.umma.wear.presentation

import com.app.umma.watchbridge.contract.WatchBridgeErrorCode
import com.app.umma.watchbridge.contract.WatchChatStatus
import com.app.umma.wear.domain.model.WearPhoneTarget

enum class WearChatScreenMode {
    PRE_CONNECT,
    CONNECTED,
    READY,
    LISTENING,
    THINKING,
    SPEAKING,
    RECONNECTING,
    ERROR
}

enum class WearConnectionUiMode {
    INITIAL_GUIDE,
    DISCOVERY_RESULT,
    ATTACHED
}

data class WearChatUiState(
    val screenMode: WearChatScreenMode = WearChatScreenMode.PRE_CONNECT,
    val connectionUiMode: WearConnectionUiMode = WearConnectionUiMode.INITIAL_GUIDE,
    val chatStatus: WatchChatStatus = WatchChatStatus.UNAVAILABLE,
    val currentSessionId: String? = null,
    val errorCode: WatchBridgeErrorCode? = null,
    val titleText: String = "휴대폰 연결",
    val bodyText: String = "휴대폰에서 대화창을 먼저 여신 후\n연결 버튼을 눌러주세요.",
    val statusChipText: String? = null,
    val isAttached: Boolean = false,
    val isSending: Boolean = false,
    val isAttachPending: Boolean = false,
    val isRecording: Boolean = false,
    val isPlaying: Boolean = false,
    val canStartPtt: Boolean = false,
    val canReleasePtt: Boolean = false,
    val hasAttemptedAttach: Boolean = false,
    val showTargetControls: Boolean = false,
    val transientBannerMessage: String? = null,
    val availableTargets: List<WearPhoneTarget> = emptyList(),
    val selectedTargetId: String? = null,
    val selectedTargetDisplayName: String? = null,
    val isTargetChooserVisible: Boolean = false,
    val attachErrorMessage: String? = null
)
