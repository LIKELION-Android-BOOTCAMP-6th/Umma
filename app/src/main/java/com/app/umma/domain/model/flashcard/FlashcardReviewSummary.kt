package com.app.umma.domain.model.flashcard

/**
 * SRS 복습 결과 저장 후 Summary 갱신에 필요한 카드 수 스냅샷입니다.
 */
data class FlashcardReviewSummary(
    // 현재 시점에 다시 복습 대상이 되는 카드 수.
    val dueFlashcards: Int,
    // 현재 언어에 저장된 전체 플래시카드 수.
    val savedFlashcards: Int
)
