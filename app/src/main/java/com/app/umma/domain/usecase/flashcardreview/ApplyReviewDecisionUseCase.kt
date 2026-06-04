package com.app.umma.domain.usecase.flashcardreview

import com.app.umma.domain.model.flashcard.Flashcard
import com.app.umma.domain.model.flashcard.FlashcardUpdateResult
import com.app.umma.domain.model.flashcard.ReviewDecision
import com.app.umma.domain.model.flashcard.ReviewScheduleResult
import com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateInput
import com.app.umma.domain.repository.FlashcardRepository
import com.app.umma.domain.usecase.learningstate.ApplyFlashcardSummaryUpdateUseCase
import javax.inject.Inject

/** 복습 평가를 스케줄 계산과 저장 갱신으로 이어주는 조율 UseCase입니다. */
class ApplyReviewDecisionUseCase @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
    private val schedulePolicy: ReviewSchedulePolicy,
    private val applyFlashcardSummaryUpdateUseCase: ApplyFlashcardSummaryUpdateUseCase
) {
    suspend operator fun invoke(
        userId: String,
        card: Flashcard,
        decision: ReviewDecision
    ): Result<FlashcardUpdateResult> {
        if (decision.flashcardId != card.id) {
            return Result.failure(
                IllegalArgumentException("review decision card id must match current card")
            )
        }

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
            result = nextResult,
            lastReviewRating = decision.rating,
            lastReviewedAt = decision.reviewedAt
        )

        val savedUpdate = updateResult.getOrElse { throwable ->
            return Result.failure(throwable)
        }

        // 3) schedule 저장이 끝난 뒤 같은 Flashcard 원본에서 due/saved count를 다시 계산한다.
        val summarySnapshot = flashcardRepository.getReviewSummary(
            userId = userId,
            language = card.language,
            now = decision.reviewedAt
        ).getOrElse { throwable ->
            rollbackSchedule(userId = userId, card = card)
            return Result.failure(throwable)
        }

        // 4) LS는 계산된 숫자를 Summary에 반영만 한다. sourceEventId로 같은 review의 중복 반영을 막는다.
        val summaryResult = applyFlashcardSummaryUpdateUseCase(
            FlashcardSummaryUpdateInput(
                uid = userId,
                lang = card.language,
                dueFlashcards = summarySnapshot.dueFlashcards,
                notifiableDueFlashcards = summarySnapshot.notifiableDueFlashcards,
                savedFlashcards = summarySnapshot.savedFlashcards,
                sourceEventId = "srs-review:${card.id}:${decision.reviewedAt}",
                updatedAt = decision.reviewedAt
            )
        )

        if (summaryResult.isFailure) {
            // Summary까지 반영돼야 SRS local completion이 끝난 것으로 본다.
            // 실패하면 이전 schedule 값으로 보상 갱신해 카드 원본만 앞서 나가는 상태를 줄인다.
            rollbackSchedule(userId = userId, card = card)
            return Result.failure(summaryResult.exceptionOrNull() ?: IllegalStateException("summary update failed"))
        }

        return Result.success(savedUpdate)
    }

    private suspend fun rollbackSchedule(
        userId: String,
        card: Flashcard
    ) {
        // rollback 실패는 원래 실패 원인을 가리지 않도록 여기서 삼킨다.
        // dirty 상태는 남을 수 있지만, nextReviewAt/interval/easeFactor는 이전 값으로 되돌리는 것이 우선이다.
        flashcardRepository.updateFlashcardSchedule(
            userId = userId,
            cardId = card.id,
            result = ReviewScheduleResult(
                interval = card.schedule.interval,
                easeFactor = card.schedule.easeFactor,
                nextReviewAt = card.schedule.nextReviewAt
            ),
            lastReviewRating = card.lastReviewRating,
            lastReviewedAt = card.lastReviewedAt
        )
    }
}
