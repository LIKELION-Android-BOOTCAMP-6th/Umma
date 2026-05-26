package com.app.umma.presentation.chat

import com.app.umma.domain.model.realtime.AIState
import com.app.umma.domain.model.user.Topic

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
 * @property lastHandledFinalTurnId 중복 append 방지를 위한 마지막 turnId
 * @property showSubtitle 자막 on/off 토글용 상태
 * @property inputLevel 입력 오디오 레벨
 * @property outputLevel 출력 오디오 레벨
 * @property isSavingTurn 현재 확정 turn 저장 중 여부
 * @property saveErrorMessage turn 저장 오류 메시지
 * @property reconnectAttempt 현재 자동 재연결 시도 횟수
 * @property maxReconnectAttempts 최대 자동 재연결 시도 횟수
 * @property isRecoverableError 사용자 재시도 가능 오류 여부
 * @property microphonePermissionDenied 마이크 권한 거부 여부
 * @property fallbackMessage 재연결 폴백 시 메시지
 * @property didFallbackToNewSession 재연결 폴백 후 새 세션 실행 여부
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
    val lastHandledFinalTurnId: String? = null,
    val showSubtitle: Boolean = false,
    val inputLevel: Float = 0f,
    val outputLevel: Float = 0f,
    val errorMessage: String? = null,
    val reconnectAttempt: Int = 0,
    val maxReconnectAttempts: Int = 0,
    val isRecoverableError: Boolean = false,
    val microphonePermissionDenied: Boolean = false,
    val didFallbackToNewSession: Boolean = false, // 재연결 시 fallback에 빠졌는 지 여부
    val fallbackMessage: String? = null, // 해당 fallback의 message
    val selectedTopic: List<Topic> = emptyList(), // 사용자가 선택한 관심 주제 목록, 5개여야 저장 가능
    /**
     * ChatScreen 진입 시 사용자의 interestTopics 가 비어있으면 true 로 설정.
     * Topic 5개 선택 후 저장 완료 시 false 로 변경.
     */
    val showTopicDialog: Boolean = false,
    // 저장 중 중복 클릭 방지용 true 일 때 버튼 비활성화
    val isTopicSaving: Boolean = false,
    val topicError: String? = null,
    val isSavingTurn: Boolean = false,
    val saveErrorMessage: String? = null,
) {
    /**
     * 유저가 발화를 시작할 수 있는 경우 ->
     * 현재 세션 준비가 되었고 녹음중이 아니고 AI가 말, 생각, 재연결 상태가 아닐 때
     * */
    val canStartUserTurn: Boolean
        get() = sessionState == SessionState.READY && !isRecording
                && aiState != AIState.SPEAKING
                && aiState != AIState.THINKING
                && aiState != AIState.RECONNECTING

    /**
     * 유저가 발화를 끝낼 수 있는 경우 ->
     * 유저의 발화가 끝난 경우 || 녹음중인 경우
     * */
    val canEndUserTurn: Boolean
        get() = isRecording
}

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
     * Live transport 재연결 중 상태입니다.
     */
    RECONNECTING,

    /**
     * 세션 오류 상태입니다.
     */
    ERROR
}
