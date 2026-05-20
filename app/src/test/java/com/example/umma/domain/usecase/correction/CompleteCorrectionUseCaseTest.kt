package com.example.umma.domain.usecase.correction

import com.example.umma.domain.model.correction.CompleteCorrectionInput
import com.example.umma.domain.model.correction.CorrectionSaveRequest
import com.example.umma.domain.model.correction.CorrectionSaveResult
import com.example.umma.domain.model.correction.CorrectionSuggestion
import com.example.umma.domain.model.learningstate.ConversationTurn
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.LangState
import com.example.umma.domain.model.learningstate.LangStateUpdateInput
import com.example.umma.domain.model.learningstate.TurnSpeaker
import com.example.umma.domain.repository.CorrectionRepository
import com.example.umma.domain.repository.LearningStateRepo
import com.example.umma.domain.usecase.learningstate.ApplyLanguageStateUpdateUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class CompleteCorrectionUseCaseTest {

    private val events = CopyOnWriteArrayList<String>()
    private val correctionRepository = RecordingCorrectionRepository(events)
    private val learningStateRepo = RecordingLearningStateRepo(events)
    private val applyLanguageStateUpdateUseCase = ApplyLanguageStateUpdateUseCase(learningStateRepo)
    private val useCase = CompleteCorrectionUseCase(
        prepareSaveRequestUseCase = PrepareSaveRequestUseCase(),
        correctionRepository = correctionRepository,
        applyLanguageStateUpdateUseCase = applyLanguageStateUpdateUseCase
    )

    @Test
    fun `runs save and state update in order`() = kotlinx.coroutines.runBlocking {
        val suggestion = CorrectionSuggestion(
            id = "s-1",
            lang = LangCode.EN,
            sourceCandidateIds = listOf("c-1"),
            sourceTurnIndex = 0,
            beforeText = "i go school",
            nativeText = "나는 학교에 간다",
            afterText = "I go school.",
            explanation = "demo"
        )

        val input = CompleteCorrectionInput(
            selectedSuggestions = listOf(suggestion),
            langStateUpdateInput = LangStateUpdateInput(
                uid = "uid-1",
                lang = LangCode.EN,
                sessionMemoryKey = "session-en",
                analysisEventId = "analysis-1",
                currentState = LangState.initial(LangCode.EN),
                preparedState = null,
                recentUserTurns = listOf(
                    ConversationTurn(
                        speaker = TurnSpeaker.USER,
                        text = "i go school",
                        tokenCount = 3,
                        durationMs = 2_000L
                    )
                ),
                correctionResult = null,
                flashcardReviewEvents = emptyList(),
                analyzedAt = 1_000L
            ),
            requestedAt = 1_500L
        )

        val result = useCase(input)

        assertTrue(result.isSuccess)
        val completed = result.getOrThrow()
        assertEquals(listOf("s-1"), completed.savedFlashcardIds)
        assertEquals(listOf("s-1"), completed.pendingSyncFlashcardIds)
        assertEquals("session-en", completed.sessionMemoryKey)
        assertEquals(listOf("save", "update"), events)

        assertNotNull(learningStateRepo.lastUpdateInput)
        assertFalse(learningStateRepo.lastUpdateInput!!.correctionAvailableOverride!!)
    }

    @Test
    fun `fails when selected suggestions are empty`() = kotlinx.coroutines.runBlocking {
        val input = CompleteCorrectionInput(
            selectedSuggestions = emptyList(),
            langStateUpdateInput = baseUpdateInput()
        )

        val result = useCase(input)

        assertTrue(result.isFailure)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `rolls back saved flashcards when state update fails`() = kotlinx.coroutines.runBlocking {
        val suggestion = baseSuggestion()
        learningStateRepo.failUpdate = true

        val result = useCase(
            CompleteCorrectionInput(
                selectedSuggestions = listOf(suggestion),
                langStateUpdateInput = baseUpdateInput()
            )
        )

        assertTrue(result.isFailure)
        assertEquals(listOf("save", "update", "rollback-save"), events)
    }

    private fun baseUpdateInput(): LangStateUpdateInput {
        return LangStateUpdateInput(
            uid = "uid-1",
            lang = LangCode.EN,
            sessionMemoryKey = "session-en",
            analysisEventId = "analysis-1",
            currentState = LangState.initial(LangCode.EN),
            preparedState = null,
            recentUserTurns = listOf(
                ConversationTurn(
                    speaker = TurnSpeaker.USER,
                    text = "hello",
                    tokenCount = 1,
                    durationMs = 1_000L
                )
            ),
            correctionResult = null,
            flashcardReviewEvents = emptyList(),
            analyzedAt = 1_000L
        )
    }

    private fun baseSuggestion(): CorrectionSuggestion {
        return CorrectionSuggestion(
            id = "s-1",
            lang = LangCode.EN,
            sourceCandidateIds = listOf("c-1"),
            sourceTurnIndex = 0,
            beforeText = "i go school",
            nativeText = "나는 학교에 간다",
            afterText = "I go to school.",
            explanation = "go 뒤에는 to school 을 사용한다."
        )
    }

    private class RecordingCorrectionRepository(
        private val events: MutableList<String>
    ) : CorrectionRepository {
        override suspend fun generateSuggestions(
            input: com.example.umma.domain.model.correction.GenerateSuggestionsInput
        ): Result<List<CorrectionSuggestion>> {
            return Result.success(emptyList())
        }

        override suspend fun saveFlashcards(
            request: CorrectionSaveRequest
        ): Result<CorrectionSaveResult> {
            events += "save"
            return Result.success(
                CorrectionSaveResult(
                    localSavedFlashcardIds = request.flashcards.map { it.suggestionId },
                    pendingSyncFlashcardIds = request.flashcards.map { it.suggestionId },
                    savedAt = request.requestedAt
                )
            )
        }

        override suspend fun rollbackFlashcards(
            request: CorrectionSaveRequest
        ): Result<Unit> {
            events += "rollback-save"
            return Result.success(Unit)
        }
    }

    private class RecordingLearningStateRepo(
        private val events: MutableList<String>
    ) : LearningStateRepo {
        var lastUpdateInput: LangStateUpdateInput? = null
        var failUpdate: Boolean = false

        override fun observeLearningState(): Flow<com.example.umma.domain.model.learningstate.GlobalLangState> =
            flowOf(com.example.umma.domain.model.learningstate.GlobalLangState.initial())

        override fun observeUserPref(): Flow<com.example.umma.domain.model.learningstate.UserLangPref?> =
            flowOf(null)

        override fun observeLangState(lang: LangCode): Flow<com.example.umma.domain.model.learningstate.LangState?> =
            flowOf(null)

        override fun observeDashSummary(lang: LangCode): Flow<com.example.umma.domain.model.learningstate.DashSummary?> =
            flowOf(null)

        override fun observeSessionSummary(lang: LangCode): Flow<com.example.umma.domain.model.learningstate.SessionSummary?> =
            flowOf(null)

        override fun observeFlashcardSummary(lang: LangCode): Flow<com.example.umma.domain.model.learningstate.FlashcardSummary?> =
            flowOf(null)

        override suspend fun preload(): Result<Unit> = Result.success(Unit)

        override suspend fun changeSelectedLang(lang: LangCode): Result<Unit> = Result.success(Unit)

        override suspend fun updateLanguageState(input: LangStateUpdateInput): Result<Unit> {
            events += "update"
            if (failUpdate) {
                return Result.failure(IllegalStateException("update failed"))
            }
            lastUpdateInput = input
            return Result.success(Unit)
        }

        override suspend fun createInitial(
            userUid: String,
            userPref: com.example.umma.domain.model.learningstate.UserLangPref,
            langState: com.example.umma.domain.model.learningstate.LangState,
            dashSummary: com.example.umma.domain.model.learningstate.DashSummary,
            sessionSummary: com.example.umma.domain.model.learningstate.SessionSummary,
            flashcardSummary: com.example.umma.domain.model.learningstate.FlashcardSummary
        ): Result<Unit> = Result.success(Unit)

        override suspend fun clear(): Result<Unit> = Result.success(Unit)

        override suspend fun sync(): Result<Unit> = Result.success(Unit)
    }

}
