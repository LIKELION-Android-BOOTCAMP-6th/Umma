package com.example.umma.presentation.chat

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.umma.presentation.util.calculateLevel
import com.example.umma.data.source.local.AudioPlayer
import com.example.umma.data.source.local.AudioRecorder
import com.example.umma.domain.model.audio.AudioInputFrame
import com.example.umma.domain.model.learningstate.TurnSpeaker
import com.example.umma.domain.model.realtime.AIEvent
import com.example.umma.domain.model.realtime.AIState
import com.example.umma.domain.model.user.Topic
import com.example.umma.domain.usecase.auth.GetCurrentUserUidUseCase
import com.example.umma.domain.usecase.chat.ObserveAIEventUseCase
import com.example.umma.domain.usecase.chat.SendAudioDataUseCase
import com.example.umma.domain.usecase.chat.StartSessionUseCase
import com.example.umma.domain.usecase.chat.StopSessionUseCase
import com.example.umma.domain.usecase.user.GetUserProfileUseCase
import com.example.umma.domain.usecase.user.SaveInterestTopicsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AI Chat의 실시간 음성 대화 상태를 관리하는 ViewModel입니다.
 *
 * 이 ViewModel은 세션 lifecycle, 사용자 녹음 lifecycle, AI 이벤트 소비,
 * 입력/출력 오디오 강도 상태를 하나의 presentation state로 조합합니다.
 *
 * 주요 책임:
 * - AI 세션 시작/종료
 * - 사용자 turn 녹음 시작/종료
 * - 서버 실시간 이벤트 구독
 * - partial / final transcript 상태 반영
 * - 입력 레벨(inputLevel), 출력 레벨(outputLevel) UI 상태 반영
 * - interruption / error 상태 surface
 *
 * @property startSessionUseCase 실시간 AI 세션을 초기화하고 시작하는 유스케이스
 * @property observeAIEventUseCase AI 서버에서 발생하는 실시간 이벤트 스트림을 구독하는 유스케이스
 * @property sendAudioDataUseCase 녹음된 PCM 오디오 데이터를 서버로 전송하는 유스케이스
 * @property stopSessionUseCase 현재 활성 세션을 종료하고 정리하는 유스케이스
 * @property audioRecorder 마이크 입력을 수집하고 입력 강도와 함께 전달하는 오디오 입력 추상화
 * @property audioPlayer AI 음성 응답을 재생하는 오디오 출력 추상화
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val startSessionUseCase: StartSessionUseCase,
    private val observeAIEventUseCase: ObserveAIEventUseCase,
    private val sendAudioDataUseCase: SendAudioDataUseCase,
    private val stopSessionUseCase: StopSessionUseCase,
    private val audioRecorder: AudioRecorder,
    private val audioPlayer: AudioPlayer,
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val saveInterestTopicsUseCase: SaveInterestTopicsUseCase,
    private val getCurrentUserUidUseCase: GetCurrentUserUidUseCase

) : ViewModel() {

    /**
     * 화면에서 구독하는 단일 chat UI 상태입니다.
     *
     * 세션 상태, AI 상태, 녹음 여부, transcript, 입력/출력 레벨,
     * 오류 메시지를 함께 제공합니다.
     */
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState = _uiState.asStateFlow()

    /**
     * 사용자 turn 녹음 및 업로드를 담당하는 job입니다.
     *
     * beginUserTurn 호출 시 시작되고,
     * endUserTurn / stopChat / interruption / error 시 종료됩니다.
     */
    private var recordJob: Job? = null

    /**
     * AI 서버 이벤트 스트림을 구독하는 job입니다.
     *
     * 세션 시작 후 생성되며, stopChat 시 취소됩니다.
     */
    private var eventJob: Job? = null

    /**
     * AI 세션을 시작하고 실시간 이벤트 구독을 준비합니다.
     *
     * 동작:
     * 1. 이미 loading 중이면 중복 시작을 무시합니다.
     * 2. 세션 시작 성공 시 AI 이벤트 구독을 시작합니다.
     * 3. 출력 오디오 재생기를 play 상태로 전환합니다.
     *
     * 주의:
     * - 이 메서드는 녹음을 자동으로 시작하지 않습니다.
     * - 실제 사용자 음성 입력은 [beginUserTurn] 호출부터 시작됩니다.
     */
    fun startChat() {
        viewModelScope.launch {
            if (_uiState.value.sessionState == SessionState.LOADING) return@launch

            _uiState.update { it.copy(sessionState = SessionState.LOADING, errorMessage = null) }

            startSessionUseCase()
                .onSuccess {
                    // 세션 연결 성공 시 AI 이벤트 수신 루프를 시작합니다.
                    startObservingAIEvents()
                    audioPlayer.startPlaying()
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            sessionState = SessionState.ERROR,
                            aiState = AIState.ERROR,
                            errorMessage = e.message ?: "대화를 시작할 수 없습니다."
                        )
                    }
                }
        }
    }


    /**
     * 사용자 음성 입력 turn을 시작합니다.
     *
     * 동작:
     * - READY 상태에서만 녹음을 시작합니다.
     * - 이미 녹음 중이면 중복 시작하지 않습니다.
     * - [AudioInputFrame]을 수신할 때마다:
     *   - 입력 강도(inputLevel)를 UI state에 반영하고
     *   - PCM 데이터를 서버로 전송합니다.
     *
     * 오류 처리:
     * - cancellation은 정상 종료로 간주하고 다시 던집니다.
     * - 일반 예외는 ERROR 상태와 메시지로 surface합니다.
     */
    @SuppressLint("MissingPermission")
    private fun beginUserTurn() {
        val currentState = _uiState.value
        currentState.let {
            if (it.sessionState != SessionState.READY) return
            if (it.isRecording) return
        }

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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRecording = false,
                        inputLevel = 0f,
                        sessionState = SessionState.ERROR,
                        aiState = AIState.ERROR,
                        errorMessage = e.message ?: "Failed to record audio"
                    )
                }
            }

        }
    }


    /**
     * 현재 사용자 음성 입력 turn을 종료합니다.
     *
     * 녹음 job을 중단하고 입력 강도 상태를 0으로 초기화합니다.
     * turn 확정 자체는 서버의 final transcript 이벤트를 기준으로 처리됩니다.
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
     * 현재 chat 세션과 관련된 모든 실시간 작업을 종료합니다.
     *
     * 정리 대상:
     * - AI 이벤트 구독 job
     * - 녹음 job
     * - 오디오 재생기
     * - 원격 세션
     * - UI 상태
     *
     * 종료 후 상태는 기본 [ChatUiState]로 초기화됩니다.
     */
    fun stopChat() {
        viewModelScope.launch {
            // 실행 중인 이벤트 수신 및 녹음 Job을 즉시 중단합니다.
            eventJob?.cancel()
            eventJob = null

            recordJob?.cancel()
            recordJob = null

            // 현재 재생 중인 음성이 있다면 중지합니다.
            audioPlayer.stopPlaying()
            // 원격 세션 연결을 해제하고 인프라 리소스를 정리합니다.
            stopSessionUseCase()

            _uiState.value = ChatUiState()
        }
    }


    /**
     * AI 이벤트 스트림 구독을 시작합니다.
     *
     * 이미 구독 중이면 중복 시작하지 않으며,
     * 수신 이벤트를 종류별 handler로 분기해 UI 상태에 반영합니다.
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
     * 세션 초기화 진행 상태를 UI에 반영합니다.
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
     * 세션 준비 완료 상태를 UI에 반영합니다.
     *
     * @param event 새로 시작된 세션 ID를 포함한 초기화 완료 이벤트
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
     * 진행 중인 partial transcript를 role별 상태에 반영합니다.
     *
     * - USER는 [ChatUiState.userPartialTranscript]
     * - AI는 [ChatUiState.aiPartialTranscript]
     *
     * blank 텍스트는 무시합니다.
     *
     * @param event partial transcript 이벤트
     */
    private fun handlePartialTranscription(event: AIEvent.PartialTranscription) {
        if (event.text?.isBlank() ?: true) return

        _uiState.update {
            when (event.role) {
                TurnSpeaker.USER -> it.copy(
                    userPartialTranscript = event.text
                )

                TurnSpeaker.AI -> it.copy(
                    aiPartialTranscript = event.text
                )
            }
        }
    }

    /**
     * 확정된 final transcript를 role별 상태에 반영합니다.
     *
     * 해당 role의 partial transcript는 비우고,
     * 마지막 final transcript 필드를 갱신합니다.
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
    }

    /**
     * AI 오디오 응답을 재생하고 출력 강도(outputLevel)를 갱신합니다.
     *
     * 출력 강도는 PCM 바이트를 기반으로 RMS 방식으로 계산합니다.
     *
     * @param event AI 오디오 응답 이벤트
     */
    private fun handleAudioResponse(event: AIEvent.AudioResponse) {
        _uiState.update {
            it.copy(outputLevel = calculateLevel(event.audio))
        }
        audioPlayer.playAudioChunk(event.audio)
    }

    /**
     * 입력 오디오 프레임을 처리합니다.
     *
     * 동작:
     * - 입력 강도(inputLevel)를 갱신
     * - PCM 데이터를 서버로 전송
     *
     * @param frame 입력 PCM과 강도(level)를 포함한 프레임
     */
    private suspend fun handleAudioInputFrame(frame: AudioInputFrame) {
        _uiState.update {
            it.copy(inputLevel = frame.level)
        }
        sendAudioDataUseCase(frame.pcm)
    }

    /**
     * AI 내부 상태 변화를 UI에 반영합니다.
     *
     * AI 상태가 [AIState.IDLE]로 전환되면 출력 강도는 0으로 초기화합니다.
     *
     * @param event AI 상태 변경 이벤트
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
     * 세션 interruption 상태를 UI에 반영합니다.
     *
     * 현재 녹음을 중단하고 입력 강도를 초기화한 뒤,
     * AI 상태를 RECONNECTING으로 전환합니다.
     *
     * @param event 세션 interruption 이벤트
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
     * 일반 오류 상태를 UI에 반영합니다.
     *
     * 녹음을 중단하고 입력/출력 강도를 초기화한 뒤,
     * 세션 상태와 AI 상태를 ERROR로 전환합니다.
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
     * ViewModel 정리 시 오디오 출력 리소스를 해제합니다.
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


