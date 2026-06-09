package com.app.umma.watchbridge

import android.util.Log
import com.app.umma.watchbridge.contract.WatchBridgeCommand
import com.app.umma.watchbridge.contract.WatchBridgeEvent
import com.app.umma.watchbridge.contract.WatchChatStatus
import javax.inject.Inject

class WatchBridgeCommandHandler @Inject constructor(
    private val controller: PhoneChatSessionController,
    private val eventMapper: WatchBridgeEventMapper,
    private val watchPhoneLauncher: WatchPhoneLauncher,
    private val runtimeCoordinator: WatchChatRuntimeCoordinator,
    private val audioRelay: WatchAudioOutputRelay
) {
    suspend fun handle(command: WatchBridgeCommand, sourceNodeId: String): WatchBridgeEvent {
        val snapshot = controller.currentSnapshot()
        Log.d(
            TAG,
            "handle command=$command attached=${snapshot.watchAttached} input=${snapshot.activeInputSurface} warm=${snapshot.isWarmForWatch} status=${snapshot.status} sessionId=${snapshot.activeSessionId}"
        )
        return when (command) {
            WatchBridgeCommand.OpenWatchChat -> {
                runtimeCoordinator.registerActiveNode(sourceNodeId)
                runtimeCoordinator.noteWatchActivity()
                val event = eventMapper.toSnapshot(snapshot)
                Log.d(TAG, "OpenWatchChat response=${event.summary()}")
                event
            }
            WatchBridgeCommand.StartWatchChatSession -> {
                if (!snapshot.isWarmForWatch) {
                    val event = eventMapper.phoneWarmUpRequired()
                    Log.d(TAG, "StartWatchChatSession rejected=${event.summary()}")
                    event
                } else {
                    controller.attachWatch()
                    runtimeCoordinator.registerActiveNode(sourceNodeId)
                    runtimeCoordinator.noteWatchActivity()
                    val event = eventMapper.toSnapshot(controller.currentSnapshot())
                    Log.d(TAG, "StartWatchChatSession attached=${event.summary()}")
                    event
                }
            }

            WatchBridgeCommand.DetachWatchChat -> {
                runtimeCoordinator.clearActiveNode(sourceNodeId)
                val event = eventMapper.toSnapshot(controller.currentSnapshot())
                Log.d(TAG, "DetachWatchChat response=${event.summary()}")
                event
            }

            WatchBridgeCommand.PressPtt -> {
                if (!controller.canWatchStartUserTurn()) {
                    val event = eventMapper.busyByPhone(controller.currentSnapshot())
                    Log.d(TAG, "PressPtt rejected=${event.summary()}")
                    event
                } else {
                    runtimeCoordinator.registerActiveNode(sourceNodeId)
                    runtimeCoordinator.noteWatchActivity()
                    controller.markWatchRecording(true)
                    WatchBridgeEvent.Ack(
                        status = WatchChatStatus.RECORDING,
                        activeSessionId = controller.currentSnapshot().activeSessionId
                    ).also { Log.d(TAG, "PressPtt ack=${it.summary()}") }
                }
            }

            WatchBridgeCommand.ReleasePtt -> {
                if (!controller.endUserTurn(SessionOwner.WATCH, durationMs = null)) {
                    val event = eventMapper.busyByPhone(controller.currentSnapshot())
                    Log.d(TAG, "ReleasePtt rejected=${event.summary()}")
                    event
                } else {
                    runtimeCoordinator.noteWatchActivity()
                    WatchBridgeEvent.Ack(
                        status = WatchChatStatus.THINKING,
                        activeSessionId = controller.currentSnapshot().activeSessionId
                    ).also { Log.d(TAG, "ReleasePtt ack=${it.summary()}") }
                }
            }

            WatchBridgeCommand.CancelCurrentTurn -> {
                if (!controller.cancelPendingUserTurn(SessionOwner.WATCH)) {
                    val event = eventMapper.busyByPhone(controller.currentSnapshot())
                    Log.d(TAG, "CancelCurrentTurn rejected=${event.summary()}")
                    event
                } else {
                    runtimeCoordinator.noteWatchActivity()
                    WatchBridgeEvent.Ack(
                        status = WatchChatStatus.IDLE,
                        activeSessionId = controller.currentSnapshot().activeSessionId
                    ).also { Log.d(TAG, "CancelCurrentTurn ack=${it.summary()}") }
                }
            }

            WatchBridgeCommand.ReplayLastAiAudio -> {
                runtimeCoordinator.registerActiveNode(sourceNodeId)
                runtimeCoordinator.noteWatchActivity()
                val replayed = audioRelay.replayToWatch()
                val event = eventMapper.toSnapshot(controller.currentSnapshot()).copy(
                    replayAvailable = replayed || controller.currentSnapshot().replayAvailable
                )
                Log.d(TAG, "ReplayLastAiAudio response=${event.summary()}")
                event
            }

            WatchBridgeCommand.OpenOnPhone -> {
                watchPhoneLauncher.openChatOnPhone()
                runtimeCoordinator.onWatchReleased(openOnPhone = true)
                WatchBridgeEvent.Ack(
                    status = controller.currentSnapshot().status,
                    activeSessionId = controller.currentSnapshot().activeSessionId
                ).also { Log.d(TAG, "OpenOnPhone ack=${it.summary()}") }
            }

            WatchBridgeCommand.DebugReleasePhoneOwner -> {
                controller.detachWatch()
                runtimeCoordinator.registerActiveNode(sourceNodeId)
                runtimeCoordinator.noteWatchActivity()
                val event = eventMapper.toSnapshot(controller.currentSnapshot())
                Log.d(TAG, "DebugReleasePhoneOwner response=${event.summary()}")
                event
            }
        }
    }

    private fun WatchBridgeEvent.summary(): String {
        return when (this) {
            is WatchBridgeEvent.Ack ->
                "Ack(status=$status, sessionId=$activeSessionId)"
            is WatchBridgeEvent.Snapshot ->
                "Snapshot(status=$status, sessionId=$activeSessionId, errorCode=$errorCode, recoverable=$recoverable)"
        }
    }

    private companion object {
        const val TAG = "WatchBridgeCommand"
    }
}
