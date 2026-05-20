package com.example.umma.domain.model.flashcard

/** 복습 평가 후 정책이 계산한 다음 스케줄 결과입니다. */
data class ReviewScheduleResult(
    val interval: Int,
    val easeFactor: Double,
    val nextReviewAt: Long
)
