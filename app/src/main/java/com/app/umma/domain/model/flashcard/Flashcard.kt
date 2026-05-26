package com.app.umma.domain.model.flashcard

import com.app.umma.domain.model.learningstate.LangCode

/** SRS 반복학습에서 사용하는 플래시카드 원본 모델입니다. */
data class Flashcard(
    val id: String,
    val language: LangCode,
    val frontText: String,
    val backText: String,
    val explanation: String,
    val hint: String? = null,
    val schedule: FlashcardSchedule,
    val createdAt: Long,
    val updatedAt: Long
)

/** 플래시카드의 복습 스케줄 상태를 담는 값 객체입니다. */
data class FlashcardSchedule(
    val interval: Int,
    val easeFactor: Double,
    val nextReviewAt: Long
)
