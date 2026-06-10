package com.app.umma.domain.usecase.correction

import com.app.umma.domain.model.correction.CompleteCorrectionInput
import com.app.umma.domain.model.correction.CorrectionSaveRequest
import com.app.umma.domain.model.correction.CorrectionSaveResult
import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.learningstate.ConversationTurn
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateInput
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateResult
import com.app.umma.domain.model.learningstate.DashSummary
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
import com.app.umma.domain.model.flashcard.Flashcard
import com.app.umma.domain.model.flashcard.FlashcardReviewSummary
import com.app.umma.domain.model.flashcard.FlashcardUpdateResult
import com.app.umma.domain.model.flashcard.ReviewDeckState
import com.app.umma.domain.model.flashcard.ReviewRating
import com.app.umma.domain.model.flashcard.ReviewScheduleResult
import com.app.umma.domain.model.realtime.AppendTurnCommand
import com.app.umma.domain.model.realtime.CompressSessionMemoryCommand
import com.app.umma.domain.model.realtime.SessionMemory
import com.app.umma.domain.model.realtime.SessionTurn
import com.app.umma.domain.model.realtime.SummarizeTopicsCommand
import com.app.umma.domain.model.realtime.TopicSummarySaveResult
import com.app.umma.domain.model.statistics.StatisticsHistory
import com.app.umma.domain.model.statistics.StatisticsHistoryRecordResult
import com.app.umma.domain.repository.CorrectionRepository
import com.app.umma.domain.repository.FlashcardRepository
import com.app.umma.domain.repository.LearningStateRepo
import com.app.umma.domain.repository.SessionMemoryRepository
import com.app.umma.domain.repository.StatisticsRepository
import com.app.umma.domain.usecase.learningstate.ApplyCorrectionSignalUpdateUseCase
import com.app.umma.domain.usecase.learningstate.ApplyFlashcardSummaryUpdateUseCase
import com.app.umma.domain.usecase.learningstate.ApplyLanguageStateUpdateUseCase
import com.app.umma.domain.usecase.learningstate.BuildLearnerAdaptationProfileUseCase
import com.app.umma.domain.usecase.learningstate.DefaultLangStateAnalysisPolicy
import com.app.umma.domain.usecase.realtime.CompressSessionMemoryUseCase
import com.app.umma.domain.usecase.realtime.SummarizeRecentTopicsUseCase
import com.app.umma.domain.usecase.statistics.BuildStatisticsHistoryUseCase
import com.app.umma.domain.usecase.statistics.RecordStatisticsHistoryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 완료 파이프라인 프로세스 사망 재진입 시나리오 회귀 테스트.
 *
 * 위험 창(①Flashcard commit ~ `clearCorrectionCacheAfterCompletion` 이전) 에서
 * 프로세스가 사망했다가 재진입할 때, 같은 `analysisEventId` 로 파이프라인이 다시 실행되어도
 * Flashcard / LangState / Statistics 가 중복 반영되지 않음을 보증한다.
 *
 * 참고: [COR-FIX-012 #370]
 */
class CompleteCorrectionReentryTest {

    // 저장된 id 를 추적해 2회차 요청 시 INSERT IGNORE 동작을 재현하는 Stateful fake
    private val savedFlashcardIds = mutableSetOf<String>()
    private val correctionRepository = StatefulCorrectionRepository(savedFlashcardIds)

    // updateLanguageState 결과에서 preparedState 를 보존해 2회차 currentState 로 사용
    private val learningStateRepo = StatefulLearningStateRepo()

    private val statisticsRepository = TrackingStatisticsRepository()
    private val sessionMemoryRepository = NoopSessionMemoryRepository()
    private val flashcardRepository = MinimalFlashcardRepository()

    private val useCase = CompleteCorrectionUseCase(
        prepareSaveRequestUseCase = PrepareSaveRequestUseCase(CorrectionSafetyPolicy()),
        correctionRepository = correctionRepository,
        applyLanguageStateUpdateUseCase = ApplyLanguageStateUpdateUseCase(
            learningStateRepo,
            DefaultLangStateAnalysisPolicy()
        ),
        applyCorrectionSignalUpdateUseCase = ApplyCorrectionSignalUpdateUseCase(learningStateRepo),
        recordStatisticsHistoryUseCase = RecordStatisticsHistoryUseCase(
            buildStatisticsHistoryUseCase = BuildStatisticsHistoryUseCase(
                buildLearnerAdaptationProfileUseCase = BuildLearnerAdaptationProfileUseCase()
            ),
            statisticsRepository = statisticsRepository
        ),
        buildSessionCompressionPayloadUseCase = BuildSessionCompressionPayloadUseCase(),
        compressSessionMemoryUseCase = CompressSessionMemoryUseCase(sessionMemoryRepository),
        flashcardRepository = flashcardRepository,
        applyFlashcardSummaryUpdateUseCase = ApplyFlashcardSummaryUpdateUseCase(learningStateRepo),
        summarizeRecentTopicsUseCase = SummarizeRecentTopicsUseCase(sessionMemoryRepository)
    )

    // ----- 회귀 테스트 -----

    @Test
    fun `does not save new flashcards on reentry with the same analysisEventId`() = runBlocking {
        val analysisEventId = "analysis-reentry-001"

        // 1회차 완료
        useCase(makeInput(analysisEventId, LangState.initial(LangCode.EN)))

        // 2회차: 재진입 — 같은 analysisEventId, 저장된 카드 id 가 이미 존재
        val secondResult = useCase(
            makeInput(
                analysisEventId = analysisEventId,
                // currentState 는 1회차 저장 후 상태 그대로 (lastAnalysisEventId 가 세팅됨)
                currentState = learningStateRepo.savedState
            )
        )

        assertTrue(secondResult.isSuccess)
        // Flashcard 새 저장이 없어야 한다 — savedFlashcardIds 가 비어 있음
        assertTrue(
            "재진입 시 Flashcard 중복 저장 없음",
            secondResult.getOrThrow().savedFlashcardIds.isEmpty()
        )
    }

    @Test
    fun `does not apply LangState update again on reentry with the same analysisEventId`() = runBlocking {
        val analysisEventId = "analysis-reentry-002"

        // 1회차
        useCase(makeInput(analysisEventId, LangState.initial(LangCode.EN)))
        val firstUpdateCount = learningStateRepo.updateCallCount

        // 2회차: currentState.lastAnalysisEventId == analysisEventId → ApplyLanguageStateUpdateUseCase early-return
        useCase(makeInput(analysisEventId, learningStateRepo.savedState))

        // dedup early-return 으로 2회차에서 repo.updateLanguageState 가 재호출되면 안 된다
        assertEquals(
            "2회차 완료 재진입 시 LangState update 는 1회차 이후 추가 호출이 없어야 한다",
            firstUpdateCount,
            learningStateRepo.updateCallCount
        )
    }

    @Test
    fun `produces deterministic statistics history id on reentry with the same analysisEventId`() = runBlocking {
        val analysisEventId = "analysis-reentry-003"

        // 1회차
        useCase(makeInput(analysisEventId, LangState.initial(LangCode.EN)))
        val firstHistoryIdCount = statisticsRepository.recordedHistoryIds.size

        // 2회차
        useCase(makeInput(analysisEventId, learningStateRepo.savedState))

        // Statistics history id = "${userId}_${lang}_${sourceEventId}" 로 결정적.
        // 2회차에서도 같은 id 가 set.add 로 들어오므로 고유 id 수는 늘지 않는다.
        assertEquals(
            "동일 analysisEventId 재완료 시 Statistics 고유 history id 수가 늘지 않는다",
            firstHistoryIdCount,
            statisticsRepository.recordedHistoryIds.size
        )
    }

    @Test
    fun `saves flashcards on first completion and returns success on reentry`() = runBlocking {
        val analysisEventId = "analysis-reentry-004"

        val firstResult = useCase(makeInput(analysisEventId, LangState.initial(LangCode.EN)))
        assertTrue(firstResult.isSuccess)
        assertFalse("1회차는 카드가 저장되어야 한다", firstResult.getOrThrow().savedFlashcardIds.isEmpty())

        val reentryResult = useCase(makeInput(analysisEventId, learningStateRepo.savedState))
        // 재진입이 실패 처리되어선 안 된다 — dedup 은 성공 흐름 안에서 무해하게 처리된다
        assertTrue("재진입 2회차도 success 를 유지해야 한다", reentryResult.isSuccess)
    }

    // ----- 헬퍼 -----

    private fun makeInput(analysisEventId: String, currentState: LangState): CompleteCorrectionInput {
        return CompleteCorrectionInput(
            selectedSuggestions = listOf(
                CorrectionSuggestion(
                    id = "s-reentry-1",
                    lang = LangCode.EN,
                    sourceCandidateIds = listOf("c-1"),
                    sourceTurnIndex = 0,
                    beforeText = "i go school",
                    nativeText = "나는 학교에 간다",
                    afterText = "I go to school.",
                    explanation = "전치사 to 추가"
                )
            ),
            langStateUpdateInput = LangStateUpdateInput(
                uid = "uid-reentry",
                lang = LangCode.EN,
                sessionMemoryKey = "session-en-reentry",
                analysisEventId = analysisEventId,
                currentState = currentState,
                preparedState = null,
                recentUserTurns = listOf(
                    ConversationTurn(
                        speaker = TurnSpeaker.USER,
                        text = "i go school",
                        tokenCount = 3,
                        durationMs = 1_500L
                    )
                ),
                correctionResult = null,
                flashcardReviewEvents = emptyList(),
                analyzedAt = 1_000L,
            ),
            requestedAt = 1_500L,
        )
    }

    // ----- Stateful Fakes -----

    /**
     * 한 번 저장된 suggestionId 를 기억해 두 번째 요청에서는 `localSavedFlashcardIds=[]` 를 반환한다.
     * Room INSERT IGNORE 동작을 재현한다.
     */
    private class StatefulCorrectionRepository(
        private val persistedIds: MutableSet<String>
    ) : CorrectionRepository {

        override suspend fun generateSuggestions(
            input: com.app.umma.domain.model.correction.GenerateSuggestionsInput
        ): Result<List<CorrectionSuggestion>> = Result.success(emptyList())

        override suspend fun saveFlashcards(request: CorrectionSaveRequest): Result<CorrectionSaveResult> {
            // 이미 저장된 id 를 걸러내 새로 insert 되는 id 만 반환 (INSERT IGNORE 재현)
            val newIds = request.flashcards
                .map { it.suggestionId }
                .filter { persistedIds.add(it) }

            return Result.success(
                CorrectionSaveResult(
                    localSavedFlashcardIds = newIds,
                    pendingSyncFlashcardIds = newIds,
                    savedAt = request.requestedAt,
                )
            )
        }

        override suspend fun rollbackFlashcards(request: CorrectionSaveRequest): Result<Unit> {
            request.flashcards.forEach { persistedIds.remove(it.suggestionId) }
            return Result.success(Unit)
        }
    }

    /**
     * updateLanguageState 호출 후 preparedState(policy 가 lastAnalysisEventId 를 세팅한 상태)를 보존한다.
     * 다음 호출의 currentState 로 넘겨 ApplyLanguageStateUpdateUseCase 의 dedup 을 트리거할 수 있다.
     * 호출 횟수도 추적해 2회차에서 실제로 repo 에 도달하지 않았는지 검증한다.
     */
    private inner class StatefulLearningStateRepo : LearningStateRepo {

        var savedState: LangState = LangState.initial(LangCode.EN)
        var updateCallCount: Int = 0

        override fun observeLearningState(): Flow<GlobalLangState> = flowOf(GlobalLangState.initial())
        override fun observeUserPref(): Flow<UserLangPref?> = flowOf(null)
        override fun observeLangState(lang: LangCode): Flow<LangState?> = flowOf(null)
        override fun observeDashSummary(lang: LangCode): Flow<DashSummary?> = flowOf(null)
        override fun observeSessionSummary(lang: LangCode): Flow<SessionSummary?> = flowOf(null)
        override fun observeFlashcardSummary(lang: LangCode): Flow<FlashcardSummary?> = flowOf(null)
        override suspend fun preload(): Result<Unit> = Result.success(Unit)
        override suspend fun changeSelectedLang(lang: LangCode): Result<Unit> = Result.success(Unit)
        override suspend fun changePrimaryLang(lang: LangCode): Result<Unit> = Result.success(Unit)

        override suspend fun updateLanguageState(input: LangStateUpdateInput): Result<LearningStateUpdateResult> {
            updateCallCount++
            // preparedState 는 ApplyLanguageStateUpdateUseCase 가 policy 를 적용해 주입한 상태다.
            // lastAnalysisEventId 가 여기에 세팅되므로, 다음 호출의 currentState 로 쓸 수 있다.
            savedState = input.preparedState ?: input.currentState
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

        override suspend fun updateFlashcardSummary(input: FlashcardSummaryUpdateInput): Result<FlashcardSummaryUpdateResult> {
            val flashcardSummary = FlashcardSummary(
                lang = input.lang,
                dueFlashcards = input.dueFlashcards,
                savedFlashcards = input.savedFlashcards,
                updatedAt = input.updatedAt
            )
            val dashSummary = DashSummary.initial(input.lang)
                .copy(
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

        override suspend fun updateCorrectionSignal(input: CorrectionSignalUpdateInput): Result<CorrectionSignalUpdateResult> {
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
            userPref: UserLangPref,
            langState: LangState,
            dashSummary: DashSummary,
            sessionSummary: SessionSummary,
            flashcardSummary: FlashcardSummary
        ): Result<Unit> = Result.success(Unit)

        override suspend fun clear(): Result<Unit> = Result.success(Unit)
        override suspend fun sync(): Result<Unit> = Result.success(Unit)
    }

    /**
     * 기록된 history id 를 Set 으로 관리한다.
     * Set.add 는 중복을 허용하지 않아 Room REPLACE 의 "단일 row 유지" 의미를 재현한다.
     */
    private class TrackingStatisticsRepository : StatisticsRepository {
        val recordedHistoryIds = mutableSetOf<String>()

        override fun observeHistory(
            userId: String,
            language: LangCode
        ): Flow<com.app.umma.domain.model.statistics.StatisticsHistoryState> =
            flowOf(com.app.umma.domain.model.statistics.StatisticsHistoryState.Empty)

        override suspend fun recordHistory(history: StatisticsHistory): Result<StatisticsHistoryRecordResult> {
            recordedHistoryIds.add(history.id)
            return Result.success(
                StatisticsHistoryRecordResult(
                    historyId = history.id,
                    sourceEventId = history.sourceEventId,
                    applied = true,
                    isSyncPending = false,
                    recordedAt = history.recordedAt
                )
            )
        }
    }

    private class NoopSessionMemoryRepository : SessionMemoryRepository {
        override suspend fun appendTurn(command: AppendTurnCommand): Result<Unit> = Result.success(Unit)
        override fun observeRecentFullContext(language: LangCode): Flow<List<SessionTurn>> = emptyFlow()
        override suspend fun getSessionMemory(language: LangCode): Result<SessionMemory> =
            Result.failure(UnsupportedOperationException("not used"))
        override suspend fun compressSessionMemory(command: CompressSessionMemoryCommand): Result<Unit> =
            Result.success(Unit)
        override fun getCorrectionContext(language: LangCode): Flow<List<SessionTurn>> = emptyFlow()
        override fun getFlashcardContext(language: LangCode): Flow<List<SessionTurn>> = emptyFlow()
        override suspend fun syncPendingTurns(language: LangCode): Result<Unit> = Result.success(Unit)
        override suspend fun summarizeAndSaveTopics(command: SummarizeTopicsCommand): Result<TopicSummarySaveResult> =
            Result.success(TopicSummarySaveResult(applied = false, displayTitle = null))
    }

    private class MinimalFlashcardRepository : FlashcardRepository {
        override fun observeDueFlashcards(userId: String, language: LangCode): Flow<ReviewDeckState> =
            emptyFlow()

        override suspend fun updateFlashcardSchedule(
            userId: String,
            cardId: String,
            result: ReviewScheduleResult,
            lastReviewRating: ReviewRating?,
            lastReviewedAt: Long?
        ): Result<FlashcardUpdateResult> = Result.failure(UnsupportedOperationException("not used"))

        override suspend fun getReviewSummary(
            userId: String,
            language: LangCode,
            now: Long
        ): Result<FlashcardReviewSummary> = Result.success(
            FlashcardReviewSummary(dueFlashcards = 0, savedFlashcards = 1)
        )

        override suspend fun syncDirtyFlashcards(userId: String): Result<Int> = Result.success(0)

        override suspend fun getFlashcards(userId: String, language: LangCode): Result<List<Flashcard>> =
            Result.success(emptyList())

        override suspend fun deleteFlashcards(userId: String, flashcardIds: List<String>): Result<Unit> =
            Result.success(Unit)
    }
}
