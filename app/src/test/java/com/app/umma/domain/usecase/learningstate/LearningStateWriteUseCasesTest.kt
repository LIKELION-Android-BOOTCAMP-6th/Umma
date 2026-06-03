package com.app.umma.domain.usecase.learningstate

import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.ConversationTurn
import com.app.umma.domain.model.learningstate.CorrectionResult
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateInput
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateResult
import com.app.umma.domain.model.learningstate.FlashcardSummary
import com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateInput
import com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateResult
import com.app.umma.domain.model.learningstate.GlobalLangState
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LangStateUpdateInput
import com.app.umma.domain.model.learningstate.LearningStateUpdateResult
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.learningstate.UserLangPref
import com.app.umma.domain.model.learningstate.VocabLevel
import com.app.umma.domain.repository.LearningStateRepo
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
        // duplicate event 재입력은 계산 자체를 막아야 하므로, policy 호출 여부까지 같이 본다.
        val repo = RecordingLearningStateRepo()
        val policy = RecordingLangStateAnalysisPolicy()
        val useCase = ApplyLanguageStateUpdateUseCase(repo, policy)
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
        assertEquals(0, policy.analyzeCalls)
    }

    @Test
    fun `force reanalysis bypasses duplicate guard and delegates to policy`() = runBlocking {
        // forceReanalysis는 중복 이벤트라도 사용자가 다시 분석을 요청한 상황을 재현한다.
        val repo = RecordingLearningStateRepo()
        val policy = RecordingLangStateAnalysisPolicy()
        val useCase = ApplyLanguageStateUpdateUseCase(repo, policy)
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
                analyzedAt = 3_000L,
                forceReanalysis = true
            )
        ).getOrThrow()

        // forceReanalysis는 같은 eventId라도 정책 계산을 다시 수행해야 하는 명시적 override다.
        assertTrue(result.applied)
        assertEquals(1, policy.analyzeCalls)
        assertEquals(1, repo.languageStateUpdateCalls)
        assertEquals("analysis-1", result.savedState.lastAnalysisEventId)
    }

    @Test
    fun `uses prepared state without recalculating policy`() = runBlocking {
        // caller가 미리 preparedState를 만든 경우에는 재계산 없이 그대로 저장소로 넘겨야 한다.
        val repo = RecordingLearningStateRepo()
        val policy = RecordingLangStateAnalysisPolicy()
        val useCase = ApplyLanguageStateUpdateUseCase(repo, policy)
        val current = LangState.initial(
            lang = LangCode.EN,
            createdAt = 1_000L,
            updatedAt = 2_000L
        )
        val prepared = current.copy(
            updatedAt = 3_000L,
            lastAnalyzedAt = 3_000L,
            lastAnalysisEventId = "prepared-1"
        )

        val result = useCase(
            LangStateUpdateInput(
                uid = "uid-1",
                lang = LangCode.EN,
                sessionMemoryKey = "session-en",
                analysisEventId = "prepared-1",
                currentState = current,
                preparedState = prepared,
                recentUserTurns = emptyList(),
                correctionResult = null,
                flashcardReviewEvents = emptyList(),
                analyzedAt = 3_000L
            )
        ).getOrThrow()

        // caller가 이미 preparedState를 만든 경우에는 기존 계약대로 그 snapshot을 그대로 저장소에 넘긴다.
        assertEquals(prepared, result.savedState)
        assertEquals(0, policy.analyzeCalls)
        assertEquals(1, repo.languageStateUpdateCalls)
    }

    @Test
    fun `passes valid flashcard summary update to repository`() = runBlocking {
        // SRS가 계산한 요약값은 그대로 전역 summary에 반영되어야 한다.
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
    fun `calculates vocabulary and expression metrics from correction batch`() = runBlocking {
        // 실제 user turn이 있을 때만 policy가 내부 metric을 채우는지 확인한다.
        val repo = RecordingLearningStateRepo()
        val useCase = ApplyLanguageStateUpdateUseCase(repo, DefaultLangStateAnalysisPolicy())
        val current = LangState.initial(
            lang = LangCode.EN,
            createdAt = 1_000L,
            updatedAt = 2_000L
        )

        val result = useCase(
            LangStateUpdateInput(
                uid = "uid-1",
                lang = LangCode.EN,
                sessionMemoryKey = "session-en",
                analysisEventId = "analysis-vocab-1",
                currentState = current,
                recentUserTurns = listOf(
                    ConversationTurn(
                        speaker = TurnSpeaker.USER,
                        text = "I planned a weekend trip because the museum exhibition looked inspiring.",
                        tokenCount = 11,
                        durationMs = 5_000L
                    ),
                    ConversationTurn(
                        speaker = TurnSpeaker.USER,
                        text = "The neighborhood cafe was quiet, so I practiced describing the atmosphere.",
                        tokenCount = 11,
                        durationMs = 5_500L
                    )
                ),
                correctionResult = CorrectionResult(
                    correctedText = "I planned a weekend trip because the museum exhibition looked inspiring.",
                    correctionCount = 1,
                    notes = "One expression was corrected for natural wording."
                ),
                flashcardReviewEvents = emptyList(),
                analyzedAt = 3_000L
            )
        ).getOrThrow()

        val savedState = result.savedState

        // user turn 기반으로 계산 가능한 값은 repository가 아니라 UseCase에서 preparedState로 만든다.
        // 이 값들이 채워져야 StatisticsHistory가 LearningState snapshot을 그대로 저장해도 real 데이터처럼 보인다.
        assertTrue(savedState.internal.vocabularyAppropriateness > 0.0)
        assertTrue(savedState.internal.lexicalDiversity > 0.0)
        assertTrue(savedState.internal.sentenceComplexity > 0.0)
        assertTrue(savedState.internal.avgUtteranceLength > 0.0)
        assertTrue(savedState.internal.naturalExpressionUsage > 0.0)
        assertTrue(savedState.internal.errorRecurrence > 0.0)
        assertEquals(VocabLevel.A2, savedState.internal.vocabularyLevel)
        assertEquals(VocabLevel.A2, savedState.external.vocabularyLevel)
        assertTrue(savedState.external.expressionRange > 0)
        assertTrue(savedState.external.fluencyScore > 0.0)
        assertTrue(savedState.external.naturalnessScore > 0.0)
        assertEquals(1, repo.languageStateUpdateCalls)
    }

    @Test
    fun `keeps expression range when repeated batch has fewer unique tokens`() = runBlocking {
        // 반복 표현이 적은 batch가 기존 expressionRange를 떨어뜨리지 않는지 확인한다.
        val repo = RecordingLearningStateRepo()
        val useCase = ApplyLanguageStateUpdateUseCase(repo, DefaultLangStateAnalysisPolicy())
        val current = LangState.initial(
            lang = LangCode.EN,
            createdAt = 1_000L,
            updatedAt = 2_000L
        ).copy(
            external = LangState.initial(LangCode.EN).external.copy(
                expressionRange = 30
            )
        )

        val result = useCase(
            LangStateUpdateInput(
                uid = "uid-1",
                lang = LangCode.EN,
                sessionMemoryKey = "session-en",
                analysisEventId = "analysis-repeat-1",
                currentState = current,
                recentUserTurns = listOf(
                    ConversationTurn(
                        speaker = TurnSpeaker.USER,
                        text = "I visited a museum and the museum was quiet.",
                        tokenCount = 9,
                        durationMs = 4_000L
                    )
                ),
                correctionResult = CorrectionResult(
                    correctedText = "I visited a museum, and it was quiet.",
                    correctionCount = 1
                ),
                flashcardReviewEvents = emptyList(),
                analyzedAt = 3_000L
            )
        ).getOrThrow()

        // 별도 expression signature 저장소가 생기기 전에는 반복 단어를 "신규 표현"처럼 누적하지 않는다.
        // 현재 batch의 고유 token 수가 이전 expressionRange보다 작으면 기존 표현 폭을 유지한다.
        assertEquals(30, result.savedState.external.expressionRange)
        assertEquals(1, repo.languageStateUpdateCalls)
    }

    @Test
    fun `default policy keeps existing metrics when input has no analyzable signal`() {
        // 분석할 사용자 발화가 없으면 null을 0으로 바꾸지 않고 기존 snapshot을 유지해야 한다.
        val policy = DefaultLangStateAnalysisPolicy()
        val current = LangState.initial(
            lang = LangCode.EN,
            createdAt = 1_000L,
            updatedAt = 2_000L
        ).copy(
            internal = LangState.initial(LangCode.EN).internal.copy(
                grammarAccuracy = 0.64,
                vocabularyAppropriateness = 0.55,
                lexicalDiversity = 0.48,
                vocabularyLevel = VocabLevel.B1,
                sentenceComplexity = 0.42,
                speechRate = 0.51,
                pauseFrequency = 0.12,
                avgUtteranceLength = 0.46,
                spokenNaturalness = 0.58,
                naturalExpressionUsage = 0.49,
                errorRecurrence = 0.22,
                reviewRetention = 0.7
            ),
            external = LangState.initial(LangCode.EN).external.copy(
                vocabularyLevel = VocabLevel.B1,
                grammarAccuracy = 0.64,
                expressionRange = 30,
                fluencyScore = 0.46,
                naturalnessScore = 0.53
            )
        )

        val next = policy.analyze(
            LangStateUpdateInput(
                uid = "uid-1",
                lang = LangCode.EN,
                sessionMemoryKey = "session-en",
                analysisEventId = "empty-1",
                currentState = current,
                recentUserTurns = emptyList(),
                correctionResult = null,
                flashcardReviewEvents = emptyList(),
                analyzedAt = 3_000L
            )
        )

        // null/empty 입력에서는 측정값이 없으므로 내부 metric과 expressionRange가 사라지면 안 된다.
        assertEquals(current.internal, next.internal)
        assertEquals(30, next.external.expressionRange)
        assertEquals(VocabLevel.B1, next.external.vocabularyLevel)
        assertEquals(3_000L, next.updatedAt)
        assertEquals("empty-1", next.lastAnalysisEventId)
    }

    @Test
    fun `passes correction signal update to repository`() = runBlocking {
        // lightweight signal 경로는 LangState 계산 없이 summary flag만 갱신해야 한다.
        val repo = RecordingLearningStateRepo()
        val useCase = ApplyCorrectionSignalUpdateUseCase(repo)

        val result = useCase(
            CorrectionSignalUpdateInput(
                uid = "uid-1",
                lang = LangCode.EN,
                sessionMemoryKey = "session-en",
                sourceEventId = "turn-1",
                recentMinutes = 7,
                recentTopic = "Travel",
                updatedAt = 5_000L
            )
        ).getOrThrow()

        // lightweight signal 은 LangState 분석 없이 session/dash summary 만 함께 갱신해야 한다.
        // 기본 Chat final turn 신호는 correctionAvailable=true 입력으로 repo까지 그대로 전달된다.
        assertTrue(result.applied)
        assertEquals(1, repo.correctionSignalUpdateCalls)
        assertEquals("turn-1", repo.lastCorrectionSignalInput?.sourceEventId)
        assertEquals(true, repo.lastCorrectionSignalInput?.correctionAvailable)
        assertTrue(result.sessionSummary.correctionAvailable)
        assertTrue(result.dashSummary.correctionAvailable)
        assertEquals(7, result.sessionSummary.recentMinutes)
        assertEquals("Travel", result.sessionSummary.recentTopic)
    }

    @Test
    fun `rejects negative flashcard summary counts`() = runBlocking {
        // 음수 due count는 repository에 전달하기 전에 바로 차단한다.
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
        // fake repo는 저장 여부만 기록하고, 실제 계산/저장소 부작용은 만들지 않는다.
        private val state = MutableStateFlow(GlobalLangState.initial())
        var languageStateUpdateCalls: Int = 0
        var flashcardSummaryUpdateCalls: Int = 0
        var correctionSignalUpdateCalls: Int = 0
        var lastCorrectionSignalInput: CorrectionSignalUpdateInput? = null

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
            // UseCase가 만든 preparedState가 그대로 저장소로 넘어왔는지 확인하려고 echo한다.
            languageStateUpdateCalls += 1
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

        override suspend fun updateCorrectionSignal(
            input: CorrectionSignalUpdateInput
        ): Result<CorrectionSignalUpdateResult> {
            // 입력 검증 후 repo에 정확히 전달됐는지만 확인하고, 정책 값은 바꾸지 않는다.
            correctionSignalUpdateCalls += 1
            lastCorrectionSignalInput = input
            val sessionSummary = SessionSummary(
                lang = input.lang,
                correctionAvailable = input.correctionAvailable,
                recentMinutes = input.recentMinutes ?: 0,
                recentTopic = input.recentTopic,
                updatedAt = input.updatedAt
            )
            val dashSummary = DashSummary(
                lang = input.lang,
                recentMinutes = input.recentMinutes ?: 0,
                recentTopic = input.recentTopic,
                correctionAvailable = input.correctionAvailable,
                dueFlashcards = 0,
                savedFlashcards = 0,
                grammarDelta = 0,
                fluencyDelta = 0,
                vocabDelta = 0,
                naturalnessDelta = 0,
                updatedAt = input.updatedAt
            )
            return Result.success(
                CorrectionSignalUpdateResult(
                    lang = input.lang,
                    sessionSummary = sessionSummary,
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

    private class RecordingLangStateAnalysisPolicy : LangStateAnalysisPolicy {
        // duplicate 방어 이후에만 policy가 호출되는지 세기 위한 계측값이다.
        var analyzeCalls: Int = 0

        override fun analyze(input: LangStateUpdateInput): LangState {
            analyzeCalls += 1
            // 테스트용 policy는 계산 책임이 UseCase 밖으로 분리됐는지만 확인한다.
            // 실제 산출식 검증은 DefaultLangStateAnalysisPolicy가 담당한다.
            return input.currentState.copy(
                updatedAt = input.analyzedAt,
                lastAnalyzedAt = input.analyzedAt,
                lastAnalysisEventId = input.analysisEventId ?: input.currentState.lastAnalysisEventId
            )
        }
    }
}
