package com.example.umma.presentation.chat

import com.example.umma.domain.model.realtime.AIState

/**
 * AI Chat 화면의 UI 상태를 정의하는 데이터 클래스입니다.
 *
 * @property sessionState 전체 세션의 연결 상태 ([SessionState.IDLE] -> [SessionState.LOADING] -> [SessionState.READY])
 * @property aiState AI의 구체적인 동작 상태 (Listening, Thinking, Speaking)
 * @property activeSessionId 현재 활성화된 세션의 고유 식별자 (앱 상태 복원용)
 * @property lastTranscription UI에 표시할 마지막 자막 텍스트
 * @property errorMessage 사용자에게 안내할 오류 메시지
 */
data class ChatUiState(
    val sessionState: SessionState = SessionState.IDLE,
    val aiState: AIState = AIState.IDLE,
    val activeSessionId: String? = null,
    val lastTranscription: String = "",
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
