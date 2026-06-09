package com.app.umma.watchbridge

import android.util.Log
import com.app.umma.di.ApplicationScope
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.realtime.AIEvent
import com.app.umma.domain.model.realtime.AIState
import com.app.umma.domain.usecase.chat.RetryConnectionResult
import com.app.umma.watchbridge.contract.WatchBridgeErrorCode
import com.app.umma.watchbridge.contract.WatchChatStatus
import com.app.umma.watchbridge.contract.WatchInputSurface
import com.app.umma.watchbridge.contract.WatchOutputSurface
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

    fun getCurrentSessionLang() = runtime.getCurrentSessionLang()

    fun setAppForeground(isForeground: Boolean) {
        _snapshot.update {
            val next = it.copy(appInForeground = isForeground)
            next.withResolvedOutputSurface()
        }
        Log.d(TAG, "setAppForeground: isForeground=$isForeground")
    }

    fun setChatRouteVisible(isVisible: Boolean) {
        _snapshot.update {
            val next = it.copy(chatRouteVisible = isVisible)
            next.withResolvedOutputSurface()
        }
        Log.d(TAG, "setChatRouteVisible: isVisible=$isVisible")
    }

    fun attachWatch(): Boolean {
        val current = snapshot.value
        if (!current.isWarmForWatch) return false
        _snapshot.update {
            it.copy(
                watchAttached = true
            ).withResolvedOutputSurface()
        }
        Log.d(TAG, "attachWatch success: sessionId=${snapshot.value.activeSessionId}")
        return true
    }

    fun detachWatch() {
        _snapshot.update {
            it.copy(
                watchAttached = false,
                activeInputSurface = if (it.activeInputSurface == WatchInputSurface.WATCH) {
                    WatchInputSurface.NONE
                } else {
                    it.activeInputSurface
                }
            ).withResolvedOutputSurface()
        }
        Log.d(TAG, "detachWatch")
    }

    fun canWatchStartUserTurn(): Boolean {
        val current = snapshot.value
        return current.watchAttached &&
            current.isWarmForWatch &&
            current.activeInputSurface != WatchInputSurface.PHONE
    }

    fun canPhoneStartUserTurn(): Boolean {
        val current = snapshot.value
        return !current.watchAttached && current.activeInputSurface != WatchInputSurface.WATCH
    }

    fun markWatchRecording(isRecording: Boolean) {
        val current = snapshot.value
        if (!current.watchAttached) {
            Log.d(TAG, "markWatchRecording ignored: watch is detached")
            return
        }
        _snapshot.update {
            it.copy(
                activeInputSurface = if (isRecording) WatchInputSurface.WATCH else WatchInputSurface.NONE,
                status = if (isRecording) WatchChatStatus.RECORDING else statusFromAiState(AIState.IDLE)
            ).withResolvedOutputSurface()
        }
        Log.d(TAG, "markWatchRecording: isRecording=$isRecording")
    }

    fun markPhoneRecording(isRecording: Boolean) {
        if (isRecording && snapshot.value.watchAttached) {
            Log.d(TAG, "markPhoneRecording ignored: watch is attached")
            return
        }
        _snapshot.update {
            val nextInputSurface = when {
                isRecording -> WatchInputSurface.PHONE
                it.activeInputSurface == WatchInputSurface.PHONE -> WatchInputSurface.NONE
                else -> it.activeInputSurface
            }
            it.copy(activeInputSurface = nextInputSurface).withResolvedOutputSurface()
        }
        Log.d(TAG, "markPhoneRecording: isRecording=$isRecording")
    }

    suspend fun startSession(owner: SessionOwner): Result<String> {
        Log.d(TAG, "startSession requested by owner=$owner")
        val result = runtime.startSession()
        syncRepositoryState()
        markPhoneRecording(false)
        return result
    }

    suspend fun retryConnection(owner: SessionOwner): RetryConnectionResult {
        Log.d(TAG, "retryConnection requested by owner=$owner")
        val result = runtime.retryConnection()
        syncRepositoryState()
        return result
    }

    suspend fun stopSession(owner: SessionOwner, clearAppSession: Boolean) {
        runtime.stopSession(clearAppSession = clearAppSession)
        _snapshot.update {
            it.copy(
                status = WatchChatStatus.IDLE,
                activeSessionId = if (clearAppSession) null else runtime.getActiveSessionId(),
                currentLang = runtime.getCurrentSessionLang(),
                watchAttached = false,
                activeInputSurface = WatchInputSurface.NONE,
                replayAvailable = false,
                errorCode = null,
                errorMessage = null,
                recoverableError = false
            ).withResolvedOutputSurface()
        }
        Log.d(TAG, "stopSession completed: owner=$owner clearAppSession=$clearAppSession")
    }

    suspend fun sendAudioData(owner: SessionOwner, audio: ByteArray): Boolean {
        val current = snapshot.value
        val expectedSurface = if (owner == SessionOwner.WATCH) {
            WatchInputSurface.WATCH
        } else {
            WatchInputSurface.PHONE
        }
        if (current.activeInputSurface != expectedSurface) {
            Log.d(
                TAG,
                "sendAudioData rejected: requester=$owner currentInputSurface=${current.activeInputSurface}"
            )
            return false
        }
        runtime.sendAudioData(audio)
        Log.v(TAG, "sendAudioData forwarded: owner=$owner bytes=${audio.size}")
        return true
    }

    fun endUserTurn(owner: SessionOwner, durationMs: Long?): Boolean {
        val current = snapshot.value
        val expectedSurface = if (owner == SessionOwner.WATCH) {
            WatchInputSurface.WATCH
        } else {
            WatchInputSurface.PHONE
        }
        if (current.activeInputSurface != expectedSurface) {
            Log.d(
                TAG,
                "endUserTurn rejected: requester=$owner currentInputSurface=${current.activeInputSurface}"
            )
            return false
        }
        runtime.endUserTurn(durationMs)
        _snapshot.update {
            it.copy(
                activeInputSurface = WatchInputSurface.NONE,
                status = WatchChatStatus.THINKING
            ).withResolvedOutputSurface()
        }
        Log.d(TAG, "endUserTurn accepted: owner=$owner durationMs=$durationMs")
        return true
    }

    fun cancelPendingUserTurn(owner: SessionOwner): Boolean {
        val current = snapshot.value
        val expectedSurface = if (owner == SessionOwner.WATCH) {
            WatchInputSurface.WATCH
        } else {
            WatchInputSurface.PHONE
        }
        if (current.activeInputSurface != expectedSurface && current.activeInputSurface != WatchInputSurface.NONE) {
            Log.d(
                TAG,
                "cancelPendingUserTurn rejected: requester=$owner currentInputSurface=${current.activeInputSurface}"
            )
            return false
        }
        runtime.cancelPendingUserTurn()
        _snapshot.update {
            it.copy(
                activeInputSurface = WatchInputSurface.NONE,
                status = if (it.activeSessionId != null) WatchChatStatus.READY else WatchChatStatus.IDLE
            ).withResolvedOutputSurface()
        }
        Log.d(TAG, "cancelPendingUserTurn accepted: owner=$owner")
        return true
    }

    fun recoverWatchInputTimeout() {
        val current = snapshot.value
        if (current.activeInputSurface != WatchInputSurface.WATCH) {
            return
        }
        runtime.cancelPendingUserTurn()
        _snapshot.update {
            it.copy(
                activeInputSurface = WatchInputSurface.NONE,
                status = if (it.activeSessionId != null) WatchChatStatus.READY else WatchChatStatus.IDLE,
                recoverableError = true,
                errorCode = WatchBridgeErrorCode.RECOVERABLE_ERROR,
                errorMessage = "Watch input timed out before turn completion."
            ).withResolvedOutputSurface()
        }
        Log.w(TAG, "recoverWatchInputTimeout")
    }

    fun releaseOwner(owner: SessionOwner) {
        if (owner == SessionOwner.WATCH) {
            detachWatch()
        }
    }

    private fun syncRepositoryState(statusOverride: WatchChatStatus? = null) {
        _snapshot.update {
            it.copy(
                activeSessionId = runtime.getActiveSessionId(),
                currentLang = runtime.getCurrentSessionLang(),
                status = statusOverride ?: it.status
            ).withResolvedOutputSurface()
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
                    ).withResolvedOutputSurface()
                }
            }

            is AIEvent.Initialized -> {
                Log.d(TAG, "applyEvent Initialized sessionId=${event.sessionId}")
                _snapshot.update {
                    it.copy(
                        status = if (it.activeInputSurface == WatchInputSurface.WATCH) {
                            WatchChatStatus.RECORDING
                        } else {
                            WatchChatStatus.READY
                        },
                        activeSessionId = event.sessionId,
                        currentLang = runtime.getCurrentSessionLang(),
                        recoverableError = false,
                        errorCode = null,
                        errorMessage = null
                    ).withResolvedOutputSurface()
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
                    ).withResolvedOutputSurface()
                }
            }

            is AIEvent.AudioResponse -> {
                Log.v(TAG, "applyEvent AudioResponse bytes=${event.audio.size}")
                _snapshot.update {
                    it.copy(replayAvailable = true).withResolvedOutputSurface()
                }
            }

            is AIEvent.StateChanged -> {
                Log.d(TAG, "applyEvent StateChanged state=${event.state}")
                _snapshot.update {
                    it.copy(
                        status = if (it.activeInputSurface == WatchInputSurface.WATCH) {
                            WatchChatStatus.RECORDING
                        } else {
                            statusFromAiState(event.state)
                        }
                    ).withResolvedOutputSurface()
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
                    ).withResolvedOutputSurface()
                }
            }

            is AIEvent.Reconnected -> {
                Log.d(TAG, "applyEvent Reconnected sessionId=${event.sessionId}")
                _snapshot.update {
                    it.copy(
                        status = if (it.activeInputSurface == WatchInputSurface.WATCH) {
                            WatchChatStatus.RECORDING
                        } else {
                            WatchChatStatus.READY
                        },
                        activeSessionId = event.sessionId,
                        currentLang = runtime.getCurrentSessionLang(),
                        recoverableError = false,
                        errorCode = null,
                        errorMessage = null
                    ).withResolvedOutputSurface()
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
                    ).withResolvedOutputSurface()
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
                    ).withResolvedOutputSurface()
                }
            }

            is AIEvent.ChatUsageReported,
            is AIEvent.PartialTranscription -> Unit
        }
    }

    private fun PhoneChatSessionSnapshot.withResolvedOutputSurface(): PhoneChatSessionSnapshot {
        val nextOutputSurface = when {
            !watchAttached -> WatchOutputSurface.PHONE
            else -> WatchOutputSurface.WATCH
        }
        return copy(
            owner = SessionOwner.PHONE,
            activeOutputSurface = nextOutputSurface
        )
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
