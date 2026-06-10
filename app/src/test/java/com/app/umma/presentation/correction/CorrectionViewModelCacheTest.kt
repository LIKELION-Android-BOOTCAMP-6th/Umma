package com.app.umma.presentation.correction

import android.os.SystemClock
import android.util.Log
import com.app.umma.core.tts.TextToSpeechController
import com.app.umma.data.repository.correction.CorrectionSuggestionFixtures
import com.app.umma.devtools.correctionpromptreview.ReportCorrectionPromptReviewUseCase
import com.app.umma.domain.model.correction.CachedCorrectionResult
import com.app.umma.domain.model.correction.CompleteCorrectionResult
import com.app.umma.domain.model.correction.CorrectionCandidate
import com.app.umma.domain.model.correction.CorrectionFlashcardSaveItem
import com.app.umma.domain.model.correction.CorrectionSaveRequest
import com.app.umma.domain.model.correction.PrepareCorrectionSaveRequestResult
import com.app.umma.domain.model.flashcard.Flashcard
import com.app.umma.domain.model.flashcard.FlashcardSchedule
import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.FlashcardSummary
import com.app.umma.domain.model.learningstate.GlobalLangState
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LangStateUpdateInput
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.UserLangPref
import com.app.umma.domain.model.realtime.SessionTurn
import com.app.umma.domain.usecase.auth.GetCurrentUserUidUseCase
import com.app.umma.domain.usecase.correction.ClearCorrectionCacheUseCase
import com.app.umma.domain.usecase.correction.CompleteCorrectionUseCase
import com.app.umma.domain.usecase.correction.ExtractSessionCandidatesUseCase
import com.app.umma.domain.usecase.correction.FilterCorrectionCandidatesForSafetyUseCase
import com.app.umma.domain.usecase.correction.GenerateSuggestionsUseCase
import com.app.umma.domain.usecase.correction.GetCachedCorrectionUseCase
import com.app.umma.domain.usecase.correction.PrepareSaveRequestUseCase
import com.app.umma.domain.usecase.correction.ReconcileSavedCorrectionOnReentryUseCase
import com.app.umma.domain.usecase.correction.SaveCorrectionCacheUseCase
import com.app.umma.domain.usecase.correction.FilteredCorrectionCandidatesResult
import com.app.umma.domain.usecase.flashcardreview.GetFlashcardsUseCase
import com.app.umma.domain.usecase.learningstate.BuildLangStateUpdateInputUseCase
import com.app.umma.domain.usecase.learningstate.BuildLearnerAdaptationProfileUseCase
import com.app.umma.domain.usecase.learningstate.ObserveLearningStateUseCase
import com.app.umma.domain.usecase.learningstate.PreloadLearningStateUseCase
import com.app.umma.domain.usecase.realtime.GetCorrectionContextUseCase
import com.app.umma.domain.usecase.realtime.GetSessionMemoryUseCase
import com.app.umma.test.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CorrectionViewModelCacheTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `cache hit restores content without triggering generation`() = runTest {
        val suggestions = CorrectionSuggestionFixtures.contentSuggestions(LangCode.EN)
        val harness = buildViewModel(
            observeLearningStateFlow = flowOf(readyGlobal(lang = LangCode.EN, sessionUpdatedAt = 1_234L)),
            cachedCorrection = CachedCorrectionResult(
                language = LangCode.EN,
                suggestions = suggestions,
                sessionFingerprint = 1_234L,
                primaryLanguage = LangCode.KO,
                cachedAt = 5_000L,
            ),
        )

        harness.viewModel.onEnter()
        advanceUntilIdle()

        assertEquals(CorrectionUiState.Phase.Content, harness.viewModel.uiState.value.phase)
        assertEquals(suggestions, harness.viewModel.uiState.value.suggestions)
        assertTrue(harness.viewModel.uiState.value.selectedSuggestionIds.isEmpty())
        coVerify(exactly = 0) { harness.generateSuggestions.invoke(any()) }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `재진입 정합성 갭 - 캐시 hit 이지만 suggestion 이 전부 저장된 경우 Content 복원 없이 캐시를 정리한다`() = runTest {
        // 시나리오: Flashcard commit 후 프로세스 사망 → 재진입 → 캐시 있음 + 카드 이미 저장됨(갭)
        val suggestions = CorrectionSuggestionFixtures.contentSuggestions(LangCode.EN)
        val clearCacheEvents = mutableListOf<String>()
        val harness = buildViewModel(
            observeLearningStateFlow = flowOf(readyGlobal(lang = LangCode.EN, sessionUpdatedAt = 1_234L)),
            cachedCorrection = CachedCorrectionResult(
                language = LangCode.EN,
                suggestions = suggestions,
                sessionFingerprint = 1_234L,
                primaryLanguage = LangCode.KO,
                cachedAt = 5_000L,
            ),
            reconcileOutcome = ReconcileSavedCorrectionOnReentryUseCase.Outcome.AlreadySaved,
            clearCacheAnswer = { _, _ -> clearCacheEvents += "clear" },
        )

        harness.viewModel.onEnter()
        advanceUntilIdle()

        // Phase.Content 복원이 일어나면 안 된다 — 카드는 이미 저장됐으므로 교정 미완료가 아니다.
        assertTrue(
            "재진입 갭 복구 시 Phase.Content 로 복원되면 안 된다",
            harness.viewModel.uiState.value.phase != CorrectionUiState.Phase.Content
        )
        // 새로운 AI 제안 생성도 트리거되면 안 된다.
        coVerify(exactly = 0) { harness.generateSuggestions.invoke(any()) }
        // 잔존 캐시는 정리돼야 한다.
        assertTrue("재진입 갭 복구 시 캐시가 정리돼야 한다", clearCacheEvents.isNotEmpty())
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `재진입 정합성 갭 - reconcile 이 NotSaved 를 반환하면 캐시 복원 정상 흐름으로 진행한다`() = runTest {
        // 시나리오: 캐시 hit 이지만 아직 저장 안 된 경우 → 정상 캐시 복원
        val suggestions = CorrectionSuggestionFixtures.contentSuggestions(LangCode.EN)
        val harness = buildViewModel(
            observeLearningStateFlow = flowOf(readyGlobal(lang = LangCode.EN, sessionUpdatedAt = 1_234L)),
            cachedCorrection = CachedCorrectionResult(
                language = LangCode.EN,
                suggestions = suggestions,
                sessionFingerprint = 1_234L,
                primaryLanguage = LangCode.KO,
                cachedAt = 5_000L,
            ),
            // NotSaved = 기본값, 명시적으로 전달해 의도 드러냄
            reconcileOutcome = ReconcileSavedCorrectionOnReentryUseCase.Outcome.NotSaved,
        )

        harness.viewModel.onEnter()
        advanceUntilIdle()

        // NotSaved → 정상 캐시 복원 → Content
        assertEquals(CorrectionUiState.Phase.Content, harness.viewModel.uiState.value.phase)
        assertEquals(suggestions, harness.viewModel.uiState.value.suggestions)
        coVerify(exactly = 0) { harness.generateSuggestions.invoke(any()) }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `stale loading flashcards from previous attempt are ignored`() = runTest {
        val firstCardsGate = CompletableDeferred<List<Flashcard>>()
        val secondCardsGate = CompletableDeferred<List<Flashcard>>()
        var flashcardRequestCount = 0
        val harness = buildViewModel(
            observeLearningStateFlow = flowOf(readyGlobal(lang = LangCode.EN, sessionUpdatedAt = 1_234L)),
            generateSuggestionsResult = Result.failure(IllegalStateException("boom")),
            getFlashcardsAnswer = { _, _ ->
                val gate = if (flashcardRequestCount++ == 0) firstCardsGate else secondCardsGate
                Result.success(gate.await())
            },
        )

        harness.viewModel.onEnter()
        runCurrent()
        advanceTimeBy(GENERATION_TOTAL_DURATION_MS)
        runCurrent()

        assertEquals(CorrectionUiState.Phase.Error, harness.viewModel.uiState.value.phase)

        harness.viewModel.onRetryClicked()
        runCurrent()

        assertEquals(CorrectionUiState.Phase.Generating, harness.viewModel.uiState.value.phase)
        assertTrue(harness.viewModel.uiState.value.loadingFlashcards.isEmpty())

        firstCardsGate.complete(listOf(sampleFlashcard(id = "old-card", front = "old", back = "stale")))
        runCurrent()

        assertTrue(harness.viewModel.uiState.value.loadingFlashcards.isEmpty())

        secondCardsGate.complete(listOf(sampleFlashcard(id = "new-card", front = "new", back = "fresh")))
        runCurrent()

        assertEquals(
            listOf(CorrectionLoadingCard(front = "new", back = "fresh")),
            harness.viewModel.uiState.value.loadingFlashcards,
        )
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `completion clear waits for delayed cache save and remains final mutation`() = runTest {
        val suggestions = CorrectionSuggestionFixtures.contentSuggestions(LangCode.EN)
        val saveGate = CompletableDeferred<Unit>()
        val cacheEvents = mutableListOf<String>()
        val harness = buildViewModel(
            observeLearningStateFlow = flowOf(readyGlobal(lang = LangCode.EN, sessionUpdatedAt = 1_234L)),
            generateSuggestionsResult = Result.success(suggestions),
            prepareSaveRequestResult = samplePreparedResult(),
            completeCorrectionResult = Result.success(sampleCompletionResult()),
            correctionContextFlow = flowOf(emptyList()),
            langStateUpdateInput = sampleLangStateUpdateInput(),
            saveCacheAnswer = { _, _, _, _, _, _ ->
                cacheEvents += "save-start"
                saveGate.await()
                cacheEvents += "save-end"
            },
            clearCacheAnswer = { _, _ ->
                cacheEvents += "clear"
            },
        )

        harness.viewModel.onEnter()
        runCurrent()
        advanceTimeBy(GENERATION_TOTAL_DURATION_MS)
        runCurrent()

        assertEquals(CorrectionUiState.Phase.Content, harness.viewModel.uiState.value.phase)

        harness.viewModel.toggleSuggestionSelection(suggestions.first().id)
        harness.viewModel.onSaveClicked()
        runCurrent()

        assertEquals(listOf("save-start"), cacheEvents)

        saveGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("save-start", "save-end", "clear"), cacheEvents)
    }

    private fun buildViewModel(
        observeLearningStateFlow: Flow<GlobalLangState>,
        cachedCorrection: CachedCorrectionResult? = null,
        reconcileOutcome: ReconcileSavedCorrectionOnReentryUseCase.Outcome =
            ReconcileSavedCorrectionOnReentryUseCase.Outcome.NotSaved,
        generateSuggestionsResult: Result<List<com.app.umma.domain.model.correction.CorrectionSuggestion>> = Result.failure(
            IllegalStateException("unused")
        ),
        getFlashcardsAnswer: suspend (String, LangCode) -> Result<List<Flashcard>> = { _, _ ->
            Result.success(emptyList())
        },
        saveCacheAnswer: suspend (String?, LangCode, List<com.app.umma.domain.model.correction.CorrectionSuggestion>, Long?, LangCode?, Long) -> Unit =
            { _, _, _, _, _, _ -> Unit },
        clearCacheAnswer: suspend (String?, LangCode) -> Unit = { _, _ -> Unit },
        prepareSaveRequestResult: PrepareCorrectionSaveRequestResult = samplePreparedResult(),
        completeCorrectionResult: Result<CompleteCorrectionResult> = Result.failure(IllegalStateException("unused")),
        correctionContextFlow: Flow<List<SessionTurn>> = flowOf(emptyList()),
        langStateUpdateInput: LangStateUpdateInput = sampleLangStateUpdateInput(),
    ): ViewModelHarness {
        val preloadLearningState = mockk<PreloadLearningStateUseCase>()
        val observeLearningState = mockk<ObserveLearningStateUseCase>()
        val getCorrectionContext = mockk<GetCorrectionContextUseCase>()
        val getSessionMemory = mockk<GetSessionMemoryUseCase>()
        val extractSessionCandidates = mockk<ExtractSessionCandidatesUseCase>()
        val filterCorrectionCandidatesForSafety = mockk<FilterCorrectionCandidatesForSafetyUseCase>()
        val generateSuggestions = mockk<GenerateSuggestionsUseCase>()
        val getCachedCorrection = mockk<GetCachedCorrectionUseCase>()
        val saveCorrectionCache = mockk<SaveCorrectionCacheUseCase>()
        val clearCorrectionCache = mockk<ClearCorrectionCacheUseCase>()
        val buildLearnerAdaptationProfile = mockk<BuildLearnerAdaptationProfileUseCase>(relaxed = true)
        val getCurrentUserUid = mockk<GetCurrentUserUidUseCase>()
        val prepareSaveRequest = mockk<PrepareSaveRequestUseCase>()
        val completeCorrection = mockk<CompleteCorrectionUseCase>()
        val buildLangStateUpdateInput = mockk<BuildLangStateUpdateInputUseCase>()
        val getFlashcards = mockk<GetFlashcardsUseCase>()
        val reconcileSavedCorrectionOnReentry = mockk<ReconcileSavedCorrectionOnReentryUseCase>()
        val ttsController = mockk<TextToSpeechController>(relaxed = true)
        val reportCorrectionPromptReviewUseCase = mockk<ReportCorrectionPromptReviewUseCase>(relaxed = true)

        mockkStatic(Log::class)
        mockkStatic(SystemClock::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { SystemClock.elapsedRealtime() } answers { 0L }

        coEvery { preloadLearningState.invoke() } returns Result.success(Unit)
        every { observeLearningState.invoke() } returns observeLearningStateFlow
        every { getCurrentUserUid.getCurrentUserUid() } returns "user-1"
        every { getCorrectionContext.invoke(any()) } returns correctionContextFlow
        coEvery { getSessionMemory.invoke(any()) } returns Result.failure(IllegalStateException("unused"))
        every { extractSessionCandidates.invoke(any(), any(), any()) } returns listOf(sampleCandidate())
        every { filterCorrectionCandidatesForSafety.invoke(any()) } returns FilteredCorrectionCandidatesResult(
            allowedCandidates = listOf(sampleCandidate()),
            blockedCandidates = emptyList(),
        )
        every { buildLangStateUpdateInput.invoke(any()) } returns Result.success(langStateUpdateInput)
        every { prepareSaveRequest.invoke(any(), any(), any()) } returns Result.success(prepareSaveRequestResult)
        coEvery { completeCorrection.invoke(any()) } returns completeCorrectionResult
        coEvery { getFlashcards.invoke(any(), any()) } coAnswers {
            getFlashcardsAnswer(firstArg(), secondArg())
        }
        coEvery { saveCorrectionCache.invoke(any(), any(), any(), any(), any(), any()) } coAnswers {
            saveCacheAnswer(
                firstArg(),
                secondArg(),
                thirdArg(),
                arg(3),
                arg(4),
                arg(5),
            )
        }
        coEvery { clearCorrectionCache.invoke(any(), any()) } coAnswers {
            clearCacheAnswer(firstArg(), secondArg())
        }
        coEvery { getCachedCorrection.invoke(any(), any(), any()) } returns cachedCorrection
        coEvery {
            reconcileSavedCorrectionOnReentry.invoke(any(), any(), any(), any(), any())
        } returns Result.success(reconcileOutcome)
        coEvery { generateSuggestions.invoke(any()) } returns generateSuggestionsResult

        return ViewModelHarness(
            viewModel = CorrectionViewModel(
            preloadLearningState = preloadLearningState,
            observeLearningState = observeLearningState,
            getCorrectionContext = getCorrectionContext,
            getSessionMemory = getSessionMemory,
            extractSessionCandidates = extractSessionCandidates,
            filterCorrectionCandidatesForSafety = filterCorrectionCandidatesForSafety,
            generateSuggestions = generateSuggestions,
            getCachedCorrection = getCachedCorrection,
            saveCorrectionCache = saveCorrectionCache,
            clearCorrectionCache = clearCorrectionCache,
            buildLearnerAdaptationProfile = buildLearnerAdaptationProfile,
            getCurrentUserUid = getCurrentUserUid,
            prepareSaveRequest = prepareSaveRequest,
            completeCorrection = completeCorrection,
            buildLangStateUpdateInput = buildLangStateUpdateInput,
            getFlashcards = getFlashcards,
            reconcileSavedCorrectionOnReentry = reconcileSavedCorrectionOnReentry,
            ttsController = ttsController,
            reportCorrectionPromptReviewUseCase = reportCorrectionPromptReviewUseCase,
            ),
            generateSuggestions = generateSuggestions,
        )
    }

    private fun readyGlobal(
        lang: LangCode,
        sessionUpdatedAt: Long,
    ): GlobalLangState = GlobalLangState(
        userPref = UserLangPref.initial(primaryLang = LangCode.KO, selectedLang = lang),
        langStates = mapOf(lang to LangState.initial(lang)),
        dashSummaries = mapOf(lang to DashSummary.initial(lang)),
        sessionSummaries = mapOf(
            lang to SessionSummary(
                lang = lang,
                correctionAvailable = true,
                recentMinutes = 12,
                recentTopic = "Travel",
                updatedAt = sessionUpdatedAt,
            ),
        ),
        flashcardSummaries = mapOf(lang to FlashcardSummary.initial(lang)),
    )

    private fun sampleFlashcard(
        id: String,
        front: String,
        back: String,
    ): Flashcard = Flashcard(
        id = id,
        language = LangCode.EN,
        frontText = front,
        backText = back,
        explanation = "demo",
        schedule = FlashcardSchedule(interval = 1, easeFactor = 2.5, nextReviewAt = 0L),
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun samplePreparedResult(): PrepareCorrectionSaveRequestResult = PrepareCorrectionSaveRequestResult(
        request = sampleSaveRequest(),
        saveableSuggestionIds = listOf("s-1"),
        qualityFilteredSuggestionIds = emptyList(),
        safetyBlockedSuggestionIds = emptyList(),
    )

    private fun sampleSaveRequest(): CorrectionSaveRequest = CorrectionSaveRequest(
        uid = "user-1",
        lang = LangCode.EN,
        flashcards = listOf(
            CorrectionFlashcardSaveItem(
                suggestionId = "s-1",
                frontText = "before",
                backText = "after",
                explanation = "because",
            ),
        ),
        requestedAt = 1_700_000_000_000L,
    )

    private fun sampleCompletionResult(): CompleteCorrectionResult = CompleteCorrectionResult(
        savedFlashcardIds = listOf("card-1"),
        pendingSyncFlashcardIds = emptyList(),
        sessionMemoryKey = "session-1",
        completedAt = 1_700_000_000_000L,
    )

    private fun sampleLangStateUpdateInput(): LangStateUpdateInput = LangStateUpdateInput(
        uid = "user-1",
        lang = LangCode.EN,
        sessionMemoryKey = "session-1",
        analysisEventId = "analysis-1",
        currentState = LangState.initial(LangCode.EN),
        recentUserTurns = emptyList(),
        correctionResult = null,
        flashcardReviewEvents = emptyList(),
        analyzedAt = 1_700_000_000_000L,
    )

    private fun sampleCandidate(): CorrectionCandidate =
        CorrectionSuggestionFixtures.sampleCandidate(lang = LangCode.EN)

    private companion object {
        const val GENERATION_TOTAL_DURATION_MS = 17_501L
    }

    private data class ViewModelHarness(
        val viewModel: CorrectionViewModel,
        val generateSuggestions: GenerateSuggestionsUseCase,
    )
}
