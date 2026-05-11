package com.example.umma.domain.model

/**
 * Session Memory 전체를 대신해 Global State가 들고 있는 언어별 요약 스냅샷.
 *
 * LS-004의 역할은 원본 recentFullContext를 복제하지 않고도,
 * Dashboard / AI Chat / Correction 진입 판단에 필요한 최소 정보만 빠르게 읽을 수 있게
 * 하는 것이다.
 */
data class SessionSummaryVO(
    val language: LanguageCode,
    val correctionAvailable: Boolean,
    val recentConversationMinutes: Int,
    val recentConversationTopic: String?,
    val updatedAt: Long? = null
) {
    companion object {
        /**
         * 세션 요약이 아직 없을 때 사용하는 기본 스냅샷.
         *
         * 앱은 이 값을 통해 "대화 기록 없음" 상태를 안전하게 렌더링할 수 있다.
         */
        fun initial(language: LanguageCode): SessionSummaryVO {
            return SessionSummaryVO(
                language = language,
                correctionAvailable = false,
                recentConversationMinutes = 0,
                recentConversationTopic = null,
                updatedAt = null
            )
        }
    }
}
