package com.app.umma.watchbridge.contract

import kotlinx.serialization.Serializable

@Serializable
enum class WatchChatStatus {
    UNAVAILABLE,
    IDLE,
    READY,
    RECORDING,
    THINKING,
    SPEAKING,
    RECONNECTING,
    ERROR
}

@Serializable
enum class WatchBridgeErrorCode {
    PHONE_WARM_UP_REQUIRED,
    BUSY_BY_PHONE,
    NO_ACTIVE_SESSION,
    CONNECTION_UNAVAILABLE,
    RECOVERABLE_ERROR,
    TERMINAL_ERROR,
    UNKNOWN
}

@Serializable
enum class WatchTurnRole {
    USER,
    ASSISTANT
}

@Serializable
data class WatchRecentTurn(
    val id: String,
    val role: WatchTurnRole,
    val text: String
)
