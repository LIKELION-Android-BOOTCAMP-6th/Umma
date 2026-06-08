package com.app.umma.data.repository.fake

import com.app.umma.data.repository.fake.demo.DemoLearningStateProvider
import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateInput
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateResult
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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fake LearningStateRepo — mockDebug 화면 시나리오 검증용.
 *
 * 데모 시나리오별 초기 데이터는 DemoLearningStateProvider가 조립한다.
 * 이 repository는 조립된 state를 observe/update/sync하는 공통 fake 동작만 담당한다.
 */
@Singleton
class FakeLearningStateRepo @Inject constructor(
    private val demoLearningStateProvider: DemoLearningStateProvider
) : LearningStateRepo {

    private val _state = MutableStateFlow(
        demoLearningStateProvider.initialState()
    )

    /**
     * 테스트에서 provider가 만든 state를 직접 주입할 때 사용한다.
     *
     * 도메인별 preset 타입을 FakeLearningStateRepo가 직접 알면 공통 fake가 다시 충돌 지점이 되므로,
     * repository에는 완성된 GlobalLangState만 넣는다.
     */
    fun seedStateForTest(state: GlobalLangState) {
        _state.value = state
    }

    // 데모 preset은 기본적으로 sync 성공 흐름을 사용한다. 실패/무변경 sync가 필요하면 별도 preset으로 확장한다.
    private val syncBehavior: SyncBehavior = SyncBehavior.SUCCESS

    // Statistics 언어 변경 시나리오는 selectedLang 변경이 성공하는 정상 흐름을 기준으로 한다.
    private val changeBehavior: ChangeBehavior = ChangeBehavior.SUCCESS

    // LS-009 중복 신호 확인용. Fake에서도 같은 turn 재호출이 no-op으로 보이도록 맞춘다.
    private val lastCorrectionSignalEventIds = ConcurrentHashMap<LangCode, String>()

    override fun observeLearningState(): Flow<GlobalLangState> = _state.asStateFlow()
    override fun observeUserPref() = _state.map { it.userPref }
    override fun observeLangState(lang: LangCode) = _state.map { it.langStates[lang] }
    override fun observeDashSummary(lang: LangCode) = _state.map { it.dashSummaries[lang] }
    override fun observeSessionSummary(lang: LangCode) = _state.map { it.sessionSummaries[lang] }
    override fun observeFlashcardSummary(lang: LangCode) =
        _state.map { it.flashcardSummaries[lang] }

    override suspend fun preload(): Result<Unit> {
        // mockDebug preset은 생성 시점에 state를 주입하므로 preload는 별도 I/O를 하지 않는다.
        return Result.success(Unit)
    }

    /**
     * fake sync는 서버 fetch 대신 updatedAt만 갱신한다.
     * 화면은 이 emit을 통해 "cache 후 최신화" 형태의 재렌더링만 확인하면 된다.
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
        // 데모에서는 언어 변경 emit이 실제 화면 흐름처럼 비동기로 들어오는지만 재현한다.
        delay(CHANGE_DELAY_MS)
        if (changeBehavior == ChangeBehavior.FAILURE) {
            return Result.failure(
                IOException("simulated changeSelectedLang failure (FakeLearningStateRepo)")
            )
        }
        // selectedLang만 바꿔 StatisticsViewModel이 새 language context를 다시 조립하게 만든다.
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

    override suspend fun changePrimaryLang(lang: LangCode): Result<Unit> {
        val current = _state.value
        val userPref = current.userPref ?: return Result.success(Unit)

        _state.value = current.copy(
            userPref = userPref.copy(
                primaryLang = lang,
                updatedAt = System.currentTimeMillis()
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

    override suspend fun updateCorrectionSignal(
        input: CorrectionSignalUpdateInput
    ): Result<CorrectionSignalUpdateResult> {
        // Fake도 real repo와 같은 계약을 흉내 내야 ViewModel 테스트가 의미를 가진다.
        // 즉, session/dash summary를 함께 바꾸고 중복 이벤트는 no-op으로 돌려준다.
        val current = _state.value
        val selectedLang = current.userPref?.selectedLang ?: return Result.failure(
            IllegalStateException("selected learning language is missing")
        )
        if (selectedLang != input.lang) {
            return Result.failure(
                IllegalArgumentException("correction signal lang must match selected learning language")
            )
        }

        // Fake도 missing summary를 자동 생성하지 않는다.
        // real repo와 다르게 동작하면 mock 화면에서만 LS 정합성 문제가 숨겨질 수 있다.
        val previousSession = current.sessionSummaries[input.lang] ?: return Result.failure(
            IllegalStateException("session summary is missing for lang=${input.lang.code}")
        )
        val previousDash = current.dashSummaries[input.lang] ?: return Result.failure(
            IllegalStateException("dash summary is missing for lang=${input.lang.code}")
        )

        // real repo와 같이 event id만으로 stale retry를 막는다.
        // summary 값까지 조건에 넣으면 교정 완료 후 false 상태에서 같은 turn이 다시 켜질 수 있다.
        if (lastCorrectionSignalEventIds[input.lang] == input.sourceEventId) {
            return Result.success(
                CorrectionSignalUpdateResult(
                    lang = input.lang,
                    sessionSummary = previousSession,
                    dashSummary = previousDash,
                    applied = false,
                    sourceEventId = input.sourceEventId,
                    updatedAt = previousSession.updatedAt ?: input.updatedAt
                )
            )
        }

        val nextMinutes = input.recentMinutes ?: previousSession.recentMinutes
        val nextTopic = input.recentTopic ?: previousSession.recentTopic ?: previousDash.recentTopic
        // Fake도 정책을 다시 계산하지 않고 input 값을 저장해야 real repo와 같은 책임 경계를 검증할 수 있다.
        val nextSession = previousSession.copy(
            correctionAvailable = input.correctionAvailable,
            recentMinutes = nextMinutes,
            recentTopic = nextTopic,
            updatedAt = input.updatedAt
        )
        val nextDash = previousDash.copy(
            correctionAvailable = input.correctionAvailable,
            recentMinutes = nextMinutes,
            recentTopic = nextTopic,
            updatedAt = input.updatedAt
        )

        _state.value = current.copy(
            sessionSummaries = current.sessionSummaries + (input.lang to nextSession),
            dashSummaries = current.dashSummaries + (input.lang to nextDash)
        )
        lastCorrectionSignalEventIds[input.lang] = input.sourceEventId
        return Result.success(
            CorrectionSignalUpdateResult(
                lang = input.lang,
                sessionSummary = nextSession,
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
        lastCorrectionSignalEventIds.clear()
        return Result.success(Unit)
    }

    private fun GlobalLangState.withRefreshedTimestamps(): GlobalLangState {
        // 값 자체는 preset이 정의한 시나리오를 유지하고, 최신화 emit만 만들기 위해 timestamp만 바꾼다.
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
        private const val SYNC_DELAY_MS = 800L
        private const val CHANGE_DELAY_MS = 800L
    }
}
