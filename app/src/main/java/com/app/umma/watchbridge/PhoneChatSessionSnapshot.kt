package com.app.umma.watchbridge

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.watchbridge.contract.WatchBridgeErrorCode
import com.app.umma.watchbridge.contract.WatchChatStatus
import com.app.umma.watchbridge.contract.WatchInputSurface
import com.app.umma.watchbridge.contract.WatchOutputSurface
import com.app.umma.watchbridge.contract.WatchRecentTurn

data class PhoneChatSessionSnapshot(
    val owner: SessionOwner = SessionOwner.PHONE,
    val status: WatchChatStatus = WatchChatStatus.IDLE,
    val activeSessionId: String? = null,
    val currentLang: LangCode? = null,
    val watchAttached: Boolean = false,
    val activeInputSurface: WatchInputSurface = WatchInputSurface.NONE,
    val activeOutputSurface: WatchOutputSurface = WatchOutputSurface.PHONE,
    val chatRouteVisible: Boolean = false,
    val appInForeground: Boolean = true,
    val replayAvailable: Boolean = false,
    val recentTurns: List<WatchRecentTurn> = emptyList(),
    val recoverableError: Boolean = false,
    val errorCode: WatchBridgeErrorCode? = null,
    val errorMessage: String? = null
) {
    val isWarmForWatch: Boolean
        get() = activeSessionId != null && status in setOf(
            WatchChatStatus.READY,
            WatchChatStatus.RECONNECTING,
            WatchChatStatus.THINKING,
            WatchChatStatus.SPEAKING
        )

    val isWatchRecording: Boolean
        get() = activeInputSurface == WatchInputSurface.WATCH && status == WatchChatStatus.RECORDING

    val isPhoneRecording: Boolean
        get() = activeInputSurface == WatchInputSurface.PHONE

    val isWatchInputActive: Boolean
        get() = activeInputSurface == WatchInputSurface.WATCH

    val canAcceptPendingWatchInputChannel: Boolean
        get() = watchAttached && activeInputSurface != WatchInputSurface.PHONE
}
