package com.app.umma.domain.usecase.learningstate

import com.app.umma.domain.model.chat.ChatConversationEvidence
import com.app.umma.domain.model.chat.ChatConversationEvidenceSource
import com.app.umma.domain.model.chat.ConversationConsistencyEvidence
import com.app.umma.domain.model.chat.ConversationSustainabilityEvidence
import com.app.umma.domain.model.chat.LanguageDependenceEvidence
import com.app.umma.domain.model.chat.ResponseDifficultyFitEvidence
import com.app.umma.domain.model.chat.TargetLanguageComprehensionEvidence
import com.app.umma.domain.model.chat.TargetLanguageProductionEvidence
import com.app.umma.domain.model.learningstate.ChatEvidenceSummary
import com.app.umma.domain.model.learningstate.ChatSignalUpdateInput
import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.ConversationTurn
import com.app.umma.domain.model.learningstate.CorrectionEditSpan
import com.app.umma.domain.model.learningstate.CorrectionImprovementType
import com.app.umma.domain.model.learningstate.CorrectionIssueCategory
import com.app.umma.domain.model.learningstate.CorrectionLearningSignal
import com.app.umma.domain.model.learningstate.CorrectionResult
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateInput
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateResult
import com.app.umma.domain.model.learningstate.CorrectionSeverity
import com.app.umma.domain.model.learningstate.FlashcardSummary
import com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateInput
import com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateResult
import com.app.umma.domain.model.learningstate.GlobalLangState
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.OnboardingGuideStage
import com.app.umma.domain.model.learningstate.LangStateUpdateInput
import com.app.umma.domain.model.learningstate.LanguageFeatureSignal
import com.app.umma.domain.model.learningstate.LearningFocusType
import com.app.umma.domain.model.learningstate.LearningMetricKey
import com.app.umma.domain.model.learningstate.LearningSignalSource
import com.app.umma.domain.model.learningstate.LangStateSummaryUpdatePolicy
import com.app.umma.domain.model.learningstate.LearningStateUpdateResult
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.SpokenRegister
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
import org.junit.Assert.assertNotNull
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
    fun `chat signal accumulates ChatSession evidence without changing internal or external metrics`() = runBlocking {
        // Chat source는 대화 지속 능력 근거만 남기며 점수 snapshot은 직접 움직이면 안 된다.
        val repo = RecordingLearningStateRepo()
        val useCase = ApplyChatSignalUpdateUseCase(repo)

        val result = useCase(
            chatInput(
                evidence = chatEvidence(
                    confidence = ProfileConfidence.Medium,
                    production = TargetLanguageProductionEvidence.SimpleSentences,
                    comprehension = TargetLanguageComprehensionEvidence.SimpleSentence,
                    sustainability = ConversationSustainabilityEvidence.SustainedSimple
                )
            )
        ).getOrThrow()

        assertTrue(result.applied)
        assertEquals(1, repo.languageStateUpdateCalls)
        assertEquals(repo.initialInternal, result.savedState.internal)
        assertEquals(repo.initialExternal, result.savedState.external)
        assertEquals("chat-session:session-1", result.savedState.analysisMeta.lastChatAnalysisEventId)
        assertEquals(repo.initialState.lastAnalysisEventId, result.savedState.lastAnalysisEventId)
        assertEquals(
            TargetLanguageProductionEvidence.SimpleSentences,
            result.savedState.analysisMeta.chatEvidenceSummary?.targetLanguageProduction
        )
        assertEquals(1, result.savedState.analysisMeta.chatEvidenceSummary?.observedCount)
        assertTrue(result.savedState.analysisMeta.metricEvidence.values.any { evidence ->
            evidence.sourceTypes.contains(LearningSignalSource.ChatSession)
        })
    }

    @Test
    fun `chat signal keeps correction analysis event id separate from chat idempotency id`() = runBlocking {
        // Correction 재시도 중복 방어는 LangState.lastAnalysisEventId를 사용한다.
        // Chat update가 이 값을 chat-session id로 덮으면 같은 correction batch가 다시 반영될 수 있다.
        val repo = RecordingLearningStateRepo(
            initialState = LangState.initial(LangCode.EN, createdAt = 1_000L).copy(
                lastAnalysisEventId = "correction:session-1",
                lastAnalyzedAt = 1_000L
            )
        )
        val useCase = ApplyChatSignalUpdateUseCase(repo)

        val result = useCase(chatInput(evidence = chatEvidence())).getOrThrow()

        assertTrue(result.applied)
        assertEquals("correction:session-1", result.savedState.lastAnalysisEventId)
        assertEquals("chat-session:session-1", result.savedState.analysisMeta.lastChatAnalysisEventId)
    }

    @Test
    fun `chat signal with low confidence stores first summary without metric evidence`() = runBlocking {
        // 첫 세션 Low confidence를 버리면 초저숙련 사용자의 공식 Chat 상태가 계속 비어 있을 수 있다.
        // metric evidence는 여전히 제외하지만, summary는 낮은 신뢰도의 대화 단서로 저장한다.
        val repo = RecordingLearningStateRepo()
        val useCase = ApplyChatSignalUpdateUseCase(repo)

        val result = useCase(
            chatInput(evidence = chatEvidence(confidence = ProfileConfidence.Low))
        ).getOrThrow()

        assertTrue(result.applied)
        assertTrue(result.savedState.analysisMeta.metricEvidence.isEmpty())
        assertEquals(ProfileConfidence.Low, result.savedState.analysisMeta.chatEvidenceSummary?.confidence)
        assertEquals(1, result.savedState.analysisMeta.chatEvidenceSummary?.observedCount)
        assertEquals("chat-session:session-1", result.savedState.analysisMeta.lastChatAnalysisEventId)
    }

    @Test
    fun `chat signal with low confidence updates previous low summary`() = runBlocking {
        // 기존 summary도 Low라면 새 Low를 버리지 않는다. 초저숙련자의 최근 상태는 계속 추적되어야 한다.
        val previousSummary = chatSummary(
            confidence = ProfileConfidence.Low,
            production = TargetLanguageProductionEvidence.WordsOrFragments,
            observedCount = 1
        )
        val repo = RecordingLearningStateRepo(
            initialState = LangState.initial(LangCode.EN, createdAt = 1_000L).copy(
                analysisMeta = LangState.initial(LangCode.EN).analysisMeta.copy(
                    chatEvidenceSummary = previousSummary
                )
            )
        )
        val useCase = ApplyChatSignalUpdateUseCase(repo)

        val result = useCase(
            chatInput(
                evidence = chatEvidence(
                    confidence = ProfileConfidence.Low,
                    production = TargetLanguageProductionEvidence.ShortPhrases
                )
            )
        ).getOrThrow()

        assertEquals(
            TargetLanguageProductionEvidence.ShortPhrases,
            result.savedState.analysisMeta.chatEvidenceSummary?.targetLanguageProduction
        )
        assertEquals(2, result.savedState.analysisMeta.chatEvidenceSummary?.observedCount)
        assertEquals(10_000L, result.savedState.analysisMeta.chatEvidenceSummary?.lastObservedAt)
    }

    @Test
    fun `chat signal with low confidence does not overwrite medium summary`() = runBlocking {
        // 신뢰 가능한 기존 summary가 있으면 짧거나 애매한 Low 세션 하나로 다음 세션 band를 흔들지 않는다.
        val previousSummary = chatSummary(
            confidence = ProfileConfidence.Medium,
            production = TargetLanguageProductionEvidence.SimpleSentences,
            observedCount = 3
        )
        val repo = RecordingLearningStateRepo(
            initialState = LangState.initial(LangCode.EN, createdAt = 1_000L).copy(
                analysisMeta = LangState.initial(LangCode.EN).analysisMeta.copy(
                    chatEvidenceSummary = previousSummary
                )
            )
        )
        val useCase = ApplyChatSignalUpdateUseCase(repo)

        val result = useCase(
            chatInput(
                evidence = chatEvidence(
                    confidence = ProfileConfidence.Low,
                    production = TargetLanguageProductionEvidence.WordsOrFragments
                )
            )
        ).getOrThrow()

        assertEquals(previousSummary, result.savedState.analysisMeta.chatEvidenceSummary)
        assertTrue(result.savedState.analysisMeta.metricEvidence.isEmpty())
    }

    @Test
    fun `chat signal skips duplicate source session by lastChatAnalysisEventId`() = runBlocking {
        // Correction의 lastAnalysisEventId와 별개로 Chat source 자체의 중복 반영을 막아야 한다.
        val repo = RecordingLearningStateRepo()
        val useCase = ApplyChatSignalUpdateUseCase(repo)
        val input = chatInput(evidence = chatEvidence())

        val first = useCase(input).getOrThrow()
        val second = useCase(input).getOrThrow()

        assertTrue(first.applied)
        assertFalse(second.applied)
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
    fun `UseCase computes correct recentMinutes from durationMs when RecalculateFromInput`() = runBlocking {
        // 리팩토링 전 repo.calculateRecentMinutes 와 동일한 결과가 UseCase 에서 계산되는지 검증한다.
        // durationMs 합 120_000ms = 2분 → recentMinutes=2
        val repo = RecordingLearningStateRepo()
        val useCase = ApplyLanguageStateUpdateUseCase(repo, DefaultLangStateAnalysisPolicy())
        val current = LangState.initial(LangCode.EN, createdAt = 1_000L)

        useCase(
            LangStateUpdateInput(
                uid = "uid-1",
                lang = LangCode.EN,
                sessionMemoryKey = "session-en",
                analysisEventId = "event-minutes-1",
                currentState = current,
                recentUserTurns = listOf(
                    ConversationTurn(speaker = TurnSpeaker.USER, text = "Hello.", durationMs = 60_000L),
                    ConversationTurn(speaker = TurnSpeaker.USER, text = "How are you?", durationMs = 60_000L)
                ),
                correctionResult = null,
                flashcardReviewEvents = emptyList(),
                analyzedAt = 2_000L,
                summaryUpdatePolicy = LangStateSummaryUpdatePolicy.RecalculateFromInput
            )
        ).getOrThrow()

        val preparedDash = repo.lastLangStateUpdateInput?.preparedDashSummary
        assertNotNull("RecalculateFromInput 시 preparedDashSummary 가 반드시 존재해야 한다", preparedDash)
        assertEquals("120000ms = 2분", 2, preparedDash?.recentMinutes)
        val preparedSession = repo.lastLangStateUpdateInput?.preparedSessionSummary
        assertNotNull("RecalculateFromInput 시 preparedSessionSummary 가 반드시 존재해야 한다", preparedSession)
        assertEquals(2, preparedSession?.recentMinutes)
    }

    @Test
    fun `UseCase computes zero recentMinutes when turns have no durationMs`() = runBlocking {
        // recentUserTurns 가 비어 있거나 durationMs 가 없으면 0 분 — 기존 동작 보존.
        val repo = RecordingLearningStateRepo()
        val useCase = ApplyLanguageStateUpdateUseCase(repo, DefaultLangStateAnalysisPolicy())
        val current = LangState.initial(LangCode.EN, createdAt = 1_000L)

        useCase(
            LangStateUpdateInput(
                uid = "uid-1",
                lang = LangCode.EN,
                sessionMemoryKey = "session-en",
                analysisEventId = "event-minutes-2",
                currentState = current,
                recentUserTurns = listOf(
                    ConversationTurn(speaker = TurnSpeaker.USER, text = "Hi.", durationMs = null)
                ),
                correctionResult = null,
                flashcardReviewEvents = emptyList(),
                analyzedAt = 2_000L,
                summaryUpdatePolicy = LangStateSummaryUpdatePolicy.RecalculateFromInput
            )
        ).getOrThrow()

        assertEquals(0, repo.lastLangStateUpdateInput?.preparedDashSummary?.recentMinutes)
    }

    @Test
    fun `UseCase computes correctionAvailable from override when RecalculateFromInput`() = runBlocking {
        // correctionAvailableOverride=false 이면 user turn 이 있어도 correctionAvailable=false 가 되어야 한다.
        val repo = RecordingLearningStateRepo()
        val useCase = ApplyLanguageStateUpdateUseCase(repo, DefaultLangStateAnalysisPolicy())
        val current = LangState.initial(LangCode.EN, createdAt = 1_000L)

        useCase(
            LangStateUpdateInput(
                uid = "uid-1",
                lang = LangCode.EN,
                sessionMemoryKey = "session-en",
                analysisEventId = "event-correction-1",
                currentState = current,
                recentUserTurns = listOf(
                    ConversationTurn(speaker = TurnSpeaker.USER, text = "Hello.", durationMs = 10_000L)
                ),
                correctionResult = null,
                correctionAvailableOverride = false,
                flashcardReviewEvents = emptyList(),
                analyzedAt = 2_000L,
                summaryUpdatePolicy = LangStateSummaryUpdatePolicy.RecalculateFromInput
            )
        ).getOrThrow()

        // override=false 이므로 user turn 이 있어도 correctionAvailable 은 false
        assertFalse(
            "correctionAvailableOverride=false 이면 correctionAvailable 이 false 여야 한다",
            repo.lastLangStateUpdateInput?.preparedDashSummary?.correctionAvailable == true
        )
        assertFalse(repo.lastLangStateUpdateInput?.preparedSessionSummary?.correctionAvailable == true)
    }

    @Test
    fun `UseCase derives correctionAvailable from hasUserTurns when override is null`() = runBlocking {
        // correctionAvailableOverride=null 이면 USER turn 존재 여부로 결정된다.
        val repo = RecordingLearningStateRepo()
        val useCase = ApplyLanguageStateUpdateUseCase(repo, DefaultLangStateAnalysisPolicy())
        val current = LangState.initial(LangCode.EN, createdAt = 1_000L)

        useCase(
            LangStateUpdateInput(
                uid = "uid-1",
                lang = LangCode.EN,
                sessionMemoryKey = "session-en",
                analysisEventId = "event-correction-2",
                currentState = current,
                recentUserTurns = listOf(
                    ConversationTurn(speaker = TurnSpeaker.USER, text = "Hello.", durationMs = 5_000L)
                ),
                correctionResult = null,
                correctionAvailableOverride = null,
                flashcardReviewEvents = emptyList(),
                analyzedAt = 2_000L,
                summaryUpdatePolicy = LangStateSummaryUpdatePolicy.RecalculateFromInput
            )
        ).getOrThrow()

        // USER turn 이 있으므로 correctionAvailable=true
        assertTrue(
            "USER turn 이 있으면 correctionAvailable 이 true 여야 한다",
            repo.lastLangStateUpdateInput?.preparedDashSummary?.correctionAvailable == true
        )
    }

    @Test
    fun `UseCase computes delta values matching deltaFromInternal formula`() = runBlocking {
        // grammarDelta = (grammarAccuracy * 100).toInt().coerceIn(0, 100)
        // preparedState.external.grammarAccuracy=0.75 → grammarDelta=75
        val repo = RecordingLearningStateRepo()
        val policy = RecordingLangStateAnalysisPolicy(
            // external.grammarAccuracy=0.75, fluencyScore=0.5, naturalnessScore=0.6 로 고정된 preparedState 반환
            // VocabLevel: A1=0, A2=1, B1=2, B2=3, C1=4, C2=5
            // B1.ordinal=2 → 2/5=0.4 → vocabDelta=40
            fakeNext = LangState.initial(LangCode.EN, createdAt = 1_000L).let { base ->
                base.copy(
                    external = base.external.copy(
                        grammarAccuracy = 0.75,
                        fluencyScore = 0.50,
                        naturalnessScore = 0.60,
                        vocabularyLevel = VocabLevel.B1
                    )
                )
            }
        )
        val useCase = ApplyLanguageStateUpdateUseCase(repo, policy)
        val current = LangState.initial(LangCode.EN, createdAt = 1_000L)

        useCase(
            LangStateUpdateInput(
                uid = "uid-1",
                lang = LangCode.EN,
                sessionMemoryKey = "session-en",
                analysisEventId = "event-delta-1",
                currentState = current,
                recentUserTurns = listOf(
                    ConversationTurn(speaker = TurnSpeaker.USER, text = "Test.", durationMs = 30_000L)
                ),
                correctionResult = null,
                flashcardReviewEvents = emptyList(),
                analyzedAt = 2_000L,
                summaryUpdatePolicy = LangStateSummaryUpdatePolicy.RecalculateFromInput
            )
        ).getOrThrow()

        val dash = repo.lastLangStateUpdateInput?.preparedDashSummary
        assertNotNull(dash)
        assertEquals("grammarDelta=(0.75*100).toInt()=75", 75, dash?.grammarDelta)
        assertEquals("fluencyDelta=(0.50*100).toInt()=50", 50, dash?.fluencyDelta)
        assertEquals("naturalnesssDelta=(0.60*100).toInt()=60", 60, dash?.naturalnessDelta)
        // B1.ordinal=2, 2/5=0.4 → (0.4*100).toInt()=40
        assertEquals("vocabDelta=40", 40, dash?.vocabDelta)
    }

    @Test
    fun `UseCase does not compute summary for PreserveExisting policy`() = runBlocking {
        // PreserveExisting(Chat evidence 경로)에서는 prepared Summary 가 null 이어야 한다.
        val repo = RecordingLearningStateRepo()
        val useCase = ApplyLanguageStateUpdateUseCase(repo, DefaultLangStateAnalysisPolicy())
        val current = LangState.initial(LangCode.EN, createdAt = 1_000L)
        val prepared = current.copy(updatedAt = 2_000L, lastAnalysisEventId = "chat-1")

        useCase(
            LangStateUpdateInput(
                uid = "uid-1",
                lang = LangCode.EN,
                sessionMemoryKey = "session-en",
                analysisEventId = "chat-1",
                currentState = current,
                preparedState = prepared,
                recentUserTurns = listOf(
                    ConversationTurn(speaker = TurnSpeaker.USER, text = "hello", durationMs = 0L)
                ),
                correctionResult = null,
                flashcardReviewEvents = emptyList(),
                analyzedAt = 2_000L,
                summaryUpdatePolicy = LangStateSummaryUpdatePolicy.PreserveExisting
            )
        ).getOrThrow()

        // PreserveExisting 이면 UseCase 는 Summary 를 계산하지 않으므로 null 이어야 한다.
        assertTrue(
            "PreserveExisting 에서 preparedDashSummary 는 null 이어야 한다",
            repo.lastLangStateUpdateInput?.preparedDashSummary == null
        )
        assertTrue(
            "PreserveExisting 에서 preparedSessionSummary 는 null 이어야 한다",
            repo.lastLangStateUpdateInput?.preparedSessionSummary == null
        )
    }

    @Test
    fun `UseCase uses existing summary as base when seedSummaries provided`() = runBlocking {
        // repo 에 기존 DashSummary 가 있을 때 recentTopic fallback 이 올바르게 동작하는지 확인한다.
        // recentTopic=null(input) → currentSession.recentTopic → currentDash.recentTopic 순 fallback.
        val repo = RecordingLearningStateRepo()
        val lang = LangCode.EN
        repo.seedSummaries(
            lang = lang,
            dash = DashSummary.initial(lang).copy(recentTopic = "PreviousTopic"),
            session = SessionSummary.initial(lang).copy(recentTopic = null)
        )
        val useCase = ApplyLanguageStateUpdateUseCase(repo, DefaultLangStateAnalysisPolicy())
        val current = LangState.initial(lang, createdAt = 1_000L)

        useCase(
            LangStateUpdateInput(
                uid = "uid-1",
                lang = lang,
                sessionMemoryKey = "session-en",
                analysisEventId = "event-topic-1",
                currentState = current,
                recentUserTurns = listOf(
                    ConversationTurn(speaker = TurnSpeaker.USER, text = "Hi.", durationMs = 5_000L)
                ),
                correctionResult = null,
                recentTopic = null,         // 입력에 주제가 없으므로 기존 값 보존
                flashcardReviewEvents = emptyList(),
                analyzedAt = 2_000L,
                summaryUpdatePolicy = LangStateSummaryUpdatePolicy.RecalculateFromInput
            )
        ).getOrThrow()

        // session.recentTopic=null → dash.recentTopic="PreviousTopic" fallback
        assertEquals(
            "recentTopic 이 null 이면 기존 DashSummary 의 topic 을 유지해야 한다",
            "PreviousTopic",
            repo.lastLangStateUpdateInput?.preparedDashSummary?.recentTopic
        )
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
    fun `adds correction signal evidence and active focus without breaking correction count fallback`() {
        // CorrectionLearningSignal이 들어오면 단순 correctionCount만 보는 대신 어떤 문제가 반복됐는지 근거를 남겨야 한다.
        val policy = DefaultLangStateAnalysisPolicy()
        val current = LangState.initial(
            lang = LangCode.EN,
            createdAt = 1_000L,
            updatedAt = 2_000L
        )

        val next = policy.analyze(
            LangStateUpdateInput(
                uid = "uid-1",
                lang = LangCode.EN,
                sessionMemoryKey = "session-en",
                analysisEventId = "signal-1",
                currentState = current,
                recentUserTurns = listOf(
                    ConversationTurn(
                        speaker = TurnSpeaker.USER,
                        text = "I go school yesterday",
                        tokenCount = 4,
                        durationMs = 3_000L
                    )
                ),
                correctionResult = CorrectionResult(
                    correctedText = "I went to school yesterday.",
                    correctionCount = 1,
                    learningSignals = listOf(
                        correctionSignal(
                            issueCategories = listOf(
                                CorrectionIssueCategory.GrammarForm,
                                CorrectionIssueCategory.WordOrder
                            ),
                            languageFeatures = listOf(
                                LanguageFeatureSignal(LangCode.EN, "EN.Tense")
                            ),
                            improvementTypes = listOf(CorrectionImprovementType.GrammarFixed)
                        )
                    )
                ),
                flashcardReviewEvents = emptyList(),
                analyzedAt = 3_000L
            )
        )

        val grammarEvidence = next.analysisMeta.metricEvidence[LearningMetricKey.GrammarAccuracy]
        val tenseFocus = next.analysisMeta.activeFocus.firstOrNull { it.type == LearningFocusType.Tense }
        val wordOrderFocus = next.analysisMeta.activeFocus.firstOrNull { it.type == LearningFocusType.WordOrder }

        // metricEvidence는 prompt에 raw 문장을 넣지 않고, 어떤 장기 지표의 근거인지만 compact하게 저장한다.
        assertNotNull(grammarEvidence)
        assertEquals(1, grammarEvidence?.observedCount)
        assertTrue(grammarEvidence?.sourceTypes?.contains(LearningSignalSource.CorrectionSignal) == true)
        // activeFocus는 다음 Chat/Correction에서 도울 반복 약점 후보라서 language feature와 issue category를 함께 반영한다.
        assertNotNull(tenseFocus)
        assertNotNull(wordOrderFocus)
        assertEquals(3_000L, next.analysisMeta.lastSignalAt)
    }

    @Test
    fun `drops invalid correction signal without failing existing analysis`() {
        // confidence 범위 밖 signal은 장기 profile 오염을 막기 위해 버리지만, correction 완료 자체는 유지해야 한다.
        val policy = DefaultLangStateAnalysisPolicy()
        val current = LangState.initial(
            lang = LangCode.EN,
            createdAt = 1_000L,
            updatedAt = 2_000L
        )

        val next = policy.analyze(
            LangStateUpdateInput(
                uid = "uid-1",
                lang = LangCode.EN,
                sessionMemoryKey = "session-en",
                analysisEventId = "invalid-signal-1",
                currentState = current,
                recentUserTurns = listOf(
                    ConversationTurn(
                        speaker = TurnSpeaker.USER,
                        text = "I visited museum",
                        tokenCount = 3,
                        durationMs = 2_000L
                    )
                ),
                correctionResult = CorrectionResult(
                    correctedText = "I visited a museum.",
                    correctionCount = 1,
                    learningSignals = listOf(
                        correctionSignal(confidence = 1.4)
                    )
                ),
                flashcardReviewEvents = emptyList(),
                analyzedAt = 3_000L
            )
        )

        // invalid signal은 evidence/focus에 남지 않지만, 분석 timestamp와 기존 metric 계산은 계속 진행된다.
        assertTrue(next.analysisMeta.metricEvidence.isEmpty())
        assertTrue(next.analysisMeta.activeFocus.isEmpty())
        assertEquals(3_000L, next.lastAnalyzedAt)
        assertTrue(next.internal.lexicalDiversity > 0.0)
    }

    @Test
    fun `meaning not preserved signal keeps long term score neutral`() {
        // 의미가 바뀐 교정은 사용자의 실력 변화 근거가 아니라 품질 방어 신호다.
        // 따라서 점수를 내리지 않는 것뿐 아니라 "교정 0건"처럼 해석해 점수를 올려서도 안 된다.
        val policy = DefaultLangStateAnalysisPolicy()
        val current = LangState.initial(
            lang = LangCode.EN,
            createdAt = 1_000L,
            updatedAt = 2_000L
        ).copy(
            internal = LangState.initial(LangCode.EN).internal.copy(grammarAccuracy = 0.6),
            external = LangState.initial(LangCode.EN).external.copy(grammarAccuracy = 0.6)
        )

        val next = policy.analyze(
            LangStateUpdateInput(
                uid = "uid-1",
                lang = LangCode.EN,
                sessionMemoryKey = "session-en",
                analysisEventId = "meaning-risk-1",
                currentState = current,
                recentUserTurns = listOf(
                    ConversationTurn(
                        speaker = TurnSpeaker.USER,
                        text = "I hungry apple",
                        tokenCount = 3,
                        durationMs = 2_000L
                    )
                ),
                correctionResult = CorrectionResult(
                    correctedText = "I'm hungry. I want an apple.",
                    correctionCount = 1,
                    learningSignals = listOf(
                        correctionSignal(
                            meaningPreserved = false,
                            issueCategories = listOf(CorrectionIssueCategory.MeaningMismatch)
                        )
                    )
                ),
                flashcardReviewEvents = emptyList(),
                analyzedAt = 3_000L
            )
        )

        // correctionCount가 있어도 meaningPreserved=false signal만 있으면 grammar score는 상향/하향 없이 보존된다.
        assertEquals(0.6, next.internal.grammarAccuracy, 0.0001)
        // evidence는 남겨 다음 분석/검토에서 의미 보존 실패를 추적할 수 있게 한다.
        assertNotNull(next.analysisMeta.metricEvidence[LearningMetricKey.GrammarAccuracy])
    }

    @Test
    fun `over expanded correction signal does not directly move long term score`() {
        // source보다 corrected가 크게 확장된 교정은 "사용자가 그 수준을 구사했다"는 근거가 아니다.
        // 그래서 LearningState는 edit span과 improvement type을 비교해 장기 score 반영을 막아야 한다.
        val policy = DefaultLangStateAnalysisPolicy()
        val current = LangState.initial(
            lang = LangCode.EN,
            createdAt = 1_000L,
            updatedAt = 2_000L
        ).copy(
            // 낮은 내부 지표를 명시해 초저숙련 사용자에게 과한 확장이 들어온 상황을 재현한다.
            internal = LangState.initial(LangCode.EN).internal.copy(
                grammarAccuracy = 0.6,
                vocabularyAppropriateness = 0.25,
                sentenceComplexity = 0.2,
                spokenNaturalness = 0.25,
                naturalExpressionUsage = 0.25
            ),
            // external은 이 policy의 guard 판단 재료가 아니므로 기존 projection과 분리된 상태를 보장한다.
            external = LangState.initial(LangCode.EN).external.copy(grammarAccuracy = 0.6)
        )

        val next = policy.analyze(
            LangStateUpdateInput(
                uid = "uid-1",
                lang = LangCode.EN,
                sessionMemoryKey = "session-en",
                analysisEventId = "over-expand-1",
                currentState = current,
                recentUserTurns = listOf(
                    ConversationTurn(
                        speaker = TurnSpeaker.USER,
                        text = "apple",
                        tokenCount = 1,
                        durationMs = 1_000L
                    )
                ),
                correctionResult = CorrectionResult(
                    correctedText = "I would really like to have an apple, please.",
                    correctionCount = 1,
                    learningSignals = listOf(
                        correctionSignal(
                            sourceText = "apple",
                            correctedText = "I would really like to have an apple, please.",
                            issueCategories = listOf(CorrectionIssueCategory.MissingContext),
                            improvementTypes = listOf(CorrectionImprovementType.StructureExpanded),
                            editSpans = listOf(
                                CorrectionEditSpan(
                                    sourceFragment = "apple",
                                    correctedFragment = "I want an apple",
                                    issueCategory = CorrectionIssueCategory.MissingContext,
                                    languageFeatureKey = null,
                                    improvementType = CorrectionImprovementType.StructureExpanded
                                ),
                                CorrectionEditSpan(
                                    sourceFragment = "apple",
                                    correctedFragment = "I would like an apple",
                                    issueCategory = CorrectionIssueCategory.MissingContext,
                                    languageFeatureKey = null,
                                    improvementType = CorrectionImprovementType.StructureExpanded
                                ),
                                CorrectionEditSpan(
                                    sourceFragment = "apple",
                                    correctedFragment = "I would really like to have an apple",
                                    issueCategory = CorrectionIssueCategory.MissingContext,
                                    languageFeatureKey = null,
                                    improvementType = CorrectionImprovementType.StructureExpanded
                                )
                            ),
                            confidence = 0.6
                        )
                    )
                ),
                flashcardReviewEvents = emptyList(),
                analyzedAt = 3_000L
            )
        )

        val sentenceEvidence = next.analysisMeta.metricEvidence[LearningMetricKey.SentenceComplexity]
        val expressionEvidence = next.analysisMeta.metricEvidence[LearningMetricKey.NaturalExpressionUsage]
        val missingContextFocus = next.analysisMeta.activeFocus.firstOrNull {
            it.type == LearningFocusType.MissingContext
        }

        // correctionCount가 있어도 과도한 확장으로 판단되면 장기 grammar score는 상향/하향 없이 보존된다.
        assertEquals(0.6, next.internal.grammarAccuracy, 0.0001)
        // issue-driven evidence와 focus는 남겨 다음 대화/교정이 사용자의 missing context를 도울 수 있게 한다.
        assertNotNull(sentenceEvidence)
        assertNotNull(missingContextFocus)
        // stretch improvement는 guard 때문에 "자연 표현을 이미 쓸 수 있다"는 Up 근거로 남기지 않는다.
        assertEquals(null, expressionEvidence)
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

    private class RecordingLearningStateRepo(
        val initialState: LangState = LangState.initial(LangCode.EN)
    ) : LearningStateRepo {
        // fake repo는 저장 여부만 기록하고, 실제 계산/저장소 부작용은 만들지 않는다.
        // Chat 중복 방어 테스트는 observeLangState가 이전 저장 결과를 다시 읽어야 하므로 StateFlow로 보관한다.
        private val state = MutableStateFlow(
            GlobalLangState.initial().copy(
                langStates = mapOf(initialState.lang to initialState)
            )
        )
        var languageStateUpdateCalls: Int = 0
        var flashcardSummaryUpdateCalls: Int = 0
        var correctionSignalUpdateCalls: Int = 0
        var lastCorrectionSignalInput: CorrectionSignalUpdateInput? = null
        // UseCase 가 계산한 prepared Summary 가 그대로 전달됐는지 확인하기 위해 캡처한다.
        var lastLangStateUpdateInput: LangStateUpdateInput? = null

        /** 테스트에서 현재 dash/session summary 를 미리 심어두기 위한 helper. */
        fun seedSummaries(
            lang: LangCode,
            dash: DashSummary,
            session: SessionSummary
        ) {
            state.value = state.value.copy(
                dashSummaries = state.value.dashSummaries + (lang to dash),
                sessionSummaries = state.value.sessionSummaries + (lang to session)
            )
        }

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

        override suspend fun changePrimaryLang(lang: LangCode): Result<Unit> = Result.success(Unit)

        override suspend fun updateLanguageState(
            input: LangStateUpdateInput
        ): Result<LearningStateUpdateResult> {
            // UseCase가 만든 preparedState/preparedSummary가 그대로 저장소로 넘어왔는지 확인하려고 echo한다.
            languageStateUpdateCalls += 1
            lastLangStateUpdateInput = input
            val savedState = input.preparedState ?: input.currentState
            state.value = state.value.copy(
                langStates = state.value.langStates + (input.lang to savedState)
            )
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

        override suspend fun setOnboardingGuideStage(lang: LangCode, stage: OnboardingGuideStage): Result<Unit> =
            Result.success(Unit)

        override suspend fun clear(): Result<Unit> = Result.success(Unit)

        override suspend fun sync(): Result<Unit> = Result.success(Unit)

        val initialInternal = initialState.internal
        val initialExternal = initialState.external
    }

    private fun chatInput(
        evidence: ChatConversationEvidence = chatEvidence()
    ): ChatSignalUpdateInput {
        // Chat update 입력은 저장 확정 USER turn을 포함해야 repository summary가 잘못 꺼지지 않는다.
        // 실제 summary는 PreserveExisting으로 보존되지만, 입력 검증은 USER turn 부재를 공식 evidence 반영 제외 조건으로 본다.
        return ChatSignalUpdateInput(
            uid = "uid-1",
            lang = LangCode.EN,
            sessionMemoryKey = "uid-1_en",
            sourceSessionId = "session-1",
            evidence = evidence,
            recentUserTurns = listOf(
                ConversationTurn(
                    speaker = TurnSpeaker.USER,
                    text = "I like coffee",
                    tokenCount = 3,
                    durationMs = 2_000L,
                    confidence = 0.9
                )
            ),
            analyzedAt = 10_000L
        )
    }

    private fun chatEvidence(
        confidence: ProfileConfidence = ProfileConfidence.Medium,
        production: TargetLanguageProductionEvidence = TargetLanguageProductionEvidence.SimpleSentences,
        comprehension: TargetLanguageComprehensionEvidence = TargetLanguageComprehensionEvidence.SimpleSentence,
        sustainability: ConversationSustainabilityEvidence = ConversationSustainabilityEvidence.SustainedSimple,
        supportDependence: LanguageDependenceEvidence = LanguageDependenceEvidence.Low,
        scaffoldDependence: LanguageDependenceEvidence = LanguageDependenceEvidence.Low
    ): ChatConversationEvidence {
        // 기본 fixture는 LangState evidence 저장 조건을 통과하는 중간 신뢰도 Chat 세션을 재현한다.
        return ChatConversationEvidence(
            selectedLang = LangCode.EN,
            targetLanguageComprehension = comprehension,
            targetLanguageProduction = production,
            supportLanguageDependence = supportDependence,
            aiScaffoldingDependence = scaffoldDependence,
            conversationSustainability = sustainability,
            consistency = ConversationConsistencyEvidence.Mixed,
            responseDifficultyFit = ResponseDifficultyFitEvidence.Fits,
            confidence = confidence,
            source = ChatConversationEvidenceSource.GeminiConversationAnalysis,
            sourceSessionId = "session-1",
            updatedAt = 10_000L
        )
    }

    private fun chatSummary(
        confidence: ProfileConfidence,
        production: TargetLanguageProductionEvidence,
        observedCount: Int
    ): ChatEvidenceSummary {
        // 이전 LangState에 이미 저장된 Chat 상태를 재현한다.
        // 갱신 정책 테스트에서는 confidence와 production만 바꾸고 나머지 단서는 안정적인 기본값으로 둔다.
        return ChatEvidenceSummary(
            targetLanguageComprehension = TargetLanguageComprehensionEvidence.SimpleSentence,
            targetLanguageProduction = production,
            supportLanguageDependence = LanguageDependenceEvidence.Low,
            aiScaffoldingDependence = LanguageDependenceEvidence.Low,
            conversationSustainability = ConversationSustainabilityEvidence.SustainedSimple,
            consistency = ConversationConsistencyEvidence.Mixed,
            responseDifficultyFit = ResponseDifficultyFitEvidence.Fits,
            confidence = confidence,
            observedCount = observedCount,
            lastObservedAt = 5_000L
        )
    }

    private class RecordingLangStateAnalysisPolicy(
        // delta 검증 테스트처럼 고정된 external metric 이 필요한 경우 직접 주입한다.
        // null 이면 기존처럼 currentState.copy 로 최소 상태를 돌려준다.
        private val fakeNext: LangState? = null
    ) : LangStateAnalysisPolicy {
        // duplicate 방어 이후에만 policy가 호출되는지 세기 위한 계측값이다.
        var analyzeCalls: Int = 0

        override fun analyze(input: LangStateUpdateInput): LangState {
            analyzeCalls += 1
            // 테스트용 policy는 계산 책임이 UseCase 밖으로 분리됐는지만 확인한다.
            // 실제 산출식 검증은 DefaultLangStateAnalysisPolicy가 담당한다.
            return fakeNext ?: input.currentState.copy(
                updatedAt = input.analyzedAt,
                lastAnalyzedAt = input.analyzedAt,
                lastAnalysisEventId = input.analysisEventId ?: input.currentState.lastAnalysisEventId
            )
        }
    }

    private fun correctionSignal(
        sourceText: String = "I go school yesterday",
        correctedText: String = "I went to school yesterday.",
        issueCategories: List<CorrectionIssueCategory> = listOf(CorrectionIssueCategory.GrammarForm),
        languageFeatures: List<LanguageFeatureSignal> = emptyList(),
        improvementTypes: List<CorrectionImprovementType> = listOf(CorrectionImprovementType.GrammarFixed),
        editSpans: List<CorrectionEditSpan> = listOf(
            CorrectionEditSpan(
                sourceFragment = "go",
                correctedFragment = "went",
                issueCategory = CorrectionIssueCategory.GrammarForm,
                languageFeatureKey = "EN.Tense",
                improvementType = CorrectionImprovementType.GrammarFixed
            )
        ),
        meaningPreserved: Boolean = true,
        confidence: Double? = 0.8
    ): CorrectionLearningSignal {
        // 테스트 signal은 handover 계약의 최소 필수값을 모두 채워 policy 검증 대상만 바꿀 수 있게 한다.
        return CorrectionLearningSignal(
            candidateId = "candidate-1",
            sourceTurnId = "turn-1",
            sourceTurnIndex = 0,
            sourceText = sourceText,
            correctedText = correctedText,
            issueCategories = issueCategories,
            languageFeatures = languageFeatures,
            improvementTypes = improvementTypes,
            editSpans = editSpans,
            register = SpokenRegister.EverydaySpoken,
            severity = CorrectionSeverity.MajorPattern,
            meaningPreserved = meaningPreserved,
            confidence = confidence
        )
    }
}
