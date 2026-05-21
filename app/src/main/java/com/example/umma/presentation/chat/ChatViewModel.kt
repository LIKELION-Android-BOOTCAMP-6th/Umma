package com.example.umma.presentation.chat

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.umma.domain.audio.AudioInput
import com.example.umma.domain.audio.AudioOutput
import com.example.umma.domain.model.audio.AudioInputFrame
import com.example.umma.domain.model.learningstate.TurnSpeaker
import com.example.umma.domain.model.realtime.AIEvent
import com.example.umma.domain.model.realtime.AIState
import com.example.umma.domain.model.user.Topic
import com.example.umma.domain.usecase.auth.GetCurrentUserUidUseCase
import com.example.umma.domain.model.realtime.AppendTurnCommand
import com.example.umma.domain.model.realtime.SessionTurn
import com.example.umma.domain.usecase.chat.ObserveAIEventUseCase
import com.example.umma.domain.usecase.chat.SendAudioDataUseCase
import com.example.umma.domain.usecase.chat.StartSessionUseCase
import com.example.umma.domain.usecase.chat.StopSessionUseCase
import com.example.umma.domain.usecase.user.GetUserProfileUseCase
import com.example.umma.domain.usecase.user.SaveInterestTopicsUseCase
import com.example.umma.domain.usecase.realtime.AppendTurnUseCase
import com.example.umma.presentation.util.calculateLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * AI Chat 실시간 대화 상태를 관리하는 ViewModel 입니다.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val startSessionUseCase: StartSessionUseCase,
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

            _uiState.update {
                it.copy(
                    sessionState = SessionState.LOADING,
                    errorMessage = null
                )
            }

            startSessionUseCase()
                .onSuccess {
                    startObservingAIEvents()
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
     * 사용자 발화 turn 녹음을 시작합니다.
     */
    @SuppressLint("MissingPermission")
    private fun beginUserTurn() {
        val currentState = _uiState.value
        if (currentState.sessionState != SessionState.READY) return
        if (currentState.isRecording) return
        if (recordJob?.isActive == true) return

        _uiState.update {
            it.copy(
                isRecording = true,
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
                        errorMessage = error.message ?: "Failed to record audio"
                    )
                }
            }
        }
    }

    /**
     * 현재 사용자 발화 turn 녹음을 종료합니다.
     */
    fun endUserTurn() {
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
     *
     * @param event final transcript 이벤트
     */
    private fun handleFinalTranscription(event: AIEvent.FinalTranscription) {
        if (event.text.isBlank()) return

        _uiState.update {
            when (event.role) {
                TurnSpeaker.USER -> it.copy(
                    userPartialTranscript = "",
                    lastFinalUserTranscript = event.text
                )

                TurnSpeaker.AI -> it.copy(
                    aiPartialTranscript = "",
                    lastFinalAITranscript = event.text
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
                isRecording = false,
                inputLevel = 0f,
                aiState = AIState.RECONNECTING,
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
