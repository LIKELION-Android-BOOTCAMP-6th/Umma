package com.example.umma.data.repository.fake

import com.example.umma.domain.model.learningstate.DashSummary
import com.example.umma.domain.model.learningstate.FlashcardSummary
import com.example.umma.domain.model.learningstate.GlobalLangState
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.LangState
import com.example.umma.domain.model.learningstate.LangStateUpdateInput
import com.example.umma.domain.model.learningstate.SessionSummary
import com.example.umma.domain.model.learningstate.UserLangPref
import com.example.umma.domain.repository.LearningStateRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fake LearningStateRepo — Dashboard 화면 시나리오 검증용.
 *
 * SSOT: USER_FLOW_MOCK_REAL_DATA_GUIDE.md §5.2 / §7.1 / §7.3
 *
 * 사용법:
 *  1) di/RepositoryModule.kt 의 bindLearningStateRepo() 파라미터 타입을
 *     LearningStateRepoImpl → FakeLearningStateRepo 로 한 줄 교체 (import 도 동일)
 *  2) 아래 ACTIVE_FIXTURE 를 검증 시나리오에 맞게 토글
 *  3) 빌드 → 화면 + logcat (DashboardViewModel 태그) 확인
 *  4) 머지 전 RepositoryModule 을 real 로 원복
 *     ※ 본 파일 자체는 레포에 남아도 OK
 *
 * Fixture 매핑:
 *  - emptyDataStore  : 진짜 신규 사용자 (Initial Setup 도 안 한 상태)
 *  - onboardingDone  : Initial Setup 직후, 대화 0 회
 *  - activeUser      : 며칠 학습한 사용자, 오래된 cache 가정
 *
 * DASH-001 미사용 메서드 (updateLanguageState / createInitial) 는
 * Result.success 만 반환. DASH-002 본 구현 / 다음 단계에서 필요해지면 채울 것
 */
@Singleton
class FakeLearningStateRepo @Inject constructor() : LearningStateRepo {

    /**
     * 검증할 시나리오를 여기서 한 줄 토글한다.
     *
     * - FakeFixtures.emptyDataStore  → Empty UI (lang=null)
     * - FakeFixtures.onboardingDone → Empty UI (lang=EN, isEffectivelyEmpty)
     * - FakeFixtures.activeUser     → Content (lang=EN, recentTopic="Travel" 등)
     */

//    private val _state = MutableStateFlow(FakeFixtures.emptyDataStore) // DASH-001 AC 8: '신규 사용자는 Empty Dashboard UI가 출력된다.' 확인용
//    private val _state = MutableStateFlow(FakeFixtures.onboardingDone) // DASH-001 AC 5: '현재 선택 언어 기준 Dashboard 카드 데이터가 정상 출력된다.' 중  선택 언어는 있되 기록 전무 시 확인용
    private val _state = MutableStateFlow(FakeFixtures.activeUser) // DASH-001 AC 2, AC 3, AC 4, AC 5, AC 12 확인용
    // DASH-001 AC 2: Dashboard 진입 시 UserLangPref preload가 수행된다.
    // DASH-001 AC 3: selectedLearningLanguage가 확인된다.
    // DASH-001 AC 4: Local Cache 기반으로 Dashboard가 빠르게 렌더링된다.
    // DASH-001 AC 5: 현재 선택 언어 기준 Dashboard 카드 데이터가 정상 출력된다.
    // DASH-001 AC 12: 오래된 cache 데이터가 존재하더라도 우선 렌더링된다.

    override fun observeLearningState(): Flow<GlobalLangState> = _state.asStateFlow()
    override fun observeUserPref() = _state.map { it.userPref }
    override fun observeLangState(lang: LangCode) = _state.map { it.langStates[lang] }
    override fun observeDashSummary(lang: LangCode) = _state.map { it.dashSummaries[lang] }
    override fun observeSessionSummary(lang: LangCode) = _state.map { it.sessionSummaries[lang] }
    override fun observeFlashcardSummary(lang: LangCode) = _state.map { it.flashcardSummaries[lang] }

    override suspend fun preload(): Result<Unit> {
        // Fake 는 init 시점에 _state 가 채워져 있어 noop(No Operation). (호출 자체는 정상 수행된다)
        return Result.success(Unit)
    }

    override suspend fun changeSelectedLang(lang: LangCode): Result<Unit> {
        // DASH-006 본 구현 검증 시 활용. selectedLang 만 바꾸고 나머지는 유지.
        val current = _state.value
        val userPref = current.userPref ?: return Result.success(Unit)
        _state.value = current.copy(
            userPref = userPref.copy(
                selectedLang = lang,
                learningLangs = (userPref.learningLangs + lang).distinct()
            )
        )
        return Result.success(Unit)
    }

    // 필요해지는 시점에 채울 것
    override suspend fun updateLanguageState(input: LangStateUpdateInput): Result<Unit> =
        Result.success(Unit)

    override suspend fun createInitial(
        userUid: String,
        userPref: UserLangPref,
        langState: LangState,
        dashSummary: DashSummary,
        sessionSummary: SessionSummary,
        flashcardSummary: FlashcardSummary
    ): Result<Unit> = Result.success(Unit)

    override suspend fun clear(): Result<Unit> {
        _state.value = GlobalLangState.initial()
        return Result.success(Unit)
    }
}

/**
 * Dashboard 화면 시나리오 fixture.
 *
 * updatedAt 의 STALE_TIMESTAMP 는 "오래된 cache" 의 의도를 표현하기 위한 고정값일 뿐,
 * 현재 코드는 timestamp 를 검사하지 않으므로 값 자체는 동작에 영향이 없음.
 * Firebase background sync 가 들어오면 이 값이 최신 timestamp 로 덮여야 함
 */
private object FakeFixtures {

    // AC 8: 진짜 신규 사용자, Initial Setup 도 아직.
    val emptyDataStore: GlobalLangState = GlobalLangState.initial()

    // AC 5 의 Empty 분기 검증: Initial Setup 끝났지만 활동 0 회.
    val onboardingDone: GlobalLangState = run {
        val lang = LangCode.EN
        GlobalLangState(
            userPref = UserLangPref.initial(
                nativeLang = LangCode.KO,
                primaryLang = lang
            ),
            langStates = mapOf(lang to LangState.initial(lang)),
            dashSummaries = mapOf(lang to DashSummary.initial(lang)),
            sessionSummaries = mapOf(lang to SessionSummary.initial(lang)),
            flashcardSummaries = mapOf(lang to FlashcardSummary.initial(lang)),
            isPreloaded = true
        )
    }

    // AC 메인 검증: 며칠 학습한 사용자.
    val activeUser: GlobalLangState = run {
        val primary = LangCode.EN
        GlobalLangState(
            userPref = UserLangPref(
                nativeLang = LangCode.KO,
                primaryLang = primary,
                selectedLang = primary,
                learningLangs = listOf(LangCode.EN, LangCode.JA),
                updatedAt = STALE_TIMESTAMP
            ),
            langStates = mapOf(
                primary to LangState.initial(primary).copy(updatedAt = STALE_TIMESTAMP),
                LangCode.JA to LangState.initial(LangCode.JA)
            ),
            dashSummaries = mapOf(
                primary to DashSummary(
                    lang = primary,
                    recentMinutes = 30,
                    recentTopic = "Travel",
                    correctionAvailable = true,
                    dueFlashcards = 12,
                    savedFlashcards = 84,
                    grammarDelta = 8,
                    fluencyDelta = 12,
                    vocabDelta = 5,
                    naturalnessDelta = 15,
                    updatedAt = STALE_TIMESTAMP
                ),
                LangCode.JA to DashSummary(
                    lang = LangCode.JA,
                    recentMinutes = 15,
                    recentTopic = "日常会話",
                    correctionAvailable = true,
                    dueFlashcards = 5,
                    savedFlashcards = 22,
                    grammarDelta = 3,
                    fluencyDelta = 4,
                    vocabDelta = 2,
                    naturalnessDelta = 6,
                    updatedAt = STALE_TIMESTAMP
                )
            ),
            sessionSummaries = mapOf(
                primary to SessionSummary(
                    lang = primary,
                    correctionAvailable = true,
                    recentMinutes = 30,
                    recentTopic = "Travel",
                    updatedAt = STALE_TIMESTAMP
                ),
                LangCode.JA to SessionSummary(
                    lang = LangCode.JA,
                    correctionAvailable = true,
                    recentMinutes = 15,
                    recentTopic = "日常会話",
                    updatedAt = STALE_TIMESTAMP
                )
            ),
            flashcardSummaries = mapOf(
                primary to FlashcardSummary(
                    lang = primary,
                    dueFlashcards = 12,
                    savedFlashcards = 84,
                    updatedAt = STALE_TIMESTAMP
                ),
                LangCode.JA to FlashcardSummary(
                    lang = LangCode.JA,
                    dueFlashcards = 5,
                    savedFlashcards = 22,
                    updatedAt = STALE_TIMESTAMP
                )
            ),
            isPreloaded = true
        )
    }

    // 2023-11-14 부근. 캐시가 "오래됐다" 의 의도를 코드에 남기기 위한 고정값.
    private const val STALE_TIMESTAMP: Long = 1_700_000_000_000L
}