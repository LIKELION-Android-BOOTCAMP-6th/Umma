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
import com.app.umma.wear.domain.model.WearPhoneTarget
import com.app.umma.wear.domain.repository.WearChatRepository
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
    private var thinkingTimeoutJob: Job? = null
    private var ignoredThinkingSessionId: String? = null

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
        scope.launch {
            refreshPhoneTargets()
        }
    }

    override suspend fun refreshPhoneTargets(): Result<Unit> {
        return dataSource.listConnectedPhoneTargets().mapCatching { targets ->
            _chatState.update { current ->
                val nextSelected = resolveSelectedTarget(
                    currentTargets = targets,
                    selectedTargetId = current.selectedTargetId
                )
                current.copy(
                    availableTargets = targets,
                    selectedTargetId = nextSelected?.nodeId,
                    selectedTargetDisplayName = nextSelected?.displayName,
                    attachErrorMessage = if (targets.isEmpty()) {
                        "휴대폰 연결 상태를 확인해주세요.\n연결 가능한 휴대폰을 찾지 못했어요."
                    } else if (
                        current.selectedTargetId != null &&
                        nextSelected?.nodeId != current.selectedTargetId
                    ) {
                        "선택한 휴대폰을 다시 찾을 수 없어\n현재 감지된 기기로 전환했어요."
                    } else {
                        current.attachErrorMessage
                    }
                )
            }
            Unit
        }.onFailure { error ->
            _chatState.update { current ->
                current.copy(
                    attachErrorMessage = error.message ?: "휴대폰 목록을 불러오지 못했어요."
                )
            }
        }
    }

    override suspend fun selectPhoneTarget(nodeId: String): Result<Unit> {
        val currentTargets = _chatState.value.availableTargets
        val target = currentTargets.firstOrNull { it.nodeId == nodeId }
            ?: return Result.failure(IllegalArgumentException("Selected target is unavailable."))

        _chatState.update { current ->
            current.copy(
                selectedTargetId = target.nodeId,
                selectedTargetDisplayName = target.displayName,
                isTargetChooserVisible = false,
                attachErrorMessage = null
            )
        }
        return Result.success(Unit)
    }

    override suspend fun setTargetChooserVisible(visible: Boolean) {
        _chatState.update { current ->
            current.copy(isTargetChooserVisible = visible)
        }
    }

    override suspend fun attachToPhoneSession(): Result<Unit> {
        refreshPhoneTargets()
        val selectedTarget = resolveSelectedTarget(
            currentTargets = _chatState.value.availableTargets,
            selectedTargetId = _chatState.value.selectedTargetId
        )
        if (selectedTarget == null) {
            _chatState.update { current ->
                current.copy(
                    attachErrorMessage = "휴대폰 연결 상태를 확인해주세요.\n연결 가능한 휴대폰을 찾지 못했어요."
                )
            }
            return Result.failure(IllegalStateException("No connected phone target."))
        }

        _chatState.update { current ->
            current.copy(
                selectedTargetId = selectedTarget.nodeId,
                selectedTargetDisplayName = selectedTarget.displayName,
                attachErrorMessage = null
            )
        }

        val sendResult = dataSource.sendCommand(
            command = WatchBridgeCommand.StartWatchChatSession,
            nodeId = selectedTarget.nodeId
        )
        if (sendResult.isFailure) {
            val error = sendResult.exceptionOrNull()
            _chatState.update { current ->
                current.copy(
                    attachErrorMessage = error.toLocalAttachErrorMessage(current.selectedTargetDisplayName)
                )
            }
        }
        return sendResult.map { Unit }
    }

    override suspend fun detachFromPhoneSession(): Result<Unit> {
        return runCatching {
            val targetId = _chatState.value.selectedTargetId
                ?: error("No selected phone target.")
            audioInput.stopRecording()
            recordingJob?.cancel()
            recordingJob = null
            dataSource.closeInputAudioChannel()
            audioOutput.stopPlaying()
            dataSource.sendCommand(WatchBridgeCommand.DetachWatchChat, targetId).getOrThrow()
            _chatState.update { current ->
                current.copy(
                    attachErrorMessage = null,
                    isTargetChooserVisible = false
                )
            }
        }
    }

    override suspend fun startUserTurn(): Result<Unit> {
        return runCatching {
            val targetId = requireSelectedTargetId()
            val nodeId = dataSource.sendCommand(WatchBridgeCommand.PressPtt, targetId).getOrThrow()
            dataSource.openInputAudioChannel(nodeId).getOrThrow()
            recordingJob?.cancel()
            recordingJob = scope.launch {
                try {
                    audioInput.startRecording().collect { chunk ->
                        dataSource.writeInputAudioChunk(chunk).getOrElse { error ->
                            throw WatchInputChannelWriteException(error)
                        }
                    }
                } catch (error: Throwable) {
                    if (error is CancellationException) return@launch
                    if (error is WatchInputChannelWriteException) {
                        Log.w(TAG, "Stopping watch recording because input channel write failed", error.cause)
                        handleInputChannelWriteFailure()
                    } else {
                        Log.e(TAG, "Watch recording job failed unexpectedly", error)
                        clearLocalRecordingState()
                    }
                    audioInput.stopRecording()
                    dataSource.closeInputAudioChannel()
                }
            }
        }
    }

    override suspend fun finishUserTurn(): Result<Unit> {
        return runCatching {
            val targetId = requireSelectedTargetId()
            audioInput.stopRecording()
            recordingJob?.cancel()
            recordingJob = null
            dataSource.closeInputAudioChannel()
            val releaseResult = dataSource.sendCommand(WatchBridgeCommand.ReleasePtt, targetId)
            if (releaseResult.isFailure) {
                Log.w(TAG, "ReleasePtt failed; attempting recovery", releaseResult.exceptionOrNull())
                recoverReleasePttFailure(targetId).getOrThrow()
            }
        }
    }

    override suspend fun releasePhoneOwnerForDebug(): Result<Unit> {
        return dataSource.sendCommand(
            command = WatchBridgeCommand.DebugReleasePhoneOwner,
            nodeId = requireSelectedTargetId()
        ).map { Unit }
    }

    private fun applyBridgeEvent(event: WatchBridgeEvent) {
        when (event) {
            is WatchBridgeEvent.Ack -> {
                if (shouldIgnoreThinkingEvent(status = event.status, sessionId = event.activeSessionId)) {
                    Log.d(TAG, "ignoring stale thinking ack: sessionId=${event.activeSessionId}")
                    return
                }
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
                if (shouldIgnoreThinkingEvent(status = event.status, sessionId = event.activeSessionId)) {
                    Log.d(TAG, "ignoring stale thinking snapshot: sessionId=${event.activeSessionId}")
                    return
                }
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
        val userFacingErrorMessage = errorMessage.toUserFacingBridgeErrorMessage()
        _chatState.update { current ->
            val isRecording = status == WatchChatStatus.RECORDING
            val effectiveStatus = when {
                current.isPlaying -> WatchChatStatus.SPEAKING
                !isAttached && !isRecording -> WatchChatStatus.UNAVAILABLE
                else -> status
            }
            val isAttachError = !isAttached && errorCode != null
            current.copy(
                isAttached = isAttached,
                chatStatus = effectiveStatus,
                currentSessionId = currentSessionId,
                errorCode = errorCode,
                errorMessage = userFacingErrorMessage,
                attachErrorMessage = if (isAttachError) {
                    errorCode.toBridgeAttachErrorMessage(userFacingErrorMessage)
                } else {
                    current.attachErrorMessage?.takeIf { !isAttached }
                },
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
        syncThinkingWatchdog(
            isAttached = isAttached,
            status = status,
            currentSessionId = currentSessionId
        )
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

    private suspend fun recoverReleasePttFailure(targetId: String): Result<Unit> {
        val cancelResult = dataSource.sendCommand(WatchBridgeCommand.CancelCurrentTurn, targetId)
        if (cancelResult.isSuccess) {
            return Result.success(Unit)
        }

        Log.w(TAG, "CancelCurrentTurn failed; detaching watch session", cancelResult.exceptionOrNull())
        return dataSource.sendCommand(WatchBridgeCommand.DetachWatchChat, targetId).map { Unit }
    }

    private fun syncThinkingWatchdog(
        isAttached: Boolean,
        status: WatchChatStatus,
        currentSessionId: String?
    ) {
        if (!isAttached || currentSessionId == null || status != WatchChatStatus.THINKING) {
            if (status != WatchChatStatus.THINKING || !isAttached) {
                ignoredThinkingSessionId = null
            }
            thinkingTimeoutJob?.cancel()
            thinkingTimeoutJob = null
            return
        }

        if (thinkingTimeoutJob?.isActive == true) return
        thinkingTimeoutJob = scope.launch {
            delay(THINKING_TIMEOUT_MS)
            recoverThinkingTimeout(sessionId = currentSessionId)
        }
    }

    private fun shouldIgnoreThinkingEvent(
        status: WatchChatStatus,
        sessionId: String?
    ): Boolean {
        val ignoredSessionId = ignoredThinkingSessionId ?: return false
        if (sessionId != ignoredSessionId) {
            ignoredThinkingSessionId = null
            return false
        }
        if (status == WatchChatStatus.THINKING) {
            return true
        }
        ignoredThinkingSessionId = null
        return false
    }

    private suspend fun recoverThinkingTimeout(sessionId: String) {
        val current = _chatState.value
        if (!current.isAttached || current.currentSessionId != sessionId) return
        if (current.chatStatus != WatchChatStatus.THINKING) return

        val targetId = current.selectedTargetId ?: return
        ignoredThinkingSessionId = sessionId
        Log.w(TAG, "Thinking timed out; attempting watch-side recovery for sessionId=$sessionId")
        val recoveryResult = recoverReleasePttFailure(targetId)
        if (recoveryResult.isFailure) {
            Log.w(TAG, "Thinking timeout recovery command failed", recoveryResult.exceptionOrNull())
        }
        _chatState.update { state ->
            val canRetry = state.isAttached && !state.isPlaying
            state.copy(
                chatStatus = WatchChatStatus.ERROR,
                errorCode = WatchBridgeErrorCode.RECOVERABLE_ERROR,
                errorMessage = "답변을 받지 못했어요.\n다시 말씀해주세요.",
                isRecording = false,
                canStartPtt = canRetry,
                canReleasePtt = false
            )
        }
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

    private fun handleInputChannelWriteFailure() {
        _chatState.update { current ->
            val canRetry = current.isAttached && !current.isPlaying
            current.copy(
                chatStatus = WatchChatStatus.ERROR,
                errorCode = WatchBridgeErrorCode.RECOVERABLE_ERROR,
                errorMessage = "전송 중 연결이 끊어졌어요.\n다시 말씀해주세요.",
                isRecording = false,
                canStartPtt = canRetry,
                canReleasePtt = false
            )
        }
    }

    private fun requireSelectedTargetId(): String {
        return _chatState.value.selectedTargetId ?: error("No selected phone target.")
    }

    private fun resolveSelectedTarget(
        currentTargets: List<WearPhoneTarget>,
        selectedTargetId: String?
    ): WearPhoneTarget? {
        return currentTargets.firstOrNull { it.nodeId == selectedTargetId }
            ?: currentTargets.firstOrNull { it.isNearby }
            ?: currentTargets.firstOrNull()
    }

    private fun Throwable?.toLocalAttachErrorMessage(selectedTargetDisplayName: String?): String {
        val targetLabel = selectedTargetDisplayName ?: "휴대폰"
        val message = this?.message.orEmpty()
        return when {
            message.contains("not connected", ignoreCase = true) ->
                "선택한 $targetLabel 연결을 다시 찾을 수 없어요.\n기기 변경 후 다시 시도해주세요."

            message.contains("No connected phone target", ignoreCase = true) ->
                "휴대폰 연결 상태를 확인해주세요.\n연결 가능한 휴대폰을 찾지 못했어요."

            else ->
                "$targetLabel 로 연결 요청을 보내지 못했어요.\n잠시 후 다시 시도해주세요."
        }
    }

    private fun String?.toUserFacingBridgeErrorMessage(): String? {
        val rawMessage = this?.trim().orEmpty()
        if (rawMessage.isBlank()) return null

        val normalizedMessage = rawMessage.lowercase()
        return when {
            normalizedMessage.contains("unable to resolve host") ||
                normalizedMessage.contains("no address associated with hostname") ||
                normalizedMessage.contains("failed to connect") ||
                normalizedMessage.contains("connection refused") ||
                normalizedMessage.contains("network is unreachable") ||
                normalizedMessage.contains("network") ||
                normalizedMessage.contains("offline") ||
                normalizedMessage.contains("timeout") ||
                normalizedMessage.contains("timed out") ||
                normalizedMessage.contains("socket") ||
                normalizedMessage.contains("ioexception") ->
                "네트워크 상태를 확인한 뒤\n다시 시도해주세요."

            else -> rawMessage
        }
    }

    private fun WatchBridgeErrorCode?.toBridgeAttachErrorMessage(errorMessage: String?): String {
        return when (this) {
            WatchBridgeErrorCode.PHONE_WARM_UP_REQUIRED ->
                "휴대폰에서 AI 대화창을 먼저 열어주세요.\n대화 준비가 끝나면 다시 연결할 수 있어요."

            WatchBridgeErrorCode.NO_ACTIVE_SESSION ->
                "휴대폰에서 진행 중인 대화가 없어요.\n먼저 휴대폰에서 대화를 시작해주세요."

            WatchBridgeErrorCode.CONNECTION_UNAVAILABLE ->
                "휴대폰 연결 상태를 확인해주세요.\n네트워크 경로를 찾지 못했어요."

            WatchBridgeErrorCode.BUSY_BY_PHONE ->
                "휴대폰에서 이미 입력 중이에요.\n휴대폰 입력이 끝난 뒤 다시 시도해주세요."

            WatchBridgeErrorCode.RECOVERABLE_ERROR ->
                errorMessage ?: "일시적인 오류가 발생했어요.\n잠시 후 다시 연결해주세요."

            WatchBridgeErrorCode.TERMINAL_ERROR ->
                errorMessage ?: "연결을 진행할 수 없어요.\n휴대폰 앱 상태를 확인해주세요."

            WatchBridgeErrorCode.UNKNOWN, null ->
                errorMessage ?: "휴대폰 연결에 실패했어요.\n잠시 후 다시 시도해주세요."
        }
    }

    private companion object {
        const val TAG = "WearChatRepository"
        const val THINKING_TIMEOUT_MS = 15_000L
    }
}

private class WatchInputChannelWriteException(
    cause: Throwable?
) : RuntimeException(cause)
