package com.example.umma.domain.usecase.correction

import com.example.umma.domain.model.correction.CompleteCorrectionInput
import com.example.umma.domain.model.correction.CorrectionSaveRequest
import com.example.umma.domain.model.correction.CorrectionSaveResult
import com.example.umma.domain.model.correction.CorrectionSuggestion
import com.example.umma.domain.model.learningstate.ConversationTurn
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.LangState
import com.example.umma.domain.model.learningstate.LangStateUpdateInput
import com.example.umma.domain.model.learningstate.LearningStateUpdateResult
import com.example.umma.domain.model.learningstate.FlashcardSummaryUpdateInput
import com.example.umma.domain.model.learningstate.FlashcardSummaryUpdateResult
import com.example.umma.domain.model.learningstate.TurnSpeaker
import com.example.umma.domain.model.statistics.StatisticsHistory
import com.example.umma.domain.model.statistics.StatisticsHistoryRecordResult
import com.example.umma.domain.model.realtime.AppendTurnCommand
import com.example.umma.domain.model.realtime.CompressSessionMemoryCommand
import com.example.umma.domain.model.realtime.SessionMemory
import com.example.umma.domain.model.realtime.SessionTurn
import com.example.umma.domain.repository.CorrectionRepository
import com.example.umma.domain.repository.LearningStateRepo
import com.example.umma.domain.repository.StatisticsRepository
import com.example.umma.domain.repository.SessionMemoryRepository
import com.example.umma.domain.usecase.learningstate.ApplyLanguageStateUpdateUseCase
import com.example.umma.domain.usecase.realtime.CompressSessionMemoryUseCase
import com.example.umma.domain.usecase.statistics.BuildStatisticsHistoryUseCase
import com.example.umma.domain.usecase.statistics.RecordStatisticsHistoryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class CompleteCorrectionUseCaseTest {

    // 완료 파이프라인은 순서가 중요한 계약이다.
    // Recording fake 들은 실제 저장소 대신 호출 순서와 실패 정책만 작게 관찰한다.
    private val events = CopyOnWriteArrayList<String>()
    private val correctionRepository = RecordingCorrectionRepository(events)
    private val learningStateRepo = RecordingLearningStateRepo(events)
    private val statisticsRepository = RecordingStatisticsRepository(events)
    private val sessionMemoryRepository = RecordingSessionMemoryRepository(events)
    private val applyLanguageStateUpdateUseCase = ApplyLanguageStateUpdateUseCase(learningStateRepo)
    private val useCase = CompleteCorrectionUseCase(
        prepareSaveRequestUseCase = PrepareSaveRequestUseCase(),
        correctionRepository = correctionRepository,
        applyLanguageStateUpdateUseCase = applyLanguageStateUpdateUseCase,
        recordStatisticsHistoryUseCase = RecordStatisticsHistoryUseCase(
            buildStatisticsHistoryUseCase = BuildStatisticsHistoryUseCase(),
            statisticsRepository = statisticsRepository
        ),
        buildSessionCompressionPayloadUseCase = BuildSessionCompressionPayloadUseCase(),
        compressSessionMemoryUseCase = CompressSessionMemoryUseCase(sessionMemoryRepository)
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
        // 성공 경로는 local save -> LangState/Summary update -> Statistics 기록 -> RT compression 순서를 지켜야 한다.
        // 이 순서가 깨지면 저장되지 않은 교정을 완료 처리하거나, 처리 전 대화를 압축할 수 있다.
        assertEquals(listOf("s-1"), completed.savedFlashcardIds)
        assertEquals(listOf("s-1"), completed.pendingSyncFlashcardIds)
        assertEquals("session-en", completed.sessionMemoryKey)
        assertTrue(completed.sessionCompressionApplied)
        assertFalse(completed.sessionCompressionPending)
        assertTrue(completed.statisticsHistoryApplied)
        assertFalse(completed.statisticsHistoryPending)
        assertEquals(listOf("save", "update", "record-history", "compress"), events)

        assertNotNull(learningStateRepo.lastUpdateInput)
        // 완료된 세션이 다시 Correction 대기 상태로 보이지 않도록 correctionAvailable 을 false 로 내린다.
        assertFalse(learningStateRepo.lastUpdateInput!!.correctionAvailableOverride!!)
        assertNotNull(sessionMemoryRepository.lastCompressionCommand)
    }

    @Test
    fun `fails when selected suggestions are empty`() = kotlinx.coroutines.runBlocking {
        val input = CompleteCorrectionInput(
            selectedSuggestions = emptyList(),
            langStateUpdateInput = baseUpdateInput()
        )

        val result = useCase(input)

        // 선택된 카드가 없으면 save/update/compress 중 어느 단계도 건드리지 않아야 한다.
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
        // LangState 갱신 실패는 local completion 실패다.
        // 이미 저장한 Flashcard 는 보상 rollback 으로 되돌려 부분 완료 상태를 남기지 않는다.
        assertEquals(listOf("save", "update", "rollback-save"), events)
    }

    @Test
    fun `does not rollback previously saved duplicate flashcards when state update fails`() =
        kotlinx.coroutines.runBlocking {
            val suggestion = baseSuggestion()
            learningStateRepo.failUpdate = true
            correctionRepository.nextSaveResult = CorrectionSaveResult(
                localSavedFlashcardIds = emptyList(),
                pendingSyncFlashcardIds = emptyList(),
                savedAt = 1_000L
            )

            val result = useCase(
                CompleteCorrectionInput(
                    selectedSuggestions = listOf(suggestion),
                    langStateUpdateInput = baseUpdateInput()
                )
            )

            assertTrue(result.isFailure)
            // 중복 저장으로 새로 생성된 카드가 없으면 이번 완료 흐름이 만든 local 변경도 없다.
            // 이때 rollback을 호출하면 이미 존재하던 Flashcard를 지울 수 있으므로 호출하지 않는다.
        assertEquals(listOf("save", "update"), events)
    }

    @Test
    fun `does not rollback saved state when statistics history record fails`() = kotlinx.coroutines.runBlocking {
        val suggestion = baseSuggestion()
        statisticsRepository.failRecord = true

        val result = useCase(
            CompleteCorrectionInput(
                selectedSuggestions = listOf(suggestion),
                langStateUpdateInput = baseUpdateInput()
            )
        )

        assertTrue(result.isSuccess)
        val completed = result.getOrThrow()
        // Statistics 기록 실패는 local completion 실패로 끌어올리지 않는다.
        // Flashcard/LangState 저장은 유지되고, statistics 쪽만 진단 메시지로 남는다.
        // 이 테스트는 correction 완료와 statistics 기록을 서로 다른 책임 경계로 본다.
        assertTrue(completed.savedFlashcardIds.isNotEmpty())
        assertTrue(completed.statisticsHistoryApplied.not())
        assertFalse(completed.statisticsHistoryPending)
        assertNotNull(completed.statisticsHistoryErrorMessage)
        assertEquals(listOf("save", "update", "record-history", "compress"), events)
    }

    @Test
    fun `does not rollback saved state when compression fails`() = kotlinx.coroutines.runBlocking {
        val suggestion = baseSuggestion()
        sessionMemoryRepository.failCompression = true

        val result = useCase(
            CompleteCorrectionInput(
                selectedSuggestions = listOf(suggestion),
                langStateUpdateInput = baseUpdateInput()
            )
        )

        assertTrue(result.isSuccess)
        val completed = result.getOrThrow()
        // compression 은 RT-003 후속 정리라 실패해도 사용자 저장 결과는 유지한다.
        // 대신 pending flag 로 후속 재시도 대상임을 알려준다.
        // statistics 쪽이 아니라 RT-003 쪽 실패라는 점을 같이 확인한다.
        assertFalse(completed.sessionCompressionApplied)
        assertTrue(completed.sessionCompressionPending)
        assertEquals(listOf("save", "update", "record-history", "compress"), events)
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
        // 특정 테스트에서 repository 결과를 주입해 중복 저장, pending sync 없음 같은 경계 상황을 만든다.
        // null이면 일반적인 "local 저장 성공 + remote sync 대기" 결과를 반환한다.
        var nextSaveResult: CorrectionSaveResult? = null

        override suspend fun generateSuggestions(
            input: com.example.umma.domain.model.correction.GenerateSuggestionsInput
        ): Result<List<CorrectionSuggestion>> {
            return Result.success(emptyList())
        }

        override suspend fun saveFlashcards(
            request: CorrectionSaveRequest
        ): Result<CorrectionSaveResult> {
            events += "save"
            nextSaveResult?.let { result ->
                nextSaveResult = null
                return Result.success(result)
            }

            // remote sync 가 아직 남아있는 일반적인 local-first 결과를 흉내 낸다.
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

        override suspend fun updateLanguageState(
            input: LangStateUpdateInput
        ): Result<LearningStateUpdateResult> {
            events += "update"
            // state update 실패를 주입해 CompleteCorrectionUseCase 의 rollback 경로를 확인한다.
            if (failUpdate) {
                return Result.failure(IllegalStateException("update failed"))
            }
            lastUpdateInput = input
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
            val flashcardSummary =
                com.example.umma.domain.model.learningstate.FlashcardSummary(
                    lang = input.lang,
                    dueFlashcards = input.dueFlashcards,
                    savedFlashcards = input.savedFlashcards,
                    updatedAt = input.updatedAt
                )
            val dashSummary = com.example.umma.domain.model.learningstate.DashSummary.initial(input.lang)
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

    private class RecordingStatisticsRepository(
        private val events: MutableList<String>
    ) : StatisticsRepository {
        var failRecord: Boolean = false

        override fun observeHistory(
            userId: String,
            language: LangCode
        ): Flow<com.example.umma.domain.model.statistics.StatisticsHistoryState> {
            return flowOf(com.example.umma.domain.model.statistics.StatisticsHistoryState.Empty)
        }

        override suspend fun recordHistory(
            history: StatisticsHistory
        ): Result<StatisticsHistoryRecordResult> {
            events += "record-history"
            if (failRecord) {
                return Result.failure(IllegalStateException("statistics history save failed"))
            }
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

    private class RecordingSessionMemoryRepository(
        private val events: MutableList<String>
    ) : SessionMemoryRepository {
        var failCompression: Boolean = false
        var lastCompressionCommand: CompressSessionMemoryCommand? = null

        override suspend fun appendTurn(command: AppendTurnCommand): Result<Unit> {
            return Result.success(Unit)
        }

        override fun observeRecentFullContext(language: LangCode): Flow<List<SessionTurn>> {
            return emptyFlow()
        }

        override suspend fun getSessionMemory(language: LangCode): Result<SessionMemory> {
            return Result.failure(UnsupportedOperationException("not used"))
        }

        override suspend fun compressSessionMemory(
            command: CompressSessionMemoryCommand
        ): Result<Unit> {
            events += "compress"
            lastCompressionCommand = command
            // compression 실패는 fatal 이 아니라 pending 으로 내려가야 하므로 별도 실패 스위치를 둔다.
            return if (failCompression) {
                Result.failure(IllegalStateException("compression failed"))
            } else {
                Result.success(Unit)
            }
        }

        override fun getCorrectionContext(language: LangCode): Flow<List<SessionTurn>> {
            return emptyFlow()
        }

        override fun getFlashcardContext(language: LangCode): Flow<List<SessionTurn>> {
            return emptyFlow()
        }

        override suspend fun syncPendingTurns(language: LangCode): Result<Unit> {
            return Result.success(Unit)
        }
    }

}
