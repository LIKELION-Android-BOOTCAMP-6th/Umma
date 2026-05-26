package com.app.umma.presentation.statistics

import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.ExternalMetrics
import com.app.umma.domain.model.learningstate.FlashcardSummary
import com.app.umma.domain.model.learningstate.GlobalLangState
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
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
        val learningRepo = FakeLearningStateRepo(initialState = statisticsState(LangCode.EN))
        val statisticsRepo = ControlledStatisticsRepository()

        StatisticsViewModel(
            observeLearningStateUseCase = ObserveLearningStateUseCase(learningRepo),
            preloadLearningStateUseCase = PreloadLearningStateUseCase(learningRepo),
            observeStatisticsHistoryUseCase = ObserveStatisticsHistoryUseCase(statisticsRepo),
            refreshStatisticsHistoryUseCase = RefreshStatisticsHistoryUseCase(statisticsRepo),
            syncPendingStatisticsHistoriesUseCase = SyncPendingStatisticsHistoriesUseCase(statisticsRepo),
            getStatisticsOverviewUseCase = GetStatisticsOverviewUseCase(
                getCurrentUserUidUseCase = GetCurrentUserUidUseCase(FakeAuthRepository("user-1")),
                observeLearningStateUseCase = ObserveLearningStateUseCase(learningRepo)
            ),
            getMetricHistoryPointsUseCase = GetMetricHistoryPointsUseCase(statisticsRepo)
        )

        advanceUntilIdle()

        assertEquals(listOf("user-1"), statisticsRepo.syncedUserIds)
    }

    @Test
    fun `old chart result does not overwrite latest language state`() = runTest {
        // 언어 변경 중 오래 걸린 chart 응답이 늦게 도착해도 최신 선택 언어 상태를 덮지 않는지 본다.
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
                observeLearningStateUseCase = ObserveLearningStateUseCase(learningRepo)
            ),
            getMetricHistoryPointsUseCase = GetMetricHistoryPointsUseCase(statisticsRepo)
        )

        assertEquals(LangCode.EN, viewModel.uiState.value.selectedLearningLanguage)

        // EN chart 요청은 아직 완료되지 않은 상태에서 언어를 KO로 바꿔, 오래된 결과가 최신 화면을 덮지 않는지 본다.
        viewModel.onMetricClick(StatisticsMetricType.GrammarAccuracy)
        assertTrue(viewModel.uiState.value.metricChartState is StatisticsMetricChartState.Loading)

        learningRepo.emit(statisticsState(LangCode.KO))
        assertEquals(LangCode.KO, viewModel.uiState.value.selectedLearningLanguage)
        assertTrue(viewModel.uiState.value.metricChartState is StatisticsMetricChartState.Hidden)

        // 늦게 도착한 EN 결과를 풀어도, 이미 KO로 전환된 화면을 되돌리면 안 된다.
        statisticsRepo.release(LangCode.EN)

        assertEquals(LangCode.KO, viewModel.uiState.value.selectedLearningLanguage)
        assertTrue(viewModel.uiState.value.metricChartState is StatisticsMetricChartState.Hidden)
    }

    private fun statisticsState(lang: LangCode): GlobalLangState {
        // lang 별로 서로 다른 external snapshot을 만들어, selected language 전환이 화면에 반영되는지 검증한다.
        val selectedExternal = if (lang == LangCode.EN) {
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

        return GlobalLangState(
            userPref = UserLangPref.initial(
                nativeLang = LangCode.KO,
                primaryLang = LangCode.EN
            ).copy(
                selectedLang = lang,
                learningLangs = listOf(LangCode.EN, LangCode.KO)
            ),
            langStates = mapOf(
                LangCode.EN to LangState.initial(LangCode.EN).copy(
                    external = ExternalMetrics(
                        vocabularyLevel = VocabLevel.B2,
                        grammarAccuracy = 0.81,
                        expressionRange = 8,
                        fluencyScore = 0.79,
                        naturalnessScore = 0.77
                    ),
                    updatedAt = 1_000L
                ),
                LangCode.KO to LangState.initial(LangCode.KO).copy(
                    external = selectedExternal,
                    updatedAt = 2_000L
                )
            ),
            dashSummaries = mapOf(
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
        override suspend fun updateLanguageState(
            input: com.app.umma.domain.model.learningstate.LangStateUpdateInput
        ): Result<com.app.umma.domain.model.learningstate.LearningStateUpdateResult> =
            Result.failure(UnsupportedOperationException())

        override suspend fun updateFlashcardSummary(
            input: com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateInput
        ): Result<com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateResult> =
            Result.failure(UnsupportedOperationException())

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

    private class FakeAuthRepository(
        private val uid: String?
    ) : AuthRepository {
        override val currentUserUid: Flow<String?> = MutableStateFlow(uid)

        override suspend fun signInWithGoogle(idToken: String): Result<String> =
            Result.success(uid.orEmpty())

        override fun getCurrentUserUid(): String? = uid
        override fun getCurrentUserEmail(): String? = "test@example.com"
        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
        override suspend fun hasValidSession(): Result<Boolean> = Result.success(true)
    }

    private class ControlledStatisticsRepository : StatisticsRepository {
        // language별로 다른 completion 시점을 만들어, 오래된 결과가 늦게 도착하는 상황을 재현한다.
        private val pendingResults = mutableMapOf<LangCode, CompletableDeferred<StatisticsHistoryState>>()
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
        ): Result<Unit> = Result.success(Unit)

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
