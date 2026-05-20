package com.example.umma.domain.usecase.flashcardreview

import com.example.umma.domain.model.flashcard.Flashcard
import com.example.umma.domain.model.flashcard.FlashcardSchedule
import com.example.umma.domain.model.flashcard.FlashcardUpdateResult
import com.example.umma.domain.model.flashcard.ReviewDecision
import com.example.umma.domain.model.flashcard.ReviewRating
import com.example.umma.domain.model.flashcard.ReviewDeckState
import com.example.umma.domain.model.flashcard.ReviewScheduleResult
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.repository.FlashcardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyReviewDecisionUseCaseTest {

    @Test
    fun `applies calculated schedule to repository update`() = runBlocking {
        // UseCase 는 정책 계산과 저장소 반영의 연결만 담당하고, 계산 자체는 policy 에 맡긴다.
        // 이 테스트는 "평가 버튼 -> schedule 계산 -> repository update"의 순서만 검증한다.
        val repository = RecordingFlashcardRepository()
        val useCase = ApplyReviewDecisionUseCase(
            flashcardRepository = repository,
            schedulePolicy = ReviewSchedulePolicy()
        )
        val card = baseCard()

        val result = useCase(
            userId = "uid-1",
            card = card,
            decision = ReviewDecision(
                flashcardId = card.id,
                rating = ReviewRating.GOOD,
                reviewedAt = 1_000L
            )
        )

        assertTrue(result.isSuccess)
        assertEquals("uid-1", repository.lastUserId)
        assertEquals(card.id, repository.lastCardId)
        // UseCase 는 정책 계산을 직접 저장하지 않고, 계산 결과를 Repository 계약으로 넘긴다.
        assertEquals(
            ReviewScheduleResult(
                interval = 3_600,
                easeFactor = 2.5,
                nextReviewAt = 1_000L + 3_600L * 60L * 1_000L
            ),
            repository.lastScheduleResult
        )
    }

    @Test
    fun `returns failure when repository update fails`() = runBlocking {
        // 저장소가 실패하면 UseCase 가 임의로 삼키지 않고 상위 Retry 경계로 그대로 올린다.
        // local 저장 실패가 화면에서 Retry 상태가 되도록 연결되는지 확인한다.
        val repository = RecordingFlashcardRepository(
            updateResult = Result.failure(IllegalStateException("update failed"))
        )
        val useCase = ApplyReviewDecisionUseCase(
            flashcardRepository = repository,
            schedulePolicy = ReviewSchedulePolicy()
        )

        val result = useCase(
            userId = "uid-1",
            card = baseCard(),
            decision = ReviewDecision(
                flashcardId = "card-1",
                rating = ReviewRating.AGAIN,
                reviewedAt = 1_000L
            )
        )

        // 저장 실패는 여기서 삼키지 않고 화면의 Retry 상태로 이어질 수 있게 그대로 반환한다.
        assertTrue(result.isFailure)
        assertEquals("update failed", result.exceptionOrNull()?.message)
    }

    private fun baseCard(): Flashcard {
        // 카드 원본은 schedule 이 포함된 상태로 들어와야 정책 계산이 일관된다.
        // UseCase 에 들어오는 card 는 이미 deck 에서 선택된 현재 카드라는 가정이다.
        return Flashcard(
            id = "card-1",
            language = LangCode.EN,
            frontText = "나는 학교에 간다",
            backText = "I go to school.",
            explanation = "go 뒤에는 to school 을 사용한다.",
            hint = null,
            schedule = FlashcardSchedule(
                interval = 1_440,
                easeFactor = 2.5,
                nextReviewAt = 0L
            ),
            createdAt = 0L,
            updatedAt = 0L
        )
    }

    private class RecordingFlashcardRepository(
        private val updateResult: Result<FlashcardUpdateResult> =
            Result.success(FlashcardUpdateResult(cardId = "card-1", isSyncPending = true))
    ) : FlashcardRepository {
        var lastUserId: String? = null
            private set
        var lastCardId: String? = null
            private set
        var lastScheduleResult: ReviewScheduleResult? = null
            private set

        override fun observeDueFlashcards(
            userId: String,
            language: LangCode
        ): Flow<ReviewDeckState> {
            return flowOf(ReviewDeckState.Empty)
        }

        override suspend fun updateFlashcardSchedule(
            userId: String,
            cardId: String,
            result: ReviewScheduleResult
        ): Result<FlashcardUpdateResult> {
            lastUserId = userId
            lastCardId = cardId
            lastScheduleResult = result
            return updateResult
        }
    }
}
