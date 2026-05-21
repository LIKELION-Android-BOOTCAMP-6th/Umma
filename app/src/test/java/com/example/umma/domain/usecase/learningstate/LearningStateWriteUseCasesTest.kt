package com.example.umma.domain.usecase.learningstate

import com.example.umma.domain.model.learningstate.DashSummary
import com.example.umma.domain.model.learningstate.FlashcardSummary
import com.example.umma.domain.model.learningstate.FlashcardSummaryUpdateInput
import com.example.umma.domain.model.learningstate.FlashcardSummaryUpdateResult
import com.example.umma.domain.model.learningstate.GlobalLangState
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.LangState
import com.example.umma.domain.model.learningstate.LangStateUpdateInput
import com.example.umma.domain.model.learningstate.LearningStateUpdateResult
import com.example.umma.domain.model.learningstate.SessionSummary
import com.example.umma.domain.model.learningstate.UserLangPref
import com.example.umma.domain.repository.LearningStateRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningStateWriteUseCasesTest {

    @Test
    fun `skips duplicate analysisEventId before repository write`() = runBlocking {
        val repo = RecordingLearningStateRepo()
        val useCase = ApplyLanguageStateUpdateUseCase(repo)
        val current = LangState.initial(
            lang = LangCode.EN,
            createdAt = 1_000L,
            updatedAt = 2_000L
        ).copy(lastAnalysisEventId = "analysis-1")

        val result = useCase(
            LangStateUpdateInput(
                uid = "uid-1",
                lang = LangCode.EN,
                sessionMemoryKey = "session-en",
                analysisEventId = "analysis-1",
                currentState = current,
                recentUserTurns = emptyList(),
                correctionResult = null,
                flashcardReviewEvents = emptyList(),
                analyzedAt = 3_000L
            )
        ).getOrThrow()

        // 같은 analysisEventId는 저장소에 다시 들어가면 안 되므로, apply=false + repo 호출 0회를 확인한다.
        assertFalse(result.applied)
        assertEquals("analysis-1", result.sourceEventId)
        assertEquals(0, repo.languageStateUpdateCalls)
    }

    @Test
    fun `passes valid flashcard summary update to repository`() = runBlocking {
        val repo = RecordingLearningStateRepo()
        val useCase = ApplyFlashcardSummaryUpdateUseCase(repo)

        val result = useCase(
            FlashcardSummaryUpdateInput(
                uid = "uid-1",
                lang = LangCode.EN,
                dueFlashcards = 3,
                savedFlashcards = 10,
                sourceEventId = "review-1",
                updatedAt = 4_000L
            )
        ).getOrThrow()

        // SRS가 계산한 due count는 FlashcardSummary와 DashSummary에 같은 값으로 들어가야 한다.
        assertTrue(result.applied)
        assertEquals(3, result.flashcardSummary.dueFlashcards)
        assertEquals(3, result.dashSummary.dueFlashcards)
        assertEquals(1, repo.flashcardSummaryUpdateCalls)
    }

    @Test
    fun `rejects negative flashcard summary counts`() = runBlocking {
        val repo = RecordingLearningStateRepo()
        val useCase = ApplyFlashcardSummaryUpdateUseCase(repo)

        val result = useCase(
            FlashcardSummaryUpdateInput(
                uid = "uid-1",
                lang = LangCode.EN,
                dueFlashcards = -1,
                savedFlashcards = 10,
                sourceEventId = "review-1",
                updatedAt = 4_000L
            )
        )

        // 음수 due count는 LS 쪽으로 넘기기 전에 바로 막는다.
        assertTrue(result.isFailure)
        assertEquals(0, repo.flashcardSummaryUpdateCalls)
    }

    private class RecordingLearningStateRepo : LearningStateRepo {
        private val state = MutableStateFlow(GlobalLangState.initial())
        var languageStateUpdateCalls: Int = 0
        var flashcardSummaryUpdateCalls: Int = 0

        override fun observeLearningState(): Flow<GlobalLangState> = state

        override fun observeUserPref(): Flow<UserLangPref?> = state.map { it.userPref }

        override fun observeLangState(lang: LangCode): Flow<LangState?> =
            state.map { it.langStates[lang] }

        override fun observeDashSummary(lang: LangCode): Flow<DashSummary?> =
            state.map { it.dashSummaries[lang] }

        override fun observeSessionSummary(lang: LangCode): Flow<SessionSummary?> =
            state.map { it.sessionSummaries[lang] }

        override fun observeFlashcardSummary(lang: LangCode): Flow<FlashcardSummary?> =
            state.map { it.flashcardSummaries[lang] }

        override suspend fun preload(): Result<Unit> = Result.success(Unit)

        override suspend fun changeSelectedLang(lang: LangCode): Result<Unit> = Result.success(Unit)

        override suspend fun updateLanguageState(
            input: LangStateUpdateInput
        ): Result<LearningStateUpdateResult> {
            languageStateUpdateCalls += 1
            // UseCase가 계산한 preparedState가 저장소에 그대로 전달되는지 확인하기 위해 echo한다.
            val savedState = input.preparedState ?: input.currentState
            return Result.success(
                LearningStateUpdateResult(
                    lang = input.lang,
                    savedState = savedState,
                    sourceEventId = input.analysisEventId ?: "${input.lang.code}:${input.analyzedAt}",
                    applied = true,
                    updatedAt = input.analyzedAt
                )
            )
        }

        override suspend fun updateFlashcardSummary(
            input: FlashcardSummaryUpdateInput
        ): Result<FlashcardSummaryUpdateResult> {
            flashcardSummaryUpdateCalls += 1
            // 저장소 응답은 화면 표시와 동일한 due count / saved count를 돌려줘야 한다.
            val flashcardSummary = FlashcardSummary(
                lang = input.lang,
                dueFlashcards = input.dueFlashcards,
                savedFlashcards = input.savedFlashcards,
                updatedAt = input.updatedAt
            )
            val dashSummary = DashSummary.initial(input.lang).copy(
                dueFlashcards = input.dueFlashcards,
                savedFlashcards = input.savedFlashcards,
                updatedAt = input.updatedAt
            )
            return Result.success(
                FlashcardSummaryUpdateResult(
                    lang = input.lang,
                    flashcardSummary = flashcardSummary,
                    dashSummary = dashSummary,
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

        override suspend fun clear(): Result<Unit> = Result.success(Unit)

        override suspend fun sync(): Result<Unit> = Result.success(Unit)
    }
}
