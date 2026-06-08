package com.app.umma.watchbridge

import com.app.umma.watchbridge.contract.WatchBridgeErrorCode
import com.app.umma.watchbridge.contract.WatchBridgeEvent
import com.app.umma.watchbridge.contract.WatchChatStatus
import com.app.umma.watchbridge.contract.WatchInputSurface
import javax.inject.Inject

class WatchBridgeEventMapper @Inject constructor() {
    fun toSnapshot(snapshot: PhoneChatSessionSnapshot): WatchBridgeEvent.Snapshot {
        return WatchBridgeEvent.Snapshot(
            status = when {
                !snapshot.watchAttached && snapshot.activeSessionId == null ->
                    WatchChatStatus.UNAVAILABLE
                else -> snapshot.status
            },
            activeSessionId = snapshot.activeSessionId,
            currentLanguageCode = snapshot.currentLang?.code,
            watchAttached = snapshot.watchAttached,
            activeInputSurface = snapshot.activeInputSurface,
            activeOutputSurface = snapshot.activeOutputSurface,
            replayAvailable = snapshot.replayAvailable,
            recentTurns = snapshot.recentTurns,
            recoverable = snapshot.recoverableError,
            errorCode = snapshot.errorCode,
            errorMessage = snapshot.errorMessage
        )
    }

    fun phoneWarmUpRequired(): WatchBridgeEvent.Snapshot {
        return WatchBridgeEvent.Snapshot(
            status = WatchChatStatus.UNAVAILABLE,
            errorCode = WatchBridgeErrorCode.PHONE_WARM_UP_REQUIRED,
            errorMessage = "Phone chat must already be warm before watch remote mic can attach."
        )
    }

    fun busyByPhone(snapshot: PhoneChatSessionSnapshot): WatchBridgeEvent.Snapshot {
        return WatchBridgeEvent.Snapshot(
            status = snapshot.status,
            activeSessionId = snapshot.activeSessionId,
            currentLanguageCode = snapshot.currentLang?.code,
            watchAttached = snapshot.watchAttached,
            activeInputSurface = snapshot.activeInputSurface,
            activeOutputSurface = snapshot.activeOutputSurface,
            replayAvailable = snapshot.replayAvailable,
            recentTurns = snapshot.recentTurns,
            recoverable = false,
            errorCode = WatchBridgeErrorCode.BUSY_BY_PHONE,
            errorMessage = if (snapshot.activeInputSurface == WatchInputSurface.PHONE) {
                "Phone chat is currently using the microphone."
            } else {
                "Phone chat cannot switch watch input right now."
            }
        )
    }
}
