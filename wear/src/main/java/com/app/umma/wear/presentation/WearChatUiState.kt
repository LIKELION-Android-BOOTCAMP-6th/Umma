package com.app.umma.wear.presentation

import com.app.umma.watchbridge.contract.WatchBridgeErrorCode
import com.app.umma.watchbridge.contract.WatchChatStatus

data class WearChatUiState(
    val chatStatus: WatchChatStatus = WatchChatStatus.UNAVAILABLE,
    val currentSessionId: String? = null,
    val errorCode: WatchBridgeErrorCode? = null,
    val statusText: String = "휴대폰 채팅에 연결이 필요합니다.",
    val isSending: Boolean = false,
    val isRecording: Boolean = false,
    val isPlaying: Boolean = false,
    val canStartPtt: Boolean = false,
    val canReleasePtt: Boolean = false,
    val showDebugActions: Boolean = false
)
