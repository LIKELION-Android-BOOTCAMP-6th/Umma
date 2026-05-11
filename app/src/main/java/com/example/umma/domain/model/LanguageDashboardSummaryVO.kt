package com.example.umma.domain.model

/**
 * Dashboard가 빠르게 렌더링하기 위해 읽는 언어별 요약 데이터.
 *
 * LS-002의 목적은 Session Memory / Language State 원본을 직접 계산하지 않고도,
 * 현재 선택 언어의 최근 학습 상태를 한 화면에서 바로 보여줄 수 있는 최소 구조를
 * 고정하는 것이다.
 */
data class LanguageDashboardSummaryVO(
    val language: String,
    val recentConversationMinutes: Int,
    val recentConversationTopic: String?,
    val correctionAvailable: Boolean,
    val dueFlashcards: Int,
    val recentSavedFlashcards: Int,
    val grammarScoreDelta: Int,
    val fluencyScoreDelta: Int,
    val vocabularyScoreDelta: Int,
    val naturalnessScoreDelta: Int,
    val schemaVersion: Int = SCHEMA_VERSION,
    val updatedAt: Long? = null
) {
    companion object {
        const val SCHEMA_VERSION = 1

        /**
         * Initial Setup 또는 요약 문서가 아직 없을 때 사용할 기본 Dashboard Summary.
         *
         * Dashboard는 이 값을 기반으로 Empty 상태를 즉시 렌더링할 수 있어야 한다.
         */
        fun initial(language: String): LanguageDashboardSummaryVO {
            return LanguageDashboardSummaryVO(
                language = language,
                recentConversationMinutes = 0,
                recentConversationTopic = null,
                correctionAvailable = false,
                dueFlashcards = 0,
                recentSavedFlashcards = 0,
                grammarScoreDelta = 0,
                fluencyScoreDelta = 0,
                vocabularyScoreDelta = 0,
                naturalnessScoreDelta = 0,
                schemaVersion = SCHEMA_VERSION,
                updatedAt = null
            )
        }
    }
}
