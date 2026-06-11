package com.app.umma.domain.usecase.flashcardreview

import com.app.umma.domain.model.flashcard.Flashcard
import com.app.umma.domain.model.flashcard.FlashcardReviewSummary
import com.app.umma.domain.model.flashcard.FlashcardSchedule
import com.app.umma.domain.model.flashcard.FlashcardUpdateResult
import com.app.umma.domain.model.flashcard.ReviewDecision
import com.app.umma.domain.model.flashcard.ReviewRating
import com.app.umma.domain.model.flashcard.ReviewDeckState
import com.app.umma.domain.model.flashcard.ReviewScheduleResult
import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.FlashcardSummary
import com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateInput
import com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateResult
import com.app.umma.domain.model.learningstate.GlobalLangState
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.OnboardingGuideStage
import com.app.umma.domain.model.learningstate.LangStateUpdateInput
import com.app.umma.domain.model.learningstate.LearningStateUpdateResult
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.UserLangPref
import com.app.umma.domain.repository.FlashcardRepository
import com.app.umma.domain.repository.LearningStateRepo
import com.app.umma.domain.usecase.learningstate.ApplyFlashcardSummaryUpdateUseCase
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
        val learningStateRepo = RecordingLearningStateRepo()
        val useCase = ApplyReviewDecisionUseCase(
            flashcardRepository = repository,
            schedulePolicy = ReviewSchedulePolicy(),
            applyFlashcardSummaryUpdateUseCase = ApplyFlashcardSummaryUpdateUseCase(learningStateRepo)
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
        // schedule 저장 뒤에는 같은 Flashcard 원본에서 count를 다시 계산해 LS Summary까지 갱신한다.
        assertEquals(1, learningStateRepo.flashcardSummaryUpdateCalls)
        assertEquals(2, learningStateRepo.lastFlashcardSummaryInput?.dueFlashcards)
        assertEquals(5, learningStateRepo.lastFlashcardSummaryInput?.savedFlashcards)
    }

    @Test
    fun `returns failure when repository update fails`() = runBlocking {
        // 저장소가 실패하면 UseCase 가 임의로 삼키지 않고 상위 Retry 경계로 그대로 올린다.
        // local 저장 실패가 화면에서 Retry 상태가 되도록 연결되는지 확인한다.
        val repository = RecordingFlashcardRepository(
            updateResult = Result.failure(IllegalStateException("update failed"))
        )
        val learningStateRepo = RecordingLearningStateRepo()
        val useCase = ApplyReviewDecisionUseCase(
            flashcardRepository = repository,
            schedulePolicy = ReviewSchedulePolicy(),
            applyFlashcardSummaryUpdateUseCase = ApplyFlashcardSummaryUpdateUseCase(learningStateRepo)
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
        assertEquals(0, learningStateRepo.flashcardSummaryUpdateCalls)
    }

    @Test
    fun `rejects decision for a different flashcard`() = runBlocking {
        // 화면 상태가 어긋나 다른 카드의 decision 이 들어오면 저장소를 건드리지 않고 바로 실패시킨다.
        // 이 방어가 없으면 현재 카드가 아닌 항목의 평가가 현재 카드 schedule에 섞일 수 있다.
        val repository = RecordingFlashcardRepository()
        val learningStateRepo = RecordingLearningStateRepo()
        val useCase = ApplyReviewDecisionUseCase(
            flashcardRepository = repository,
            schedulePolicy = ReviewSchedulePolicy(),
            applyFlashcardSummaryUpdateUseCase = ApplyFlashcardSummaryUpdateUseCase(learningStateRepo)
        )

        val result = useCase(
            userId = "uid-1",
            card = baseCard(),
            decision = ReviewDecision(
                flashcardId = "other-card",
                rating = ReviewRating.GOOD,
                reviewedAt = 1_000L
            )
        )

        assertTrue(result.isFailure)
        assertEquals("review decision card id must match current card", result.exceptionOrNull()?.message)
        assertEquals(0, repository.updateCalls)
        assertEquals(0, learningStateRepo.flashcardSummaryUpdateCalls)
    }

    @Test
    fun `rolls back schedule when summary update fails`() = runBlocking {
        // SRS local completion은 schedule 저장과 Summary 반영이 함께 끝나야 완료다.
        // Summary 단계가 실패하면 이전 schedule로 보상 갱신해 카드 원본만 앞서 나가지 않게 한다.
        val repository = RecordingFlashcardRepository()
        val learningStateRepo = RecordingLearningStateRepo(summaryFailure = IllegalStateException("summary failed"))
        val useCase = ApplyReviewDecisionUseCase(
            flashcardRepository = repository,
            schedulePolicy = ReviewSchedulePolicy(),
            applyFlashcardSummaryUpdateUseCase = ApplyFlashcardSummaryUpdateUseCase(learningStateRepo)
        )
        val card = baseCard()

        val result = useCase(
            userId = "uid-1",
            card = card,
            decision = ReviewDecision(
                flashcardId = card.id,
                rating = ReviewRating.EASY,
                reviewedAt = 1_000L
            )
        )

        assertTrue(result.isFailure)
        assertEquals("summary failed", result.exceptionOrNull()?.message)
        assertEquals(2, repository.updateCalls)
        assertEquals(
            ReviewScheduleResult(
                interval = card.schedule.interval,
                easeFactor = card.schedule.easeFactor,
                nextReviewAt = card.schedule.nextReviewAt
            ),
            repository.lastScheduleResult
        )
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
            Result.success(FlashcardUpdateResult(cardId = "card-1", isSyncPending = true)),
        private val summaryResult: Result<FlashcardReviewSummary> =
            Result.success(FlashcardReviewSummary(dueFlashcards = 2, savedFlashcards = 5))
    ) : FlashcardRepository {
        var lastUserId: String? = null
            private set
        var lastCardId: String? = null
            private set
        var lastScheduleResult: ReviewScheduleResult? = null
            private set
        var updateCalls: Int = 0
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
            result: ReviewScheduleResult,
            lastReviewRating: ReviewRating?,
            lastReviewedAt: Long?
        ): Result<FlashcardUpdateResult> {
            updateCalls += 1
            lastUserId = userId
            lastCardId = cardId
            lastScheduleResult = result
            return updateResult
        }

        override suspend fun getReviewSummary(
            userId: String,
            language: LangCode,
            now: Long
        ): Result<FlashcardReviewSummary> {
            // UseCase가 review 후 Summary count를 요청하는지 확인한다.
            return summaryResult
        }

        override suspend fun syncDirtyFlashcards(userId: String): Result<Int> {
            // ApplyReviewDecisionUseCase는 단일 카드 schedule 저장과 summary 갱신만 검증한다.
            return Result.success(0)
        }

        override suspend fun getFlashcards(
            userId: String,
            language: LangCode
        ): Result<List<Flashcard>> {
            // 목록 조회는 ApplyReviewDecisionUseCase의 검증 범위가 아니므로 빈 결과로 계약만 맞춘다.
            return Result.success(emptyList())
        }

        override suspend fun deleteFlashcards(
            userId: String,
            flashcardIds: List<String>
        ): Result<Unit> {
            // 이 테스트는 복습 결정 적용만 검증하므로 삭제 경로는 사용하지 않는다.
            // 인터페이스 확장으로 인한 컴파일 계약만 충족한다.
            return Result.success(Unit)
        }
    }

    private class RecordingLearningStateRepo(
        private val summaryFailure: Throwable? = null
    ) : LearningStateRepo {
        var flashcardSummaryUpdateCalls: Int = 0
            private set
        var lastFlashcardSummaryInput: FlashcardSummaryUpdateInput? = null
            private set

        override fun observeLearningState(): Flow<GlobalLangState> = flowOf(GlobalLangState.initial())
        override fun observeUserPref(): Flow<UserLangPref?> = flowOf(null)
        override fun observeLangState(lang: LangCode): Flow<LangState?> = flowOf(null)
        override fun observeDashSummary(lang: LangCode): Flow<DashSummary?> = flowOf(null)
        override fun observeSessionSummary(lang: LangCode): Flow<SessionSummary?> = flowOf(null)
        override fun observeFlashcardSummary(lang: LangCode): Flow<FlashcardSummary?> = flowOf(null)
        override suspend fun preload(): Result<Unit> = Result.success(Unit)
        override suspend fun changeSelectedLang(lang: LangCode): Result<Unit> = Result.success(Unit)
        override suspend fun changePrimaryLang(lang: LangCode): Result<Unit> = Result.success(Unit)
        override suspend fun updateLanguageState(input: LangStateUpdateInput): Result<LearningStateUpdateResult> =
            Result.failure(UnsupportedOperationException("not used"))

        override suspend fun updateFlashcardSummary(
            input: FlashcardSummaryUpdateInput
        ): Result<FlashcardSummaryUpdateResult> {
            flashcardSummaryUpdateCalls += 1
            lastFlashcardSummaryInput = input
            summaryFailure?.let { return Result.failure(it) }
            return Result.success(
                FlashcardSummaryUpdateResult(
                    lang = input.lang,
                    flashcardSummary = FlashcardSummary(
                        lang = input.lang,
                        dueFlashcards = input.dueFlashcards,
                        savedFlashcards = input.savedFlashcards,
                        updatedAt = input.updatedAt
                    ),
                    dashSummary = DashSummary.initial(input.lang).copy(
                        dueFlashcards = input.dueFlashcards,
                        savedFlashcards = input.savedFlashcards,
                        updatedAt = input.updatedAt
                    ),
                    applied = true,
                    sourceEventId = input.sourceEventId,
                    updatedAt = input.updatedAt
                )
            )
        }

        override suspend fun createInitial(
            userUid: String,
            userPref: UserLangPref,
            langState: LangState,
            dashSummary: DashSummary,
            sessionSummary: SessionSummary,
            flashcardSummary: FlashcardSummary
        ): Result<Unit> = Result.success(Unit)

        override suspend fun setOnboardingGuideStage(lang: LangCode, stage: OnboardingGuideStage): Result<Unit> =
            Result.success(Unit)

        override suspend fun clear(): Result<Unit> = Result.success(Unit)
        override suspend fun sync(): Result<Unit> = Result.success(Unit)
    }
}
