package com.app.umma.presentation.correction

import android.util.Log
import com.app.umma.core.tts.TextToSpeechController
import com.app.umma.data.repository.correction.CorrectionSuggestionFixtures
import com.app.umma.devtools.correctionpromptreview.ReportCorrectionPromptReviewUseCase
import com.app.umma.domain.model.correction.CachedCorrectionResult
import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.FlashcardSummary
import com.app.umma.domain.model.learningstate.GlobalLangState
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.UserLangPref
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
import com.app.umma.domain.usecase.flashcardreview.GetFlashcardsUseCase
import com.app.umma.domain.usecase.learningstate.BuildLangStateUpdateInputUseCase
import com.app.umma.domain.usecase.learningstate.BuildLearnerAdaptationProfileUseCase
import com.app.umma.domain.usecase.learningstate.ObserveLearningStateUseCase
import com.app.umma.domain.usecase.learningstate.PreloadLearningStateUseCase
import com.app.umma.domain.usecase.realtime.GetCorrectionContextUseCase
import com.app.umma.domain.usecase.realtime.GetSessionMemoryUseCase
import com.app.umma.test.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class CorrectionViewModelTtsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `onPlaySuggestionAudio is no-op when selected language is missing`() {
        val ttsController = mockk<TextToSpeechController>(relaxed = true)
        val viewModel = buildViewModel(
            observeLearningState = flowOf(GlobalLangState.initial()),
            ttsController = ttsController,
        )

        viewModel.onPlaySuggestionAudio(sampleSuggestion())

        assertNull(viewModel.uiState.value.speakingSuggestionId)
        verify(exactly = 0) { ttsController.setLanguage(any()) }
        verify(exactly = 0) { ttsController.speak(any(), any(), any(), any()) }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `onPlaySuggestionAudio is no-op when afterText is blank`() = runTest {
        val ttsController = mockk<TextToSpeechController>(relaxed = true)
        val suggestion = sampleSuggestion().copy(afterText = " ")
        val viewModel = buildReadyViewModel(suggestion, ttsController)
        advanceUntilIdle()

        viewModel.onPlaySuggestionAudio(suggestion)

        assertNull(viewModel.uiState.value.speakingSuggestionId)
        verify(exactly = 0) { ttsController.setLanguage(any()) }
        verify(exactly = 0) { ttsController.speak(any(), any(), any(), any()) }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `onPlaySuggestionAudio is no-op when setLanguage fails`() = runTest {
        val ttsController = mockk<TextToSpeechController>(relaxed = true)
        every { ttsController.setLanguage(LangCode.EN) } returns false
        val suggestion = sampleSuggestion()
        val viewModel = buildReadyViewModel(suggestion, ttsController)
        advanceUntilIdle()

        viewModel.onPlaySuggestionAudio(suggestion)

        assertNull(viewModel.uiState.value.speakingSuggestionId)
        verify(exactly = 1) { ttsController.setLanguage(LangCode.EN) }
        verify(exactly = 0) { ttsController.speak(any(), any(), any(), any()) }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `onPlaySuggestionAudio sets and clears speakingSuggestionId around playback`() = runTest {
        val ttsController = mockk<TextToSpeechController>()
        val onComplete = slot<() -> Unit>()
        every { ttsController.setLanguage(LangCode.EN) } returns true
        every {
            ttsController.speak(any(), capture(onComplete), any(), any())
        } returns true
        val suggestion = sampleSuggestion()
        val viewModel = buildReadyViewModel(suggestion, ttsController)
        advanceUntilIdle()

        viewModel.onPlaySuggestionAudio(suggestion)

        assertEquals(suggestion.id, viewModel.uiState.value.speakingSuggestionId)
        verify(exactly = 1) {
            ttsController.speak(suggestion.afterText, any(), any(), any())
        }

        onComplete.captured.invoke()

        assertNull(viewModel.uiState.value.speakingSuggestionId)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `onPlaySuggestionAudio replaces active speakingSuggestionId when a new card is tapped`() = runTest {
        val ttsController = mockk<TextToSpeechController>()
        every { ttsController.setLanguage(LangCode.EN) } returns true
        every { ttsController.speak(any(), any(), any(), any()) } returns true
        val first = sampleSuggestion()
        val second = first.copy(id = "s-2", afterText = "You go to school.")
        val viewModel = buildReadyViewModel(first, ttsController)
        advanceUntilIdle()

        viewModel.onPlaySuggestionAudio(first)
        assertEquals(first.id, viewModel.uiState.value.speakingSuggestionId)

        viewModel.onPlaySuggestionAudio(second)

        assertEquals(second.id, viewModel.uiState.value.speakingSuggestionId)
        verify(exactly = 2) { ttsController.speak(any(), any(), any(), any()) }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `onPlaySuggestionAudio clears speakingSuggestionId when speak cannot start`() = runTest {
        val ttsController = mockk<TextToSpeechController>()
        every { ttsController.setLanguage(LangCode.EN) } returns true
        every { ttsController.speak(any(), any(), any(), any()) } returns false
        val suggestion = sampleSuggestion()
        val viewModel = buildReadyViewModel(suggestion, ttsController)
        advanceUntilIdle()

        viewModel.onPlaySuggestionAudio(suggestion)

        assertNull(viewModel.uiState.value.speakingSuggestionId)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `older callback for same card cannot clear latest playback state`() = runTest {
        val ttsController = mockk<TextToSpeechController>()
        val callbacks = mutableListOf<() -> Unit>()
        every { ttsController.setLanguage(LangCode.EN) } returns true
        every {
            ttsController.speak(any(), any(), any(), any())
        } answers {
            callbacks += (args[1] as () -> Unit)
            true
        }
        val suggestion = sampleSuggestion()
        val viewModel = buildReadyViewModel(suggestion, ttsController)
        advanceUntilIdle()

        viewModel.onPlaySuggestionAudio(suggestion)
        viewModel.onPlaySuggestionAudio(suggestion)

        callbacks.first().invoke()

        assertEquals(suggestion.id, viewModel.uiState.value.speakingSuggestionId)

        callbacks.last().invoke()

        assertNull(viewModel.uiState.value.speakingSuggestionId)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `stopPronunciation stops tts and clears speakingSuggestionId`() = runTest {
        val ttsController = mockk<TextToSpeechController>()
        every { ttsController.setLanguage(LangCode.EN) } returns true
        every { ttsController.speak(any(), any(), any(), any()) } returns true
        every { ttsController.stop() } returns Unit
        val suggestion = sampleSuggestion()
        val viewModel = buildReadyViewModel(suggestion, ttsController)
        advanceUntilIdle()

        viewModel.onPlaySuggestionAudio(suggestion)
        assertEquals(suggestion.id, viewModel.uiState.value.speakingSuggestionId)

        viewModel.stopPronunciation()

        verify(exactly = 1) { ttsController.stop() }
        assertNull(viewModel.uiState.value.speakingSuggestionId)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `stopPronunciation is safe to call multiple times without crash`() = runTest {
        val ttsController = mockk<TextToSpeechController>()
        every { ttsController.setLanguage(LangCode.EN) } returns true
        every { ttsController.speak(any(), any(), any(), any()) } returns true
        every { ttsController.stop() } returns Unit
        val suggestion = sampleSuggestion()
        val viewModel = buildReadyViewModel(suggestion, ttsController)
        advanceUntilIdle()

        viewModel.stopPronunciation()
        viewModel.stopPronunciation()

        verify(exactly = 2) { ttsController.stop() }
        assertNull(viewModel.uiState.value.speakingSuggestionId)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `stale onComplete callback cannot restore speakingSuggestionId after stopPronunciation`() = runTest {
        val ttsController = mockk<TextToSpeechController>()
        val onComplete = slot<() -> Unit>()
        every { ttsController.setLanguage(LangCode.EN) } returns true
        every { ttsController.speak(any(), capture(onComplete), any(), any()) } returns true
        every { ttsController.stop() } returns Unit
        val suggestion = sampleSuggestion()
        val viewModel = buildReadyViewModel(suggestion, ttsController)
        advanceUntilIdle()

        viewModel.onPlaySuggestionAudio(suggestion)
        // 화면 이탈로 정지
        viewModel.stopPronunciation()
        assertNull(viewModel.uiState.value.speakingSuggestionId)

        // 이미 무효화된 stale 콜백이 뒤늦게 호출되어도 상태를 변경하지 않는다
        onComplete.captured.invoke()

        assertNull(viewModel.uiState.value.speakingSuggestionId)
    }

    private fun buildReadyViewModel(
        suggestion: CorrectionSuggestion,
        ttsController: TextToSpeechController,
    ): CorrectionViewModel {
        val viewModel = buildViewModel(
            observeLearningState = flowOf(readyGlobal(LangCode.EN, 1_234L)),
            ttsController = ttsController,
            cachedSuggestion = suggestion,
        )
        viewModel.onEnter()
        return viewModel
    }

    private fun buildViewModel(
        observeLearningState: Flow<GlobalLangState>,
        ttsController: TextToSpeechController,
        cachedSuggestion: CorrectionSuggestion? = null,
    ): CorrectionViewModel {
        val preloadLearningState = mockk<PreloadLearningStateUseCase>()
        val observeLearningStateUseCase = mockk<ObserveLearningStateUseCase>()
        val getCorrectionContext = mockk<GetCorrectionContextUseCase>()
        val getSessionMemory = mockk<GetSessionMemoryUseCase>()
        val extractSessionCandidates = mockk<ExtractSessionCandidatesUseCase>()
        val filterCorrectionCandidatesForSafety = mockk<FilterCorrectionCandidatesForSafetyUseCase>()
        val generateSuggestions = mockk<GenerateSuggestionsUseCase>()
        val getCachedCorrection = mockk<GetCachedCorrectionUseCase>()
        val saveCorrectionCache = mockk<SaveCorrectionCacheUseCase>(relaxed = true)
        val clearCorrectionCache = mockk<ClearCorrectionCacheUseCase>(relaxed = true)
        val buildLearnerAdaptationProfile = mockk<BuildLearnerAdaptationProfileUseCase>()
        val getCurrentUserUid = mockk<GetCurrentUserUidUseCase>()
        val prepareSaveRequest = mockk<PrepareSaveRequestUseCase>()
        val completeCorrection = mockk<CompleteCorrectionUseCase>()
        val buildLangStateUpdateInput = mockk<BuildLangStateUpdateInputUseCase>()
        val getFlashcards = mockk<GetFlashcardsUseCase>()
        val reconcileSavedCorrectionOnReentry = mockk<ReconcileSavedCorrectionOnReentryUseCase>()
        val reportCorrectionPromptReviewUseCase = mockk<ReportCorrectionPromptReviewUseCase>(relaxed = true)

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0

        coEvery { preloadLearningState.invoke() } returns Result.success(Unit)
        every { observeLearningStateUseCase.invoke() } returns observeLearningState
        every { getCurrentUserUid.getCurrentUserUid() } returns "user-1"
        if (cachedSuggestion != null) {
            coEvery {
                getCachedCorrection.invoke(
                    uid = "user-1",
                    language = LangCode.EN,
                    expectedFingerprint = 1_234L,
                )
            } returns CachedCorrectionResult(
                language = LangCode.EN,
                suggestions = listOf(cachedSuggestion),
                sessionFingerprint = 1_234L,
                primaryLanguage = LangCode.KO,
                cachedAt = 5_000L,
            )
        } else {
            coEvery { getCachedCorrection.invoke(any(), any(), any()) } returns null
        }
        coEvery { getFlashcards.invoke(any(), any()) } returns Result.success(emptyList())
        coEvery {
            reconcileSavedCorrectionOnReentry.invoke(any(), any(), any(), any(), any())
        } returns Result.success(ReconcileSavedCorrectionOnReentryUseCase.Outcome.NotSaved)

        return CorrectionViewModel(
            preloadLearningState = preloadLearningState,
            observeLearningState = observeLearningStateUseCase,
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

    private fun sampleSuggestion(): CorrectionSuggestion =
        CorrectionSuggestionFixtures.contentSuggestions(LangCode.EN).first()
}
