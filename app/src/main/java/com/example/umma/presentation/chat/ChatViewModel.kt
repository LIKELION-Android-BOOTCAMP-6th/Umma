package com.example.umma.presentation.chat

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.umma.data.source.local.AudioPlayer
import com.example.umma.data.source.local.AudioRecorder
import com.example.umma.domain.model.realtime.AIEvent
import com.example.umma.domain.model.realtime.AIState
import com.example.umma.domain.usecase.chat.ObserveAIEventUseCase
import com.example.umma.domain.usecase.chat.SendAudioDataUseCase
import com.example.umma.domain.usecase.chat.StartSessionUseCase
import com.example.umma.domain.usecase.chat.StopSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AI와의 실시간 음성 대화를 관리하는 ViewModel입니다.
 *
 * RT-001(세션 부트스트랩) 및 RT-002(음성 스트리밍) 인프라를 바탕으로
 * 세션의 생명주기 관리, AI 이벤트 수신 루프 실행, 오디오 입출력 제어를 담당합니다.
 *
 * @property startSessionUseCase 세션을 초기화하고 연결하는 유스케이스
 * @property observeAIEventUseCase AI로부터 발생하는 실시간 이벤트를 관찰하는 유스케이스
 * @property sendAudioDataUseCase 녹음된 오디오 데이터를 서버로 전송하는 유스케이스
 * @property stopSessionUseCase 세션을 안전하게 종료하는 유스케이스
 * @property audioRecorder 사용자 음성을 16k PCM 데이터로 수집하는 레코더
 * @property audioPlayer AI 응답 음성을 재생하는 플레이어
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val startSessionUseCase: StartSessionUseCase,
    private val observeAIEventUseCase: ObserveAIEventUseCase,
    private val sendAudioDataUseCase: SendAudioDataUseCase,
    private val stopSessionUseCase: StopSessionUseCase,
    private val audioRecorder: AudioRecorder,
    private val audioPlayer: AudioPlayer
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState = _uiState.asStateFlow()

    private var chatJob: Job? = null

    /**
     * AI Chat 세션을 시작하고 관련 인프라를 초기화합니다.
     *
     * 1. 현재 세션 상태가 [SessionState.LOADING]인 경우 중복 호출을 방지합니다.
     * 2. [startSessionUseCase]를 통해 Firebase AI Logic 연결을 시도합니다.
     * 3. 연결 성공 시 [startChatLoop]를 호출하여 실시간 이벤트 관찰을 시작합니다.
     */
    fun startChat() {
        viewModelScope.launch {
            if (_uiState.value.sessionState == SessionState.LOADING) return@launch

            _uiState.update { it.copy(sessionState = SessionState.LOADING, errorMessage = null) }

            startSessionUseCase()
                .onSuccess {
                    // 세션 연결 성공 시 AI 이벤트 수신 루프를 시작합니다.
                    startChatLoop()
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            sessionState = SessionState.ERROR,
                            errorMessage = e.message ?: "대화를 시작할 수 없습니다"
                        )
                    }
                }
        }
    }

    /**
     * AI 서버로부터의 이벤트 수신과 사용자 오디오 전송 루프를 실행합니다.
     *
     * [AIEvent] 스트림을 구독하여 UI 상태를 갱신하거나 오디오를 재생하며,
     * 동시에 [AudioRecorder]로부터 수집된 오디오 데이터를 서버로 스트리밍합니다.
     * 이 작업은 [chatJob]으로 관리되며 세션 종료 시 취소됩니다.
     */
    @SuppressLint("MissingPermission")
    private fun startChatLoop() {
        chatJob = viewModelScope.launch {
            // [Coroutine 1] AI 서버로부터 실시간 이벤트를 수신하는 루프입니다.
            launch {
                observeAIEventUseCase().collect { event ->
                    when (event) {
                        is AIEvent.Initialized -> {
                            // 세션 ID가 확정되면 UI를 준비 완료(READY) 상태로 변경합니다.
                            _uiState.update {
                                it.copy(
                                    sessionState = SessionState.READY,
                                    activeSessionId = event.sessionId
                                )

                            }
                        }
                        is AIEvent.Initializing -> {
                            // 세션 초기화가 진행 중임을 UI에 알립니다.
                            _uiState.update {
                                it.copy(
                                    sessionState = SessionState.LOADING
                                )
                            }
                        }
                        is AIEvent.AudioResponse -> {
                            // AI가 보내온 PCM 오디오 데이터를 플레이어를 통해 재생합니다.
                            audioPlayer.playAudioChunk(event.audio)
                        }
                        is AIEvent.PartialTranscription -> {
                            // 실시간 음성인식 도중의 부분 자막을 UI에 노출합니다.
                            _uiState.update { it.copy(lastTranscription = event.text!!) }
                        }
                        is AIEvent.FinalTranscription -> {
                            // 확정된 자막을 UI에 표시합니다 (정책: CHAT-003).
                            _uiState.update { it.copy(lastTranscription = event.text) }
                        }
                        is AIEvent.StateChanged -> {
                            // AI의 현재 상태(생각 중, 듣는 중 등)를 반영합니다.
                            _uiState.update { it.copy(aiState = event.state) }
                        }
                        is AIEvent.Error -> {
                            // 통신 중 발생한 런타임 오류를 처리합니다.
                            _uiState.update { it.copy(
                                sessionState = SessionState.ERROR,
                                errorMessage = event.message
                            )}
                        }
                    }
                }
            }

            // [Coroutine 2] 사용자 마이크로부터 수집된 오디오 데이터를 서버로 스트리밍합니다.
            launch {
                audioRecorder.startRecording().collect { audioChunk ->
                    // 16k PCM 청크 데이터를 실시간으로 전송합니다.
                    sendAudioDataUseCase(audioChunk)
                }
            }
        }
    }

    /**
     * 현재 진행 중인 모든 대화 루프를 중단하고 세션을 안전하게 종료합니다.
     *
     * 실행 중인 코루틴 Job을 취소하고, [stopSessionUseCase]를 통해 원격 세션을 닫습니다.
     * 모든 재생기 및 UI 상태를 초기화(IDLE)하여 재연결 가능한 상태로 만듭니다.
     */
    fun stopChat() {
        viewModelScope.launch {
            // 실행 중인 이벤트 수신 및 녹음 Job을 즉시 중단합니다.
            chatJob?.cancel()
            // 원격 세션 연결을 해제하고 인프라 리소스를 정리합니다.
            stopSessionUseCase()
            // 현재 재생 중인 음성이 있다면 중지합니다.
            audioPlayer.stopPlaying()
            // UI 상태를 초기 대기 상태(IDLE)로 리셋합니다.
            _uiState.update { it.copy(
                sessionState = SessionState.IDLE,
                aiState = AIState.IDLE,
                activeSessionId = null
            )}
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}