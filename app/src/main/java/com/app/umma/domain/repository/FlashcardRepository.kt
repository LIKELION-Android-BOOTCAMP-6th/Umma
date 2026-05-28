package com.app.umma.domain.repository

import com.app.umma.domain.model.flashcard.FlashcardReviewSummary
import com.app.umma.domain.model.flashcard.FlashcardUpdateResult
import com.app.umma.domain.model.flashcard.ReviewDeckState
import com.app.umma.domain.model.flashcard.ReviewScheduleResult
import com.app.umma.domain.model.learningstate.LangCode
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

    /**
     * review schedule 갱신 직후 Summary에 반영할 카드 수를 같은 원본에서 다시 계산합니다.
     */
    suspend fun getReviewSummary(
        userId: String,
        language: LangCode,
        now: Long
    ): Result<FlashcardReviewSummary>

    /**
     * dirty 상태로 남은 카드를 Firestore에 일괄 재시도 sync
     * 성공: 카드 수 반환
     * 실패: Result.failure
     */
    suspend fun syncDirtyFlashcards(userId: String): Result<Int>
}
