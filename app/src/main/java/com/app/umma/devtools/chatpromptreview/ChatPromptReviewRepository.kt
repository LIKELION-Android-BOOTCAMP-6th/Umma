package com.app.umma.devtools.chatpromptreview

import com.app.umma.domain.model.learningstate.LangCode

/**
 * 개발용 AI Chat 프롬프트 리뷰 자료수집 저장소입니다.
 *
 * 이 계약은 운영 기능의 source of truth가 아니며, 저장 실패가 대화 흐름에 영향을 주면 안 됩니다.
 * 구현체는 feature flag가 꺼져 있으면 no-op으로 동작해야 합니다.
 */
interface ChatPromptReviewRepository {
    /**
     * 리뷰 자료수집이 현재 빌드/설정에서 켜져 있는지 반환합니다.
     */
    fun isEnabled(): Boolean

    /**
     * 세션 단위 리뷰 문서를 준비합니다.
     */
    suspend fun recordSessionStarted(
        userId: String,
        sessionId: String,
        language: LangCode,
        sessionPromptTrace: String?,
        metadata: String
    ): Result<Unit>

    /**
     * 세션 리뷰 버퍼에 이벤트를 추가합니다.
     */
    suspend fun recordEvent(
        userId: String,
        event: ChatPromptReviewEvent
    ): Result<Unit>

    /**
     * 메모리에 모인 세션 리뷰 자료를 Firestore에 한 번에 저장합니다.
     */
    suspend fun flushSession(
        userId: String,
        sessionId: String
    ): Result<Unit>
}
