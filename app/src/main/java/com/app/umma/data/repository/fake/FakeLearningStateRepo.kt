package com.app.umma.data.repository.fake

import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.FlashcardSummary
import com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateInput
import com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateResult
import com.app.umma.domain.model.learningstate.GlobalLangState
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LangStateUpdateInput
import com.app.umma.domain.model.learningstate.LearningStateUpdateResult
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.UserLangPref
import com.app.umma.domain.repository.LearningStateRepo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fake LearningStateRepo — Dashboard 화면 시나리오 검증용.
 *
 * SSOT: USER_FLOW_MOCK_REAL_DATA_GUIDE.md §5.2 / §7.1 / §7.3
 *
 * 사용법:
 *  1) di/RepositoryModule.kt 의 bindLearningStateRepo() 파라미터 타입을
 *     LearningStateRepoImpl → FakeLearningStateRepo 로 한 줄 교체
 *  2) 아래 ACTIVE_FIXTURE / SYNC_BEHAVIOR 를 검증 시나리오에 맞게 토글
 *  3) 빌드 → 화면 + logcat (DashboardViewModel 태그) 확인
 *  4) 머지 전 RepositoryModule 을 real 로 원복
 *
 * DASH-001 추가:
 *  - sync(): delay 후 _state 의 updatedAt 을 현재 시각으로 갱신 → 두 번째 emit 발생
 *           (AC 12 시각 검증: logcat 에 state emit 이 두 번 찍혀야 함)
 *           (AC 12: 오래된 cache 데이터가 존재하더라도 우선 렌더링된다.)
 *  - SYNC_BEHAVIOR 로 성공/실패 시나리오 토글 (AC 7 검증용)
 *  (AC 7: Summary fetch 실패 시 fallback 데이터가 사용된다.)
 */
@Singleton
class FakeLearningStateRepo @Inject constructor() : LearningStateRepo {

    // ──────────────────────────────────────────────────────────────────
    // 초기 cache 상태
    // ──────────────────────────────────────────────────────────────────
//    private val _state = MutableStateFlow(FakeFixtures.emptyDataStore)
//    private val _state = MutableStateFlow(FakeFixtures.onboardingDone)
    private val _state = MutableStateFlow(FakeFixtures.activeUser)
//    private val _state = MutableStateFlow(FakeFixtures.corruptedSelectedLang)
//    private val _state = MutableStateFlow(FakeFixtures.emptyLearningLangs)

    /**
     * sync() 가 어떻게 끝나는지.
     *
     *  - SUCCESS: delay 후 _state.updatedAt 갱신 → 두 번째 emit (AC 1, 6, 10, 12 검증)
     *  (AC 1: Dashboard 진입 시 DashSummary fetch가 수행된다.)
     *  (AC 6: Firebase background sync가 수행된다.)
     *  (AC 10: Dashboard 재진입 시 최신 Summary 데이터가 반영된다.)
     *  (AC 12: 오래된 cache 데이터가 존재하더라도 우선 렌더링된다.)
     *
     *  - FAILURE: delay 후 Result.failure 반환, _state 미변경 (AC 7 검증)
     *  (AC 7: Summary fetch 실패 시 fallback 데이터가 사용된다.)
     *
     *  - SUCCESS_NOOP: 성공 반환만 하고 _state 미변경 (sync 호출은 됐지만 데이터 변동 없는 케이스)
     */
    private val syncBehavior: SyncBehavior = SyncBehavior.SUCCESS
//    private val syncBehavior: SyncBehavior = SyncBehavior.FAILURE

    /**
     * changeSelectedLang() 가 어떻게 끝나는지. (DASH-006 AC 8 검증용)
     *
     *  - SUCCESS: _state.userPref.selectedLang 갱신 후 Result.success
     *  - FAILURE: Result.failure 반환, _state 미변경
     *    → ViewModel observe collect 가 새 emit 안 받음 → UI 자연 보존 (AC 8 자동 rollback)
     *    → ViewModel 은 errorMessage 만 set → Snackbar 노출
     * (AC 8: 언어 변경 실패 시 이전 selectedLearningLanguage가 유지된다.)
     */
    private val changeBehavior: ChangeBehavior = ChangeBehavior.SUCCESS
//    private val changeBehavior: ChangeBehavior = ChangeBehavior.FAILURE

    override fun observeLearningState(): Flow<GlobalLangState> = _state.asStateFlow()
    override fun observeUserPref() = _state.map { it.userPref }
    override fun observeLangState(lang: LangCode) = _state.map { it.langStates[lang] }
    override fun observeDashSummary(lang: LangCode) = _state.map { it.dashSummaries[lang] }
    override fun observeSessionSummary(lang: LangCode) = _state.map { it.sessionSummaries[lang] }
    override fun observeFlashcardSummary(lang: LangCode) =
        _state.map { it.flashcardSummaries[lang] }

    override suspend fun preload(): Result<Unit> {
        // Fake 는 init 시점에 _state 가 채워져 있어 noop.
        return Result.success(Unit)
    }

    /**
     * Firebase background sync 시뮬레이션. (AC 1, 6, 7, 10, 12)
     * (AC 1: Dashboard 진입 시 DashSummary fetch가 수행된다.)
     * (AC 6: Firebase background sync가 수행된다.)
     * (AC 7: Summary fetch 실패 시 fallback 데이터가 사용된다.)
     * (AC 10: Dashboard 재진입 시 최신 Summary 데이터가 반영된다.)
     * (AC 12: 오래된 cache 데이터가 존재하더라도 우선 렌더링된다.)
     *
     * 네트워크 지연을 흉내내기 위해 delay 를 둠. 이게 있어야 AC 12 의
     * "cache 먼저 → sync 후 갱신" 사이클이 logcat 에서 시각적으로 분리됨.
     */
    override suspend fun sync(): Result<Unit> {
        delay(SYNC_DELAY_MS)
        return when (syncBehavior) {
            SyncBehavior.SUCCESS -> {
                _state.value = _state.value.withRefreshedTimestamps()
                Result.success(Unit)
            }

            SyncBehavior.SUCCESS_NOOP -> Result.success(Unit)
            SyncBehavior.FAILURE -> Result.failure(
                IOException("simulated sync failure (FakeLearningStateRepo)")
            )
        }
    }

    override suspend fun changeSelectedLang(lang: LangCode): Result<Unit> {
        // DASH-006 AC 8 검증용: FAILURE 모드면 _state 안 건드리고 실패 반환.
        // (AC 8: 언어 변경 실패 시 이전 selectedLearningLanguage가 유지된다.)
        delay(CHANGE_DELAY_MS)// DASHBOARD SPRINT TEST 용
        if (changeBehavior == ChangeBehavior.FAILURE) {
            return Result.failure(
                IOException("simulated changeSelectedLang failure (FakeLearningStateRepo)")
            )
        }
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

    override suspend fun updateLanguageState(
        input: LangStateUpdateInput
    ): Result<LearningStateUpdateResult> {
        val preparedState = input.preparedState ?: input.currentState
        _state.value = _state.value.copy(
            langStates = _state.value.langStates + (input.lang to preparedState)
        )
        return Result.success(
            LearningStateUpdateResult(
                lang = input.lang,
                savedState = preparedState,
                sourceEventId = input.analysisEventId ?: "${input.lang.code}:${input.analyzedAt}",
                applied = true,
                updatedAt = input.analyzedAt
            )
        )
    }

    override suspend fun updateFlashcardSummary(
        input: FlashcardSummaryUpdateInput
    ): Result<FlashcardSummaryUpdateResult> {
        val current = _state.value
        val previousFlashcard = current.flashcardSummaries[input.lang] ?: FlashcardSummary.initial(input.lang)
        val previousDash = current.dashSummaries[input.lang] ?: DashSummary.initial(input.lang)
        val nextFlashcard = previousFlashcard.copy(
            dueFlashcards = input.dueFlashcards,
            savedFlashcards = input.savedFlashcards,
            updatedAt = input.updatedAt
        )
        val nextDash = previousDash.copy(
            dueFlashcards = input.dueFlashcards,
            savedFlashcards = input.savedFlashcards,
            updatedAt = input.updatedAt
        )
        _state.value = current.copy(
            flashcardSummaries = current.flashcardSummaries + (input.lang to nextFlashcard),
            dashSummaries = current.dashSummaries + (input.lang to nextDash)
        )
        return Result.success(
            FlashcardSummaryUpdateResult(
                lang = input.lang,
                flashcardSummary = nextFlashcard,
                dashSummary = nextDash,
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

    override suspend fun clear(): Result<Unit> {
        _state.value = GlobalLangState.initial()
        return Result.success(Unit)
    }

    // ──────────────────────────────────────────────────────────────────
    // sync 결과를 _state 에 반영할 때 timestamp 만 현재 시각으로 갱신.
    // 실제 Firebase 라면 값 자체도 바뀌었겠지만, fake 는 "최신화됐다" 라는
    // 신호만 의미 있게 흘리면 됨. dashSummary 의 updatedAt 이 STALE → now 로
    // 바뀌니까 collect 에서 두 번째 emit 이 찍힘.
    // ──────────────────────────────────────────────────────────────────
    private fun GlobalLangState.withRefreshedTimestamps(): GlobalLangState {
        val now = System.currentTimeMillis()
        return copy(
            userPref = userPref?.copy(updatedAt = now),
            langStates = langStates.mapValues { (_, v) -> v.copy(updatedAt = now) },
            dashSummaries = dashSummaries.mapValues { (_, v) -> v.copy(updatedAt = now) },
            sessionSummaries = sessionSummaries.mapValues { (_, v) -> v.copy(updatedAt = now) },
            flashcardSummaries = flashcardSummaries.mapValues { (_, v) -> v.copy(updatedAt = now) }
        )
    }

    private enum class SyncBehavior { SUCCESS, SUCCESS_NOOP, FAILURE }
    private enum class ChangeBehavior { SUCCESS, FAILURE }

    private companion object {
        // 네트워크 지연 흉내. 너무 짧으면 logcat 의 두 emit 이 같은 frame 에 묻혀서 안 보임.
//        const val SYNC_DELAY_MS = 800L
        const val SYNC_DELAY_MS = 4000L // DASHBOARD SPRINT TEST 용
        const val CHANGE_DELAY_MS = 3000L  // DASHBOARD SPRINT TEST 용
    }
}

/**
 * Dashboard 화면 시나리오 fixture.
 *
 * updatedAt 의 STALE_TIMESTAMP 는 "오래된 cache" 의 의도를 표현하기 위한 고정값.
 * sync 가 호출되면 이 값이 현재 시각으로 덮인다.
 */
private object FakeFixtures {

    // AC 8: 진짜 신규 사용자, Initial Setup 도 아직.
    // (AC 8: 신규 사용자는 Empty Dashboard UI가 출력된다.)
    val emptyDataStore: GlobalLangState = GlobalLangState.initial()

    // AC 5 의 Empty 분기 검증: Initial Setup 끝났지만 활동 0 회.
    // (AC 5: 현재 선택 언어 기준 Dashboard 카드 데이터가 정상 출력된다.)
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

    // DASH-001 AC 메인 검증: 며칠 학습한 사용자, 오래된 cache.
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

    // DASH-006 AC 9 검증: selectedLang ∉ learningLangs 인 데이터 오염 상태.
    //   primaryLang(EN) 으로 fallback 되어야 함 + changeSelectedLang(EN) 복구 저장 발화.
    // (AC 9: selectedLearningLanguage가 없는 경우 primaryLearningLanguage로 fallback된다.)
    //
    //   selectedLang = UNKNOWN: 지원 언어(KO/EN/JA/ES) 어디에도 속하지 않는 미지원 코드.
    //   앱 다운그레이드 / DB 마이그레이션 실패 / 외부 소스 오염 시 발생 가능한 진짜 오염 상태를 표현.
    //   learningLangs 는 현재 지원 언어 전체를 채워 "사용자는 모두 등록했지만 selectedLang 만 stale" 구조.
    val corruptedSelectedLang: GlobalLangState = activeUser.copy(
        userPref = activeUser.userPref!!.copy(
            selectedLang = LangCode.UNKNOWN,
            learningLangs = listOf(LangCode.KO, LangCode.EN, LangCode.JA, LangCode.ES),
            primaryLang = LangCode.EN
        )
    )

    // DASH-006 AC 10 검증: userPref 는 있는데 learningLangs 가 empty.
    //   hasFatalError=true 로 DashboardError 화면 노출.
    // (AC 10: learningLanguages가 비어 있거나 로드 실패 시 Error 또는 Empty 상태가 표시된다.)
    val emptyLearningLangs: GlobalLangState = activeUser.copy(
        userPref = activeUser.userPref!!.copy(
            learningLangs = emptyList()
        )
    )

    private const val STALE_TIMESTAMP: Long = 1_700_000_000_000L
}
