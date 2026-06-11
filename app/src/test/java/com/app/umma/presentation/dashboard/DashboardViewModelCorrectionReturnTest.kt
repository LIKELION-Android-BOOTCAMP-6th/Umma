package com.app.umma.presentation.dashboard

import android.util.Log
import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.GlobalLangState
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.OnboardingGuideStage
import com.app.umma.domain.model.learningstate.UserLangPref
import com.app.umma.domain.usecase.auth.GetCurrentUserUidUseCase
import com.app.umma.domain.usecase.flashcardreview.SyncDirtyFlashcardsUseCase
import com.app.umma.domain.usecase.learningstate.ChangeSelectedLangUseCase
import com.app.umma.domain.usecase.learningstate.EnsureLearningStateLoadedUseCase
import com.app.umma.domain.usecase.learningstate.LearningStateLoadResult
import com.app.umma.domain.usecase.learningstate.ObserveLearningStateUseCase
import com.app.umma.domain.usecase.learningstate.SyncLearningStateUseCase
import com.app.umma.domain.usecase.onboarding.AdvanceOnboardingGuideUseCase
import com.app.umma.domain.usecase.onboarding.OnboardingGuideEvent
import com.app.umma.presentation.correction.CorrectionReturnOutcome
import com.app.umma.test.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DashboardViewModelCorrectionReturnTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `saved outcome received before state readiness advances payload language exactly once`() = runTest {
        val state = MutableStateFlow(GlobalLangState.initial())
        val advanceOnboardingGuide = mockk<AdvanceOnboardingGuideUseCase>()
        coEvery {
            advanceOnboardingGuide(OnboardingGuideEvent.CorrectionSaved, LangCode.EN)
        } returns Result.success(Unit)
        val viewModel = createViewModel(state, advanceOnboardingGuide)

        viewModel.onCorrectionReturned(CorrectionReturnOutcome.SAVED, LangCode.EN)
        viewModel.onEnter()
        advanceUntilIdle()

        coVerify(exactly = 0) {
            advanceOnboardingGuide(any(), any())
        }

        state.value = readyState(selectedLang = LangCode.KO)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            advanceOnboardingGuide(OnboardingGuideEvent.CorrectionSaved, LangCode.EN)
        }

        state.value = readyState(
            selectedLang = LangCode.KO,
            updatedAt = 1L,
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            advanceOnboardingGuide(OnboardingGuideEvent.CorrectionSaved, LangCode.EN)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `no flashcard outcome waits for state readiness and resets payload language`() = runTest {
        val state = MutableStateFlow(GlobalLangState.initial())
        val advanceOnboardingGuide = mockk<AdvanceOnboardingGuideUseCase>()
        coEvery {
            advanceOnboardingGuide(OnboardingGuideEvent.CorrectionNoFlashcard, LangCode.EN)
        } returns Result.success(Unit)
        val viewModel = createViewModel(state, advanceOnboardingGuide)

        viewModel.onCorrectionReturned(CorrectionReturnOutcome.NO_FLASHCARD, LangCode.EN)
        viewModel.onEnter()
        advanceUntilIdle()

        coVerify(exactly = 0) {
            advanceOnboardingGuide(any(), any())
        }

        state.value = readyState(selectedLang = LangCode.KO)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            advanceOnboardingGuide(OnboardingGuideEvent.CorrectionNoFlashcard, LangCode.EN)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `failed transition remains pending and retries on next state emission`() = runTest {
        val state = MutableStateFlow(GlobalLangState.initial())
        val advanceOnboardingGuide = mockk<AdvanceOnboardingGuideUseCase>()
        coEvery {
            advanceOnboardingGuide(OnboardingGuideEvent.CorrectionSaved, LangCode.EN)
        } returnsMany listOf(
            Result.failure(IllegalStateException("write failed")),
            Result.success(Unit),
        )
        val viewModel = createViewModel(state, advanceOnboardingGuide)

        viewModel.onCorrectionReturned(CorrectionReturnOutcome.SAVED, LangCode.EN)
        viewModel.onEnter()
        state.value = readyState(selectedLang = LangCode.KO)
        advanceUntilIdle()

        state.value = readyState(
            selectedLang = LangCode.KO,
            updatedAt = 1L,
        )
        advanceUntilIdle()

        coVerify(exactly = 2) {
            advanceOnboardingGuide(OnboardingGuideEvent.CorrectionSaved, LangCode.EN)
        }
    }

    private fun createViewModel(
        state: MutableStateFlow<GlobalLangState>,
        advanceOnboardingGuide: AdvanceOnboardingGuideUseCase,
    ): DashboardViewModel {
        val ensureLearningStateLoaded = mockk<EnsureLearningStateLoadedUseCase>()
        val observeLearningState = mockk<ObserveLearningStateUseCase>()
        val syncLearningState = mockk<SyncLearningStateUseCase>()
        val changeSelectedLang = mockk<ChangeSelectedLangUseCase>(relaxed = true)
        val getCurrentUserUid = mockk<GetCurrentUserUidUseCase>(relaxed = true)
        val syncDirtyFlashcards = mockk<SyncDirtyFlashcardsUseCase>(relaxed = true)

        coEvery {
            ensureLearningStateLoaded()
        } returns Result.success(LearningStateLoadResult.LoadedFromLocal)
        every { observeLearningState() } returns state
        coEvery { syncLearningState() } returns Result.success(Unit)

        return DashboardViewModel(
            ensureLearningStateLoaded = ensureLearningStateLoaded,
            observeLearningState = observeLearningState,
            syncLearningState = syncLearningState,
            changeSelectedLang = changeSelectedLang,
            getCurrentUserUid = getCurrentUserUid,
            syncDirtyFlashcards = syncDirtyFlashcards,
            advanceOnboardingGuide = advanceOnboardingGuide,
        )
    }

    private fun readyState(
        selectedLang: LangCode,
        updatedAt: Long? = null,
    ): GlobalLangState = GlobalLangState.initial().copy(
        userPref = UserLangPref(
            primaryLang = LangCode.KO,
            selectedLang = selectedLang,
            learningLangs = listOf(LangCode.KO, LangCode.EN),
            onboardingGuideStages = mapOf(
                LangCode.KO to OnboardingGuideStage.CORRECTION,
                LangCode.EN to OnboardingGuideStage.CORRECTION,
            ),
            updatedAt = updatedAt,
        ),
        isPreloaded = true,
        dashSummaries = mapOf(
            LangCode.KO to DashSummary.initial(LangCode.KO).copy(updatedAt = updatedAt),
            LangCode.EN to DashSummary.initial(LangCode.EN),
        ),
    )
}
