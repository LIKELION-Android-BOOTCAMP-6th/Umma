package com.app.umma.domain.usecase.correction

import com.app.umma.domain.model.flashcard.Flashcard
import com.app.umma.domain.model.flashcard.FlashcardSchedule
import com.app.umma.domain.model.flashcard.ReviewRating
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateInput
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateResult
import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.repository.FlashcardRepository
import com.app.umma.domain.usecase.flashcardreview.GetFlashcardsUseCase
import com.app.umma.domain.usecase.learningstate.ApplyCorrectionSignalUpdateUseCase
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ReconcileSavedCorrectionOnReentryUseCase 단위 계약 테스트.
 *
 * 참고: [COR-FIX-012 #370]
 */
class ReconcileSavedCorrectionOnReentryUseCaseTest {

    private val flashcardRepository = FakeFlashcardRepository()
    private val learningStateRepo = RecordingLearningStateRepo()

    private val useCase = ReconcileSavedCorrectionOnReentryUseCase(
        getFlashcardsUseCase = GetFlashcardsUseCase(flashcardRepository),
        applyCorrectionSignalUpdateUseCase = ApplyCorrectionSignalUpdateUseCase(learningStateRepo)
    )

    // ----- AlreadySaved: 갭 감지 -----

    @Test
    fun `returns AlreadySaved when all cached suggestion ids are already saved`() = runBlocking {
        flashcardRepository.savedCards = listOf(flashcard("s-1"), flashcard("s-2"))

        val result = useCase(
            uid = "uid-1",
            lang = LangCode.EN,
            sessionMemoryKey = "session-en",
            cachedSuggestionIds = listOf("s-1", "s-2"),
            requestedAt = 1_000L
        )

        assertEquals(ReconcileSavedCorrectionOnReentryUseCase.Outcome.AlreadySaved, result.getOrThrow())
    }

    @Test
    fun `sends correctionAvailable false signal via ApplyCorrectionSignalUpdateUseCase when AlreadySaved`() =
        runBlocking {
            flashcardRepository.savedCards = listOf(flashcard("s-1"))

            useCase(
                uid = "uid-1",
                lang = LangCode.EN,
                sessionMemoryKey = "session-en",
                cachedSuggestionIds = listOf("s-1"),
                requestedAt = 2_000L
            )

            val captured = learningStateRepo.lastSignalInput
            assertTrue("correctionAvailable 을 false 로 닫아야 한다", captured != null)
            assertEquals(false, captured!!.correctionAvailable)
            assertEquals("uid-1", captured.uid)
            assertEquals(LangCode.EN, captured.lang)
            assertEquals("session-en", captured.sessionMemoryKey)
        }

    @Test
    fun `generates deterministic sourceEventId regardless of cached suggestion id order when AlreadySaved`() = runBlocking {
        flashcardRepository.savedCards = listOf(flashcard("s-1"), flashcard("s-2"))

        // 호출 순서가 달라도 sorted 이므로 같은 eventId
        useCase(
            uid = "uid-1", lang = LangCode.EN, sessionMemoryKey = "key",
            cachedSuggestionIds = listOf("s-2", "s-1"), requestedAt = 1_000L
        )
        val firstEventId = learningStateRepo.lastSignalInput?.sourceEventId

        learningStateRepo.lastSignalInput = null

        useCase(
            uid = "uid-1", lang = LangCode.EN, sessionMemoryKey = "key",
            cachedSuggestionIds = listOf("s-1", "s-2"), requestedAt = 1_000L
        )
        val secondEventId = learningStateRepo.lastSignalInput?.sourceEventId

        assertEquals("id 정렬 순서에 무관하게 동일한 sourceEventId 를 생성한다", firstEventId, secondEventId)
    }

    // ----- NotSaved: 정상 복원 흐름 -----

    @Test
    fun `returns NotSaved when some cached suggestion ids are not yet saved`() = runBlocking {
        flashcardRepository.savedCards = listOf(flashcard("s-1")) // s-2 는 없음

        val result = useCase(
            uid = "uid-1",
            lang = LangCode.EN,
            sessionMemoryKey = "session-en",
            cachedSuggestionIds = listOf("s-1", "s-2"),
            requestedAt = 1_000L
        )

        assertEquals(ReconcileSavedCorrectionOnReentryUseCase.Outcome.NotSaved, result.getOrThrow())
    }

    @Test
    fun `does not send correctionAvailable signal when NotSaved`() = runBlocking {
        flashcardRepository.savedCards = emptyList() // 저장된 카드 없음

        useCase(
            uid = "uid-1",
            lang = LangCode.EN,
            sessionMemoryKey = "session-en",
            cachedSuggestionIds = listOf("s-1"),
            requestedAt = 1_000L
        )

        assertNull(
            "NotSaved 에서는 correctionAvailable 신호를 호출하면 안 된다",
            learningStateRepo.lastSignalInput
        )
    }

    @Test
    fun `returns NotSaved without querying flashcard repository when cachedSuggestionIds is empty`() =
        runBlocking {
            // flashcardRepository 를 실패 모드로 두어 호출 시 예외가 발생하도록 한다.
            flashcardRepository.failGet = true

            val result = useCase(
                uid = "uid-1",
                lang = LangCode.EN,
                sessionMemoryKey = "session-en",
                cachedSuggestionIds = emptyList(), // 빈 목록
                requestedAt = 1_000L
            )

            // 조회가 일어났다면 failGet=true 로 인해 Result.failure 가 됐을 것이다.
            assertEquals(ReconcileSavedCorrectionOnReentryUseCase.Outcome.NotSaved, result.getOrThrow())
        }

    // ----- 헬퍼 -----

    private fun flashcard(id: String): Flashcard = Flashcard(
        id = id,
        language = LangCode.EN,
        frontText = "front-$id",
        backText = "back-$id",
        explanation = "exp-$id",
        schedule = FlashcardSchedule(interval = 1, easeFactor = 2.5, nextReviewAt = 0L),
        createdAt = 0L,
        updatedAt = 0L
    )

    private class FakeFlashcardRepository : FlashcardRepository {
        var savedCards: List<Flashcard> = emptyList()
        var failGet: Boolean = false

        override fun observeDueFlashcards(
            userId: String,
            language: LangCode
        ) = emptyFlow<com.app.umma.domain.model.flashcard.ReviewDeckState>()

        override suspend fun updateFlashcardSchedule(
            userId: String,
            cardId: String,
            result: com.app.umma.domain.model.flashcard.ReviewScheduleResult,
            lastReviewRating: ReviewRating?,
            lastReviewedAt: Long?
        ): Result<com.app.umma.domain.model.flashcard.FlashcardUpdateResult> =
            Result.failure(UnsupportedOperationException("not used"))

        override suspend fun getReviewSummary(
            userId: String,
            language: LangCode,
            now: Long
        ): Result<com.app.umma.domain.model.flashcard.FlashcardReviewSummary> =
            Result.failure(UnsupportedOperationException("not used"))

        override suspend fun syncDirtyFlashcards(userId: String): Result<Int> = Result.success(0)

        override suspend fun getFlashcards(
            userId: String,
            language: LangCode
        ): Result<List<Flashcard>> {
            if (failGet) return Result.failure(IllegalStateException("simulated failure"))
            return Result.success(savedCards)
        }

        override suspend fun deleteFlashcards(
            userId: String,
            flashcardIds: List<String>
        ): Result<Unit> = Result.success(Unit)
    }

    /**
     * updateCorrectionSignal 만 감시한다. 다른 메서드는 이 테스트에서 호출되지 않는다.
     */
    private inner class RecordingLearningStateRepo :
        com.app.umma.domain.repository.LearningStateRepo {

        var lastSignalInput: CorrectionSignalUpdateInput? = null

        override fun observeLearningState() =
            kotlinx.coroutines.flow.flowOf(
                com.app.umma.domain.model.learningstate.GlobalLangState.initial()
            )

        override fun observeUserPref() =
            kotlinx.coroutines.flow.flowOf<com.app.umma.domain.model.learningstate.UserLangPref?>(null)

        override fun observeLangState(lang: LangCode) =
            kotlinx.coroutines.flow.flowOf<com.app.umma.domain.model.learningstate.LangState?>(null)

        override fun observeDashSummary(lang: LangCode) =
            kotlinx.coroutines.flow.flowOf<DashSummary?>(null)

        override fun observeSessionSummary(lang: LangCode) =
            kotlinx.coroutines.flow.flowOf<SessionSummary?>(null)

        override fun observeFlashcardSummary(lang: LangCode) =
            kotlinx.coroutines.flow.flowOf<com.app.umma.domain.model.learningstate.FlashcardSummary?>(null)

        override suspend fun preload(): Result<Unit> = Result.success(Unit)
        override suspend fun changeSelectedLang(lang: LangCode): Result<Unit> = Result.success(Unit)
        override suspend fun changePrimaryLang(lang: LangCode): Result<Unit> = Result.success(Unit)

        override suspend fun updateLanguageState(
            input: com.app.umma.domain.model.learningstate.LangStateUpdateInput
        ): Result<com.app.umma.domain.model.learningstate.LearningStateUpdateResult> =
            Result.failure(UnsupportedOperationException("not used in this test"))

        override suspend fun updateFlashcardSummary(
            input: com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateInput
        ): Result<com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateResult> =
            Result.failure(UnsupportedOperationException("not used in this test"))

        override suspend fun updateCorrectionSignal(
            input: CorrectionSignalUpdateInput
        ): Result<CorrectionSignalUpdateResult> {
            lastSignalInput = input
            return Result.success(
                CorrectionSignalUpdateResult(
                    lang = input.lang,
                    sessionSummary = SessionSummary.initial(input.lang)
                        .copy(correctionAvailable = input.correctionAvailable),
                    dashSummary = DashSummary.initial(input.lang)
                        .copy(correctionAvailable = input.correctionAvailable),
                    applied = true,
                    sourceEventId = input.sourceEventId,
                    updatedAt = input.updatedAt
                )
            )
        }

        override suspend fun createInitial(
            userUid: String,
            userPref: com.app.umma.domain.model.learningstate.UserLangPref,
            langState: com.app.umma.domain.model.learningstate.LangState,
            dashSummary: DashSummary,
            sessionSummary: SessionSummary,
            flashcardSummary: com.app.umma.domain.model.learningstate.FlashcardSummary
        ): Result<Unit> = Result.success(Unit)

        override suspend fun clear(): Result<Unit> = Result.success(Unit)
        override suspend fun sync(): Result<Unit> = Result.success(Unit)
    }
}
