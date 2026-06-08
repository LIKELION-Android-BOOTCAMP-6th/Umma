package com.app.umma.watchbridge.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface WatchBridgeCommand {
    @Serializable
    @SerialName("open_watch_chat")
    data object OpenWatchChat : WatchBridgeCommand

    @Serializable
    @SerialName("start_watch_chat_session")
    data object StartWatchChatSession : WatchBridgeCommand

    @Serializable
    @SerialName("detach_watch_chat")
    data object DetachWatchChat : WatchBridgeCommand

    @Serializable
    @SerialName("press_ptt")
    data object PressPtt : WatchBridgeCommand

    @Serializable
    @SerialName("release_ptt")
    data object ReleasePtt : WatchBridgeCommand

    @Serializable
    @SerialName("cancel_current_turn")
    data object CancelCurrentTurn : WatchBridgeCommand

    @Serializable
    @SerialName("replay_last_ai_audio")
    data object ReplayLastAiAudio : WatchBridgeCommand

    @Serializable
    @SerialName("open_on_phone")
    data object OpenOnPhone : WatchBridgeCommand

    @Serializable
    @SerialName("debug_release_phone_owner")
    data object DebugReleasePhoneOwner : WatchBridgeCommand
}
