package com.example.umma.domain.model

/**
 * Flashcard 전체 목록 대신, 현재 선택 언어에 대한 복습/저장 상태만 보여주는 요약 스냅샷.
 *
 * LS-004에서는 전체 Flashcard 리스트를 Store가 직접 들고 있지 않도록 하고,
 * 복습해야 할 카드 수와 최근 저장 수 정도만 전역 상태에 남긴다.
 */
data class FlashcardSummaryVO(
    val language: LanguageCode,
    val dueFlashcards: Int,
    val recentSavedFlashcards: Int,
    val updatedAt: Long? = null
) {
    companion object {
        /**
         * Flashcard 요약이 아직 없을 때 사용하는 Empty 스냅샷.
         */
        fun initial(language: LanguageCode): FlashcardSummaryVO {
            return FlashcardSummaryVO(
                language = language,
                dueFlashcards = 0,
                recentSavedFlashcards = 0,
                updatedAt = null
            )
        }
    }
}
