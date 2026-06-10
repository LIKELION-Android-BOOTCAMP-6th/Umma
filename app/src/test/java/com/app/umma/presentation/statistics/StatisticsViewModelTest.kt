package com.app.umma.presentation.statistics

import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.ChatEvidenceSummary
import com.app.umma.domain.model.learningstate.ExternalMetrics
import com.app.umma.domain.model.learningstate.FlashcardSummary
import com.app.umma.domain.model.learningstate.GlobalLangState
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.OnboardingGuideStage
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.SyncStatus
import com.app.umma.domain.model.learningstate.UserLangPref
import com.app.umma.domain.model.learningstate.VocabLevel
import com.app.umma.domain.model.statistics.StatisticsHistory
import com.app.umma.domain.model.statistics.StatisticsHistoryState
import com.app.umma.domain.model.statistics.StatisticsMetricType
import com.app.umma.domain.repository.AuthRepository
import com.app.umma.domain.repository.LearningStateRepo
import com.app.umma.domain.repository.StatisticsRepository
import com.app.umma.domain.usecase.auth.GetCurrentUserUidUseCase
import com.app.umma.domain.usecase.learningstate.BuildLearnerAdaptationProfileUseCase
import com.app.umma.domain.usecase.learningstate.ObserveLearningStateUseCase
import com.app.umma.domain.usecase.learningstate.PreloadLearningStateUseCase
import com.app.umma.domain.usecase.statistics.GetMetricHistoryPointsUseCase
import com.app.umma.domain.usecase.statistics.GetStatisticsOverviewUseCase
import com.app.umma.domain.usecase.statistics.ObserveStatisticsHistoryUseCase
import com.app.umma.domain.usecase.statistics.RefreshStatisticsHistoryUseCase
import com.app.umma.domain.usecase.statistics.SyncPendingStatisticsHistoriesUseCase
import com.app.umma.presentation.statistics.model.StatisticsMetricChartState
import com.app.umma.test.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StatisticsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `overview load retries pending histories for current user`() = runTest {
        // 화면 진입 시 overview가 준비되면 local PENDING history를 Firestore로 재시도해야 한다.
        // 이 테스트는 실제 저장 결과가 아니라 ViewModel이 retry usecase를 연결했는지를 검증한다.
        // LearningState는 selected language와 LangState.external을 제공하는 최소 정상 상태로 둔다.
        val learningRepo = FakeLearningStateRepo(initialState = statisticsState(LangCode.EN))
        // Statistics repository는 syncPendingHistories 호출 기록을 남기는 controlled fake다.
        val statisticsRepo = ControlledStatisticsRepository()

        // ViewModel init에서 observeContext()가 실행되고, overview 로딩 후 pending sync가 자동 호출된다.
        StatisticsViewModel(
            observeLearningStateUseCase = ObserveLearningStateUseCase(learningRepo),
            preloadLearningStateUseCase = PreloadLearningStateUseCase(learningRepo),
            observeStatisticsHistoryUseCase = ObserveStatisticsHistoryUseCase(statisticsRepo),
            refreshStatisticsHistoryUseCase = RefreshStatisticsHistoryUseCase(statisticsRepo),
            syncPendingStatisticsHistoriesUseCase = SyncPendingStatisticsHistoriesUseCase(statisticsRepo),
            getStatisticsOverviewUseCase = GetStatisticsOverviewUseCase(
                getCurrentUserUidUseCase = GetCurrentUserUidUseCase(FakeAuthRepository("user-1")),
                observeLearningStateUseCase = ObserveLearningStateUseCase(learningRepo),
                buildLearnerAdaptationProfileUseCase = BuildLearnerAdaptationProfileUseCase()
            ),
            getMetricHistoryPointsUseCase = GetMetricHistoryPointsUseCase(statisticsRepo)
        )

        // init에서 시작된 coroutine들이 모두 끝날 때까지 진행시켜 pending sync 호출 여부를 확정한다.
        advanceUntilIdle()

        // 현재 로그인 사용자 user-1에 대해서만 pending sync retry가 요청되어야 한다.
        assertEquals(listOf("user-1"), statisticsRepo.syncedUserIds)
    }

    @Test
    fun `conversation band click opens level guide instead of chart`() = runTest {
        // 종합 레벨은 변화 차트보다 "각 단계가 무엇을 뜻하는지" 설명하는 카드다.
        // 시작 상태는 EN으로 두어 overview와 카드 목록이 정상 준비된 화면을 만든다.
        val learningRepo = FakeLearningStateRepo(initialState = statisticsState(LangCode.EN))
        // chart repository는 있어야 하지만, 종합 레벨 클릭에서는 실제 조회가 시작되면 안 된다.
        val statisticsRepo = ControlledStatisticsRepository()
        val viewModel = StatisticsViewModel(
            observeLearningStateUseCase = ObserveLearningStateUseCase(learningRepo),
            preloadLearningStateUseCase = PreloadLearningStateUseCase(learningRepo),
            observeStatisticsHistoryUseCase = ObserveStatisticsHistoryUseCase(statisticsRepo),
            refreshStatisticsHistoryUseCase = RefreshStatisticsHistoryUseCase(statisticsRepo),
            syncPendingStatisticsHistoriesUseCase = SyncPendingStatisticsHistoriesUseCase(statisticsRepo),
            getStatisticsOverviewUseCase = GetStatisticsOverviewUseCase(
                getCurrentUserUidUseCase = GetCurrentUserUidUseCase(FakeAuthRepository("user-1")),
                observeLearningStateUseCase = ObserveLearningStateUseCase(learningRepo),
                buildLearnerAdaptationProfileUseCase = BuildLearnerAdaptationProfileUseCase()
            ),
            getMetricHistoryPointsUseCase = GetMetricHistoryPointsUseCase(statisticsRepo)
        )

        // 종합 레벨 클릭은 chart Loading 상태를 만들지 않고, 레벨 정의 안내 dialog만 열어야 한다.
        viewModel.onMetricClick(StatisticsMetricType.ConversationBand)

        assertTrue(viewModel.uiState.value.isConversationLevelGuideVisible)
        assertTrue(viewModel.uiState.value.metricChartState is StatisticsMetricChartState.Hidden)

        // 닫기 동작은 안내 dialog flag만 내리고, 차트 상태는 계속 닫힌 상태로 유지한다.
        viewModel.dismissConversationLevelGuide()

        assertFalse(viewModel.uiState.value.isConversationLevelGuideVisible)
        assertTrue(viewModel.uiState.value.metricChartState is StatisticsMetricChartState.Hidden)
    }

    @Test
    fun `old chart result does not overwrite latest language state`() = runTest {
        // 언어 변경 중 오래 걸린 chart 응답이 늦게 도착해도 최신 선택 언어 상태를 덮지 않는지 본다.
        // 시작 상태는 EN으로 두어 EN chart 요청을 먼저 발생시킨다.
        val learningRepo = FakeLearningStateRepo(initialState = statisticsState(LangCode.EN))
        // chart history 응답을 수동 release할 수 있게 controlled fake를 사용한다.
        val statisticsRepo = ControlledStatisticsRepository()
        val viewModel = StatisticsViewModel(
            observeLearningStateUseCase = ObserveLearningStateUseCase(learningRepo),
            preloadLearningStateUseCase = PreloadLearningStateUseCase(learningRepo),
            observeStatisticsHistoryUseCase = ObserveStatisticsHistoryUseCase(statisticsRepo),
            refreshStatisticsHistoryUseCase = RefreshStatisticsHistoryUseCase(statisticsRepo),
            syncPendingStatisticsHistoriesUseCase = SyncPendingStatisticsHistoriesUseCase(statisticsRepo),
            getStatisticsOverviewUseCase = GetStatisticsOverviewUseCase(
                getCurrentUserUidUseCase = GetCurrentUserUidUseCase(FakeAuthRepository("user-1")),
                observeLearningStateUseCase = ObserveLearningStateUseCase(learningRepo),
                buildLearnerAdaptationProfileUseCase = BuildLearnerAdaptationProfileUseCase()
            ),
            getMetricHistoryPointsUseCase = GetMetricHistoryPointsUseCase(statisticsRepo)
        )

        // ViewModel은 init 직후 EN selected language 기준으로 overview를 준비해야 한다.
        assertEquals(LangCode.EN, viewModel.uiState.value.selectedLearningLanguage)

        // EN chart 요청은 아직 완료되지 않은 상태에서 언어를 KO로 바꿔, 오래된 결과가 최신 화면을 덮지 않는지 본다.
        // GrammarAccuracy 클릭으로 chart dialog를 Loading 상태까지 열어 둔다.
        viewModel.onMetricClick(StatisticsMetricType.GrammarAccuracy)
        assertTrue(viewModel.uiState.value.metricChartState is StatisticsMetricChartState.Loading)

        // LearningState emit은 실제 언어 선택 변경처럼 ViewModel의 context reload를 유발한다.
        learningRepo.emit(statisticsState(LangCode.KO))
        // 최신 language context는 KO여야 한다.
        assertEquals(LangCode.KO, viewModel.uiState.value.selectedLearningLanguage)
        // language reload 시 기존 chart dialog는 닫혀야 오래된 EN 결과가 보이지 않는다.
        assertTrue(viewModel.uiState.value.metricChartState is StatisticsMetricChartState.Hidden)

        // 늦게 도착한 EN 결과를 풀어도, 이미 KO로 전환된 화면을 되돌리면 안 된다.
        // release는 이전 EN observeHistory 요청을 완료시키는 역할이다.
        statisticsRepo.release(LangCode.EN)

        // release 이후에도 selected language가 KO로 유지되어야 한다.
        assertEquals(LangCode.KO, viewModel.uiState.value.selectedLearningLanguage)
        // 오래된 EN chart 결과가 Hidden 상태를 다시 Ready로 바꾸면 안 된다.
        assertTrue(viewModel.uiState.value.metricChartState is StatisticsMetricChartState.Hidden)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `language change updates metric summary cards`() = runTest {
        // 차트 query뿐 아니라 overview 카드도 selected language 변경을 따라가야 한다.
        val learningRepo = FakeLearningStateRepo(initialState = statisticsState(LangCode.EN))
        val statisticsRepo = ControlledStatisticsRepository()
        val viewModel = StatisticsViewModel(
            observeLearningStateUseCase = ObserveLearningStateUseCase(learningRepo),
            preloadLearningStateUseCase = PreloadLearningStateUseCase(learningRepo),
            observeStatisticsHistoryUseCase = ObserveStatisticsHistoryUseCase(statisticsRepo),
            refreshStatisticsHistoryUseCase = RefreshStatisticsHistoryUseCase(statisticsRepo),
            syncPendingStatisticsHistoriesUseCase = SyncPendingStatisticsHistoriesUseCase(statisticsRepo),
            getStatisticsOverviewUseCase = GetStatisticsOverviewUseCase(
                getCurrentUserUidUseCase = GetCurrentUserUidUseCase(FakeAuthRepository("user-1")),
                observeLearningStateUseCase = ObserveLearningStateUseCase(learningRepo),
                buildLearnerAdaptationProfileUseCase = BuildLearnerAdaptationProfileUseCase()
            ),
            getMetricHistoryPointsUseCase = GetMetricHistoryPointsUseCase(statisticsRepo)
        )

        // 최초 EN 카드 값이 준비될 때까지 init coroutine을 진행한다.
        advanceUntilIdle()
        assertEquals(LangCode.EN, viewModel.uiState.value.selectedLearningLanguage)
        assertEquals("문장 Level", viewModel.uiState.value.metricSummaryCards.first().valueText)

        // LearningState emit으로 selected language를 KO로 바꾸면 overview를 다시 조립해야 한다.
        learningRepo.emit(statisticsState(LangCode.KO))
        advanceUntilIdle()

        // 언어 label과 카드 값이 모두 KO snapshot 기준으로 바뀌어야 한다.
        assertEquals(LangCode.KO, viewModel.uiState.value.selectedLearningLanguage)
        assertEquals("문장 Level", viewModel.uiState.value.metricSummaryCards.first().valueText)
        assertEquals("71%", viewModel.uiState.value.metricSummaryCards[1].valueText)
        assertEquals("81%", viewModel.uiState.value.metricSummaryCards[2].valueText)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `old chart result does not overwrite latest metric selection`() = runTest {
        // 빠른 지표 전환 중 이전 chart 요청이 늦게 끝나도 마지막으로 누른 metric만 dialog에 남아야 한다.
        // language는 고정하고 metric만 빠르게 바꾸어 chart requestVersion 방어만 분리해서 검증한다.
        val learningRepo = FakeLearningStateRepo(initialState = statisticsState(LangCode.EN))
        // 모든 chart 요청은 같은 EN history stream을 기다리도록 만들어 race 상황을 단순화한다.
        val statisticsRepo = ControlledStatisticsRepository()
        val viewModel = StatisticsViewModel(
            observeLearningStateUseCase = ObserveLearningStateUseCase(learningRepo),
            preloadLearningStateUseCase = PreloadLearningStateUseCase(learningRepo),
            observeStatisticsHistoryUseCase = ObserveStatisticsHistoryUseCase(statisticsRepo),
            refreshStatisticsHistoryUseCase = RefreshStatisticsHistoryUseCase(statisticsRepo),
            syncPendingStatisticsHistoriesUseCase = SyncPendingStatisticsHistoriesUseCase(statisticsRepo),
            getStatisticsOverviewUseCase = GetStatisticsOverviewUseCase(
                getCurrentUserUidUseCase = GetCurrentUserUidUseCase(FakeAuthRepository("user-1")),
                observeLearningStateUseCase = ObserveLearningStateUseCase(learningRepo),
                buildLearnerAdaptationProfileUseCase = BuildLearnerAdaptationProfileUseCase()
            ),
            getMetricHistoryPointsUseCase = GetMetricHistoryPointsUseCase(statisticsRepo)
        )

        // 각 클릭은 새 chart requestVersion을 만들고 이전 chart job을 취소한다.
        // 테스트 repository는 같은 history 응답을 늦게 풀어 오래된 요청들이 동시에 완료될 수 있는 상황을 만든다.
        // 첫 클릭은 곧 stale이 될 요청이다.
        viewModel.onMetricClick(StatisticsMetricType.GrammarAccuracy)
        // 두 번째 클릭도 마지막 클릭이 아니므로 stale 후보가 된다.
        viewModel.onMetricClick(StatisticsMetricType.FluencyScore)
        // 마지막 클릭만 최종 dialog 상태로 남아야 한다.
        viewModel.onMetricClick(StatisticsMetricType.NaturalnessScore)

        // 응답이 오기 전에는 마지막 선택 metric 기준 Loading 상태여야 한다.
        val loading = viewModel.uiState.value.metricChartState as StatisticsMetricChartState.Loading
        assertEquals(StatisticsMetricType.NaturalnessScore, loading.metricType)

        // 늦게 도착한 history 응답을 풀었을 때도 마지막 선택인 NaturalnessScore만 Ready 상태로 반영되어야 한다.
        // 이전 요청이 살아 있었다면 같은 release에서 앞선 metric이 화면을 덮을 수 있다.
        statisticsRepo.release(LangCode.EN)
        // release 이후 chart 변환 coroutine까지 마무리한다.
        advanceUntilIdle()

        // 최종 Ready metric이 마지막 클릭과 같아야 빠른 지표 전환 방어가 성립한다.
        val ready = viewModel.uiState.value.metricChartState as StatisticsMetricChartState.Ready
        assertEquals(StatisticsMetricType.NaturalnessScore, ready.metricType)
    }

    private fun statisticsState(lang: LangCode): GlobalLangState {
        // lang 별로 서로 다른 evidence snapshot을 만들어, selected language 전환이 화면에 반영되는지 검증한다.
        // EN은 시작 상태, KO는 언어 변경 이후 상태로 사용한다.
        val isEnglish = lang == LangCode.EN
        val selectedExternal = if (isEnglish) {
            ExternalMetrics(
                vocabularyLevel = VocabLevel.B2,
                grammarAccuracy = 0.81,
                expressionRange = 8,
                fluencyScore = 0.79,
                naturalnessScore = 0.77
            )
        } else {
            ExternalMetrics(
                vocabularyLevel = VocabLevel.B1,
                grammarAccuracy = 0.74,
                expressionRange = 7,
                fluencyScore = 0.71,
                naturalnessScore = 0.69
            )
        }
        val chatEvidence = ChatEvidenceSummary(
            targetLanguageComprehension = if (isEnglish) {
                com.app.umma.domain.model.chat.TargetLanguageComprehensionEvidence.SimpleSentence
            } else {
                com.app.umma.domain.model.chat.TargetLanguageComprehensionEvidence.NaturalFlow
            },
            targetLanguageProduction = if (isEnglish) {
                com.app.umma.domain.model.chat.TargetLanguageProductionEvidence.SimpleSentences
            } else {
                com.app.umma.domain.model.chat.TargetLanguageProductionEvidence.ConnectedTurns
            },
            supportLanguageDependence = if (isEnglish) {
                com.app.umma.domain.model.chat.LanguageDependenceEvidence.Medium
            } else {
                com.app.umma.domain.model.chat.LanguageDependenceEvidence.Low
            },
            aiScaffoldingDependence = if (isEnglish) {
                com.app.umma.domain.model.chat.LanguageDependenceEvidence.Medium
            } else {
                com.app.umma.domain.model.chat.LanguageDependenceEvidence.Low
            },
            conversationSustainability = if (isEnglish) {
                com.app.umma.domain.model.chat.ConversationSustainabilityEvidence.SustainedSimple
            } else {
                com.app.umma.domain.model.chat.ConversationSustainabilityEvidence.SustainedNatural
            },
            consistency = com.app.umma.domain.model.chat.ConversationConsistencyEvidence.Stable,
            responseDifficultyFit = com.app.umma.domain.model.chat.ResponseDifficultyFitEvidence.Fits,
            confidence = if (isEnglish) {
                com.app.umma.domain.model.learningstate.ProfileConfidence.Medium
            } else {
                com.app.umma.domain.model.learningstate.ProfileConfidence.High
            },
            observedCount = if (isEnglish) 3 else 4,
            lastObservedAt = if (isEnglish) 1_000L else 2_000L
        )
        val grammarEvidence = com.app.umma.domain.model.learningstate.MetricEvidence(
            observedCount = if (isEnglish) 3 else 4,
            confidence = if (isEnglish) 0.82 else 0.74,
            sourceTypes = setOf(com.app.umma.domain.model.learningstate.LearningSignalSource.CorrectionSignal),
            direction = com.app.umma.domain.model.learningstate.EvidenceDirection.Up,
            directionCount = if (isEnglish) 3 else 4,
            lastObservedAt = if (isEnglish) 1_000L else 2_000L
        )
        val internal = com.app.umma.domain.model.learningstate.InternalMetrics.initial().copy(
            grammarAccuracy = if (isEnglish) 0.81 else 0.74,
            speechRate = if (isEnglish) 0.79 else 0.71,
            pauseFrequency = if (isEnglish) 0.18 else 0.23,
            avgUtteranceLength = if (isEnglish) 0.68 else 0.61,
            spokenNaturalness = if (isEnglish) 0.77 else 0.69,
            naturalExpressionUsage = if (isEnglish) 0.74 else 0.66
        )
        val analysisMeta = com.app.umma.domain.model.learningstate.LangStateAnalysisMeta(
            metricEvidence = mapOf(
                com.app.umma.domain.model.learningstate.LearningMetricKey.GrammarAccuracy to grammarEvidence
            ),
            activeFocus = emptyList(),
            lastSignalAt = if (isEnglish) 1_000L else 2_000L,
            lastChatAnalysisEventId = "chat-$lang",
            chatEvidenceSummary = chatEvidence
        )
        val updatedAnalysisMeta = com.app.umma.domain.model.learningstate.LangStateAnalysisMeta(
            metricEvidence = mapOf(
                com.app.umma.domain.model.learningstate.LearningMetricKey.GrammarAccuracy to grammarEvidence.copy(
                    observedCount = if (isEnglish) 4 else 3,
                    confidence = if (isEnglish) 0.74 else 0.82,
                    lastObservedAt = if (isEnglish) 2_000L else 1_000L
                )
            ),
            activeFocus = emptyList(),
            lastSignalAt = if (isEnglish) 2_000L else 1_000L,
            lastChatAnalysisEventId = "chat-$lang-ko",
            chatEvidenceSummary = chatEvidence.copy(
                targetLanguageComprehension = if (isEnglish) {
                    com.app.umma.domain.model.chat.TargetLanguageComprehensionEvidence.NaturalFlow
                } else {
                    com.app.umma.domain.model.chat.TargetLanguageComprehensionEvidence.SimpleSentence
                },
                targetLanguageProduction = if (isEnglish) {
                    com.app.umma.domain.model.chat.TargetLanguageProductionEvidence.ConnectedTurns
                } else {
                    com.app.umma.domain.model.chat.TargetLanguageProductionEvidence.SimpleSentences
                },
                confidence = if (isEnglish) {
                    com.app.umma.domain.model.learningstate.ProfileConfidence.High
                } else {
                    com.app.umma.domain.model.learningstate.ProfileConfidence.Medium
                },
                observedCount = if (isEnglish) 4 else 3,
                lastObservedAt = if (isEnglish) 2_000L else 1_000L
            )
        )

        // ViewModel은 selectedLang과 currentLangState.updatedAt을 signature로 삼아 reload 여부를 판단한다.
        return GlobalLangState(
            userPref = UserLangPref.initial(primaryLang = LangCode.KO, selectedLang = LangCode.EN).copy(
                selectedLang = lang,
                learningLangs = listOf(LangCode.EN, LangCode.KO)
            ),
            langStates = mapOf(
                // EN state는 최초 화면 진입과 이전 chart 요청의 기준이다.
                LangCode.EN to LangState.initial(LangCode.EN).copy(
                    internal = internal,
                    external = selectedExternal,
                    analysisMeta = analysisMeta,
                    updatedAt = 1_000L
                ),
                // KO state는 언어 전환 이후 최신 context를 만들기 위한 입력이다.
                LangCode.KO to LangState.initial(LangCode.KO).copy(
                    internal = internal.copy(grammarAccuracy = if (isEnglish) 0.74 else 0.81),
                    external = selectedExternal,
                    analysisMeta = updatedAnalysisMeta,
                    updatedAt = 2_000L
                )
            ),
            dashSummaries = mapOf(
                // Statistics 테스트에서 직접 읽지는 않지만 GlobalLangState 정합성을 위해 summary를 채운다.
                LangCode.EN to DashSummary.initial(LangCode.EN),
                LangCode.KO to DashSummary.initial(LangCode.KO)
            ),
            sessionSummaries = mapOf(
                LangCode.EN to SessionSummary.initial(LangCode.EN),
                LangCode.KO to SessionSummary.initial(LangCode.KO)
            ),
            flashcardSummaries = mapOf(
                LangCode.EN to FlashcardSummary.initial(LangCode.EN),
                LangCode.KO to FlashcardSummary.initial(LangCode.KO)
            ),
            isPreloaded = true
        )
    }

    private class FakeLearningStateRepo(
        initialState: GlobalLangState
    ) : LearningStateRepo {
        // MutableStateFlow를 사용해 실제 repository observe처럼 새 GlobalLangState emit을 만들 수 있다.
        private val state = MutableStateFlow(initialState)

        fun emit(next: GlobalLangState) {
            // 실제 repository observe처럼 내부 snapshot을 교체하면 ViewModel이 새 language context로 다시 로드해야 한다.
            state.value = next
        }

        override fun observeLearningState(): Flow<GlobalLangState> = state
        override fun observeUserPref(): Flow<UserLangPref?> = state.map { it.userPref }
        override fun observeLangState(lang: LangCode): Flow<LangState?> = state.map { it.langStates[lang] }
        override fun observeDashSummary(lang: LangCode): Flow<DashSummary?> = state.map { it.dashSummaries[lang] }
        override fun observeSessionSummary(lang: LangCode): Flow<SessionSummary?> = state.map { it.sessionSummaries[lang] }
        override fun observeFlashcardSummary(lang: LangCode): Flow<FlashcardSummary?> = state.map { it.flashcardSummaries[lang] }
        override suspend fun preload(): Result<Unit> = Result.success(Unit)
        override suspend fun changeSelectedLang(lang: LangCode): Result<Unit> = Result.success(Unit)
        override suspend fun changePrimaryLang(lang: LangCode): Result<Unit> = Result.success(Unit)
        override suspend fun updateLanguageState(
            input: com.app.umma.domain.model.learningstate.LangStateUpdateInput
        ): Result<com.app.umma.domain.model.learningstate.LearningStateUpdateResult> =
            // 이 ViewModel 테스트는 update 경로를 쓰지 않으므로 호출되면 테스트 설계가 잘못된 것이다.
            Result.failure(UnsupportedOperationException())

        override suspend fun updateFlashcardSummary(
            input: com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateInput
        ): Result<com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateResult> =
            // Flashcard summary 갱신도 StatisticsViewModel의 관심사가 아니므로 사용을 금지한다.
            Result.failure(UnsupportedOperationException())

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
    }

    private class FakeAuthRepository(
        private val uid: String?
    ) : AuthRepository {
        // GetCurrentUserUidUseCase가 읽을 현재 로그인 사용자 snapshot이다.
        override val currentUserUid: Flow<String?> = MutableStateFlow(uid)

        override suspend fun signInWithGoogle(idToken: String): Result<String> =
            Result.success(uid.orEmpty())

        override fun getCurrentUserUid(): String? = uid
        override fun getCurrentUserEmail(): String? = "test@example.com"
        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
        override suspend fun hasValidSession(): Result<Boolean> = Result.success(true)
    }

    private class ControlledStatisticsRepository : StatisticsRepository {
        // language별로 다른 completion 시점을 만들어, 오래된 결과가 늦게 도착하는 상황을 재현한다.
        // CompletableDeferred를 잡아두면 테스트가 원하는 시점에 observeHistory 결과를 release할 수 있다.
        private val pendingResults = mutableMapOf<LangCode, CompletableDeferred<StatisticsHistoryState>>()
        // pending sync 호출 여부는 side effect 기록만으로 검증한다.
        val syncedUserIds = mutableListOf<String>()

        override fun observeHistory(
            userId: String,
            language: LangCode
        ): Flow<StatisticsHistoryState> = flow {
            // 첫 구독은 결과를 바로 주지 않고, 테스트가 release()할 때까지 기다린다.
            emit(pendingResults.getOrPut(language) { CompletableDeferred() }.await())
        }

        override suspend fun recordHistory(
            history: StatisticsHistory
        ): Result<com.app.umma.domain.model.statistics.StatisticsHistoryRecordResult> {
            // StatisticsViewModel은 기록을 만들지 않지만 interface 구현을 위해 성공 결과를 제공한다.
            return Result.success(
                com.app.umma.domain.model.statistics.StatisticsHistoryRecordResult(
                    historyId = history.id,
                    sourceEventId = history.sourceEventId,
                    applied = true,
                    isSyncPending = false,
                    recordedAt = history.recordedAt
                )
            )
        }

        override suspend fun refreshHistory(
            userId: String,
            language: LangCode
        ): Result<Unit> =
            // refresh 자체는 이번 ViewModel 테스트의 관심사가 아니므로 항상 성공시킨다.
            Result.success(Unit)

        override suspend fun syncPendingHistories(userId: String): Result<Int> {
            // ViewModel이 화면 진입 시 현재 userId로 pending retry를 호출했는지 확인할 수 있도록 기록한다.
            syncedUserIds += userId
            return Result.success(0)
        }

        fun release(language: LangCode) {
            // 나중에 도착한 응답을 명시적으로 완료시켜 stale result 경로를 만든다.
            pendingResults.getOrPut(language) { CompletableDeferred() }
                .complete(content(language))
        }

        private fun content(language: LangCode): StatisticsHistoryState {
            // 두 점 이상이 있어야 chart mapper가 Empty가 아니라 Ready를 만들 수 있다.
            return StatisticsHistoryState.Content(
                listOf(
                    StatisticsHistory(
                        id = "history-$language",
                        userId = "user-1",
                        language = language,
                        recordedAt = if (language == LangCode.EN) 1_000L else 2_000L,
                        vocabularyLevel = if (language == LangCode.EN) VocabLevel.B2 else VocabLevel.B1,
                        grammarAccuracy = if (language == LangCode.EN) 0.81 else 0.74,
                        expressionRange = if (language == LangCode.EN) 8 else 7,
                        fluencyScore = if (language == LangCode.EN) 0.79 else 0.71,
                        naturalnessScore = if (language == LangCode.EN) 0.77 else 0.69,
                        sourceEventId = "source-$language",
                        syncStatus = SyncStatus.SYNCED
                    ),
                    StatisticsHistory(
                        id = "history-${language}-2",
                        userId = "user-1",
                        language = language,
                        recordedAt = if (language == LangCode.EN) 2_000L else 3_000L,
                        vocabularyLevel = if (language == LangCode.EN) VocabLevel.C1 else VocabLevel.B2,
                        grammarAccuracy = if (language == LangCode.EN) 0.88 else 0.81,
                        expressionRange = if (language == LangCode.EN) 9 else 8,
                        fluencyScore = if (language == LangCode.EN) 0.85 else 0.77,
                        naturalnessScore = if (language == LangCode.EN) 0.82 else 0.74,
                        sourceEventId = "source-${language}-2",
                        syncStatus = SyncStatus.SYNCED
                    )
                )
            )
        }
    }
}
