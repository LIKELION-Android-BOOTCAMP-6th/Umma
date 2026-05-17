package com.example.umma.presentation.chat

import com.example.umma.domain.model.realtime.AIState

/**
 * AI Chat 화면의 UI 상태를 정의하는 데이터 클래스입니다.
 *
 * @property sessionState 전체 세션의 연결 상태 ([SessionState.IDLE] -> [SessionState.LOADING] -> [SessionState.READY])
 * @property aiState AI의 구체적인 동작 상태 (Listening, Thinking, Speaking, ERROR, RECONNECTING)
 * @property activeSessionId 현재 활성화된 세션의 고유 식별자 (앱 상태 복원용)
 * @property isRecording 현재 녹음중인지 여부 PTT
 * @property userPartialTranscript 현재 turn에서 진행중인 사용자 partial transcript (Buffer)
 * @property aiPartialTranscript 현재 turn에서 진행중인 AI partial transcript (Buffer)
 * @property lastFinalUserTranscript 가장 최근 확정된 사용자 final transcript
 * @property lastFinalAITranscript 가장 최근 확정된 AI final transcript
 * @property errorMessage 사용자에게 안내할 오류 메시지
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
    val errorMessage: String? = null
)

/**
 * 앱 레벨의 세션 연결 상태를 정의합니다.
 */
enum class SessionState { 
    /** 대화 시작 전 초기 상태 */
    IDLE, 
    /** 서버 연결 및 초기화 시도 중 */
    LOADING, 
    /** 통신 준비 완료 및 대화 가능 상태 */
    READY, 
    /** 연결 실패 상태 */
    ERROR 
}
