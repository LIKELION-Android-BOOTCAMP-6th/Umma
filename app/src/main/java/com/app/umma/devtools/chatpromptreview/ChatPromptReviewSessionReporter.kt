package com.app.umma.devtools.chatpromptreview

/**
 * 현재 AI Chat 세션을 프롬프트 리뷰 대상으로 신고하는 개발용 계약입니다.
 *
 * 운영 transport 계약인 `ChatRepository`에 devtools 책임이 섞이지 않도록 별도 인터페이스로 분리합니다.
 */
interface ChatPromptReviewSessionReporter {
    /**
     * 현재 활성 세션을 신고하고 지금까지 모인 review buffer를 Firestore에 저장합니다.
     */
    suspend fun reportCurrentSession(): Result<Unit>
}
