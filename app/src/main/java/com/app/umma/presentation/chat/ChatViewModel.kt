package com.app.umma.presentation.chat

import android.annotation.SuppressLint
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.umma.core.util.NetworkConnectivityMonitor
import com.app.umma.domain.audio.AudioInput
import com.app.umma.domain.audio.AudioOutput
import com.app.umma.domain.model.audio.AudioInputFrame
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateInput
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.realtime.AIEvent
import com.app.umma.domain.model.realtime.AIState
import com.app.umma.domain.model.realtime.AppendTurnCommand
import com.app.umma.domain.model.realtime.SessionTurn
import com.app.umma.domain.usecase.learningstate.ApplyCorrectionSignalUpdateUseCase
import com.app.umma.domain.model.user.Topic
import com.app.umma.domain.usecase.auth.GetCurrentUserUidUseCase
import com.app.umma.domain.usecase.chat.ObserveAIEventUseCase
import com.app.umma.domain.usecase.chat.NewSessionReason
import com.app.umma.domain.usecase.chat.RetryConnectionResult
import com.app.umma.domain.usecase.chat.RetryConnectionUseCase
import com.app.umma.domain.usecase.chat.SendAudioDataUseCase
import com.app.umma.domain.usecase.chat.SetPendingUserTurnDurationUseCase
import com.app.umma.domain.usecase.chat.StartSessionUseCase
import com.app.umma.domain.usecase.chat.StopSessionUseCase
import com.app.umma.domain.usecase.realtime.AppendTurnUseCase
import com.app.umma.domain.usecase.user.GetUserProfileUseCase
import com.app.umma.domain.usecase.user.GetUserNicknameUseCase
import com.app.umma.domain.usecase.user.SaveInterestTopicsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    private val startSessionUseCase: StartSessionUseCase,
    private val retryConnectionUseCase: RetryConnectionUseCase,
    private val observeAIEventUseCase: ObserveAIEventUseCase,
    private val sendAudioDataUseCase: SendAudioDataUseCase,
    private val setPendingUserTurnDurationUseCase: SetPendingUserTurnDurationUseCase,
    private val stopSessionUseCase: StopSessionUseCase,
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val getUserNicknameUseCase: GetUserNicknameUseCase,
    private val saveInterestTopicsUseCase: SaveInterestTopicsUseCase,
    private val getCurrentUserUidUseCase: GetCurrentUserUidUseCase,
    private val appendTurnUseCase: AppendTurnUseCase,
    private val applyCorrectionSignalUpdateUseCase: ApplyCorrectionSignalUpdateUseCase,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor,
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
    private var outputLevelJob: Job? = null
    private var currentUserTurnStartedAtMs: Long? = null
    private var currentUserTurnEndedAtMs: Long? = null

    init {
        observeAudioOutputLevel()
    }

    /**
     * 채팅 세션을 시작합니다.
     */
    fun enterChat() {
        if (enterChatJob?.isActive == true) return
        if (_uiState.value.hasActiveChatSession) {
            // 화면 회전 후 LaunchedEffect가 다시 실행되어도, 이미 준비된 세션과 화면 상태는
            // 그대로 유지한다. 여기서 loading 상태를 다시 쓰면 subtitle/final text가 초기화된다.
            startObservingAIEvents()
            return
        }

        enterChatJob = viewModelScope.launch {
            Log.d(TAG, "enterChat started")
            stopChatJob?.join()
            startObservingAIEvents()

            _uiState.update {
                it.copy(
                    entryStage = ChatEntryStage.GUARDING,
                    blockedReason = null,
                    entryMessageOverride = null,
                    sessionState = SessionState.LOADING,
                    aiState = AIState.IDLE,
                    showSubtitle = false,
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

            _uiState.update {
                it.copy(
                    entryStage = ChatEntryStage.RESTORING,
                    blockedReason = null,
                    entryMessageOverride = null,
                    sessionState = SessionState.LOADING,
                    aiState = AIState.RECONNECTING,
                    isRecoverableError = false,
                    errorMessage = null
                )
            }
            delay(entryStageDelayMs)
            val restoreResult = retryConnectionUseCase()
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

            startSessionUseCase()
                .onSuccess { sessionId ->
                    handleSessionStarted(sessionId)
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
    private fun handleSessionStarted(sessionId: String) {
        _uiState.update {
            it.copy(
                entryStage = ChatEntryStage.READY,
                blockedReason = null,
                entryMessageOverride = null,
                sessionState = SessionState.READY,
                aiState = AIState.IDLE,
                activeSessionId = sessionId,
                didFallbackToNewSession = false,
                fallbackMessage = null,
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
        setPendingUserTurnDurationUseCase(null)

        _uiState.update {
            it.copy(
                isRecording = true,
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
                inputLevel = 0f
            )
        }
    }

    /**
     * 같은 앱 세션으로 Live transport 재연결을 수동 재시도합니다.
     */
    fun retryConnection() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    entryStage = ChatEntryStage.RESTORING,
                    blockedReason = null,
                    entryMessageOverride = null,
                    sessionState = SessionState.RECONNECTING,
                    aiState = AIState.RECONNECTING,
                    isRecoverableError = false,
                    didFallbackToNewSession = false,
                    fallbackMessage = null,
                    errorMessage = null
                )
            }

            when (val result = retryConnectionUseCase()) {
                is RetryConnectionResult.Reconnected -> {
                    _uiState.update {
                        it.copy(
                            entryStage = ChatEntryStage.READY,
                            blockedReason = null,
                            entryMessageOverride = null,
                            sessionState = SessionState.READY,
                            aiState = AIState.IDLE,
                            activeSessionId = result.sessionId,
                            reconnectAttempt = 0,
                            maxReconnectAttempts = 0,
                            isRecoverableError = false,
                            didFallbackToNewSession = false,
                            fallbackMessage = null,
                            errorMessage = null
                        )
                    }
                }

                is RetryConnectionResult.Failed -> {
                    _uiState.update {
                        it.copy(
                            entryStage = ChatEntryStage.ERROR,
                            blockedReason = null,
                            entryMessageOverride = null,
                            sessionState = SessionState.ERROR,
                            aiState = AIState.ERROR,
                            isRecoverableError = true,
                            didFallbackToNewSession = false,
                            fallbackMessage = null,
                            errorMessage = toUserFacingErrorMessage(
                                rawMessage = result.message,
                                fallback = "연결이 불안정합니다. 다시 시도해 주세요."
                            )
                        )
                    }
                }

                is RetryConnectionResult.RequireNewSession -> {
                    fallbackToNewSession(
                        reason = result.reason,
                        targetLang = result.targetLang
                    )
                }
            }
        }
    }


    /**
     * 같은 앱 세션 복구가 불가능할 때 새 세션으로 전환합니다.
     * */
    private suspend fun fallbackToNewSession(
        reason: NewSessionReason,
        targetLang: LangCode?
    ) {
        Log.w(TAG, "fallbackToNewSession reason=$reason, targetLang=${targetLang?.code}")
        stopSessionUseCase()

        _uiState.update {
            it.copy(
                entryStage = ChatEntryStage.STARTING_NEW,
                blockedReason = null,
                entryMessageOverride = buildEntryMessageForNewSession(reason, targetLang),
                sessionState = SessionState.LOADING,
                aiState = AIState.IDLE,
                isRecoverableError = false,
                didFallbackToNewSession = false,
                fallbackMessage = null,
                errorMessage = null
            )
        }

        startSessionUseCase()
            .onSuccess { sessionId ->
                audioPlayer.startPlaying()
                _uiState.update {
                    it.copy(
                        entryStage = ChatEntryStage.READY,
                        blockedReason = null,
                        entryMessageOverride = null,
                        sessionState = SessionState.READY,
                        aiState = AIState.IDLE,
                        activeSessionId = sessionId,
                        reconnectAttempt = 0,
                        maxReconnectAttempts = 0,
                        isRecoverableError = false,
                        didFallbackToNewSession = true,
                        fallbackMessage = "이전 연결 복구에 실패하여 새 대화 세션으로 전환되었습니다.",
                        errorMessage = null
                    )
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        entryStage = ChatEntryStage.ERROR,
                        blockedReason = ChatBlockedReason.UNRECOVERABLE,
                        entryMessageOverride = null,
                        sessionState = SessionState.ERROR,
                        aiState = AIState.ERROR,
                        isRecoverableError = false,
                        didFallbackToNewSession = false,
                        fallbackMessage = null,
                        errorMessage = toUserFacingErrorMessage(
                            rawMessage = error.message,
                            fallback = "세션을 다시 시작할 수 없습니다. 잠시 후 다시 시도해 주세요."
                        )
                    )
                }
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
                inputLevel = 0f
            )
        }
    }

    /**
     * 현재 chat 세션을 종료하고 상태를 초기화합니다.
     */
    fun stopChat() {
        stopChatJob?.cancel()
        stopChatJob = viewModelScope.launch {
            stopChatInternal(resetUiState = true)
        }
    }

    /**
     * Chat 화면 리소스를 정리합니다.
     *
     * 화면 회전에서는 [ChatScreen]이 dispose되더라도 같은 [ChatViewModel]을 재사용할 수 있으므로
     * 이 함수를 호출하지 않는다. navigation 이탈처럼 화면이 실제로 사라지는 경우에는 [stopChat]이,
     * ViewModel 자체가 제거되는 경우에는 onCleared가 호출해 녹음/재생/Live transport를 정리한다.
     */
    private suspend fun stopChatInternal(resetUiState: Boolean) {
        eventJob?.cancel()
        eventJob = null

        recordJob?.cancel()
        audioRecorder.stopRecording()
        recordJob = null

        currentUserTurnStartedAtMs = null
        currentUserTurnEndedAtMs = null
        setPendingUserTurnDurationUseCase(null)
        audioPlayer.stopPlaying()
        stopSessionUseCase(clearAppSession = false)

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
            observeAIEventUseCase().collect { event ->
                when (event) {
                    is AIEvent.Initializing -> handleInitializing()
                    is AIEvent.Initialized -> handleInitialized(event)
                    is AIEvent.PartialTranscription -> handlePartialTranscription(event)
                    is AIEvent.FinalTranscription -> handleFinalTranscription(event)
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
            it.copy(
                entryStage = ChatEntryStage.READY,
                blockedReason = null,
                entryMessageOverride = null,
                sessionState = SessionState.READY,
                aiState = AIState.IDLE,
                activeSessionId = event.sessionId,
                reconnectAttempt = 0,
                maxReconnectAttempts = 0,
                isRecoverableError = false,
                didFallbackToNewSession = false,
                fallbackMessage = null,
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
        if (_uiState.value.lastHandledFinalTurnId == event.turnId) {
            return
        }

        _uiState.update {
            when (event.role) {
                TurnSpeaker.USER -> it.copy(
                    userPartialTranscript = "",
                    lastFinalUserTranscript = event.text,
                    lastHandledFinalTurnId = event.turnId
                )

                TurnSpeaker.AI -> it.copy(
                    aiPartialTranscript = "",
                    lastFinalAITranscript = event.text,
                    lastHandledFinalTurnId = event.turnId
                )
            }
        }

        persistFinalTurn(event)
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
                aiState = if (
                    it.aiState != AIState.RECONNECTING &&
                    it.aiState != AIState.ERROR
                ) AIState.SPEAKING else it.aiState,
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
            sendAudioDataUseCase(frame.pcm)
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
            stopRecordingForAiSpeaking()
        }

        _uiState.update {
            it.copy(
                aiState = event.state
            )
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
                inputLevel = 0f,
                outputLevel = 0f,
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
     * Live transport 재연결 완료 상태를 반영합니다.
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
                inputLevel = 0f,
                outputLevel = 0f,
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
                inputLevel = 0f,
                outputLevel = 0f,
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
        // onCleared는 Chat back stack이 제거되는 실제 종료 경계다. 화면 회전과 달리
        // 여기서는 transport와 오디오 리소스를 정리해야 다음 진입 시 잔여 녹음/재생이 남지 않는다.
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

    private fun captureCurrentUserTurnDuration() {
        val startedAt = currentUserTurnStartedAtMs ?: return
        if (currentUserTurnEndedAtMs == null) {
            currentUserTurnEndedAtMs = System.currentTimeMillis()
        }
        val endedAt = currentUserTurnEndedAtMs ?: return
        val durationMs = (endedAt - startedAt).coerceAtLeast(0L)
        setPendingUserTurnDurationUseCase(durationMs)
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

    private fun resolveBlockedReason(message: String): ChatBlockedReason {
        val normalized = message.lowercase()
        return if (
            normalized.contains("preference") ||
            normalized.contains("selected") ||
            normalized.contains("language") ||
            normalized.contains("lang")
        ) {
            ChatBlockedReason.MISSING_LANG
        } else {
            ChatBlockedReason.UNRECOVERABLE
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
            else -> code.uppercase()
        }
    }

    private companion object {
        const val TAG = "ChatViewModel"
        const val REQUIRED_TOPIC_COUNT = 5

        fun buildSessionMemoryKey(uid: String, lang: LangCode): String = "${uid}_${lang.code}"
    }
}

private val ChatUiState.hasActiveChatSession: Boolean
    get() = entryStage == ChatEntryStage.READY &&
            (sessionState == SessionState.READY || sessionState == SessionState.RECONNECTING) &&
            activeSessionId != null
// 재시도 commit
