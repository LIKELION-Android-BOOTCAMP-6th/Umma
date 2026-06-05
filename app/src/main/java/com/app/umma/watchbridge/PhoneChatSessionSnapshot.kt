package com.app.umma.watchbridge

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.watchbridge.contract.WatchBridgeErrorCode
import com.app.umma.watchbridge.contract.WatchChatStatus
import com.app.umma.watchbridge.contract.WatchRecentTurn

data class PhoneChatSessionSnapshot(
    val owner: SessionOwner = SessionOwner.NONE,
    val status: WatchChatStatus = WatchChatStatus.IDLE,
    val activeSessionId: String? = null,
    val currentLang: LangCode? = null,
    val isWatchRecording: Boolean = false,
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
}
