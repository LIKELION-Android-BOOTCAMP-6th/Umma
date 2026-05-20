package com.example.umma.domain.repository

import com.example.umma.domain.model.flashcard.FlashcardUpdateResult
import com.example.umma.domain.model.flashcard.ReviewDeckState
import com.example.umma.domain.model.flashcard.ReviewScheduleResult
import com.example.umma.domain.model.learningstate.LangCode
import kotlinx.coroutines.flow.Flow

/** 플래시카드 조회와 복습 결과 갱신을 담당하는 저장소 계약입니다. */
interface FlashcardRepository {
    /**
     * 특정 사용자와 언어의 복습 대상 카드를 상태와 함께 관찰합니다.
     */
    fun observeDueFlashcards(userId: String, language: LangCode): Flow<ReviewDeckState>

    /**
     * 평가 결과로 계산된 스케줄을 로컬 우선으로 반영합니다.
     */
    suspend fun updateFlashcardSchedule(
        userId: String,
        cardId: String,
        result: ReviewScheduleResult
    ): Result<FlashcardUpdateResult>
}
