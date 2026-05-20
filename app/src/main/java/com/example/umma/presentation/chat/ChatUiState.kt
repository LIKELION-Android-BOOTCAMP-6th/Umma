package com.example.umma.presentation.chat

import com.example.umma.domain.model.realtime.AIState
import com.example.umma.domain.model.user.Topic

/**
 * AI Chat 화면의 UI 상태를 정의하는 데이터 클래스입니다.
 *
 * @property sessionState 전체 세션 연결 상태
 * @property aiState AI 동작 상태
 * @property activeSessionId 현재 활성 세션 ID
 * @property isRecording 현재 녹음 중 여부
 * @property userPartialTranscript 현재 사용자 partial transcript
 * @property aiPartialTranscript 현재 AI partial transcript
 * @property lastFinalUserTranscript 가장 최근 사용자 final transcript
 * @property lastFinalAITranscript 가장 최근 AI final transcript
 * @property inputLevel 입력 오디오 레벨
 * @property outputLevel 출력 오디오 레벨
 * @property isSavingTurn 현재 확정 turn 저장 중 여부
 * @property saveErrorMessage turn 저장 오류 메시지
 * @property errorMessage 세션 오류 메시지
 */
data class ChatUiState(
    val sessionState: SessionState = SessionState.IDLE,
    val aiState: AIState = AIState.IDLE,
    val activeSessionId: String? = null,
    val isRecording: Boolean = false,
    val userPartialTranscript: String = "",
    val aiPartialTranscript: String = "",
    val lastFinalUserTranscript: String = "",
    val lastFinalAITranscript: String = "",
    val inputLevel: Float = 0f,
    val outputLevel: Float = 0f,
    val errorMessage: String? = null,

    // 사용자가 선택한 관심 주제 목록, 5개여야 저장 가능
    val selectedTopic: List<Topic> = emptyList(),

    /**
     * ChatScreen 진입 시 사용자의 interestTopics 가 비어있으면 true 로 설정.
     * Topic 5개 선택 후 저장 완료 시 false 로 변경.
     */
    val showTopicDialog: Boolean = false,
    // 저장 중 중복 클릭 방지용 true 일 때 버튼 비활성화
    val isTopicSaving: Boolean = false,
    val topicError: String? = null
    val isSavingTurn: Boolean = false,
    val saveErrorMessage: String? = null,
    val errorMessage: String? = null
)

/**
 * 앱 레벨 세션 연결 상태입니다.
 */
enum class SessionState {
    /**
     * 세션 시작 전 초기 상태입니다.
     */
    IDLE,

    /**
     * 세션 연결 및 초기화 중 상태입니다.
     */
    LOADING,

    /**
     * 세션 준비 완료 상태입니다.
     */
    READY,

    /**
     * 세션 오류 상태입니다.
     */
    ERROR
}
