package com.app.umma.domain.model.flashcard

/** UI에서 선택한 복습 평가를 UseCase로 넘기는 입력 모델입니다. */
data class ReviewDecision(
    val flashcardId: String,
    val rating: ReviewRating,
    val reviewedAt: Long
)
