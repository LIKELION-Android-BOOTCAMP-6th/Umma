package com.app.umma.watchbridge.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface WatchBridgeEvent {
    @Serializable
    @SerialName("snapshot")
    data class Snapshot(
        val status: WatchChatStatus,
        val activeSessionId: String? = null,
        val currentLanguageCode: String? = null,
        val watchAttached: Boolean = false,
        val activeInputSurface: WatchInputSurface = WatchInputSurface.NONE,
        val activeOutputSurface: WatchOutputSurface = WatchOutputSurface.NONE,
        val replayAvailable: Boolean = false,
        val recentTurns: List<WatchRecentTurn> = emptyList(),
        val recoverable: Boolean = false,
        val errorCode: WatchBridgeErrorCode? = null,
        val errorMessage: String? = null
    ) : WatchBridgeEvent

    @Serializable
    @SerialName("ack")
    data class Ack(
        val status: WatchChatStatus,
        val activeSessionId: String? = null
    ) : WatchBridgeEvent
}
