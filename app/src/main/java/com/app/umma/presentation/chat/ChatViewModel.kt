package com.app.umma.presentation.chat

import android.annotation.SuppressLint
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.umma.core.util.NetworkConnectivityMonitor
import com.app.umma.di.ApplicationScope
import com.app.umma.domain.audio.AudioInput
import com.app.umma.domain.audio.AudioOutput
import com.app.umma.domain.model.audio.AudioInputFrame
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateInput
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.SyncStatus
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.realtime.AIEvent
import com.app.umma.domain.model.realtime.AIState
import com.app.umma.domain.model.realtime.AppendTurnCommand
import com.app.umma.domain.model.realtime.ChatUsageRecord
import com.app.umma.domain.model.realtime.SessionTurn
import com.app.umma.domain.usecase.learningstate.ApplyCorrectionSignalUpdateUseCase
import com.app.umma.domain.model.user.Topic
import com.app.umma.domain.usecase.auth.GetCurrentUserUidUseCase
import com.app.umma.domain.usecase.chat.CleanupChatUsageUseCase
import com.app.umma.domain.usecase.chat.RecordChatUsageUseCase
import com.app.umma.domain.usecase.chat.NewSessionReason
import com.app.umma.domain.usecase.chat.RetryConnectionResult
import com.app.umma.domain.usecase.chat.SyncChatSessionUsageUseCase
import com.app.umma.domain.usecase.chat.SyncPendingChatUsageUseCase
import com.app.umma.domain.usecase.realtime.AppendTurnUseCase
import com.app.umma.domain.usecase.user.GetUserProfileUseCase
import com.app.umma.domain.usecase.user.GetUserNicknameUseCase
import com.app.umma.domain.usecase.user.SaveInterestTopicsUseCase
import com.app.umma.watchbridge.PhoneChatSessionController
import com.app.umma.watchbridge.SessionOwner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * AI Chat 실시간 대화 상태를 관리하는 ViewModel 입니다.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val phoneChatSessionController: PhoneChatSessionController,
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val getUserNicknameUseCase: GetUserNicknameUseCase,
    private val saveInterestTopicsUseCase: SaveInterestTopicsUseCase,
    private val getCurrentUserUidUseCase: GetCurrentUserUidUseCase,
    private val appendTurnUseCase: AppendTurnUseCase,
    private val applyCorrectionSignalUpdateUseCase: ApplyCorrectionSignalUpdateUseCase,
    private val recordChatUsageUseCase: RecordChatUsageUseCase,
    private val syncChatSessionUsageUseCase: SyncChatSessionUsageUseCase,
    private val syncPendingChatUsageUseCase: SyncPendingChatUsageUseCase,
    private val cleanupChatUsageUseCase: CleanupChatUsageUseCase,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val audioRecorder: AudioInput,
    private val audioPlayer: AudioOutput
) : ViewModel() {
    private val entryStageDelayMs = 350L

    /**
     * 화면에서 구독하는 단일 UI 상태입니다.
     */
    private val _uiState = MutableStateFlow(ChatUiState())

    /**
     * 외부에 노출하는 불변 UI 상태입니다.
     */
    val uiState = _uiState.asStateFlow()

    /**
     * 사용자 녹음 스트림을 담당하는 job 입니다.
     */
    private var recordJob: Job? = null

    /**
     * AI 이벤트 구독을 담당하는 job 입니다.
     */
    private var eventJob: Job? = null

    /**
     * 동시 turn 저장 중 상태를 추적하기 위한 카운터입니다.
     */
    private var pendingTurnSaveCount: Int = 0
    private var stopChatJob: Job? = null
    private var enterChatJob: Job? = null
    private var usageSyncJob: Job? = null
    private var outputLevelJob: Job? = null
    private var outputPlaybackJob: Job? = null
    private var currentUserTurnStartedAtMs: Long? = null
    private var currentUserTurnEndedAtMs: Long? = null

    init {
        observeSessionOwner()
        observeAudioOutputLevel()
        observeAudioOutputPlayback()
    }

    private fun observeSessionOwner() {
        viewModelScope.launch {
            phoneChatSessionController.snapshot.collectLatest { snapshot ->
                _uiState.update {
                    it.copy(sessionOwner = snapshot.owner)
                }
            }
        }
    }

    /**
     * 채팅 세션을 시작합니다.
     */
    fun enterChat() {
        if (enterChatJob?.isActive == true) return
        if (_uiState.value.shouldKeepChatUiOnReentry) {
            // 화면 회전 후 LaunchedEffect가 다시 실행되어도, 이미 준비된 세션과 화면 상태는
            // 그대로 유지한다. 여기서 loading 상태를 다시 쓰면 subtitle/final text와
            // "발화 중/응답 준비 중" 같은 transient 안내 문구가 초기화된다.
            startObservingAIEvents()
            return
        }

        enterChatJob = viewModelScope.launch {
            Log.d(TAG, "enterChat started")
            stopChatJob?.join()
            startObservingAIEvents()
            // 이전 세션에서 Firestore sync가 실패한 usage가 있으면 새 진입 초기에 best-effort로 복구한다.
            // 원격 sync는 네트워크 대기를 포함할 수 있으므로 세션 진입 flow와 병렬로 실행한다.
            viewModelScope.launch {
                syncPendingChatUsageBestEffort()
            }

            _uiState.update {
                it.copy(
                    // 여기부터는 새 세션 진입 flow 이므로 기존 마이크 안내 상태를 유지하면 안 된다.
                    // 회전 재진입은 위의 shouldKeepChatUiOnReentry guard 에서 이미 빠져나간다.
                    entryStage = ChatEntryStage.GUARDING,
                    blockedReason = null,
                    entryMessageOverride = null,
                    sessionState = SessionState.LOADING,
                    aiState = AIState.IDLE,
                    showSubtitle = false,
                    isAwaitingUserTranscript = false,
                    // 새 진입 flow는 화면 표시용 자막의 시작점도 새로 잡는다.
                    // 회전 재진입은 이 분기에 오지 않으므로 기존 자막 보존 요구와 충돌하지 않는다.
                    subtitleItems = emptyList(),
                    lastFinalUserTranscript = "",
                    lastFinalAITranscript = "",
                    lastHandledFinalTurnId = null,
                    handledFinalTurnIds = emptySet(),
                    isRecoverableError = false,
                    errorMessage = null
                )
            }
            delay(entryStageDelayMs)

            if (!networkConnectivityMonitor.isConnected.first()) {
                _uiState.update {
                    it.copy(
                        entryStage = ChatEntryStage.BLOCKED_NETWORK,
                        blockedReason = ChatBlockedReason.OFFLINE,
                        entryMessageOverride = null,
                        sessionState = SessionState.ERROR,
                        aiState = AIState.ERROR,
                        isRecoverableError = false,
                        errorMessage = "네트워크에 연결할 수 없습니다.\nwifi 또는 모바일 데이터를 확인해주세요."
                    )
                }
                return@launch
            }

            val currentControllerSnapshot = phoneChatSessionController.currentSnapshot()
            if (currentControllerSnapshot.owner == SessionOwner.WATCH) {
                _uiState.update {
                    it.copy(
                        entryStage = ChatEntryStage.READY,
                        blockedReason = null,
                        sessionState = SessionState.READY,
                        aiState = watchStatusToAiState(currentControllerSnapshot.status),
                        activeSessionId = currentControllerSnapshot.activeSessionId,
                        errorMessage = null,
                        isRecoverableError = false,
                        sessionOwner = SessionOwner.WATCH
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    // 기존 active session 복구를 시도하는 동안에는 입력을 막고,
                    // 화면에는 reconnecting 계열 안내만 노출한다.
                    entryStage = ChatEntryStage.RESTORING,
                    blockedReason = null,
                    entryMessageOverride = null,
                    sessionState = SessionState.LOADING,
                    aiState = AIState.RECONNECTING,
                    isAwaitingUserTranscript = false,
                    isRecoverableError = false,
                    errorMessage = null
                )
            }
            delay(entryStageDelayMs)
            val restoreResult = phoneChatSessionController.retryConnection(SessionOwner.PHONE)
            when (restoreResult) {
                is RetryConnectionResult.Reconnected -> {
                    handleSessionStarted(restoreResult.sessionId)
                    return@launch
                }
                is RetryConnectionResult.Failed -> Unit
                is RetryConnectionResult.RequireNewSession -> Unit
            }

            _uiState.update {
                it.copy(
                    // 복구 실패 후 새 세션을 만드는 경계다.
                    // 이전 turn 의 transient 안내 문구는 새 세션 상태와 섞이면 안 되므로 초기화한다.
                    entryStage = ChatEntryStage.STARTING_NEW,
                    blockedReason = null,
                    entryMessageOverride = when (restoreResult) {
                        is RetryConnectionResult.RequireNewSession ->
                            buildEntryMessageForNewSession(
                                reason = restoreResult.reason,
                                targetLang = restoreResult.targetLang
                            )
                        else -> null
                    },
                    sessionState = SessionState.LOADING,
                    aiState = AIState.IDLE,
                    isRecoverableError = false,
                    errorMessage = null
                )
            }
            delay(entryStageDelayMs)

            // restoreResult 를 기준으로 새 세션 전환이 사용자에게 안내되어야 하는지 미리 결정한다.
            // NO_ACTIVE_SESSION 은 첫 진입에서도 자연스럽게 발생하므로 fallback 완료 메시지를 띄우지 않는다.
            val fallbackCompletionMessage = restoreResult.toFallbackCompletionMessage()

            phoneChatSessionController.startSession(SessionOwner.PHONE)
                .onSuccess { sessionId ->
                    handleSessionStarted(
                        sessionId = sessionId,
                        fallbackCompletionMessage = fallbackCompletionMessage
                    )
                    audioPlayer.startPlaying()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            entryStage = ChatEntryStage.ERROR,
                            blockedReason = ChatBlockedReason.UNRECOVERABLE,
                            sessionState = SessionState.ERROR,
                            aiState = AIState.ERROR,
                            isRecoverableError = true,
                            errorMessage = toUserFacingErrorMessage(
                                rawMessage = error.message,
                                fallback = "대화를 시작할 수 없습니다.\n잠시 후 다시 시도해 주세요."
                            )
                        )
                    }
                }
        }
    }

    /**
     * startSession 성공 반환값만으로도 READY 상태를 확정합니다.
     *
     * Initialized 이벤트를 늦게 받거나 놓쳐도 UI가 LOADING에 남지 않도록 합니다.
     */
    private fun handleSessionStarted(
        sessionId: String,
        fallbackCompletionMessage: String? = null
    ) {
        _uiState.update {
            // startSession 성공 콜백이 같은 sessionId로 다시 도착할 수 있다.
            // 회전 재진입이나 지연 콜백에서 같은 세션을 다시 READY로 쓰는 경우에는
            // 현재 녹음/발화 확정 대기/AI 응답 상태를 보존해야 하단 안내 문구가 사라지지 않는다.
            val isSameSession = it.activeSessionId == sessionId
            val shouldKeepTransientState = isSameSession && it.hasTransientMicStatus
            it.copy(
                entryStage = ChatEntryStage.READY,
                blockedReason = null,
                entryMessageOverride = null,
                sessionState = SessionState.READY,
                // 같은 세션의 확인 콜백이면 현재 AI 상태를 보존한다.
                // 새 세션이면 IDLE에서 다시 시작해야 버튼이 START 상태로 돌아온다.
                aiState = if (shouldKeepTransientState) it.aiState else AIState.IDLE,
                activeSessionId = sessionId,
                // 발화 확정 대기 문구는 새 세션 시작 시에는 내려야 하지만,
                // 같은 세션의 지연 콜백에서는 사용자가 보던 상태를 유지한다.
                isAwaitingUserTranscript = if (shouldKeepTransientState) {
                    it.isAwaitingUserTranscript
                } else {
                    false
                },
                // 실제 복구 실패/언어 변경 이후 새 세션을 만든 경우에만 완료 안내를 남긴다.
                // 첫 진입의 NO_ACTIVE_SESSION 은 정상 시작 flow 이므로 fallback 메시지를 띄우지 않는다.
                didFallbackToNewSession = fallbackCompletionMessage != null,
                fallbackMessage = fallbackCompletionMessage,
                reconnectAttempt = 0,
                maxReconnectAttempts = 0,
                isRecoverableError = false,
                microphonePermissionDenied = false,
                errorMessage = null
            )
        }
    }

    /**
     * 사용자 발화 turn 녹음을 시작합니다. && 권한 체크
     */
    fun startUserTurn(hasRecordAudioPermission: Boolean) {
        if (!hasRecordAudioPermission) {
            onMicPermissionDenied(permanently = false)
            return
        }

        _uiState.update {
            it.copy(
                microphonePermissionDenied = false,
                microphonePermissionPermanentlyDenied = false,
                errorMessage = null
            )
        }

        beginUserTurn()
    }

    fun onMicPermissionDenied(permanently: Boolean) {
        _uiState.update {
            it.copy(
                microphonePermissionDenied = true,
                microphonePermissionPermanentlyDenied = permanently,
                errorMessage = if (permanently) {
                    "마이크 권한이 영구 거부되었습니다. 설정에서 권한을 허용해주세요."
                } else {
                    "마이크 권한이 필요합니다."
                }
            )
        }
    }

    /**
     * 사용자 발화 turn 녹음을 시작합니다.
     */
    @SuppressLint("MissingPermission")
    private fun beginUserTurn() {
        val currentState = _uiState.value
        if (!currentState.canStartUserTurn) return
        if (recordJob?.isActive == true) return

        currentUserTurnStartedAtMs = System.currentTimeMillis()
        currentUserTurnEndedAtMs = null
        phoneChatSessionController.cancelPendingUserTurn(SessionOwner.PHONE)

        _uiState.update {
            it.copy(
                isRecording = true,
                // 새 발화를 시작하면 이전 turn 의 확정 대기 표시는 더 이상 유효하지 않다.
                isAwaitingUserTranscript = false,
                // fallback 완료 안내는 세션 전환 사실을 알려주는 일회성 문구다.
                // 사용자가 새 발화를 시작하면 이미 대화가 정상 진행되는 상태이므로 화면에서 내린다.
                didFallbackToNewSession = false,
                fallbackMessage = null,
                inputLevel = 0f,
                errorMessage = null
            )
        }

        recordJob = viewModelScope.launch {
            try {
                audioRecorder.startRecording().collect { frame ->
                    handleAudioInputFrame(frame)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(
                    TAG,
                    "beginUserTurn failed: type=${error::class.java.simpleName}, message=${error.message}",
                    error
                )
                val userMessage = toUserFacingErrorMessage(
                    rawMessage = error.message,
                    fallback = "녹음 중 문제가 발생했습니다.\n네트워크 연결 후 다시 시도해주세요."
                )
                _uiState.update {
                    it.copy(
                        isRecording = false,
                        isAwaitingUserTranscript = false,
                        inputLevel = 0f,
                        sessionState = SessionState.ERROR,
                        aiState = AIState.ERROR,
                        isRecoverableError = false,
                        errorMessage = userMessage
                    )
                }
            }
        }
    }

    /**
     * AI speaking 진입 시 에코 루프를 막기 위해 사용자 녹음을 즉시 종료합니다.
     */
    private fun stopRecordingForAiSpeaking() {
        if (!_uiState.value.isRecording) return

        captureCurrentUserTurnDuration()
        recordJob?.cancel()
        audioRecorder.stopRecording()
        recordJob = null

        _uiState.update {
            it.copy(
                isRecording = false,
                isAwaitingUserTranscript = true,
                inputLevel = 0f
            )
        }
    }

    /**
     * 현재 사용자 발화 turn 녹음을 종료합니다.
     */
    fun endUserTurn() {
        if (!_uiState.value.canEndUserTurn) return

        captureCurrentUserTurnDuration()
        recordJob?.cancel()
        audioRecorder.stopRecording()
        recordJob = null

        _uiState.update {
            it.copy(
                isRecording = false,
                // 사용자가 명시적으로 정지 버튼을 누른 직후에는 USER final transcript 를 기다리는
                // 짧은 대기 상태를 보여준다. 실제 AI 응답 시작은 repository 가 USER transcript
                // completed 이후 response.create 를 보낼 때 진행된다.
                isAwaitingUserTranscript = true,
                inputLevel = 0f
            )
        }
    }

    /**
     * 현재 chat 세션을 종료하고 상태를 초기화합니다.
     */
    fun stopChat() {
        val sessionIdForUsageSync = _uiState.value.activeSessionId
        launchChatUsageSync(sessionIdForUsageSync)

        stopChatJob?.cancel()
        stopChatJob = viewModelScope.launch {
            // 사용자가 Chat 화면을 정상적으로 이탈하는 경로다.
            // usage sync는 applicationScope에서 분리 실행했으므로, 여기서는 UI/오디오/transport만 정리한다.
            stopChatInternal(resetUiState = true)
        }
    }

    /**
     * Chat destination 이 back stack 에 저장된 채 화면에서만 내려가는 경우에도 usage sync 를 시도합니다.
     *
     * Bottom navigation 의 saveState/restoreState 경로에서는 Composable 이 즉시 dispose 되지 않을 수 있습니다.
     * 이 경우 [stopChat]이 호출되지 않아 세션 종료 sync가 누락될 수 있으므로,
     * lifecycle ON_STOP 경계에서 현재 세션의 pending usage만 best-effort로 올립니다.
     */
    fun syncCurrentUsageForHiddenScreen() {
        val sessionId = _uiState.value.activeSessionId
        launchChatUsageSync(sessionId)
    }

    /**
     * Chat 화면 리소스를 정리합니다.
     *
     * 화면 회전에서는 [ChatScreen]이 dispose되더라도 같은 [ChatViewModel]을 재사용할 수 있으므로
     * 이 함수를 호출하지 않는다. navigation 이탈처럼 화면이 실제로 사라지는 경우에는 [stopChat]이,
     * ViewModel 자체가 제거되는 경우에는 onCleared가 호출해 녹음/재생/realtime transport를 정리한다.
     */
    private suspend fun stopChatInternal(resetUiState: Boolean) {
        eventJob?.cancel()
        eventJob = null

        recordJob?.cancel()
        audioRecorder.stopRecording()
        recordJob = null

        currentUserTurnStartedAtMs = null
        currentUserTurnEndedAtMs = null
        phoneChatSessionController.cancelPendingUserTurn(SessionOwner.PHONE)
        audioPlayer.stopPlaying()
        phoneChatSessionController.stopSession(
            owner = SessionOwner.PHONE,
            clearAppSession = false
        )

        pendingTurnSaveCount = 0
        if (resetUiState) {
            _uiState.value = ChatUiState()
        }
    }

    /**
     * AI 이벤트 스트림 구독을 시작합니다.
     */
    private fun startObservingAIEvents() {
        if (eventJob?.isActive == true) return

        eventJob = viewModelScope.launch {
            phoneChatSessionController.observeAIEvents().collect { event ->
                when (event) {
                    is AIEvent.Initializing -> handleInitializing()
                    is AIEvent.Initialized -> handleInitialized(event)
                    is AIEvent.PartialTranscription -> handlePartialTranscription(event)
                    is AIEvent.FinalTranscription -> handleFinalTranscription(event)
                    is AIEvent.ChatUsageReported -> handleChatUsageReported(event)
                    is AIEvent.AudioResponse -> handleAudioResponse(event)
                    is AIEvent.StateChanged -> handleStateChanged(event)
                    is AIEvent.SessionInterrupted -> handleSessionInterrupted(event)
                    is AIEvent.Reconnected -> handleReconnected(event)
                    is AIEvent.ReconnectFailed -> handleReconnectFailed(event)
                    is AIEvent.Error -> handleError(event)
                }
            }
        }
    }

    /**
     * 세션 초기화 상태를 반영합니다.
     */
    private fun handleInitializing() {
        _uiState.update {
            it.copy(
                sessionState = SessionState.LOADING,
                errorMessage = null
            )
        }
    }

    /**
     * 세션 초기화 완료 상태를 반영합니다.
     *
     * @param event 세션 초기화 완료 이벤트
     */
    private fun handleInitialized(event: AIEvent.Initialized) {
        _uiState.update {
            // Initialized 이벤트는 startSession 성공 이후 늦게 도착할 수 있다.
            // 같은 세션 확인 이벤트라면 현재 사용자가 말하는 중이거나 AI 응답을 기다리는
            // 화면 상태를 IDLE로 되돌리지 않는다.
            val isSameSession = it.activeSessionId == event.sessionId
            val shouldKeepTransientState = isSameSession && it.hasTransientMicStatus
            it.copy(
                entryStage = ChatEntryStage.READY,
                blockedReason = null,
                entryMessageOverride = null,
                sessionState = SessionState.READY,
                // transport 초기화 완료 이벤트는 세션 준비 확인이지, 항상 "대화가 쉬는 중"이라는 뜻은 아니다.
                // 같은 세션에서 이미 말하기/응답 대기가 진행 중이면 그 상태를 유지한다.
                aiState = if (shouldKeepTransientState) it.aiState else AIState.IDLE,
                activeSessionId = event.sessionId,
                // 회전 직후 같은 initialized 이벤트가 재처리되어도 하단 안내 문구가 사라지지 않게 한다.
                isAwaitingUserTranscript = if (shouldKeepTransientState) {
                    it.isAwaitingUserTranscript
                } else {
                    false
                },
                reconnectAttempt = 0,
                maxReconnectAttempts = 0,
                isRecoverableError = false,
                // startSession 성공 직후 Initialized가 늦게 도착할 수 있으므로,
                // 같은 세션에 이미 fallback 완료 안내가 있으면 여기서 지우지 않는다.
                didFallbackToNewSession = if (isSameSession && it.fallbackMessage != null) {
                    it.didFallbackToNewSession
                } else {
                    false
                },
                fallbackMessage = if (isSameSession) it.fallbackMessage else null,
                microphonePermissionDenied = false,
                errorMessage = null
            )
        }
    }

    /**
     * partial transcript 상태를 반영합니다.
     *
     * @param event partial transcript 이벤트
     */
    private fun handlePartialTranscription(event: AIEvent.PartialTranscription) {
        if (event.text?.isBlank() ?: true) return

        _uiState.update {
            when (event.role) {
                TurnSpeaker.USER -> it.copy(userPartialTranscript = event.text)
                TurnSpeaker.AI -> it.copy(aiPartialTranscript = event.text)
            }
        }
    }

    /**
     * final transcript 상태를 반영하고 저장을 트리거합니다.
     * 만약 text가 비어있거나, 마지막 FinalTurnId가 이번 turnId와 같으면 저장 무시
     * @param event final transcript 이벤트
     */
    private fun handleFinalTranscription(event: AIEvent.FinalTranscription) {
        if (event.text.isBlank()) {
            return
        }
        if (_uiState.value.hasHandledFinalTurn(event.turnId)) {
            return
        }

        _uiState.update {
            // final transcript는 저장 트리거이면서 화면 표시용 말풍선의 source event다.
            // partial/delta는 이 리스트에 넣지 않아 Sprint3 D 범위의 final-only 정책을 지킨다.
            val subtitleItems = it.subtitleItems.replaceLatestRoleSubtitle(event.toSubtitleItem())
            // 화면 말풍선은 역할별 최신 1개만 남기지만, 중복 저장/표시 방어는 세션 동안 처리한
            // turnId 전체를 기준으로 해야 이전 final 이벤트가 늦게 재전달되어도 다시 반영되지 않는다.
            val handledFinalTurnIds = it.handledFinalTurnIds + event.turnId

            when (event.role) {
                TurnSpeaker.USER -> it.copy(
                    userPartialTranscript = "",
                    lastFinalUserTranscript = event.text,
                    // 정지 버튼 이후 기다리던 USER final transcript 가 도착했으므로
                    // 화면의 "발화 인식 중" 상태를 종료한다.
                    isAwaitingUserTranscript = false,
                    lastHandledFinalTurnId = event.turnId,
                    handledFinalTurnIds = handledFinalTurnIds,
                    subtitleItems = subtitleItems
                )

                TurnSpeaker.AI -> it.copy(
                    aiPartialTranscript = "",
                    lastFinalAITranscript = event.text,
                    lastHandledFinalTurnId = event.turnId,
                    handledFinalTurnIds = handledFinalTurnIds,
                    subtitleItems = subtitleItems
                )
            }
        }

        persistFinalTurn(event)
    }

    /**
     * OpenAI Realtime usage 이벤트를 local-first usage 저장소로 기록합니다.
     *
     * usage는 비용 분석/플랜 설계를 위한 운영 데이터입니다. 화면 자막, SessionMemory 저장,
     * correctionAvailable 신호와 책임이 다르므로 실패해도 사용자 대화 흐름으로 전파하지 않습니다.
     *
     * @param event provider usage payload를 앱 공통 모델로 바꾼 이벤트
     */
    private suspend fun handleChatUsageReported(event: AIEvent.ChatUsageReported) {
        val uid = getCurrentUserUidUseCase.getCurrentUserUid()
        if (uid.isNullOrBlank()) {
            Log.w(TAG, "chat usage skipped: signed-in user is required")
            return
        }

        // ViewModel은 provider payload를 직접 해석하지 않고,
        // ChatRepository가 만든 domain usage 이벤트를 저장 usecase 입력으로만 변환한다.
        val record = ChatUsageRecord(
            id = event.usageEventId,
            userId = uid,
            sessionId = event.sessionId,
            turnId = event.turnId,
            language = event.sessionLang,
            kind = event.kind,
            model = event.model,
            transcriptionModel = event.transcriptionModel,
            createdAt = event.createdAt,
            usage = event.usage,
            pricingVersion = CHAT_USAGE_PRICING_VERSION,
            syncStatus = SyncStatus.PENDING
        )

        recordChatUsageUseCase(record)
            .onSuccess {
                // 수동 검증 시 Logcat만으로도 local-first 기록 여부를 빠르게 확인할 수 있게 한다.
                Log.i(
                    TAG,
                    "CHAT-ENGINE-001-B usage_recorded sessionId=${record.sessionId}, kind=${record.kind}, turnId=${record.turnId}"
                )
            }
            .onFailure { error ->
                Log.w(
                    TAG,
                    "chat usage local record failed: sessionId=${event.sessionId}, kind=${event.kind}, message=${error.message}",
                    error
                )
            }
    }

    /**
     * 확정된 turn 을 Session Memory 에 저장합니다.
     *
     * @param event 저장할 final transcript 이벤트
     */
    private fun persistFinalTurn(event: AIEvent.FinalTranscription) {
        viewModelScope.launch {
            beginTurnSave()

            val command = AppendTurnCommand(
                language = event.sessionLang,
                turn = SessionTurn(
                    turnId = event.turnId,
                    sessionId = event.sessionId,
                    text = event.text,
                    role = event.role,
                    createdAt = event.createdAt,
                    durationMs = event.durationMs,
                    tokenCount = event.tokenCount,
                    confidence = event.confidence
                )
            )

            appendTurnUseCase(command)
                .onSuccess {
                    handoverCorrectionAvailableSignal(event)
                    _uiState.update {
                        it.copy(saveErrorMessage = null)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(saveErrorMessage = error.message ?: "Turn 저장 실패")
                    }
                }

            endTurnSave()
        }
    }

    private suspend fun handoverCorrectionAvailableSignal(event: AIEvent.FinalTranscription) {
        if (event.role != TurnSpeaker.USER) return

        val uid = getCurrentUserUidUseCase.getCurrentUserUid()
        if (uid.isNullOrBlank()) {
            return
        }

        val input = CorrectionSignalUpdateInput(
            uid = uid,
            lang = event.sessionLang,
            sessionMemoryKey = buildSessionMemoryKey(uid, event.sessionLang),
            sourceEventId = event.turnId,
            correctionAvailable = true,
            updatedAt = event.createdAt
        )

        applyCorrectionSignalUpdateUseCase(input)
            .onFailure { }
    }

    /**
     * 현재 세션의 local usage를 Firestore session aggregate로 동기화합니다.
     *
     * 이 함수는 화면 이탈/세션 종료 cleanup 중 호출되는 best-effort 경로입니다.
     * remote sync가 실패해도 usage 원본은 local PENDING으로 유지되며, 사용자에게 오류 UI를 보여주지 않습니다.
     */
    private suspend fun syncCurrentChatSessionUsageBestEffort(sessionId: String?) {
        if (sessionId.isNullOrBlank()) return

        val uid = getCurrentUserUidUseCase.getCurrentUserUid()
        if (uid.isNullOrBlank()) return

        syncChatSessionUsageUseCase(uid, sessionId)
            .onSuccess { syncedCount ->
                // Firestore session aggregate sync 결과도 테스트 로그로 남긴다.
                // 실제 데이터 확인은 Firestore 문서가 기준이고, 이 로그는 수동 검증 보조값이다.
                Log.i(
                    TAG,
                    "CHAT-ENGINE-001-B usage_session_synced sessionId=$sessionId, syncedCount=$syncedCount"
                )
                cleanupChatUsageBestEffort(uid)
            }
            .onFailure { error ->
                Log.w(
                    TAG,
                    "chat usage session sync pending: sessionId=$sessionId, message=${error.message}",
                    error
                )
            }
    }

    /**
     * 화면 이탈 시점의 usage sync를 ViewModel 생명주기에서 분리해 실행합니다.
     *
     * Bottom navigation 또는 back stack 제거 직후에는 ViewModel scope가 취소될 수 있습니다.
     * usage 원본은 이미 Room에 PENDING으로 저장되어 있으므로, 이 작업은 실패해도 다음 Chat 진입 때 재시도됩니다.
     */
    private fun launchChatUsageSync(sessionId: String?) {
        if (sessionId.isNullOrBlank()) return
        if (usageSyncJob?.isActive == true) return

        usageSyncJob = applicationScope.launch {
            syncCurrentChatSessionUsageBestEffort(sessionId)
        }
    }

    /**
     * 이전 앱 실행이나 네트워크 실패로 남은 pending usage를 재시도합니다.
     *
     * pending retry는 Chat 진입의 보조 작업입니다. 실패해도 세션 시작이나 대화 입력을 막지 않습니다.
     */
    private suspend fun syncPendingChatUsageBestEffort() {
        val uid = getCurrentUserUidUseCase.getCurrentUserUid()
        if (uid.isNullOrBlank()) return

        syncPendingChatUsageUseCase(uid)
            .onSuccess { syncedCount ->
                // pending retry는 화면에 노출하지 않으므로 Logcat에 성공 건수만 남긴다.
                Log.i(TAG, "CHAT-ENGINE-001-B usage_pending_synced syncedCount=$syncedCount")
                cleanupChatUsageBestEffort(uid)
            }
            .onFailure { error ->
                Log.w(
                    TAG,
                    "chat usage pending sync skipped: message=${error.message}",
                    error
                )
            }
    }

    /**
     * Firestore sync가 끝난 오래된 usage 원본 row를 정리합니다.
     *
     * cleanup은 저장공간 관리 목적의 best-effort 작업입니다. 실패해도 사용자의 대화 흐름,
     * pending sync 재시도, final turn 저장에 영향을 주지 않아야 하므로 로그만 남깁니다.
     */
    private suspend fun cleanupChatUsageBestEffort(uid: String) {
        cleanupChatUsageUseCase(uid)
            .onSuccess { deletedCount ->
                if (deletedCount > 0) {
                    Log.i(TAG, "CHAT-ENGINE-001-B usage_local_cleanup deletedCount=$deletedCount")
                }
            }
            .onFailure { error ->
                Log.w(
                    TAG,
                    "chat usage local cleanup skipped: message=${error.message}",
                    error
                )
            }
    }

    /**
     * turn 저장 시작 상태를 반영합니다.
     */
    private fun beginTurnSave() {
        pendingTurnSaveCount += 1
        _uiState.update {
            it.copy(
                isSavingTurn = true,
                saveErrorMessage = null
            )
        }
    }

    /**
     * turn 저장 종료 상태를 반영합니다.
     */
    private fun endTurnSave() {
        pendingTurnSaveCount = (pendingTurnSaveCount - 1).coerceAtLeast(0)
        _uiState.update {
            it.copy(isSavingTurn = pendingTurnSaveCount > 0)
        }
    }

    /**
     * AI 오디오 응답을 재생하고 출력 레벨을 갱신합니다.
     *
     * @param event 오디오 응답 이벤트
     */
    private fun handleAudioResponse(event: AIEvent.AudioResponse) {
        stopRecordingForAiSpeaking()

        _uiState.update {
            it.copy(
                // audio chunk가 도착한 시점부터는 사용자가 아니라 AI 출력이 주 상태다.
                // 단, 재연결/오류 상태는 더 높은 우선순위이므로 SPEAKING으로 덮지 않는다.
                aiState = if (
                    it.aiState != AIState.RECONNECTING &&
                    it.aiState != AIState.ERROR
                ) AIState.SPEAKING else it.aiState,
                // AI 음성이 시작되면 사용자 발화 확정 대기 상태는 화면에서 더 이상 주 상태가 아니다.
                isAwaitingUserTranscript = false
            )
        }
        audioPlayer.playAudioChunk(event.audio)
    }

    /**
     * 입력 오디오 프레임을 서버로 전송합니다.
     *
     * @param frame 입력 오디오 프레임
     */
    private suspend fun handleAudioInputFrame(frame: AudioInputFrame) {
        _uiState.update {
            it.copy(inputLevel = frame.level)
        }
        try {
            phoneChatSessionController.sendAudioData(SessionOwner.PHONE, frame.pcm)
        } catch (error: Exception) {
            Log.e(
                TAG,
                "sendAudioData failed: bytes=${frame.pcm.size}, type=${error::class.java.simpleName}, message=${error.message}",
                error
            )
            throw error
        }
    }

    /**
     * AI 상태 변화를 반영합니다.
     *
     * @param event 상태 변화 이벤트
     */
    private fun handleStateChanged(event: AIEvent.StateChanged) {
        if (event.state == AIState.SPEAKING) {
            // AI가 말하기 시작했는데 사용자가 아직 녹음 중이면 에코와 중복 입력을 막기 위해 즉시 끊는다.
            stopRecordingForAiSpeaking()
        }

        _uiState.update { current ->
            val next = current.copy(
                aiState = event.state,
                // SPEAKING 상태는 이미 AI 응답 구간이므로 "발화 인식 중" 안내보다 우선한다.
                // 그 외 상태에서는 직전 대기 문구가 필요한 짧은 구간이 있어 기존 값을 유지한다.
                isAwaitingUserTranscript = if (event.state == AIState.SPEAKING) {
                    false
                } else {
                    current.isAwaitingUserTranscript
                }
            )
            Log.d(
                TAG,
                "aiState changed: ${current.aiState}->${next.aiState}, " +
                    "isAudioOutputPlaying=${next.isAudioOutputPlaying}, mic=${next.micControlState}"
            )
            Log.d(
                DIAG_TAG,
                "ui_ai_state_changed ${current.aiState}->${next.aiState} " +
                    "audioPlaying=${next.isAudioOutputPlaying} mic=${next.micControlState} " +
                    "recording=${next.isRecording} awaitingUserTranscript=${next.isAwaitingUserTranscript}"
            )
            next
        }
    }

    /**
     * 세션 중단 상태를 반영합니다.
     *
     * @param event 세션 중단 이벤트
     * - RECONNECTING으로 전환
     */
    private fun handleSessionInterrupted(event: AIEvent.SessionInterrupted) {
        Log.w(
            TAG,
            "handleSessionInterrupted reason=${event.reason}, attempt=${event.attempt}/${event.maxAttempts}, message=${event.message}"
        )
        recordJob?.cancel()
        recordJob = null

        _uiState.update {
            it.copy(
                sessionState = SessionState.RECONNECTING,
                isRecording = false,
                isAwaitingUserTranscript = false,
                inputLevel = 0f,
                outputLevel = 0f,
                isAudioOutputPlaying = false,
                userPartialTranscript = "",
                aiPartialTranscript = "",
                aiState = AIState.RECONNECTING,
                reconnectAttempt = event.attempt,
                maxReconnectAttempts = event.maxAttempts,
                isRecoverableError = false,
                fallbackMessage = null,
                errorMessage = toUserFacingErrorMessage(
                    rawMessage = event.message,
                    fallback = "연결이 일시적으로 끊겼어요. 자동으로 다시 연결 중입니다."
                )
            )
        }
    }

    /**
     * realtime transport 재연결 완료 상태를 반영합니다.
     *
     * @param event 재연결 완료 이벤트
     */
    private fun handleReconnected(event: AIEvent.Reconnected) {
        Log.d(TAG, "handleReconnected sessionId=${event.sessionId}")
        _uiState.update {
            it.copy(
                entryStage = ChatEntryStage.READY,
                blockedReason = null,
                entryMessageOverride = null,
                sessionState = SessionState.READY,
                aiState = AIState.IDLE,
                activeSessionId = event.sessionId,
                isAwaitingUserTranscript = false,
                reconnectAttempt = 0,
                didFallbackToNewSession = false,
                fallbackMessage = null,
                maxReconnectAttempts = 0,
                isRecoverableError = false,
                errorMessage = null
            )
        }
    }

    /**
     * 자동 재연결 실패 상태를 반영합니다.
     *
     * @param event 재연결 실패 이벤트
     */
    private fun handleReconnectFailed(event: AIEvent.ReconnectFailed) {
        Log.e(
            TAG,
            "handleReconnectFailed recoverable=${event.recoverable}, message=${event.message}"
        )
        recordJob?.cancel()
        recordJob = null

        _uiState.update {
            it.copy(
                entryStage = ChatEntryStage.ERROR,
                entryMessageOverride = null,
                isRecording = false,
                isAwaitingUserTranscript = false,
                inputLevel = 0f,
                outputLevel = 0f,
                isAudioOutputPlaying = false,
                userPartialTranscript = "",
                aiPartialTranscript = "",
                sessionState = SessionState.ERROR,
                aiState = AIState.ERROR,
                isRecoverableError = event.recoverable,
                fallbackMessage = null,
                errorMessage = toUserFacingErrorMessage(
                    rawMessage = event.message,
                    fallback = "네트워크 연결이 끊겼습니다. 다시 시도해 주세요."
                )
            )
        }
    }

    /**
     * 일반 오류 상태를 반영합니다.
     *
     * @param event 오류 이벤트
     */
    private fun handleError(event: AIEvent.Error) {
        Log.e(TAG, "handleError message=${event.message}")
        recordJob?.cancel()
        recordJob = null

        _uiState.update {
            it.copy(
                entryStage = ChatEntryStage.ERROR,
                entryMessageOverride = null,
                isRecording = false,
                isAwaitingUserTranscript = false,
                inputLevel = 0f,
                outputLevel = 0f,
                isAudioOutputPlaying = false,
                sessionState = SessionState.ERROR,
                aiState = AIState.ERROR,
                isRecoverableError = false,
                fallbackMessage = null,
                errorMessage = toUserFacingErrorMessage(
                    rawMessage = event.message,
                    fallback = "문제가 발생했습니다. 잠시 후 다시 시도해 주세요."
                )
            )
        }
    }

    /**
     * ViewModel 종료 시 오디오 플레이어 리소스를 해제합니다.
     */
    override fun onCleared() {
        super.onCleared()
        outputLevelJob?.cancel()
        outputPlaybackJob?.cancel()
        // onCleared는 Chat back stack이 제거되는 실제 종료 경계다. 화면 회전과 달리
        // 여기서는 transport와 오디오 리소스만 즉시 정리한다.
        // Firestore usage sync는 네트워크 요청이라 onCleared/runBlocking 경계에 묶지 않고,
        // local PENDING row를 다음 Chat 진입의 pending retry가 처리하도록 둔다.
        runBlocking {
            stopChatInternal(resetUiState = false)
        }
    }

    /**
     * 출력 오디오 레벨을 즉시 0으로 떨구지 않도록 attack/decay 형태로 완화합니다.
     */

    /**
     * 마지막 오디오 청크 이후에는 짧게 유지했다가 점진적으로 감쇠시킵니다.
     */

    /**
     * 출력 레벨 감쇠 루프를 정리합니다.
     */
    private fun observeAudioOutputLevel() {
        if (outputLevelJob?.isActive == true) return

        outputLevelJob = viewModelScope.launch {
            audioPlayer.outputLevel.collectLatest { level ->
                _uiState.update { it.copy(outputLevel = level.coerceIn(0f, 1f)) }
            }
        }
    }

    /**
     * 실제 로컬 스피커 재생 상태를 UI 입력 방어에 반영합니다.
     *
     * OpenAI response.done은 서버의 응답 생성 완료 이벤트이고, 기기 스피커의 재생 완료 이벤트가 아니다.
     * AudioPlayer 큐에 남은 음성이 있으면 사용자는 아직 Umma의 말을 듣는 중이므로 마이크를 비활성화한다.
     */
    private fun observeAudioOutputPlayback() {
        if (outputPlaybackJob?.isActive == true) return

        outputPlaybackJob = viewModelScope.launch {
            audioPlayer.isPlaying.collectLatest { isPlaying ->
                _uiState.update { current ->
                    val next = current.copy(isAudioOutputPlaying = isPlaying)
                    Log.d(
                        TAG,
                        "audio playback changed: isPlaying=$isPlaying, " +
                            "aiState=${next.aiState}, mic=${next.micControlState}"
                    )
                    Log.d(
                        DIAG_TAG,
                        "ui_audio_playback_changed isPlaying=$isPlaying " +
                            "aiState=${next.aiState} mic=${next.micControlState} " +
                            "sessionState=${next.sessionState}"
                    )
                    next
                }
            }
        }
    }

    private fun captureCurrentUserTurnDuration() {
        val startedAt = currentUserTurnStartedAtMs ?: return
        if (currentUserTurnEndedAtMs == null) {
            currentUserTurnEndedAtMs = System.currentTimeMillis()
        }
        val endedAt = currentUserTurnEndedAtMs ?: return
        val durationMs = (endedAt - startedAt).coerceAtLeast(0L)
        // 사용자가 정지 버튼으로 turn 을 끝낸 시점은 단순 duration 저장보다 의미가 크다.
        // OpenAI Realtime 은 이 호출을 기준으로 input audio commit 을 수행하고,
        // USER transcript 확정 이후 AI response 를 시작한다.
        phoneChatSessionController.endUserTurn(SessionOwner.PHONE, durationMs)
    }

    /**
     * 로컬 재생 버퍼에 쌓인 출력 오디오 길이를 누적해 실제 재생 tail을 추정합니다.
     */

    /**
     * 자막 토클
     * */
    fun toggleSubtitle() {
        _uiState.update { it.copy(showSubtitle = !it.showSubtitle) }
    }

    // ChatScreen 진입 시 호출
    fun checkInterestTopics() {
        viewModelScope.launch {
            val uid = getCurrentUserUidUseCase.getCurrentUserUid() ?: return@launch
            val nickname = getUserNicknameUseCase(uid).orEmpty()
            if (nickname.isNotBlank()) {
                _uiState.update { it.copy(userNickname = nickname) }
            }
            val profile = getUserProfileUseCase(uid) ?: return@launch

            if (profile.interestTopics.isEmpty()) {
                _uiState.update { it.copy(showTopicDialog = true) }
            }
        }
    }


    // 관심 주제 다이얼로그에서 선택/해제
    fun toggleTopic(topic: Topic) {
        // 저장 중에는 사용자가 선택 목록을 바꿔 저장 요청의 입력과 화면 상태가 어긋나지 않게 한다.
        if (_uiState.value.isTopicSaving) return

        // 현재 선택 목록을 복사해서 immutable UiState 를 직접 수정하지 않고 새 상태로 교체한다.
        val current = _uiState.value.selectedTopic.toMutableList()
        // 이미 선택된 주제를 다시 누르면 선택 해제로 처리한다.
        if (current.contains(topic)) {
            current.remove(topic)
        // 최대 5개까지만 추가해 저장 정책과 UI 선택 가능 범위를 맞춘다.
        } else if (current.size < 5) {
            current.add(topic)
        }
        _uiState.update {
            it.copy(
                // 변경된 선택 목록을 UiState 에 반영해 회전 후에도 같은 상태를 렌더링한다.
                selectedTopic = current,
                // 5개를 채운 순간 이전의 "5개 선택 필요" 오류를 즉시 내려 UI가 성공 조건과 모순되지 않게 한다.
                topicError = if (current.size == REQUIRED_TOPIC_COUNT) null else it.topicError
            )
        }
    }


    fun saveInterestTopics() {
        viewModelScope.launch {
            // 저장 버튼 연타나 recomposition 중복 호출이 같은 관심주제 저장 요청을 여러 번 만들지 않게 한다.
            if (_uiState.value.isTopicSaving) return@launch

            // 현재 로그인 사용자가 없으면 저장 대상이 없으므로 요청을 만들지 않는다.
            val uid = getCurrentUserUidUseCase.getCurrentUserUid() ?: return@launch
            // 도메인 저장 계약은 Topic enum name 목록을 받으므로 화면 선택값을 name 으로 변환한다.
            val topics = _uiState.value.selectedTopic.map { it.name }

            // 버튼 비활성화가 기본 방어지만, 외부 호출/상태 경합에 대비해 ViewModel 에서도 5개 정책을 다시 검증한다.
            if (topics.size != REQUIRED_TOPIC_COUNT) {
                _uiState.update { it.copy(topicError = "주제를 정확히 5개 선택해 주세요.") }
                return@launch
            }
            // 저장 진행 중에는 버튼/선택 변경을 막기 위해 loading 상태를 먼저 올린다.
            _uiState.update { it.copy(isTopicSaving = true) }
            // 실제 사용자 프로필의 관심주제를 저장하는 기존 usecase 계약을 그대로 사용한다.
            val result = saveInterestTopicsUseCase(uid, topics)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        // 저장이 끝났으므로 loading 을 내린다.
                        isTopicSaving = false,
                        // 저장 성공 후에는 필수 설정 단계가 완료되었으므로 다이얼로그를 닫는다.
                        showTopicDialog = false,
                        // 성공 상태에서 이전 안내/오류 문구가 남지 않게 정리한다.
                        topicError = null
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        // 실패해도 사용자가 다시 시도할 수 있도록 저장 중 상태는 해제한다.
                        isTopicSaving = false,
                        // 실패 사유는 같은 안내 영역에 보여 사용자가 현재 상태를 이해할 수 있게 한다.
                        topicError = "저장에 실패했습니다."
                    )
                }
            }
        }
    }

    private fun toUserFacingErrorMessage(rawMessage: String?, fallback: String): String {
        val normalized = rawMessage.orEmpty().lowercase()
        if (normalized.isBlank()) return fallback

        val isNetworkRelated = normalized.contains("network") ||
            normalized.contains("offline") ||
            normalized.contains("timeout") ||
            normalized.contains("timed out") ||
            normalized.contains("socket") ||
            normalized.contains("ioexception") ||
            normalized.contains("unable to resolve host") ||
            normalized.contains("connection")

        return if (isNetworkRelated) {
            "네트워크가 불안정해 연결이 끊겼습니다. 네트워크 상태를 확인한 뒤 다시 시도해 주세요."
        } else {
            fallback
        }
    }

    private fun buildEntryMessageForNewSession(
        reason: NewSessionReason,
        targetLang: LangCode?
    ): String? {
        return when (reason) {
            NewSessionReason.LANG_CHANGED -> {
                val languageName = targetLang?.toDisplayName() ?: "선택한 언어"
                "$languageName 세션으로 전환 중..."
            }
            NewSessionReason.NO_ACTIVE_SESSION,
            NewSessionReason.RESTORE_UNAVAILABLE -> null
        }
    }

    private fun LangCode.toDisplayName(): String {
        return when (this) {
            LangCode.KO -> "한국어"
            LangCode.EN -> "영어"
            LangCode.JA -> "일본어"
            LangCode.DE -> "독일어"
            else -> code.uppercase()
        }
    }

    private fun watchStatusToAiState(status: com.app.umma.watchbridge.contract.WatchChatStatus): AIState {
        return when (status) {
            com.app.umma.watchbridge.contract.WatchChatStatus.RECORDING -> AIState.LISTENING
            com.app.umma.watchbridge.contract.WatchChatStatus.THINKING -> AIState.THINKING
            com.app.umma.watchbridge.contract.WatchChatStatus.SPEAKING -> AIState.SPEAKING
            com.app.umma.watchbridge.contract.WatchChatStatus.RECONNECTING -> AIState.RECONNECTING
            com.app.umma.watchbridge.contract.WatchChatStatus.ERROR -> AIState.ERROR
            else -> AIState.IDLE
        }
    }

    private companion object {
        const val TAG = "ChatViewModel"
        const val DIAG_TAG = "AiChatPlayback"
        const val REQUIRED_TOPIC_COUNT = 5
        const val CHAT_USAGE_PRICING_VERSION = "openai-realtime-2026-05"

        fun buildSessionMemoryKey(uid: String, lang: LangCode): String = "${uid}_${lang.code}"
    }
}

/**
 * 같은 final event가 화면 말풍선과 저장 요청에 중복 반영되지 않도록 판단합니다.
 *
 * lastHandledFinalTurnId는 기존 즉시 중복 방어를 유지하고, handledFinalTurnIds는
 * 역할별 최신 말풍선 교체 이후에도 과거 turnId를 기억해 늦게 재전달된 final 이벤트를 막는다.
 */
private fun ChatUiState.hasHandledFinalTurn(turnId: String): Boolean {
    return lastHandledFinalTurnId == turnId || handledFinalTurnIds.contains(turnId)
}

/**
 * 현재 화면에는 전체 subtitle history가 아니라 역할별 최신 final 자막만 유지합니다.
 *
 * 새 USER final이 오면 이전 USER 말풍선을 교체하고, 새 AI final이 오면 이전 AI 말풍선을 교체한다.
 * 남은 다른 역할의 말풍선은 그대로 둔 뒤 새 항목을 뒤에 붙여, 화면에는 최신 두 역할의 도착 순서만 남긴다.
 */
private fun List<ChatSubtitleItem>.replaceLatestRoleSubtitle(
    item: ChatSubtitleItem
): List<ChatSubtitleItem> {
    return filterNot { it.role == item.role }.plus(item)
}

/**
 * realtime final event를 현재 화면 전용 말풍선 모델로 변환합니다.
 *
 * 저장 모델인 SessionTurn과 분리해 두면 화면 디자인 요구가 바뀌어도
 * SessionMemory append 계약이나 correctionAvailable 신호를 함께 수정할 필요가 없다.
 */
private fun AIEvent.FinalTranscription.toSubtitleItem(): ChatSubtitleItem {
    return ChatSubtitleItem(
        id = turnId,
        role = role,
        text = text
    )
}

/**
 * 새 세션 시작이 사용자에게 "복구/전환 완료"로 안내되어야 하는지 결정합니다.
 *
 * RetryConnectionUseCase 는 첫 진입에서도 NO_ACTIVE_SESSION 을 반환할 수 있다.
 * 그래서 모든 새 세션 시작을 fallback 으로 보여주지 않고, 실제 복구 실패나 언어 전환처럼
 * 사용자가 기존 세션에서 다른 세션으로 넘어갔다고 이해해야 하는 경우만 메시지를 만든다.
 */
private fun RetryConnectionResult.toFallbackCompletionMessage(): String? {
    return when (this) {
        is RetryConnectionResult.Failed ->
            "이전 연결 복구에 실패하여 새 대화 세션으로 전환되었습니다."
        is RetryConnectionResult.RequireNewSession -> when (reason) {
            NewSessionReason.LANG_CHANGED ->
                "학습 언어가 변경되어 새 대화 세션으로 전환되었습니다."
            NewSessionReason.RESTORE_UNAVAILABLE ->
                "이전 연결 복구에 실패하여 새 대화 세션으로 전환되었습니다."
            NewSessionReason.NO_ACTIVE_SESSION -> null
        }
        is RetryConnectionResult.Reconnected -> null
    }
}

/**
 * ChatScreen 이 configuration change 로 다시 compose 될 때 기존 화면 상태를 유지할 수 있는지 판단합니다.
 *
 * 정상 READY 세션은 activeSessionId 를 기준으로 보존한다. 다만 회전 중 지연 이벤트 경계에서
 * transient 마이크 상태가 남아 있으면 새 진입 flow 로 덮어쓰지 않아 하단 안내 문구를 유지한다.
 */
private val ChatUiState.shouldKeepChatUiOnReentry: Boolean
    get() = entryStage == ChatEntryStage.READY &&
            (sessionState == SessionState.READY || sessionState == SessionState.RECONNECTING) &&
            (activeSessionId != null || hasTransientMicStatus)

/**
 * 세션 자체는 같지만 화면에 유지해야 하는 순간 상태가 있는지 판단합니다.
 *
 * 같은 sessionId 의 initialized/sessionStarted 이벤트가 늦게 도착해도 이 값이 true 이면
 * 녹음 중, 발화 인식 대기, AI 응답 중 문구를 IDLE 상태로 덮어쓰지 않는다.
 */
private val ChatUiState.hasTransientMicStatus: Boolean
    get() = isRecording ||
            isAwaitingUserTranscript ||
            aiState == AIState.THINKING ||
            aiState == AIState.SPEAKING ||
            aiState == AIState.RECONNECTING
