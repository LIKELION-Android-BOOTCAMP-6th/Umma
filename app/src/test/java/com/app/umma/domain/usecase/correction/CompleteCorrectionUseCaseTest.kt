package com.app.umma.domain.usecase.correction

import com.app.umma.domain.model.correction.CompleteCorrectionInput
import com.app.umma.domain.model.correction.CorrectionSaveRequest
import com.app.umma.domain.model.correction.CorrectionSaveResult
import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.learningstate.ConversationTurn
import com.app.umma.domain.model.learningstate.CorrectionLearningSignal
import com.app.umma.domain.model.learningstate.CorrectionSeverity
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateInput
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateResult
import com.app.umma.domain.model.learningstate.SpokenRegister
import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.FlashcardSummary
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.GlobalLangState
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LangStateUpdateInput
import com.app.umma.domain.model.learningstate.LearningStateUpdateResult
import com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateInput
import com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateResult
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.UserLangPref
import com.app.umma.domain.model.statistics.StatisticsHistory
import com.app.umma.domain.model.statistics.StatisticsHistoryRecordResult
import com.app.umma.domain.model.realtime.AppendTurnCommand
import com.app.umma.domain.model.realtime.CompressSessionMemoryCommand
import com.app.umma.domain.model.realtime.SessionMemory
import com.app.umma.domain.model.realtime.SessionTurn
import com.app.umma.domain.model.flashcard.Flashcard
import com.app.umma.domain.model.flashcard.FlashcardReviewSummary
import com.app.umma.domain.model.flashcard.FlashcardUpdateResult
import com.app.umma.domain.model.flashcard.ReviewDeckState
import com.app.umma.domain.model.flashcard.ReviewRating
import com.app.umma.domain.model.flashcard.ReviewScheduleResult
import com.app.umma.domain.model.realtime.TopicSummarySaveResult
import com.app.umma.domain.repository.CorrectionRepository
import com.app.umma.domain.repository.FlashcardRepository
import com.app.umma.domain.repository.LearningStateRepo
import com.app.umma.domain.repository.StatisticsRepository
import com.app.umma.domain.repository.SessionMemoryRepository
import com.app.umma.domain.usecase.learningstate.ApplyCorrectionSignalUpdateUseCase
import com.app.umma.domain.usecase.learningstate.ApplyFlashcardSummaryUpdateUseCase
import com.app.umma.domain.usecase.learningstate.ApplyLanguageStateUpdateUseCase
import com.app.umma.domain.usecase.learningstate.DefaultLangStateAnalysisPolicy
import com.app.umma.domain.usecase.learningstate.BuildLearnerAdaptationProfileUseCase
import com.app.umma.domain.model.realtime.SummarizeTopicsCommand
import com.app.umma.domain.usecase.realtime.CompressSessionMemoryUseCase
import com.app.umma.domain.usecase.realtime.SummarizeRecentTopicsUseCase
import com.app.umma.domain.usecase.statistics.BuildStatisticsHistoryUseCase
import com.app.umma.domain.usecase.statistics.RecordStatisticsHistoryUseCase
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
    private val flashcardRepository = RecordingFlashcardRepository()
    private val applyLanguageStateUpdateUseCase = ApplyLanguageStateUpdateUseCase(
        learningStateRepo,
        DefaultLangStateAnalysisPolicy()
    )
    private val applyCorrectionSignalUpdateUseCase = ApplyCorrectionSignalUpdateUseCase(
        learningStateRepo
    )
    private val useCase = CompleteCorrectionUseCase(
        prepareSaveRequestUseCase = PrepareSaveRequestUseCase(CorrectionSafetyPolicy()),
        correctionRepository = correctionRepository,
        applyLanguageStateUpdateUseCase = applyLanguageStateUpdateUseCase,
        applyCorrectionSignalUpdateUseCase = applyCorrectionSignalUpdateUseCase,
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

    @Test
    fun `runs save and state update in order`() = kotlinx.coroutines.runBlocking {
        val suggestion = CorrectionSuggestion(
            id = "s-1",
            lang = LangCode.EN,
            sourceCandidateIds = listOf("c-1"),
            sourceTurnIndex = 0,
            beforeText = "i go school",
            nativeText = "나는 학교에 간다",
            afterText = "I go to school.",
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
        // 성공 경로는 local save -> Flashcard Summary 갱신 -> LangState/Summary update
        //   -> Statistics 기록 -> RT compression 순서를 지켜야 한다.
        // 이 순서가 깨지면 저장되지 않은 교정을 완료 처리하거나, 처리 전 대화를 압축할 수 있다.
        assertEquals(listOf("s-1"), completed.savedFlashcardIds)
        assertEquals(listOf("s-1"), completed.pendingSyncFlashcardIds)
        assertEquals("session-en", completed.sessionMemoryKey)
        assertTrue(completed.sessionCompressionApplied)
        assertFalse(completed.sessionCompressionPending)
        assertTrue(completed.statisticsHistoryApplied)
        assertFalse(completed.statisticsHistoryPending)
        // (#162-D) Flashcard Summary 갱신이 성공 경로에서 반영되어야 한다.
        assertTrue(completed.flashcardSummaryApplied)
        assertFalse(completed.flashcardSummaryPending)
        // (#162-D) saveFlashcards 직후, LangState 갱신(update) 직전에 update-flashcard-summary 가 위치해야 한다.
        // (#173) summarize-topics 는 Dashboard title 을 update 에 전달해야 하므로 update 직전에 위치한다.
        assertEquals(
            listOf("save", "update-flashcard-summary", "summarize-topics", "update", "record-history", "compress"),
            events
        )

        assertNotNull(learningStateRepo.lastUpdateInput)
        // 완료된 세션이 다시 Correction 대기 상태로 보이지 않도록 correctionAvailable 을 false 로 내린다.
        assertFalse(learningStateRepo.lastUpdateInput!!.correctionAvailableOverride!!)
        assertNotNull(sessionMemoryRepository.lastCompressionCommand)
    }

    @Test
    fun `aggregates learning signals of selected suggestions into correction result`() = kotlinx.coroutines.runBlocking {
        // COR-TUNE-02: 선택된 suggestion 의 learningSignal 만 CorrectionResult.learningSignals 로 집계되어
        // LangState 갱신 입력으로 흘러야 한다. signal 이 없는 suggestion 은 mapNotNull 로 빠진다.
        val signal = CorrectionLearningSignal(
            candidateId = "c-1",
            sourceTurnId = "turn-1",
            sourceTurnIndex = 0,
            sourceText = "i go school",
            correctedText = "I go to school.",
            issueCategories = emptyList(),
            languageFeatures = emptyList(),
            improvementTypes = emptyList(),
            editSpans = emptyList(),
            register = SpokenRegister.EverydaySpoken,
            severity = CorrectionSeverity.MinorForm,
            meaningPreserved = true,
            confidence = 0.7
        )
        val withSignal = baseSuggestion().copy(id = "s-1", learningSignal = signal)
        // 두 번째 suggestion 은 signal 이 없다(null). 집계에서 자연스럽게 제외되어야 한다.
        val withoutSignal = baseSuggestion().copy(id = "s-2", learningSignal = null)

        val result = useCase(
            CompleteCorrectionInput(
                selectedSuggestions = listOf(withSignal, withoutSignal),
                langStateUpdateInput = baseUpdateInput()
            )
        )

        assertTrue(result.isSuccess)
        val captured = learningStateRepo.lastUpdateInput!!.correctionResult!!
        // signal 이 있는 suggestion 1개만 집계된다.
        assertEquals(1, captured.learningSignals.size)
        assertEquals("c-1", captured.learningSignals.single().candidateId)
        // 품질 필터/중복 제거로 저장 대상에 남은 suggestion 만 LangState 근거가 된다.
        assertEquals(1, captured.correctionCount)
    }

    @Test
    fun `keeps learning signal in evaluation when sourceLang matches selected language`() =
        kotlinx.coroutines.runBlocking {
            // COR-TUNE-011: 발화 원문 언어(sourceLang)가 학습 대상 언어(selectedLang)와 같으면
            // 종전대로 능력 근거(metricEvidence/activeFocus) 평가에 반영되어야 한다.
            val suggestion = baseSuggestion().copy(
                id = "s-1",
                sourceLang = LangCode.EN,
                learningSignal = baseLearningSignal(candidateId = "c-1")
            )

            val result = useCase(
                CompleteCorrectionInput(
                    selectedSuggestions = listOf(suggestion),
                    langStateUpdateInput = baseUpdateInput()
                )
            )

            assertTrue(result.isSuccess)
            val captured = learningStateRepo.lastUpdateInput!!.correctionResult!!
            assertEquals(1, captured.learningSignals.size)
            assertEquals("c-1", captured.learningSignals.single().candidateId)
        }

    @Test
    fun `excludes learning signal from evaluation when sourceLang differs from selected language but still saves the card`() =
        kotlinx.coroutines.runBlocking {
            // COR-TUNE-011 핵심 시나리오: 모국어/제3언어 발화는 의도 포착·교정 카드(앞면 모국어/뒷면 학습언어)는
            // 정상 생성되지만, 능력 평가(metricEvidence/activeFocus)에서는 제외되어야 한다(평가 오염 방지).
            val suggestion = baseSuggestion().copy(
                id = "s-1",
                sourceLang = LangCode.KO,
                learningSignal = baseLearningSignal(candidateId = "c-1")
            )

            val result = useCase(
                CompleteCorrectionInput(
                    selectedSuggestions = listOf(suggestion),
                    langStateUpdateInput = baseUpdateInput()
                )
            )

            assertTrue(result.isSuccess)
            val completed = result.getOrThrow()
            // 카드 저장 경로는 평가 게이트와 무관하게 그대로 동작한다 — 학습자는 학습 언어 표현을 그대로 얻는다.
            assertEquals(listOf("s-1"), completed.savedFlashcardIds)

            val captured = learningStateRepo.lastUpdateInput!!.correctionResult!!
            // correctionCount/correctedText 는 카드·표시 목적 필드라 게이트 영향을 받지 않는다.
            assertEquals(1, captured.correctionCount)
            // learningSignal 만 평가 집계에서 제외된다.
            assertTrue(captured.learningSignals.isEmpty())
        }

    @Test
    fun `keeps learning signal in evaluation when sourceLang is unknown (no-op fallback)`() =
        kotlinx.coroutines.runBlocking {
            // COR-TUNE-011 / COR-TUNE-011-FIX: AI 가 sourceLang 을 누락하거나 "unknown"/미지원 코드로
            // 보고하면 mapper 가 보수적으로 null 을 채운다(Method B). 게이트는 "언어 불명 → 종전처럼 통과"로
            // 동작해 과제외로 정상 학습 신호를 잃지 않아야 한다(null=통과 원칙).
            val suggestion = baseSuggestion().copy(
                id = "s-1",
                sourceLang = null,
                learningSignal = baseLearningSignal(candidateId = "c-1")
            )

            val result = useCase(
                CompleteCorrectionInput(
                    selectedSuggestions = listOf(suggestion),
                    langStateUpdateInput = baseUpdateInput()
                )
            )

            assertTrue(result.isSuccess)
            val captured = learningStateRepo.lastUpdateInput!!.correctionResult!!
            assertEquals(1, captured.learningSignals.size)
            assertEquals("c-1", captured.learningSignals.single().candidateId)
        }

    @Test
    fun `judges sourceLang gate independently per candidate in a mixed-language session`() =
        kotlinx.coroutines.runBlocking {
            // COR-TUNE-011 예외 케이스: 한 세션에 여러 언어가 섞여도 후보별로 독립 판정한다(세션 전체 게이트 아님).
            val matching = baseSuggestion().copy(
                id = "s-1",
                sourceLang = LangCode.EN,
                learningSignal = baseLearningSignal(candidateId = "c-1")
            )
            val differing = baseSuggestion().copy(
                id = "s-2",
                beforeText = "나는 어제 학교 갔어",
                nativeText = "나는 어제 학교에 갔다",
                afterText = "I went to school yesterday.",
                sourceLang = LangCode.KO,
                learningSignal = baseLearningSignal(candidateId = "c-2")
            )

            val result = useCase(
                CompleteCorrectionInput(
                    selectedSuggestions = listOf(matching, differing),
                    langStateUpdateInput = baseUpdateInput()
                )
            )

            assertTrue(result.isSuccess)
            val captured = learningStateRepo.lastUpdateInput!!.correctionResult!!
            // 학습 대상 언어로 말한 후보의 신호만 반영되고, 다른 언어 발화의 신호는 빠진다.
            assertEquals(1, captured.learningSignals.size)
            assertEquals("c-1", captured.learningSignals.single().candidateId)
            // 카드/카운트는 두 후보 모두 포함한다 — 교정 자체는 언어 불문 동일하게 동작한다.
            assertEquals(2, captured.correctionCount)
        }

    @Test
    fun `completes as noop when all save request flashcards are excluded by quality filter`() =
        kotlinx.coroutines.runBlocking {
            val result = useCase(
                CompleteCorrectionInput(
                    selectedSuggestions = listOf(
                        baseSuggestion().copy(
                            id = "s-1",
                            beforeText = "same",
                            afterText = "same"
                        ),
                        baseSuggestion().copy(
                            id = "s-2",
                            afterText = "A"
                        )
                    ),
                    langStateUpdateInput = baseUpdateInput()
                )
            )

            assertTrue(result.isSuccess)
            val completed = result.getOrThrow()
            assertTrue(completed.savedFlashcardIds.isEmpty())
            assertTrue(completed.pendingSyncFlashcardIds.isEmpty())
            assertFalse(completed.flashcardSummaryApplied)
            assertFalse(completed.statisticsHistoryApplied)
            assertFalse(completed.sessionCompressionApplied)
            assertEquals(
                com.app.umma.domain.model.correction.CorrectionSaveZeroReason.QUALITY_FILTERED,
                completed.zeroSaveReason
            )
            assertEquals(0, completed.safetyBlockedSuggestionCount)
            assertEquals(listOf("update-correction-signal"), events)
            assertEquals(false, learningStateRepo.lastCorrectionSignalInput!!.correctionAvailable)
            assertTrue(learningStateRepo.lastCorrectionSignalInput!!.sourceEventId.isNotBlank())
            assertEquals(null, learningStateRepo.lastUpdateInput)
            assertEquals(null, sessionMemoryRepository.lastCompressionCommand)
        }

    @Test
    fun `completes as noop when all selected suggestions are blocked by safety policy`() =
        kotlinx.coroutines.runBlocking {
            val result = useCase(
                CompleteCorrectionInput(
                    selectedSuggestions = listOf(
                        baseSuggestion().copy(
                            id = "s-1",
                            beforeText = "how to make a bomb",
                            afterText = "How do I make a bomb?"
                        )
                    ),
                    langStateUpdateInput = baseUpdateInput()
                )
            )

            assertTrue(result.isSuccess)
            val completed = result.getOrThrow()
            assertTrue(completed.savedFlashcardIds.isEmpty())
            assertEquals(
                com.app.umma.domain.model.correction.CorrectionSaveZeroReason.SAFETY_BLOCKED,
                completed.zeroSaveReason
            )
            assertEquals(1, completed.safetyBlockedSuggestionCount)
            assertEquals(listOf("update-correction-signal"), events)
            assertEquals(null, learningStateRepo.lastUpdateInput)
        }

    @Test
    fun `empty save request completion fails when correction signal cannot be closed`() =
        kotlinx.coroutines.runBlocking {
            learningStateRepo.failCorrectionSignalUpdate = true

            val result = useCase(
                CompleteCorrectionInput(
                    selectedSuggestions = listOf(
                        baseSuggestion().copy(
                            id = "s-1",
                            beforeText = "same",
                            afterText = "same"
                        )
                    ),
                    langStateUpdateInput = baseUpdateInput()
                )
            )

            assertTrue(result.isFailure)
            assertEquals(listOf("update-correction-signal"), events)
            assertEquals(null, learningStateRepo.lastUpdateInput)
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
        // Flashcard Summary 갱신(update-flashcard-summary)은 LangState 갱신 실패 전에 발생하므로
        // rollback 대상이 아니다. topic summary 도 RT-003 소유 저장소라 rollback 하지 않는다.
        assertEquals(
            listOf("save", "update-flashcard-summary", "summarize-topics", "update", "rollback-save"),
            events
        )
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
            assertEquals(
                listOf("save", "update-flashcard-summary", "summarize-topics", "update"),
                events
            )
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
        //
        // COR-007-B 도메인 경계 회귀: 본 success 결과가 presentation 의 applyCompletionOutcome 으로
        // 흘러갈 때 동일 onSuccess 분기로 Phase.Done 에 진입한다는 정책을 CorrectionUiStateTest 의
        // `applyCompletionOutcome with statistics history pending still transitions to Done` 가 이어 검증한다.
        assertTrue(completed.savedFlashcardIds.isNotEmpty())
        assertTrue(completed.statisticsHistoryApplied.not())
        assertFalse(completed.statisticsHistoryPending)
        assertNotNull(completed.statisticsHistoryErrorMessage)
        assertEquals(
            listOf("save", "update-flashcard-summary", "summarize-topics", "update", "record-history", "compress"),
            events
        )
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
        //
        // COR-007-B 도메인 경계 회귀: compression 실패 → success + pending=true 라는 도메인 계약을 본 테스트가 못 박고,
        // presentation 쪽 비차단(`Phase.Done` + NavigateToDashboard) 은
        // CorrectionUiStateTest 의 `applyCompletionOutcome with compression pending still transitions to Done` 가 잇는다.
        assertFalse(completed.sessionCompressionApplied)
        assertTrue(completed.sessionCompressionPending)
        assertEquals(
            listOf("save", "update-flashcard-summary", "summarize-topics", "update", "record-history", "compress"),
            events
        )
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

    /** COR-TUNE-011 게이트 테스트용 학습 신호 fixture. candidateId 만 바꿔 후보별로 구분한다. */
    private fun baseLearningSignal(candidateId: String): CorrectionLearningSignal {
        return CorrectionLearningSignal(
            candidateId = candidateId,
            sourceTurnId = "turn-1",
            sourceTurnIndex = 0,
            sourceText = "i go school",
            correctedText = "I go to school.",
            issueCategories = emptyList(),
            languageFeatures = emptyList(),
            improvementTypes = emptyList(),
            editSpans = emptyList(),
            register = SpokenRegister.EverydaySpoken,
            severity = CorrectionSeverity.MinorForm,
            meaningPreserved = true,
            confidence = 0.7
        )
    }

    private class RecordingCorrectionRepository(
        private val events: MutableList<String>
    ) : CorrectionRepository {
        // 특정 테스트에서 repository 결과를 주입해 중복 저장, pending sync 없음 같은 경계 상황을 만든다.
        // null이면 일반적인 "local 저장 성공 + remote sync 대기" 결과를 반환한다.
        var nextSaveResult: CorrectionSaveResult? = null

        override suspend fun generateSuggestions(
            input: com.app.umma.domain.model.correction.GenerateSuggestionsInput
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
        var lastCorrectionSignalInput: CorrectionSignalUpdateInput? = null
        var failUpdate: Boolean = false
        var failCorrectionSignalUpdate: Boolean = false

        override fun observeLearningState(): Flow<GlobalLangState> =
            flowOf(GlobalLangState.initial())

        override fun observeUserPref(): Flow<UserLangPref?> =
            flowOf(null)

        override fun observeLangState(lang: LangCode): Flow<LangState?> =
            flowOf(null)

        override fun observeDashSummary(lang: LangCode): Flow<DashSummary?> =
            flowOf(null)

        override fun observeSessionSummary(lang: LangCode): Flow<SessionSummary?> =
            flowOf(null)

        override fun observeFlashcardSummary(lang: LangCode): Flow<FlashcardSummary?> =
            flowOf(null)

        override suspend fun preload(): Result<Unit> = Result.success(Unit)

        override suspend fun changeSelectedLang(lang: LangCode): Result<Unit> = Result.success(Unit)

        override suspend fun changePrimaryLang(lang: LangCode): Result<Unit> = Result.success(Unit)

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
            // Flashcard Summary 갱신 호출 순서를 파이프라인 이벤트 목록에 기록한다.
            // (#162-D) 파이프라인에서 saveFlashcards 직후, applyLanguageStateUpdateUseCase 직전 위치를 검증한다.
            events += "update-flashcard-summary"
            val flashcardSummary =
                FlashcardSummary(
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

        override suspend fun updateCorrectionSignal(
            input: CorrectionSignalUpdateInput
        ): Result<CorrectionSignalUpdateResult> {
            events += "update-correction-signal"
            if (failCorrectionSignalUpdate) {
                return Result.failure(IllegalStateException("correction signal update failed"))
            }
            lastCorrectionSignalInput = input
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

    private class RecordingStatisticsRepository(
        private val events: MutableList<String>
    ) : StatisticsRepository {
        var failRecord: Boolean = false

        override fun observeHistory(
            userId: String,
            language: LangCode
        ): Flow<com.app.umma.domain.model.statistics.StatisticsHistoryState> {
            return flowOf(com.app.umma.domain.model.statistics.StatisticsHistoryState.Empty)
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
        var failSummarize: Boolean = false
        var lastCompressionCommand: CompressSessionMemoryCommand? = null
        var nextTopicSummaryResult: TopicSummarySaveResult? = null

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

        override suspend fun summarizeAndSaveTopics(
            command: SummarizeTopicsCommand
        ): Result<TopicSummarySaveResult> {
            // 이벤트를 기록해 파이프라인에서 LangState update 직전 위치를 검증할 수 있게 한다. (#173)
            events += "summarize-topics"
            return if (failSummarize) {
                Result.failure(IllegalStateException("topic summary failed"))
            } else {
                val result = nextTopicSummaryResult ?: TopicSummarySaveResult(
                    applied = true,
                    displayTitle = "여행 계획"
                )
                nextTopicSummaryResult = null
                Result.success(
                    TopicSummarySaveResult(
                        applied = result.applied,
                        displayTitle = result.displayTitle,
                        // COR-TUNE-010: nextTopicSummaryResult 가 AI 매핑(recentTopics/summaries)을 주입하면
                        // 그대로 흘려보내 BuildSessionCompressionPayloadUseCase 의 SSOT 우선 분기를 검증할 수 있게 한다.
                        recentTopics = result.recentTopics,
                        summaries = result.summaries
                    )
                )
            }
        }
    }

    /**
     * FlashcardRepository Fake.
     *
     * getReviewSummary 실패 주입으로 (#162-D) pending-only 실패 정책을 검증한다.
     * 이벤트를 공유 목록에 기록하지 않는다 — 순서 검증은 LearningStateRepo.updateFlashcardSummary 이벤트로 충분하다.
     */
    private class RecordingFlashcardRepository : FlashcardRepository {
        var failGetReviewSummary: Boolean = false

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
        ): Result<FlashcardReviewSummary> {
            if (failGetReviewSummary) {
                return Result.failure(IllegalStateException("getReviewSummary failed"))
            }
            return Result.success(FlashcardReviewSummary(dueFlashcards = 1, savedFlashcards = 1))
        }

        override suspend fun syncDirtyFlashcards(userId: String): Result<Int> {
            // CompleteCorrectionUseCase는 저장 직후 summary 경계만 검증하므로 dirty sync는 이 테스트 범위가 아니다.
            return Result.success(0)
        }

        override suspend fun getFlashcards(
            userId: String,
            language: LangCode
        ): Result<List<Flashcard>> {
            // 이 테스트는 correction 완료 후 summary 갱신만 다루므로 목록 조회는 사용하지 않는다.
            return Result.success(emptyList())
        }

        override suspend fun deleteFlashcards(
            userId: String,
            flashcardIds: List<String>
        ): Result<Unit> {
            // CompleteCorrectionUseCase는 flashcard 삭제 경로를 호출하지 않는다.
            // fake는 인터페이스 계약만 맞추고, 삭제가 필요한 테스트는 별도 use case에서 다룬다.
            return Result.success(Unit)
        }
    }

    @Test
    fun `flashcard summary pending when getReviewSummary fails`() = kotlinx.coroutines.runBlocking {
        // getReviewSummary 실패 시 Done 흐름은 계속 진행되고 flashcardSummaryPending=true 로만 남는다.
        flashcardRepository.failGetReviewSummary = true

        val result = useCase(
            CompleteCorrectionInput(
                selectedSuggestions = listOf(baseSuggestion()),
                langStateUpdateInput = baseUpdateInput()
            )
        )

        assertTrue(result.isSuccess)
        val completed = result.getOrThrow()
        // summary 갱신 실패가 Done 흐름을 막지 않아야 한다.
        assertFalse(completed.flashcardSummaryApplied)
        assertTrue(completed.flashcardSummaryPending)
        // update-flashcard-summary 이벤트가 없는 것으로 getReviewSummary 실패 후 summary 반영이 스킵됐음을 확인한다.
        // summarize-topics 는 flashcard summary 와 독립적으로 동작하므로 여전히 실행된다.
        assertEquals(
            listOf("save", "summarize-topics", "update", "record-history", "compress"),
            events
        )
    }

    @Test
    fun `passes AI summary title to LangState update instead of compression keyword`() = kotlinx.coroutines.runBlocking {
        // (#173) Dashboard ConversationCard "주제" 칩은 compression keyword 가 아니라
        // 세션 요약 AI 가 만든 표시용 title 을 사용해야 한다.
        // baseSuggestion + baseUpdateInput 의 compression keyword 는 "hello" 이지만,
        // recentTopic 으로는 fake AI title 인 "여행 계획" 만 전달한다.
        val result = useCase(
            CompleteCorrectionInput(
                selectedSuggestions = listOf(baseSuggestion()),
                langStateUpdateInput = baseUpdateInput()
            )
        )

        assertTrue(result.isSuccess)
        assertNotNull(learningStateRepo.lastUpdateInput)
        assertEquals("여행 계획", learningStateRepo.lastUpdateInput!!.recentTopic)
        // compression command 의 recentTopics 는 압축 메타데이터로만 남고 Dashboard recentTopic 에 쓰이지 않는다.
        assertEquals("hello", sessionMemoryRepository.lastCompressionCommand!!.recentTopics.firstOrNull())
    }

    @Test
    fun `passes null recentTopic when AI summary title is empty`() = kotlinx.coroutines.runBlocking {
        // (#173) AI 요약은 성공했지만 표시용 title 이 비어 있으면,
        // compression keyword 로 fallback 하지 않고 null 을 전달해 기존 recentTopic 을 보존한다.
        sessionMemoryRepository.nextTopicSummaryResult = TopicSummarySaveResult(
            applied = true,
            displayTitle = null
        )

        val result = useCase(
            CompleteCorrectionInput(
                selectedSuggestions = listOf(baseSuggestion()),
                langStateUpdateInput = baseUpdateInput()
            )
        )

        assertTrue(result.isSuccess)
        assertNotNull(learningStateRepo.lastUpdateInput)
        assertEquals(null, learningStateRepo.lastUpdateInput!!.recentTopic)
    }

    @Test
    fun `topic summaries pending when summarizeRecentTopics fails`() = kotlinx.coroutines.runBlocking {
        // AI 요약 실패 시 Done 흐름은 계속 진행되고 topicSummariesPending=true 로만 남는다.
        // 기존 topicSummaries 는 변경하지 않는다. (#162-C)
        sessionMemoryRepository.failSummarize = true

        val result = useCase(
            CompleteCorrectionInput(
                selectedSuggestions = listOf(baseSuggestion()),
                langStateUpdateInput = baseUpdateInput()
            )
        )

        assertTrue(result.isSuccess)
        val completed = result.getOrThrow()
        // AI 요약 실패가 Done 흐름을 막지 않아야 한다.
        assertFalse(completed.topicSummariesApplied)
        assertTrue(completed.topicSummariesPending)
        assertEquals(null, learningStateRepo.lastUpdateInput!!.recentTopic)
        // summarize-topics 이벤트가 발화됐고, update/compression 은 그 뒤에 여전히 실행됐음을 확인한다.
        assertEquals(
            listOf("save", "update-flashcard-summary", "summarize-topics", "update", "record-history", "compress"),
            events
        )
    }

    @Test
    fun `compression command uses AI-mapped topics as SSOT when topic summary AI succeeds`() = kotlinx.coroutines.runBlocking {
        // COR-TUNE-010: 2단계(summarize-topics) AI 매핑 결과가 그대로 5단계 압축 SSOT 로 흘러야 한다.
        // 코드 기반 단어빈도("hello")/before->after 요약이 AI 결과를 덮어쓰면 이중 쓰기가 재발한다.
        sessionMemoryRepository.nextTopicSummaryResult = TopicSummarySaveResult(
            applied = true,
            displayTitle = "여행 계획",
            recentTopics = listOf("여행 계획", "박물관 나들이"),
            summaries = listOf("학습자가 과거형으로 여행 이야기를 연습했다.")
        )

        val result = useCase(
            CompleteCorrectionInput(
                selectedSuggestions = listOf(baseSuggestion()),
                langStateUpdateInput = baseUpdateInput()
            )
        )

        assertTrue(result.isSuccess)
        val command = sessionMemoryRepository.lastCompressionCommand
        assertNotNull(command)
        assertEquals(listOf("여행 계획", "박물관 나들이"), command!!.recentTopics)
        assertEquals(listOf("학습자가 과거형으로 여행 이야기를 연습했다."), command.topicSummaries)
    }

    @Test
    fun `compression falls back to code-based extraction when topic summary AI fails`() = kotlinx.coroutines.runBlocking {
        // AI 매핑이 실패해도(topicSummariesPending=true) 압축 흐름은 막히지 않고
        // 기존 코드 기반 단어빈도/before->after 요약 폴백으로 진행해야 한다(pending 정책 유지).
        sessionMemoryRepository.failSummarize = true

        val result = useCase(
            CompleteCorrectionInput(
                selectedSuggestions = listOf(baseSuggestion()),
                langStateUpdateInput = baseUpdateInput()
            )
        )

        assertTrue(result.isSuccess)
        val command = sessionMemoryRepository.lastCompressionCommand
        assertNotNull(command)
        // baseUpdateInput 의 recentUserTurns 텍스트("hello")에서 추출한 코드 기반 키워드가 폴백으로 들어가야 한다.
        assertEquals("hello", command!!.recentTopics.firstOrNull())
        assertTrue(
            "코드 기반 before->after 요약 폴백이 동작하지 않음",
            command.topicSummaries.first().contains("i go school")
        )
    }

}
