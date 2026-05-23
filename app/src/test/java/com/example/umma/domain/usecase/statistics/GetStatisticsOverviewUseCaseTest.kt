package com.example.umma.domain.usecase.statistics

import com.example.umma.domain.model.learningstate.DashSummary
import com.example.umma.domain.model.learningstate.ExternalMetrics
import com.example.umma.domain.model.learningstate.FlashcardSummary
import com.example.umma.domain.model.learningstate.GlobalLangState
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.LangState
import com.example.umma.domain.model.learningstate.LearningStateUpdateResult
import com.example.umma.domain.model.learningstate.SessionSummary
import com.example.umma.domain.model.learningstate.UserLangPref
import com.example.umma.domain.model.learningstate.VocabLevel
import com.example.umma.domain.repository.AuthRepository
import com.example.umma.domain.repository.LearningStateRepo
import com.example.umma.domain.model.statistics.StatisticsMetricType
import com.example.umma.domain.usecase.auth.GetCurrentUserUidUseCase
import com.example.umma.domain.usecase.learningstate.ObserveLearningStateUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetStatisticsOverviewUseCaseTest {

    @Test
    fun `builds overview from current selected language and external metrics`() = runBlocking {
        val repo = RecordingLearningStateRepo(state = statsReadyState())
        val useCase = GetStatisticsOverviewUseCase(
            getCurrentUserUidUseCase = GetCurrentUserUidUseCase(FakeAuthRepository("user-1")),
            observeLearningStateUseCase = ObserveLearningStateUseCase(repo)
        )

        val result = useCase().getOrThrow()

        // Statistics 초기 진입은 current selected language 기준이어야 한다.
        // 여기서 userId, selectedLanguage, external metrics 가 한 번에 잡혀야
        // STAT-002/003가 이어받을 입력이 흔들리지 않는다.
        assertEquals("user-1", result.userId)
        assertEquals(LangCode.EN, result.selectedLearningLanguage)
        assertEquals(VocabLevel.B2, result.currentExternalMetrics.vocabularyLevel)
        assertEquals(StatisticsMetricType.entries.size, result.availableMetricTypes.size)
        assertTrue(result.historyQueryState is com.example.umma.domain.model.statistics.StatisticsHistoryQueryState.Ready)
    }

    @Test
    fun `fails when selected language is missing`() = runBlocking {
        val repo = RecordingLearningStateRepo(state = GlobalLangState.initial())
        val useCase = GetStatisticsOverviewUseCase(
            getCurrentUserUidUseCase = GetCurrentUserUidUseCase(FakeAuthRepository("user-1")),
            observeLearningStateUseCase = ObserveLearningStateUseCase(repo)
        )

        val result = useCase()

        // selectedLang 이 없으면 Statistics 화면은 조용히 Error 상태로 빠져야 한다.
        // 이 케이스는 진입 자체는 가능하지만, current language context 조립은 불가능한 상황을 본다.
        assertTrue(result.isFailure)
    }

    @Test
    fun `fails when current language state is missing`() = runBlocking {
        val repo = RecordingLearningStateRepo(
            state = GlobalLangState(
                userPref = UserLangPref.initial(
                    nativeLang = LangCode.KO,
                    primaryLang = LangCode.EN
                ),
                langStates = emptyMap(),
                dashSummaries = emptyMap(),
                sessionSummaries = emptyMap(),
                flashcardSummaries = emptyMap(),
                isPreloaded = true
            )
        )
        val useCase = GetStatisticsOverviewUseCase(
            getCurrentUserUidUseCase = GetCurrentUserUidUseCase(FakeAuthRepository("user-1")),
            observeLearningStateUseCase = ObserveLearningStateUseCase(repo)
        )

        val result = useCase()

        // selectedLang 은 있는데 LangState 가 없으면 history query-ready 상태를 만들 수 없다.
        // 즉, "언어는 고를 수 있지만 그 언어의 상태 snapshot 이 아직 없는" 초기화 실패다.
        assertTrue(result.isFailure)
    }

    private fun statsReadyState(): GlobalLangState {
        // 정상 진입 시나리오: selectedLang, LangState.external, Dashboard/Session/Flashcard 요약이
        // 모두 준비된 상태를 하나의 fixture 로 만들어 overview 조립 기준을 고정한다.
        val lang = LangCode.EN
        val external = ExternalMetrics(
            vocabularyLevel = VocabLevel.B2,
            grammarAccuracy = 0.73,
            expressionRange = 7,
            fluencyScore = 0.81,
            naturalnessScore = 0.68
        )
        val langState = LangState.initial(lang).copy(external = external)
        return GlobalLangState(
            userPref = UserLangPref.initial(
                nativeLang = LangCode.KO,
                primaryLang = lang
            ),
            langStates = mapOf(lang to langState),
            dashSummaries = mapOf(lang to DashSummary.initial(lang)),
            sessionSummaries = mapOf(lang to SessionSummary.initial(lang)),
            flashcardSummaries = mapOf(lang to FlashcardSummary.initial(lang)),
            isPreloaded = true
        )
    }

    private class RecordingLearningStateRepo(
        state: GlobalLangState
    ) : LearningStateRepo {
        private val backingState = MutableStateFlow(state)

        // observe 계열은 모두 backingState 를 같은 source of truth 로 공유한다.
        override fun observeLearningState(): Flow<GlobalLangState> = backingState

        override fun observeUserPref(): Flow<UserLangPref?> = backingState.map { it.userPref }

        override fun observeLangState(lang: LangCode): Flow<LangState?> =
            backingState.map { it.langStates[lang] }

        override fun observeDashSummary(lang: LangCode): Flow<DashSummary?> =
            backingState.map { it.dashSummaries[lang] }

        override fun observeSessionSummary(lang: LangCode): Flow<SessionSummary?> =
            backingState.map { it.sessionSummaries[lang] }

        override fun observeFlashcardSummary(lang: LangCode): Flow<FlashcardSummary?> =
            backingState.map { it.flashcardSummaries[lang] }

        override suspend fun preload(): Result<Unit> = Result.success(Unit)

        override suspend fun changeSelectedLang(lang: LangCode): Result<Unit> = Result.success(Unit)

        override suspend fun updateLanguageState(
            input: com.example.umma.domain.model.learningstate.LangStateUpdateInput
        ): Result<LearningStateUpdateResult> = Result.failure(UnsupportedOperationException())

        override suspend fun updateFlashcardSummary(
            input: com.example.umma.domain.model.learningstate.FlashcardSummaryUpdateInput
        ): Result<com.example.umma.domain.model.learningstate.FlashcardSummaryUpdateResult> =
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
        // Statistics 초기 컨텍스트는 currentUserUid 의존성을 분리해서 확인해야 하므로
        // auth repository 는 최소 구현만 제공한다.
        override val currentUserUid: Flow<String?> = MutableStateFlow(uid)

        override suspend fun signInWithGoogle(idToken: String): Result<String> =
            Result.success(uid.orEmpty())

        override fun getCurrentUserUid(): String? = uid

        override fun getCurrentUserEmail(): String? = "test@example.com"

        override suspend fun signOut(): Result<Unit> = Result.success(Unit)

        override suspend fun hasValidSession(): Result<Boolean> = Result.success(true)
    }
}
