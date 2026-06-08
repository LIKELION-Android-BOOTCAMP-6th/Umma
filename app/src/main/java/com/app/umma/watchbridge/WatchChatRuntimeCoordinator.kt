package com.app.umma.watchbridge

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.app.umma.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Singleton
class WatchChatRuntimeCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val controller: PhoneChatSessionController,
    private val connectionStore: ActiveWatchConnectionStore,
    private val eventSink: WatchBridgeEventSink,
    private val eventMapper: WatchBridgeEventMapper,
    private val audioRelay: WatchAudioOutputRelay
) {
    private var isForegroundServiceRunning = false
    private var attachOnlyTimeoutJob: Job? = null
    private var inputGraceTimeoutJob: Job? = null

    init {
        applicationScope.launch {
            controller.snapshot.collectLatest { snapshot ->
                pushSnapshotIfConnected(snapshot)
                refreshForegroundService(snapshot)
                refreshStabilityTimers(snapshot)
            }
        }
    }

    fun setAppForeground(isForeground: Boolean) {
        controller.setAppForeground(isForeground)
        if (isForeground) {
            stopForegroundService()
        } else {
            refreshForegroundService(controller.currentSnapshot())
        }
    }

    fun setChatRouteVisible(isVisible: Boolean) {
        controller.setChatRouteVisible(isVisible)
        if (!isVisible) {
            clearActiveNode()
            stopForegroundService()
        } else {
            refreshForegroundService(controller.currentSnapshot())
        }
    }

    fun registerActiveNode(nodeId: String) {
        connectionStore.registerNode(nodeId)
        refreshStabilityTimers(controller.currentSnapshot())
        applicationScope.launch {
            pushSnapshotIfConnected(controller.currentSnapshot())
        }
    }

    fun clearActiveNode(expectedNodeId: String? = null) {
        val cleared = connectionStore.clearNode(expectedNodeId)
        if (!cleared) return
        audioRelay.clear()
        controller.detachWatch()
        refreshForegroundService(controller.currentSnapshot())
        refreshStabilityTimers(controller.currentSnapshot())
    }

    fun noteWatchActivity() {
        connectionStore.noteActivity()
        refreshForegroundService(controller.currentSnapshot())
        refreshStabilityTimers(controller.currentSnapshot())
    }

    fun onInputChannelOpened(nodeId: String) {
        connectionStore.registerNode(nodeId)
        connectionStore.setInputChannelOpen(true)
        noteWatchActivity()
    }

    fun onInputChannelClosed(nodeId: String? = null) {
        val currentNodeId = connectionStore.state.value.nodeId
        if (nodeId != null && currentNodeId != null && nodeId != currentNodeId) return
        connectionStore.setInputChannelOpen(false)
        refreshForegroundService(controller.currentSnapshot())
        refreshStabilityTimers(controller.currentSnapshot())
    }

    suspend fun onWatchReleased(openOnPhone: Boolean = false) {
        clearActiveNode()
        if (openOnPhone) {
            stopForegroundService()
        }
    }

    private suspend fun pushSnapshotIfConnected(snapshot: PhoneChatSessionSnapshot) {
        val nodeId = connectionStore.state.value.nodeId ?: return
        runCatching {
            eventSink.send(nodeId, eventMapper.toSnapshot(snapshot))
        }.onFailure { error ->
            Log.e(TAG, "Failed to push snapshot to active watch node", error)
        }
    }

    private fun refreshForegroundService(snapshot: PhoneChatSessionSnapshot) {
        val shouldRunForegroundService =
            snapshot.watchAttached &&
                snapshot.isWarmForWatch &&
                !snapshot.appInForeground &&
                connectionStore.state.value.nodeId != null

        if (shouldRunForegroundService) {
            ensureForegroundService()
        } else {
            stopForegroundService()
        }
    }

    private fun refreshStabilityTimers(snapshot: PhoneChatSessionSnapshot) {
        val connection = connectionStore.state.value
        refreshAttachOnlyTimeout(snapshot, connection)
        refreshInputGraceTimeout(snapshot, connection)
    }

    private fun refreshAttachOnlyTimeout(
        snapshot: PhoneChatSessionSnapshot,
        connection: ActiveWatchConnectionState
    ) {
        val shouldTrackAttachOnlyIdle =
            snapshot.watchAttached &&
                snapshot.status == com.app.umma.watchbridge.contract.WatchChatStatus.READY &&
                snapshot.activeInputSurface == com.app.umma.watchbridge.contract.WatchInputSurface.NONE &&
                !connection.inputChannelOpen &&
                connection.nodeId != null

        if (!shouldTrackAttachOnlyIdle) {
            attachOnlyTimeoutJob?.cancel()
            attachOnlyTimeoutJob = null
            return
        }
        attachOnlyTimeoutJob?.cancel()

        attachOnlyTimeoutJob = applicationScope.launch {
            delay(ATTACH_ONLY_IDLE_TIMEOUT_MS)
            val latestSnapshot = controller.currentSnapshot()
            val latestConnection = connectionStore.state.value
            val isStillIdle =
                latestSnapshot.watchAttached &&
                    latestSnapshot.status == com.app.umma.watchbridge.contract.WatchChatStatus.READY &&
                    latestSnapshot.activeInputSurface == com.app.umma.watchbridge.contract.WatchInputSurface.NONE &&
                    !latestConnection.inputChannelOpen &&
                    latestConnection.nodeId != null &&
                    System.currentTimeMillis() - latestConnection.lastActivityAtMs >= ATTACH_ONLY_IDLE_TIMEOUT_MS

            if (isStillIdle) {
                Log.d(TAG, "attach-only idle timeout reached; detaching watch session")
                clearActiveNode()
            }
        }
    }

    private fun refreshInputGraceTimeout(
        snapshot: PhoneChatSessionSnapshot,
        connection: ActiveWatchConnectionState
    ) {
        val shouldTrackInputRecovery =
            snapshot.watchAttached &&
                snapshot.activeInputSurface == com.app.umma.watchbridge.contract.WatchInputSurface.WATCH &&
                !connection.inputChannelOpen

        if (!shouldTrackInputRecovery) {
            inputGraceTimeoutJob?.cancel()
            inputGraceTimeoutJob = null
            return
        }
        inputGraceTimeoutJob?.cancel()

        inputGraceTimeoutJob = applicationScope.launch {
            delay(INPUT_CHANNEL_GRACE_TIMEOUT_MS)
            val latestSnapshot = controller.currentSnapshot()
            val latestConnection = connectionStore.state.value
            val isStillMissingRelease =
                latestSnapshot.watchAttached &&
                    latestSnapshot.activeInputSurface == com.app.umma.watchbridge.contract.WatchInputSurface.WATCH &&
                    !latestConnection.inputChannelOpen &&
                    System.currentTimeMillis() - latestConnection.lastInputChannelClosedAtMs >= INPUT_CHANNEL_GRACE_TIMEOUT_MS

            if (isStillMissingRelease) {
                Log.w(TAG, "input channel closed without ReleasePtt; recovering watch input state")
                controller.recoverWatchInputTimeout()
            }
        }
    }

    private fun ensureForegroundService() {
        if (isForegroundServiceRunning) return
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WatchChatForegroundService::class.java)
            )
        }.onSuccess {
            isForegroundServiceRunning = true
            Log.d(TAG, "foreground service started")
        }.onFailure { error ->
            Log.w(TAG, "Failed to start foreground service for watch chat runtime", error)
        }
    }

    private fun stopForegroundService() {
        if (!isForegroundServiceRunning) return
        context.stopService(Intent(context, WatchChatForegroundService::class.java))
        isForegroundServiceRunning = false
        Log.d(TAG, "foreground service stopped")
    }

    private companion object {
        const val TAG = "WatchChatRuntime"
        const val ATTACH_ONLY_IDLE_TIMEOUT_MS = 15_000L
        const val INPUT_CHANNEL_GRACE_TIMEOUT_MS = 4_000L
    }
}
