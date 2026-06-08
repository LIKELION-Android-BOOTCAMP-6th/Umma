package com.app.umma.wear.domain.model

import com.app.umma.watchbridge.contract.WatchBridgeErrorCode
import com.app.umma.watchbridge.contract.WatchChatStatus

data class WearChatState(
    val isAttached: Boolean = false,
    val chatStatus: WatchChatStatus = WatchChatStatus.UNAVAILABLE,
    val currentSessionId: String? = null,
    val errorCode: WatchBridgeErrorCode? = null,
    val errorMessage: String? = null,
    val isRecording: Boolean = false,
    val isPlaying: Boolean = false,
    val canStartPtt: Boolean = false,
    val canReleasePtt: Boolean = false
)
