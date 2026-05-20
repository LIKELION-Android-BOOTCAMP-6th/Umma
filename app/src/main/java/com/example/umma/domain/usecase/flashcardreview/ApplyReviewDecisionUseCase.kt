package com.example.umma.domain.usecase.flashcardreview

import com.example.umma.domain.model.flashcard.Flashcard
import com.example.umma.domain.model.flashcard.FlashcardUpdateResult
import com.example.umma.domain.model.flashcard.ReviewDecision
import com.example.umma.domain.repository.FlashcardRepository
import javax.inject.Inject

/** 복습 평가를 스케줄 계산과 저장 갱신으로 이어주는 조율 UseCase입니다. */
class ApplyReviewDecisionUseCase @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
    private val schedulePolicy: ReviewSchedulePolicy
) {
    suspend operator fun invoke(
        userId: String,
        card: Flashcard,
        decision: ReviewDecision
    ): Result<FlashcardUpdateResult> {
        // 1) 먼저 domain 정책으로 다음 복습 시점을 계산한다.
        val nextResult = schedulePolicy.calculateNextSchedule(
            current = card.schedule,
            rating = decision.rating,
            reviewedAt = decision.reviewedAt
        )

        // 2) 계산된 결과만 저장소 계약으로 넘겨 local first 갱신을 시도한다.
        val updateResult = flashcardRepository.updateFlashcardSchedule(
            userId = userId,
            cardId = card.id,
            result = nextResult
        )

        return updateResult
    }
}
