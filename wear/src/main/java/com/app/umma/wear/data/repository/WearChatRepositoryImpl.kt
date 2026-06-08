package com.app.umma.wear.data.repository

import android.util.Log
import com.app.umma.watchbridge.contract.WatchBridgeCommand
import com.app.umma.watchbridge.contract.WatchBridgeErrorCode
import com.app.umma.watchbridge.contract.WatchBridgeEvent
import com.app.umma.watchbridge.contract.WatchChatStatus
import com.app.umma.wear.data.datasource.WearAudioOutputEvent
import com.app.umma.wear.data.datasource.WearBridgeRemoteDataSource
import com.app.umma.wear.domain.audio.WearAudioInput
import com.app.umma.wear.domain.audio.WearAudioOutput
import com.app.umma.wear.domain.model.WearChatState
import com.app.umma.wear.domain.repository.WearChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WearChatRepositoryImpl(
    private val dataSource: WearBridgeRemoteDataSource,
    private val audioInput: WearAudioInput,
    private val audioOutput: WearAudioOutput
) : WearChatRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _chatState = MutableStateFlow(WearChatState())
    override val chatState: StateFlow<WearChatState> = _chatState.asStateFlow()

    private var bridgeStatus: WatchChatStatus = WatchChatStatus.UNAVAILABLE
    private var recordingJob: Job? = null

    init {
        scope.launch {
            dataSource.observeBridgeEvents().collect(::applyBridgeEvent)
        }
        scope.launch {
            dataSource.observeAudioOutputEvents().collect(::handleAudioOutputEvent)
        }
        scope.launch {
            audioOutput.isPlaying.collectLatest { isPlaying ->
                _chatState.update { current ->
                    val effectiveStatus = if (isPlaying) WatchChatStatus.SPEAKING else bridgeStatus
                    current.copy(
                        chatStatus = effectiveStatus,
                        isPlaying = isPlaying,
                        canStartPtt = canStartPtt(
                            isAttached = current.isAttached,
                            status = effectiveStatus,
                            isRecording = current.isRecording,
                            isPlaying = isPlaying
                        ),
                        canReleasePtt = current.isRecording
                    )
                }
            }
        }
    }

    override suspend fun attachToPhoneSession(): Result<Unit> {
        return dataSource.sendCommand(WatchBridgeCommand.StartWatchChatSession).map { Unit }
    }

    override suspend fun detachFromPhoneSession(): Result<Unit> {
        return runCatching {
            audioInput.stopRecording()
            recordingJob?.cancel()
            recordingJob = null
            dataSource.closeInputAudioChannel()
            audioOutput.stopPlaying()
            dataSource.sendCommand(WatchBridgeCommand.DetachWatchChat).getOrThrow()
        }
    }

    override suspend fun startUserTurn(): Result<Unit> {
        return runCatching {
            val nodeId = dataSource.sendCommand(WatchBridgeCommand.PressPtt).getOrThrow()
            dataSource.openInputAudioChannel(nodeId).getOrThrow()
            recordingJob?.cancel()
            recordingJob = scope.launch {
                runCatching {
                    audioInput.startRecording().collect { chunk ->
                        val writeResult = dataSource.writeInputAudioChunk(chunk)
                        if (writeResult.isFailure) {
                            throw WatchInputChannelWriteException(
                                cause = writeResult.exceptionOrNull()
                            )
                        }
                    }
                }.onFailure { error ->
                    if (error is WatchInputChannelWriteException) {
                        Log.w(
                            TAG,
                            "Stopping watch recording because input channel write failed",
                            error.cause
                        )
                    } else {
                        Log.e(TAG, "Watch recording job failed unexpectedly", error)
                    }
                    audioInput.stopRecording()
                    dataSource.closeInputAudioChannel()
                    clearLocalRecordingState()
                }
            }
        }
    }

    override suspend fun finishUserTurn(): Result<Unit> {
        return runCatching {
            audioInput.stopRecording()
            recordingJob?.cancel()
            recordingJob = null
            dataSource.closeInputAudioChannel()
            val releaseResult = dataSource.sendCommand(WatchBridgeCommand.ReleasePtt)
            if (releaseResult.isFailure) {
                Log.w(TAG, "ReleasePtt failed; attempting recovery", releaseResult.exceptionOrNull())
                recoverReleasePttFailure().getOrThrow()
            }
        }
    }

    override suspend fun releasePhoneOwnerForDebug(): Result<Unit> {
        return dataSource.sendCommand(WatchBridgeCommand.DebugReleasePhoneOwner).map { Unit }
    }

    private fun applyBridgeEvent(event: WatchBridgeEvent) {
        when (event) {
            is WatchBridgeEvent.Ack -> {
                bridgeStatus = event.status
                updateState(
                    isAttached = _chatState.value.isAttached,
                    status = event.status,
                    currentSessionId = event.activeSessionId,
                    errorCode = null,
                    errorMessage = null
                )
            }

            is WatchBridgeEvent.Snapshot -> {
                bridgeStatus = event.status
                updateState(
                    isAttached = event.watchAttached,
                    status = event.status,
                    currentSessionId = event.activeSessionId,
                    errorCode = event.errorCode,
                    errorMessage = event.errorMessage
                )
            }
        }
    }

    private fun handleAudioOutputEvent(event: WearAudioOutputEvent) {
        when (event) {
            WearAudioOutputEvent.Started -> audioOutput.startPlaying()
            is WearAudioOutputEvent.Chunk -> audioOutput.playAudioChunk(event.bytes)
            WearAudioOutputEvent.Completed -> audioOutput.finishPlaybackResponse()
        }
    }

    private fun updateState(
        isAttached: Boolean,
        status: WatchChatStatus,
        currentSessionId: String?,
        errorCode: WatchBridgeErrorCode?,
        errorMessage: String?
    ) {
        _chatState.update { current ->
            val isRecording = status == WatchChatStatus.RECORDING
            val effectiveStatus = when {
                current.isPlaying -> WatchChatStatus.SPEAKING
                !isAttached && !isRecording -> WatchChatStatus.UNAVAILABLE
                else -> status
            }
            current.copy(
                isAttached = isAttached,
                chatStatus = effectiveStatus,
                currentSessionId = currentSessionId,
                errorCode = errorCode,
                errorMessage = errorMessage,
                isRecording = isRecording,
                canStartPtt = canStartPtt(
                    isAttached = isAttached,
                    status = effectiveStatus,
                    isRecording = isRecording,
                    isPlaying = current.isPlaying
                ),
                canReleasePtt = isRecording
            )
        }
        Log.d(
            TAG,
            "state updated: status=$status sessionId=$currentSessionId errorCode=$errorCode recording=${_chatState.value.isRecording} playing=${_chatState.value.isPlaying}"
        )
    }

    private fun canStartPtt(
        isAttached: Boolean,
        status: WatchChatStatus,
        isRecording: Boolean,
        isPlaying: Boolean
    ): Boolean {
        return isAttached &&
            status == WatchChatStatus.READY &&
            !isRecording &&
            !isPlaying
    }

    private suspend fun recoverReleasePttFailure(): Result<Unit> {
        val cancelResult = dataSource.sendCommand(WatchBridgeCommand.CancelCurrentTurn)
        if (cancelResult.isSuccess) {
            return Result.success(Unit)
        }

        Log.w(TAG, "CancelCurrentTurn failed; detaching watch session", cancelResult.exceptionOrNull())
        return dataSource.sendCommand(WatchBridgeCommand.DetachWatchChat).map { Unit }
    }

    private fun clearLocalRecordingState() {
        _chatState.update { current ->
            val effectiveStatus = when {
                current.isPlaying -> WatchChatStatus.SPEAKING
                !current.isAttached -> WatchChatStatus.UNAVAILABLE
                else -> bridgeStatus
            }
            current.copy(
                chatStatus = effectiveStatus,
                isRecording = false,
                canStartPtt = canStartPtt(
                    isAttached = current.isAttached,
                    status = effectiveStatus,
                    isRecording = false,
                    isPlaying = current.isPlaying
                ),
                canReleasePtt = false
            )
        }
    }

    private companion object {
        const val TAG = "WearChatRepository"
    }
}

private class WatchInputChannelWriteException(
    cause: Throwable?
) : RuntimeException(cause)
