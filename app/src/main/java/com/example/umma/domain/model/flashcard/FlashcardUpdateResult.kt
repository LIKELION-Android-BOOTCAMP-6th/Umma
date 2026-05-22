package com.example.umma.domain.model.flashcard

/** 플래시카드 복습 결과 갱신의 로컬 완료 상태를 담습니다. */
data class FlashcardUpdateResult(
    val cardId: String,
    val isSyncPending: Boolean
)
