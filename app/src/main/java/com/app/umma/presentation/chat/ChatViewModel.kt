package com.app.umma.presentation.chat

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.umma.domain.audio.AudioInput
import com.app.umma.domain.audio.AudioOutput
import com.app.umma.domain.model.audio.AudioInputFrame
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.realtime.AIEvent
import com.app.umma.domain.model.realtime.AIState
import com.app.umma.domain.model.realtime.AppendTurnCommand
import com.app.umma.domain.model.realtime.SessionTurn
import com.app.umma.domain.model.user.Topic
import com.app.umma.domain.usecase.auth.GetCurrentUserUidUseCase
import com.app.umma.domain.usecase.chat.ObserveAIEventUseCase
import com.app.umma.domain.usecase.chat.RetryConnectionResult
import com.app.umma.domain.usecase.chat.RetryConnectionUseCase
import com.app.umma.domain.usecase.chat.SendAudioDataUseCase
import com.app.umma.domain.usecase.chat.StartSessionUseCase
import com.app.umma.domain.usecase.chat.StopSessionUseCase
import com.app.umma.domain.usecase.realtime.AppendTurnUseCase
import com.app.umma.domain.usecase.user.GetUserProfileUseCase
import com.app.umma.domain.usecase.user.SaveInterestTopicsUseCase
import com.app.umma.presentation.util.calculateLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val stopSessionUseCase: StopSessionUseCase,
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val saveInterestTopicsUseCase: SaveInterestTopicsUseCase,
    private val getCurrentUserUidUseCase: GetCurrentUserUidUseCase,
    private val appendTurnUseCase: AppendTurnUseCase,
    private val audioRecorder: AudioInput,
    private val audioPlayer: AudioOutput
) : ViewModel() {

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

    /**
     * 채팅 세션을 시작합니다.
     */
    fun startChat() {
        viewModelScope.launch {
            if (_uiState.value.sessionState == SessionState.LOADING) return@launch

            startObservingAIEvents()

            _uiState.update {
                it.copy(
                    sessionState = SessionState.LOADING,
                    showSubtitle = false,
                    errorMessage = null
                )
            }

            startSessionUseCase()
                .onSuccess { sessionId ->
                    handleSessionStarted(sessionId)
                    audioPlayer.startPlaying()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            sessionState = SessionState.ERROR,
                            aiState = AIState.ERROR,
                            errorMessage = error.message ?: "대화를 시작할 수 없습니다."
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
            _uiState.update {
                it.copy(
                    microphonePermissionDenied = true,
                    errorMessage = "마이크 권한이 필요합니다."
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                microphonePermissionDenied = false,
                errorMessage = null
            )
        }

        beginUserTurn()
    }

    /**
     * 사용자 발화 turn 녹음을 시작합니다.
     */
    @SuppressLint("MissingPermission")
    private fun beginUserTurn() {
        val currentState = _uiState.value
        if (currentState.sessionState != SessionState.READY) return
        if (recordJob?.isActive == true) return

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
                _uiState.update {
                    it.copy(
                        isRecording = false,
                        inputLevel = 0f,
                        sessionState = SessionState.ERROR,
                        aiState = AIState.ERROR,
                        isRecoverableError = false,
                        errorMessage = error.message ?: "Failed to record audio"
                    )
                }
            }
        }
    }

    /**
     * 같은 앱 세션으로 Live transport 재연결을 수동 재시도합니다.
     */
    fun retryConnection() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
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
                            sessionState = SessionState.ERROR,
                            aiState = AIState.ERROR,
                            isRecoverableError = true,
                            didFallbackToNewSession = false,
                            fallbackMessage = null,
                            errorMessage = result.message
                        )
                    }
                }

                is RetryConnectionResult.RequireNewSession -> {
                    fallbackToNewSession(result.reason)
                }
            }
        }
    }


    /**
     * 같은 앱 세션 복구가 불가능할 때 새 세션으로 전환합니다.
     * */
    private suspend fun fallbackToNewSession(reason: String) {
        stopSessionUseCase()

        _uiState.update {
            it.copy(
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
                        sessionState = SessionState.ERROR,
                        aiState = AIState.ERROR,
                        isRecoverableError = false,
                        didFallbackToNewSession = false,
                        fallbackMessage = null,
                        errorMessage = error.message ?: reason
                    )
                }
            }
    }

    /**
     * 현재 사용자 발화 turn 녹음을 종료합니다.
     */
    fun endUserTurn() {
        if (!_uiState.value.canEndUserTurn) return

        recordJob?.cancel()
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
        viewModelScope.launch {
            eventJob?.cancel()
            eventJob = null

            recordJob?.cancel()
            recordJob = null

            audioPlayer.stopPlaying()
            stopSessionUseCase()

            _uiState.value = ChatUiState()
            pendingTurnSaveCount = 0
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
        if (event.text.isBlank()) return
        if (_uiState.value.lastHandledFinalTurnId == event.turnId) return

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
        _uiState.update {
            it.copy(outputLevel = calculateLevel(event.audio))
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
        sendAudioDataUseCase(frame.pcm)
    }

    /**
     * AI 상태 변화를 반영합니다.
     *
     * @param event 상태 변화 이벤트
     */
    private fun handleStateChanged(event: AIEvent.StateChanged) {
        _uiState.update {
            it.copy(
                aiState = event.state,
                outputLevel = if (event.state == AIState.IDLE) 0f else it.outputLevel
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
                errorMessage = event.message
            )
        }
    }

    /**
     * Live transport 재연결 완료 상태를 반영합니다.
     *
     * @param event 재연결 완료 이벤트
     */
    private fun handleReconnected(event: AIEvent.Reconnected) {
        _uiState.update {
            it.copy(
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
        recordJob?.cancel()
        recordJob = null

        _uiState.update {
            it.copy(
                isRecording = false,
                inputLevel = 0f,
                outputLevel = 0f,
                userPartialTranscript = "",
                aiPartialTranscript = "",
                sessionState = SessionState.ERROR,
                aiState = AIState.ERROR,
                isRecoverableError = event.recoverable,
                fallbackMessage = null,
                errorMessage = event.message
            )
        }
    }

    /**
     * 일반 오류 상태를 반영합니다.
     *
     * @param event 오류 이벤트
     */
    private fun handleError(event: AIEvent.Error) {
        recordJob?.cancel()
        recordJob = null

        _uiState.update {
            it.copy(
                isRecording = false,
                inputLevel = 0f,
                outputLevel = 0f,
                sessionState = SessionState.ERROR,
                aiState = AIState.ERROR,
                isRecoverableError = false,
                fallbackMessage = null,
                errorMessage = event.message
            )
        }
    }

    /**
     * ViewModel 종료 시 오디오 플레이어 리소스를 해제합니다.
     */
    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }

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
            val profile = getUserProfileUseCase(uid) ?: return@launch

            if (profile.interestTopics.isEmpty()) {
                _uiState.update { it.copy(showTopicDialog = true) }
            }
        }
    }


    // 관심 주제 다이얼로그에서 선택/해제
    fun toggleTopic(topic: Topic) {
        val current = _uiState.value.selectedTopic.toMutableList()
        if (current.contains(topic)) {
            current.remove(topic)
        } else if (current.size < 5) {
            current.add(topic)
        }
        _uiState.update { it.copy(selectedTopic = current) }
    }


    fun saveInterestTopics() {
        viewModelScope.launch {
            val uid = getCurrentUserUidUseCase.getCurrentUserUid() ?: return@launch
            val topics = _uiState.value.selectedTopic.map { it.name }

            // 5개 미선택 시 저장 X
            if (topics.size != 5) {
                _uiState.update { it.copy(topicError = "주제를 정확히 5개 선택해 주세요.") }
                return@launch
            }
            _uiState.update { it.copy(isTopicSaving = true) }
            val result = saveInterestTopicsUseCase(uid, topics)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isTopicSaving = false,
                        showTopicDialog = false,
                        topicError = null
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isTopicSaving = false,
                        topicError = "저장에 실패했습니다."
                    )
                }
            }
        }
    }
}
// 재시도 commit
