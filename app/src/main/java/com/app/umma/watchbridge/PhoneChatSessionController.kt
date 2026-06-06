package com.app.umma.watchbridge

import android.util.Log
import com.app.umma.di.ApplicationScope
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.realtime.AIEvent
import com.app.umma.domain.model.realtime.AIState
import com.app.umma.domain.usecase.chat.RetryConnectionResult
import com.app.umma.watchbridge.contract.WatchBridgeErrorCode
import com.app.umma.watchbridge.contract.WatchChatStatus
import com.app.umma.watchbridge.contract.WatchRecentTurn
import com.app.umma.watchbridge.contract.WatchTurnRole
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class PhoneChatSessionController @Inject constructor(
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val runtime: PhoneChatSessionRuntime
) {
    private val _snapshot = MutableStateFlow(PhoneChatSessionSnapshot())
    val snapshot: StateFlow<PhoneChatSessionSnapshot> = _snapshot.asStateFlow()

    init {
        applicationScope.launch {
            runtime.observeAIEvents().collect(::applyEvent)
        }
    }

    fun observeAIEvents(): Flow<AIEvent> = runtime.observeAIEvents()

    fun currentSnapshot(): PhoneChatSessionSnapshot = snapshot.value

    /**
     * 현재 앱 레벨 세션의 학습 언어를 반환합니다.
     *
     * 세션 종료 후 분석처럼 sessionId와 language를 함께 캡처해야 하는 경계에서만 사용하고,
     * Chat 정책 해석은 domain usecase가 담당한다.
     */
    fun getCurrentSessionLang() = runtime.getCurrentSessionLang()

    fun tryAcquireOwner(owner: SessionOwner): Boolean {
        val currentOwner = snapshot.value.owner
        if (currentOwner != SessionOwner.NONE && currentOwner != owner) {
            Log.d(TAG, "tryAcquireOwner rejected: requested=$owner current=$currentOwner")
            return false
        }
        _snapshot.update { it.copy(owner = owner) }
        Log.d(TAG, "tryAcquireOwner success: requested=$owner")
        return true
    }

    fun releaseOwner(owner: SessionOwner) {
        if (snapshot.value.owner != owner) return
        _snapshot.update {
            it.copy(
                owner = SessionOwner.NONE,
                isWatchRecording = false
            )
        }
        Log.d(TAG, "releaseOwner: released=$owner")
    }

    suspend fun startSession(owner: SessionOwner): Result<String> {
        Log.d(TAG, "startSession requested by owner=$owner")
        if (!tryAcquireOwner(owner)) {
            return Result.failure(IllegalStateException("Session is controlled by another surface."))
        }
        val result = runtime.startSession()
        syncRepositoryState()
        if (result.isFailure) {
            releaseOwner(owner)
        }
        Log.d(
            TAG,
            "startSession result: owner=$owner success=${result.isSuccess} sessionId=${runtime.getActiveSessionId()}"
        )
        return result
    }

    suspend fun retryConnection(owner: SessionOwner): RetryConnectionResult {
        Log.d(TAG, "retryConnection requested by owner=$owner")
        if (!tryAcquireOwner(owner)) {
            return RetryConnectionResult.Failed("Session is controlled by another surface.")
        }
        val result = runtime.retryConnection()
        syncRepositoryState()
        if (result is RetryConnectionResult.Failed || result is RetryConnectionResult.RequireNewSession) {
            releaseOwner(owner)
        }
        Log.d(
            TAG,
            "retryConnection result: owner=$owner result=${result::class.simpleName} sessionId=${runtime.getActiveSessionId()}"
        )
        return result
    }

    suspend fun stopSession(owner: SessionOwner, clearAppSession: Boolean) {
        if (snapshot.value.owner != owner && snapshot.value.owner != SessionOwner.NONE) {
            Log.d(
                TAG,
                "stopSession ignored: requester=$owner currentOwner=${snapshot.value.owner}"
            )
            return
        }
        runtime.stopSession(clearAppSession = clearAppSession)
        syncRepositoryState(statusOverride = WatchChatStatus.IDLE)
        releaseOwner(owner)
        Log.d(TAG, "stopSession completed: owner=$owner clearAppSession=$clearAppSession")
    }

    suspend fun sendAudioData(owner: SessionOwner, audio: ByteArray): Boolean {
        if (snapshot.value.owner != owner) {
            Log.d(
                TAG,
                "sendAudioData rejected: requester=$owner currentOwner=${snapshot.value.owner}"
            )
            return false
        }
        runtime.sendAudioData(audio)
        Log.v(TAG, "sendAudioData forwarded: owner=$owner bytes=${audio.size}")
        return true
    }

    fun endUserTurn(owner: SessionOwner, durationMs: Long?): Boolean {
        if (snapshot.value.owner != owner) {
            Log.d(
                TAG,
                "endUserTurn rejected: requester=$owner currentOwner=${snapshot.value.owner}"
            )
            return false
        }
        runtime.endUserTurn(durationMs)
        if (owner == SessionOwner.WATCH) {
            _snapshot.update { it.copy(isWatchRecording = false) }
        }
        Log.d(TAG, "endUserTurn accepted: owner=$owner durationMs=$durationMs")
        return true
    }

    fun cancelPendingUserTurn(owner: SessionOwner): Boolean {
        if (snapshot.value.owner != owner) {
            Log.d(
                TAG,
                "cancelPendingUserTurn rejected: requester=$owner currentOwner=${snapshot.value.owner}"
            )
            return false
        }
        runtime.cancelPendingUserTurn()
        if (owner == SessionOwner.WATCH) {
            _snapshot.update { it.copy(isWatchRecording = false) }
        }
        Log.d(TAG, "cancelPendingUserTurn accepted: owner=$owner")
        return true
    }

    fun markWatchRecording(isRecording: Boolean) {
        if (snapshot.value.owner != SessionOwner.WATCH) {
            Log.d(
                TAG,
                "markWatchRecording ignored: currentOwner=${snapshot.value.owner} requested=$isRecording"
            )
            return
        }
        _snapshot.update {
            it.copy(
                isWatchRecording = isRecording,
                status = if (isRecording) WatchChatStatus.RECORDING else statusFromAiState(AIState.IDLE)
            )
        }
        Log.d(TAG, "markWatchRecording: isRecording=$isRecording")
    }

    private fun syncRepositoryState(statusOverride: WatchChatStatus? = null) {
        _snapshot.update {
            it.copy(
                activeSessionId = runtime.getActiveSessionId(),
                currentLang = runtime.getCurrentSessionLang(),
                status = statusOverride ?: it.status
            )
        }
    }

    private fun applyEvent(event: AIEvent) {
        when (event) {
            AIEvent.Initializing -> {
                Log.d(TAG, "applyEvent Initializing")
                _snapshot.update {
                    it.copy(
                        status = WatchChatStatus.IDLE,
                        activeSessionId = runtime.getActiveSessionId(),
                        currentLang = runtime.getCurrentSessionLang(),
                        replayAvailable = false,
                        recoverableError = false,
                        errorCode = null,
                        errorMessage = null
                    )
                }
            }

            is AIEvent.Initialized -> {
                Log.d(TAG, "applyEvent Initialized sessionId=${event.sessionId}")
                _snapshot.update {
                    it.copy(
                        status = if (it.isWatchRecording) WatchChatStatus.RECORDING else WatchChatStatus.READY,
                        activeSessionId = event.sessionId,
                        currentLang = runtime.getCurrentSessionLang(),
                        recoverableError = false,
                        errorCode = null,
                        errorMessage = null
                    )
                }
            }

            is AIEvent.FinalTranscription -> {
                Log.d(
                    TAG,
                    "applyEvent FinalTranscription turnId=${event.turnId} role=${event.role} textLength=${event.text.length}"
                )
                val recentTurn = WatchRecentTurn(
                    id = event.turnId,
                    role = if (event.role == TurnSpeaker.USER) WatchTurnRole.USER else WatchTurnRole.ASSISTANT,
                    text = event.text.take(MAX_TURN_TEXT_LENGTH)
                )
                _snapshot.update {
                    it.copy(
                        activeSessionId = event.sessionId,
                        currentLang = event.sessionLang,
                        recentTurns = (it.recentTurns + recentTurn).takeLast(MAX_RECENT_TURNS)
                    )
                }
            }

            is AIEvent.AudioResponse -> {
                Log.v(TAG, "applyEvent AudioResponse bytes=${event.audio.size}")
                _snapshot.update { it.copy(replayAvailable = true) }
            }

            is AIEvent.StateChanged -> {
                Log.d(TAG, "applyEvent StateChanged state=${event.state}")
                _snapshot.update {
                    it.copy(
                        status = if (it.isWatchRecording) {
                            WatchChatStatus.RECORDING
                        } else {
                            statusFromAiState(event.state)
                        }
                    )
                }
            }

            is AIEvent.SessionInterrupted -> {
                Log.w(
                    TAG,
                    "applyEvent SessionInterrupted recoverable=${event.recoverable} message=${event.message}"
                )
                _snapshot.update {
                    it.copy(
                        status = if (event.recoverable) WatchChatStatus.RECONNECTING else WatchChatStatus.ERROR,
                        recoverableError = event.recoverable,
                        errorCode = if (event.recoverable) {
                            WatchBridgeErrorCode.RECOVERABLE_ERROR
                        } else {
                            WatchBridgeErrorCode.TERMINAL_ERROR
                        },
                        errorMessage = event.message
                    )
                }
            }

            is AIEvent.Reconnected -> {
                Log.d(TAG, "applyEvent Reconnected sessionId=${event.sessionId}")
                _snapshot.update {
                    it.copy(
                        status = if (it.isWatchRecording) WatchChatStatus.RECORDING else WatchChatStatus.READY,
                        activeSessionId = event.sessionId,
                        currentLang = runtime.getCurrentSessionLang(),
                        recoverableError = false,
                        errorCode = null,
                        errorMessage = null
                    )
                }
            }

            is AIEvent.ReconnectFailed -> {
                Log.e(
                    TAG,
                    "applyEvent ReconnectFailed recoverable=${event.recoverable} message=${event.message}"
                )
                _snapshot.update {
                    it.copy(
                        status = WatchChatStatus.ERROR,
                        recoverableError = event.recoverable,
                        errorCode = if (event.recoverable) {
                            WatchBridgeErrorCode.RECOVERABLE_ERROR
                        } else {
                            WatchBridgeErrorCode.TERMINAL_ERROR
                        },
                        errorMessage = event.message
                    )
                }
            }

            is AIEvent.Error -> {
                Log.e(TAG, "applyEvent Error message=${event.message}")
                _snapshot.update {
                    it.copy(
                        status = WatchChatStatus.ERROR,
                        recoverableError = false,
                        errorCode = WatchBridgeErrorCode.UNKNOWN,
                        errorMessage = event.message
                    )
                }
            }

            is AIEvent.ChatUsageReported,
            is AIEvent.PartialTranscription -> Unit
        }
    }

    private fun statusFromAiState(state: AIState): WatchChatStatus {
        return when (state) {
            AIState.IDLE -> WatchChatStatus.READY
            AIState.LISTENING -> WatchChatStatus.RECORDING
            AIState.THINKING -> WatchChatStatus.THINKING
            AIState.SPEAKING -> WatchChatStatus.SPEAKING
            AIState.RECONNECTING -> WatchChatStatus.RECONNECTING
            AIState.ERROR -> WatchChatStatus.ERROR
        }
    }

    private companion object {
        const val TAG = "WatchBridgeSession"
        const val MAX_RECENT_TURNS = 4
        const val MAX_TURN_TEXT_LENGTH = 160
    }
}
