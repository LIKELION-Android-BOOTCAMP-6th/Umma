package com.app.umma.presentation.correction

import android.util.Log
import com.app.umma.core.tts.TextToSpeechController
import com.app.umma.data.repository.correction.CorrectionSuggestionFixtures
import com.app.umma.devtools.correctionpromptreview.ReportCorrectionPromptReviewUseCase
import com.app.umma.domain.model.correction.CachedCorrectionResult
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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
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
        val global = readyGlobal(
            lang = LangCode.EN,
            sessionUpdatedAt = 1_234L,
        )
        val preloadLearningState = mockk<PreloadLearningStateUseCase>()
        val observeLearningState = mockk<ObserveLearningStateUseCase>()
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
        val ttsController = mockk<TextToSpeechController>(relaxed = true)
        val reportCorrectionPromptReviewUseCase = mockk<ReportCorrectionPromptReviewUseCase>(relaxed = true)

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0

        coEvery { preloadLearningState.invoke() } returns Result.success(Unit)
        every { observeLearningState.invoke() } returns flowOf(global)
        every { getCurrentUserUid.getCurrentUserUid() } returns "user-1"
        coEvery {
            getCachedCorrection.invoke(
                uid = "user-1",
                language = LangCode.EN,
                expectedFingerprint = 1_234L,
            )
        } returns CachedCorrectionResult(
            language = LangCode.EN,
            suggestions = suggestions,
            sessionFingerprint = 1_234L,
            primaryLanguage = LangCode.KO,
            cachedAt = 5_000L,
        )
        coEvery { getFlashcards.invoke(any(), any()) } returns Result.success(emptyList())

        val viewModel = CorrectionViewModel(
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
            ttsController = ttsController,
            reportCorrectionPromptReviewUseCase = reportCorrectionPromptReviewUseCase,
        )

        viewModel.onEnter()
        advanceUntilIdle()

        assertEquals(CorrectionUiState.Phase.Content, viewModel.uiState.value.phase)
        assertEquals(suggestions, viewModel.uiState.value.suggestions)
        assertTrue(viewModel.uiState.value.selectedSuggestionIds.isEmpty())
        coVerify(exactly = 0) { generateSuggestions.invoke(any()) }
    }

    private fun readyGlobal(
        lang: LangCode,
        sessionUpdatedAt: Long,
    ): GlobalLangState {
        return GlobalLangState(
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
    }
}
