package com.app.umma.presentation.correction

import com.app.umma.data.repository.correction.CorrectionSuggestionFixtures
import com.app.umma.domain.model.correction.CompleteCorrectionResult
import com.app.umma.domain.model.correction.CorrectionFlashcardSaveItem
import com.app.umma.domain.model.correction.CorrectionSaveRequest
import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.FlashcardSummary
import com.app.umma.domain.model.learningstate.GlobalLangState
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.UserLangPref
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * COR-001-A/B 분기 로직 + COR-002-B generate 결과 반영 회귀 테스트.
 *
 * [GlobalLangState.toCorrectionUiState] 는 [CorrectionViewModel] 의 collect 본체에서 분리한
 * 순수 함수다. ViewModel 자체는 viewModelScope/Main dispatcher 셋업이 필요해 무겁지만,
 * 이 함수는 [GlobalLangState] 만 받으면 동일한 결과를 돌려주므로 모든 AC 시나리오를
 * coroutines-test 의존성 없이 즉시 회귀할 수 있다.
 *
 * COR-001-B 에서는 [Phase.NotAvailable] 이 [Phase.Empty] 로 rename 되었고, generate 트리거 가드를
 * [shouldTriggerGeneration] pure helper 로 분리해 phase × launched 조합을 표 형태로 회귀한다.
 *
 * COR-002-B 에서는 [applyGenerationOutcome] pure helper 로 result × suggestions.isEmpty 조합을
 * 회귀한다(EmptyResult/Content/Error 3분기 + 공통 정리 invariant).
 */
class CorrectionUiStateTest {

    @Test
    fun `returns Empty when userPref is missing`() {
        // emptyDataStore 와 동일 — 신규 사용자, 아직 onboarding 도 안 함.
        // COR-001-B: 구 `NotAvailable` 의 rename — 결손 분기는 단일 Empty 로 합쳐 보여준다.
        val global = GlobalLangState.initial()

        val state = global.toCorrectionUiState()

        assertEquals(CorrectionUiState.Phase.Empty, state.phase)
        assertNull(state.selectedLearningLanguage)
        assertEquals(
            "selectedLang == null (userPref absent)",
            global.notAvailableReason()
        )
    }

    @Test
    fun `returns Empty when SessionSummary is missing for the selected language`() {
        // userPref / langState 는 있지만 sessionSummaries 가 비어있는 케이스.
        val lang = LangCode.EN
        val global = GlobalLangState(
            userPref = UserLangPref.initial(primaryLang = LangCode.KO, selectedLang = lang),
            langStates = mapOf(lang to LangState.initial(lang)),
            dashSummaries = emptyMap(),
            sessionSummaries = emptyMap(),
            flashcardSummaries = emptyMap(),
        )

        val state = global.toCorrectionUiState()

        assertEquals(CorrectionUiState.Phase.Empty, state.phase)
        assertEquals(lang, state.selectedLearningLanguage)
        assertNull(state.sessionSummary)
        assertEquals(
            "sessionSummary == null for lang=$lang",
            global.notAvailableReason()
        )
    }

    @Test
    fun `returns Empty when LangState snapshot is missing`() {
        // sessionSummary 는 살아있고 correctionAvailable=true 인데 langStates 만 비어 있는 케이스.
        val lang = LangCode.EN
        val global = GlobalLangState(
            userPref = UserLangPref.initial(primaryLang = LangCode.KO, selectedLang = lang),
            langStates = emptyMap(),
            dashSummaries = emptyMap(),
            sessionSummaries = mapOf(
                lang to SessionSummary(
                    lang = lang,
                    correctionAvailable = true,
                    recentMinutes = 30,
                    recentTopic = "Travel",
                )
            ),
            flashcardSummaries = emptyMap(),
        )

        val state = global.toCorrectionUiState()

        assertEquals(CorrectionUiState.Phase.Empty, state.phase)
        assertNotNull(state.sessionSummary)
        assertNull(state.langStateSnapshot)
        assertEquals(
            "langState snapshot == null for lang=$lang",
            global.notAvailableReason()
        )
    }

    @Test
    fun `returns Empty when correctionAvailable is false`() {
        // 모든 필드 채워졌으나 correctionAvailable 만 false — Ready 게이트가 닫혀 있음.
        val lang = LangCode.EN
        val global = buildGlobal(
            lang = lang,
            sessionSummary = SessionSummary(
                lang = lang,
                correctionAvailable = false,
                recentMinutes = 30,
                recentTopic = "Travel",
            ),
        )

        val state = global.toCorrectionUiState()

        assertEquals(CorrectionUiState.Phase.Empty, state.phase)
        // 필드는 채워서 노출 — 디버깅 / 후속 단계 참고용.
        assertEquals(lang, state.selectedLearningLanguage)
        assertNotNull(state.sessionSummary)
        assertNotNull(state.langStateSnapshot)
        assertEquals(
            "sessionSummary.correctionAvailable == false for lang=$lang",
            global.notAvailableReason()
        )
    }

    @Test
    fun `returns Ready when all fields are loaded and correctionAvailable is true`() {
        // FakeFixtures activeUser 와 같은 happy path — Ready 진입.
        val lang = LangCode.EN
        val sessionSummary = SessionSummary(
            lang = lang,
            correctionAvailable = true,
            recentMinutes = 30,
            recentTopic = "Travel",
        )
        val global = buildGlobal(lang = lang, sessionSummary = sessionSummary)

        val state = global.toCorrectionUiState()

        assertEquals(CorrectionUiState.Phase.Ready, state.phase)
        assertTrue(state.isReady)
        assertEquals(lang, state.selectedLearningLanguage)
        assertEquals(sessionSummary, state.sessionSummary)
        assertNotNull(state.langStateSnapshot)
        assertNull(global.notAvailableReason())
    }

    @Test
    fun `returns Empty when SessionSummary correctionAvailable is false even if DashSummary is true`() {
        // AC 6 (마지막 줄): "DashSummary 가 아니라 SessionSummary 기준" 검증.
        // 두 필드가 모순될 때 Ready 판정 근거가 어느 쪽인지 못박는 회귀 테스트.
        val lang = LangCode.EN
        val global = buildGlobal(
            lang = lang,
            sessionSummary = SessionSummary(
                lang = lang,
                correctionAvailable = false,
                recentMinutes = 30,
                recentTopic = "Travel",
            ),
            dashSummary = DashSummary.initial(lang).copy(correctionAvailable = true),
        )

        val state = global.toCorrectionUiState()

        assertEquals(CorrectionUiState.Phase.Empty, state.phase)
    }

    // ─── COR-004: canSave 회귀 ───────────────────────────────────────────────

    @Test
    fun `canSave is false when phase is Content but no suggestion is selected`() {
        // 사용자가 카드 화면에 들어왔지만 아직 아무것도 안 골랐을 때 저장 버튼은 비활성이어야 한다.
        val state = CorrectionUiState(
            phase = CorrectionUiState.Phase.Content,
            selectedSuggestionIds = emptySet(),
        )

        assertFalse(state.canSave)
    }

    @Test
    fun `canSave is true when phase is Content and at least one suggestion is selected`() {
        // 1개 이상 선택된 정상 케이스. 저장 버튼 활성.
        val state = CorrectionUiState(
            phase = CorrectionUiState.Phase.Content,
            selectedSuggestionIds = setOf("sugg-1"),
        )

        assertTrue(state.canSave)
    }

    @Test
    fun `canSave is false when selection exists but phase is not Content`() {
        // 생성 중 단계 등에서는 카드 자체가 안 보여야 하므로 stale 한 선택이 남더라도 저장은 막혀야 한다.
        val state = CorrectionUiState(
            phase = CorrectionUiState.Phase.Generating,
            selectedSuggestionIds = setOf("sugg-1"),
        )

        assertFalse(state.canSave)
    }

    // ─── COR-006-A: 완료 파이프라인 회귀 ────────────────────────────────────────

    @Test
    fun `canSave is false while isCompleting is true`() {
        // COR-006-A: 변환이 끝나고 완료 윈도우가 열린 직후에는 같은 버튼이 두 번째 클릭으로 다시 활성화되면 안 된다.
        val state = CorrectionUiState(
            phase = CorrectionUiState.Phase.Content,
            selectedSuggestionIds = setOf("sugg-1"),
            isCompleting = true,
        )

        assertFalse(state.canSave)
    }

    @Test
    fun `canSave is false on Phase Done`() {
        // 완료 후 같은 화면에서 재저장이 일어나지 않아야 한다. UI 가드는 Phase.Done 분기에서 카드 자체를
        // 숨기는 방식이지만, 그 이전에 canSave 자체가 false 가 되도록 회귀로 못 박는다.
        val state = CorrectionUiState(
            phase = CorrectionUiState.Phase.Done,
            selectedSuggestionIds = setOf("sugg-1"),
        )

        assertFalse(state.canSave)
    }

    @Test
    fun `correction review report state is disabled by default`() {
        val state = CorrectionUiState()

        assertFalse(state.showCorrectionReviewReportButton)
        assertFalse(state.isCorrectionReviewReporting)
        assertFalse(state.hasCorrectionReviewReported)
    }

    @Test
    fun `correction review report state can mark in flight and completed`() {
        val state = CorrectionUiState(showCorrectionReviewReportButton = true)

        val reporting = state.copy(isCorrectionReviewReporting = true)
        val completed = reporting.copy(
            isCorrectionReviewReporting = false,
            hasCorrectionReviewReported = true,
        )

        assertTrue(reporting.showCorrectionReviewReportButton)
        assertTrue(reporting.isCorrectionReviewReporting)
        assertFalse(reporting.hasCorrectionReviewReported)
        assertFalse(completed.isCorrectionReviewReporting)
        assertTrue(completed.hasCorrectionReviewReported)
    }

    @Test
    fun `computeCompletionLaunch returns AlreadyInFlight when isCompleting`() {
        // 첫 호출이 isCompleting=true 를 emit 한 직후 들어온 두 번째 트리거를 막는 가드.
        val state = CorrectionUiState(
            phase = CorrectionUiState.Phase.Content,
            isCompleting = true,
        )

        val outcome = state.computeCompletionLaunch(sampleSaveRequest())

        assertEquals(CompletionLaunchOutcome.AlreadyInFlight, outcome)
    }

    @Test
    fun `computeCompletionLaunch returns NoSaveRequest when request is null`() {
        // 변환이 실패했거나 stale 한 채로 launchCompletion 이 호출된 경우 — request 가 비어 있으면 막힌다.
        val state = CorrectionUiState(phase = CorrectionUiState.Phase.Content)

        val outcome = state.computeCompletionLaunch(null)

        assertEquals(CompletionLaunchOutcome.NoSaveRequest, outcome)
    }

    @Test
    fun `computeCompletionLaunch returns Launched on happy path`() {
        // 정상 진입 — ViewModel 은 이 분기에서 viewModelScope.launch 로 실제 호출에 진입한다.
        val state = CorrectionUiState(phase = CorrectionUiState.Phase.Content)
        val request = sampleSaveRequest()

        val outcome = state.computeCompletionLaunch(request)

        assertTrue(outcome is CompletionLaunchOutcome.Launched)
        assertEquals(request, (outcome as CompletionLaunchOutcome.Launched).saveRequest)
    }

    @Test
    fun `openCompletionWindow flips isCompleting to true`() {
        val state = CorrectionUiState(phase = CorrectionUiState.Phase.Content)

        val next = state.openCompletionWindow()

        assertTrue(next.isCompleting)
    }

    @Test
    fun `openCompletionWindow returns same instance when already completing`() {
        // 같은 인스턴스를 그대로 돌려줘 MutableStateFlow.update 가 emit 을 생략하도록 한다.
        val state = CorrectionUiState(phase = CorrectionUiState.Phase.Content, isCompleting = true)

        val next = state.openCompletionWindow()

        assertSame(state, next)
    }

    @Test
    fun `applyCompletionOutcome success transitions to Done and stores result`() {
        // 성공 분기 — Phase.Done 으로 전환되고 completionResult 가 채워지며 in-flight 윈도우가 닫힌다.
        val state = CorrectionUiState(
            phase = CorrectionUiState.Phase.Content,
            isCompleting = true,
        )
        val expected = sampleCompletionResult(savedIds = listOf("s-1", "s-2"))

        val next = state.applyCompletionOutcome(Result.success(expected))

        assertEquals(CorrectionUiState.Phase.Done, next.phase)
        assertEquals(expected, next.completionResult)
        assertFalse(next.isCompleting)
    }

    // ─── COR-007-B: pending 비차단 회귀 ──────────────────────────────────────
    // CompleteCorrectionUseCase 가 Firestore sync / Session compression / Statistics history
    // pending 케이스에서도 Result.success 로 흘려보낸다는 도메인 계약은
    // CompleteCorrectionUseCaseTest 의 compression 실패 / statistics 기록 실패 테스트가 이미 보장한다.
    // 본 3건은 그 success 결과가 presentation 경계에서 사용자 실패로 격하되지 않고
    // 동일하게 Phase.Done 으로 전환되는지를 못 박는다. pending 별도 UI 표면을 두지 않는 정책이라
    // applyCompletionOutcome 한 함수만 검증해도 화면 흐름(저장 완료 → Dashboard 복귀) 의 비차단이 보장된다.

    @Test
    fun `applyCompletionOutcome with pending sync still transitions to Done`() {
        // COR-007-B AC: "sync pending 상태가 있어도 Dashboard 복귀를 막지 않는다".
        // pendingSyncFlashcardIds 가 비어있지 않은 success 도 동일 분기로 Done 에 들어가야 한다.
        val result = sampleCompletionResult(savedIds = listOf("s-1"))
            .copy(pendingSyncFlashcardIds = listOf("s-1"))
        val state = CorrectionUiState(
            phase = CorrectionUiState.Phase.Content,
            isCompleting = true,
        )

        val next = state.applyCompletionOutcome(Result.success(result))

        assertEquals(CorrectionUiState.Phase.Done, next.phase)
        assertEquals(result, next.completionResult)
        assertFalse(next.isCompleting)
    }

    @Test
    fun `applyCompletionOutcome with compression pending still transitions to Done`() {
        // COR-007-B AC: "Session Memory compression pending 상태가 있어도 저장 완료와 Dashboard 복귀를 막지 않는다".
        // sessionCompressionPending=true + errorMessage 가 채워져 있어도 사용자 흐름은 Done.
        val result = sampleCompletionResult(savedIds = listOf("s-1"))
            .copy(
                sessionCompressionPending = true,
                sessionCompressionErrorMessage = "compression failed",
            )
        val state = CorrectionUiState(
            phase = CorrectionUiState.Phase.Content,
            isCompleting = true,
        )

        val next = state.applyCompletionOutcome(Result.success(result))

        assertEquals(CorrectionUiState.Phase.Done, next.phase)
        // pending 정보는 completionResult 안에 보관되지만 화면 표면(별도 errorReason 등) 으로는 새지 않는다.
        assertTrue(next.completionResult!!.sessionCompressionPending)
        assertFalse(next.isCompleting)
    }

    @Test
    fun `applyCompletionOutcome with statistics history pending still transitions to Done`() {
        // COR-007-B: statisticsHistoryPending=true 도 동일 정책. pending 의 종류가 늘어나도
        // applyCompletionOutcome 단일 분기로 Done 에 진입한다는 invariant 를 명시한다.
        val result = sampleCompletionResult(savedIds = listOf("s-1"))
            .copy(statisticsHistoryPending = true)
        val state = CorrectionUiState(
            phase = CorrectionUiState.Phase.Content,
            isCompleting = true,
        )

        val next = state.applyCompletionOutcome(Result.success(result))

        assertEquals(CorrectionUiState.Phase.Done, next.phase)
        assertTrue(next.completionResult!!.statisticsHistoryPending)
        assertFalse(next.isCompleting)
    }

    // ─── COR-006-B: 로컬 완료 실패 → Retry phase 회귀 ────────────────────────
    // CompleteCorrectionUseCase 가 Result.failure 로 돌려준 경우(Flashcard 저장 실패, LangState 갱신
    // 실패) 가 사용자에게 어떻게 노출되어야 하는지를 두 invariant 로 나눠 못 박는다.
    //  1. 상태 표면: Phase.Retry + completionErrorReason 채움 + isCompleting=false + completionResult=null.
    //  2. 보존 invariant: 카드 목록 / 선택 / saveRequest 가 그대로 유지되어 같은 입력으로 재시도 가능.
    // 둘을 같은 fixture 에서 분리해 테스트해야 한 invariant 가 깨졌을 때 다른 invariant 도 함께 죽지 않는다.

    @Test
    fun `applyCompletionOutcome failure transitions to Retry with errorReason`() {
        // AC: "로컬 완료 실패 결과를 받으면 Retry 상태로 남긴다."
        val state = CorrectionUiState(
            phase = CorrectionUiState.Phase.Content,
            selectedSuggestionIds = setOf("s-1"),
            saveRequest = sampleSaveRequest(),
            isCompleting = true,
        )

        val next = state.applyCompletionOutcome(Result.failure(RuntimeException("local save failed")))

        assertEquals(CorrectionUiState.Phase.Retry, next.phase)
        assertEquals("local save failed", next.completionErrorReason)
        assertFalse(next.isCompleting)
        // 직전 시도에서 보관됐을 수 있는 Done 결과는 stale 이므로 비워야 한다.
        assertNull(next.completionResult)
    }

    @Test
    fun `applyCompletionOutcome failure preserves selectedIds suggestions saveRequest`() {
        // AC: "실패 시 선택 상태와 카드 목록을 유지한다."
        // 같은 saveRequest 로 재시도가 가능해야 하므로, 카드 / 선택 / saveRequest 셋 모두 그대로 보존되는지
        // invariant 회귀로 못 박는다. 이 테스트는 Phase / reason 검증과 분리되어 있어, 어느 invariant 가
        // 깨졌는지 즉시 식별 가능하다.
        val previousRequest = sampleSaveRequest()
        val previousSuggestions = CorrectionSuggestionFixtures.contentSuggestions()
        val state = CorrectionUiState(
            phase = CorrectionUiState.Phase.Content,
            suggestions = previousSuggestions,
            selectedSuggestionIds = setOf("s-1"),
            saveRequest = previousRequest,
            isCompleting = true,
        )

        val next = state.applyCompletionOutcome(Result.failure(RuntimeException("local save failed")))

        assertEquals(previousSuggestions, next.suggestions)
        assertEquals(setOf("s-1"), next.selectedSuggestionIds)
        assertEquals(previousRequest, next.saveRequest)
    }

    @Test
    fun `applyCompletionOutcome failure with null message falls back to class simpleName`() {
        // applyGenerationOutcome 의 reason 채움 패턴과 동치 — message 가 null 이면 class simpleName 사용.
        val throwable = object : Throwable() {
            override val message: String? = null
        }
        val state = CorrectionUiState(
            phase = CorrectionUiState.Phase.Content,
            isCompleting = true,
        )

        val next = state.applyCompletionOutcome(Result.failure(throwable))

        assertEquals(CorrectionUiState.Phase.Retry, next.phase)
        // anonymous object 의 simpleName 은 빈 문자열일 수 있으므로 null 이 아닌 것만 확인.
        assertNotNull(next.completionErrorReason)
    }

    @Test
    fun `applyCompletionOutcome success on Retry transitions to Done and clears errorReason`() {
        // Retry → 재시도 성공 시 Done 으로 가고 직전에 채워졌던 completionErrorReason 이 비워지는 invariant.
        // 사용자 흐름: 첫 시도 실패 → Phase.Retry + 사유 표시 → 두 번째 시도 성공 → Phase.Done + 사유 비움.
        val state = CorrectionUiState(
            phase = CorrectionUiState.Phase.Retry,
            selectedSuggestionIds = setOf("s-1"),
            saveRequest = sampleSaveRequest(),
            completionErrorReason = "local save failed",
            isCompleting = true,
        )
        val expected = sampleCompletionResult(savedIds = listOf("s-1"))

        val next = state.applyCompletionOutcome(Result.success(expected))

        assertEquals(CorrectionUiState.Phase.Done, next.phase)
        assertEquals(expected, next.completionResult)
        // 직전 Retry 진입에서 채워진 사유는 명시적으로 비워져야 한다.
        assertNull(next.completionErrorReason)
        assertFalse(next.isCompleting)
    }

    // ─── COR-006-B: canSave 재시도 허용 회귀 ─────────────────────────────────

    @Test
    fun `canSave is true on Phase Retry with selection`() {
        // AC "Retry 시 같은 저장 요청으로 완료 파이프라인을 다시 호출" 의 화면 가드.
        // canSave 가 false 라면 사용자는 저장 버튼을 다시 누를 수 없어 재시도가 막힌다.
        val state = CorrectionUiState(
            phase = CorrectionUiState.Phase.Retry,
            selectedSuggestionIds = setOf("s-1"),
        )

        assertTrue(state.canSave)
    }

    @Test
    fun `canSave is false on Phase Retry without selection`() {
        // Retry phase 에서 사용자가 모든 카드를 다시 해제한 경우 — 저장은 막혀야 한다.
        // 0개 가드는 Content 와 동일한 invariant.
        val state = CorrectionUiState(
            phase = CorrectionUiState.Phase.Retry,
            selectedSuggestionIds = emptySet(),
        )

        assertFalse(state.canSave)
    }

    @Test
    fun `canSave is false while isCompleting on Phase Retry`() {
        // Retry → 재시도 클릭 → completion in-flight 중에는 다시 비활성화되어야 한다.
        // AC "Retry 중 중복 완료 요청이 발생하는 경우" 의 1차 차단.
        val state = CorrectionUiState(
            phase = CorrectionUiState.Phase.Retry,
            selectedSuggestionIds = setOf("s-1"),
            isCompleting = true,
        )

        assertFalse(state.canSave)
    }

    // ─── COR-001-B: 중복 방어 가드 회귀 ───────────────────────────────────────
    // [shouldTriggerGeneration] 는 [CorrectionViewModel.ensureObservation] 의 generate 트리거
    // 분기 결정을 pure function 으로 추출한 것이다. ViewModel 본문과 항상 동치여야 한다는 invariant
    // 를 phase × launched 조합 표로 못 박는다. 본 4건이 깨지면 ViewModel 본문도 함께 깨진 것.

    @Test
    fun `shouldTriggerGeneration returns true on Ready when not yet launched`() {
        // happy path — Ready 첫 emit 직후 한 번만 트리거된다.
        assertTrue(shouldTriggerGeneration(CorrectionUiState.Phase.Ready, alreadyLaunched = false))
    }

    @Test
    fun `shouldTriggerGeneration returns false when already launched`() {
        // COR-001-B AC "중복 요청 방지" 의 핵심 — GlobalLangState refresh 로 Ready 가 다시 흘러와도
        // launched 가 true 면 두 번째 generate 가 시작되면 안 된다.
        assertFalse(shouldTriggerGeneration(CorrectionUiState.Phase.Ready, alreadyLaunched = true))
    }

    @Test
    fun `shouldTriggerGeneration returns false on Empty regardless of launched flag`() {
        // 결손 분기에서는 launched 와 무관하게 generate 가 일어나면 안 된다. 사용자가 Empty 화면에서
        // CTA 클릭 없이 가만히 있어도, refresh 가 들어와도 어느 쪽이든 false.
        assertFalse(shouldTriggerGeneration(CorrectionUiState.Phase.Empty, alreadyLaunched = false))
        assertFalse(shouldTriggerGeneration(CorrectionUiState.Phase.Empty, alreadyLaunched = true))
    }

    @Test
    fun `shouldTriggerGeneration returns false on Loading Generating Content EmptyResult Error Done Retry`() {
        // Ready 이외의 모든 phase 는 generate 진입 자격이 없다는 invariant. enum 분기 완전성 회귀.
        // COR-002-B 에서 추가된 EmptyResult 도 포함 — launched 와 무관하게 false.
        // COR-006-B 에서 추가된 Retry 도 포함 — Retry 는 "사용자 명시 재시도 대기" 상태라
        // GlobalLangState refresh 로 자동 generate 가 다시 일어나면 안 된다(terminalPhases 미포함과 동치).
        listOf(
            CorrectionUiState.Phase.Loading,
            CorrectionUiState.Phase.Generating,
            CorrectionUiState.Phase.Content,
            CorrectionUiState.Phase.EmptyResult,
            CorrectionUiState.Phase.Error,
            CorrectionUiState.Phase.Done,
            CorrectionUiState.Phase.Retry,
        ).forEach { phase ->
            assertFalse(
                "$phase 에서는 generate 트리거가 일어나면 안 됨 (launched=false)",
                shouldTriggerGeneration(phase, alreadyLaunched = false),
            )
            assertFalse(
                "$phase 에서는 generate 트리거가 일어나면 안 됨 (launched=true)",
                shouldTriggerGeneration(phase, alreadyLaunched = true),
            )
        }
    }

    // ─── COR-002-B: applyGenerationOutcome 회귀 ───────────────────────────────
    // [applyGenerationOutcome] 은 [CorrectionViewModel.triggerGeneration] 의 result.fold 본문을
    // pure function 으로 추출한 것이다. result × suggestions.isEmpty 조합 표를 못 박아
    // ViewModel 분기 변경 시 이 5건이 깨지도록 한다.
    // fixture: [CorrectionSuggestionFixtures.contentSuggestions] / emptyList() / generateFailure()

    @Test
    fun `applyGenerationOutcome on success with non-empty list transitions to Content`() {
        // AC: "실제 AI 성공 응답에서 1개 이상을 생성하고 Content 상태로 전환한다."
        val suggestions = CorrectionSuggestionFixtures.contentSuggestions()
        val state = CorrectionUiState(phase = CorrectionUiState.Phase.Generating)

        val next = state.applyGenerationOutcome(Result.success(suggestions))

        assertEquals(CorrectionUiState.Phase.Content, next.phase)
        assertEquals(suggestions, next.suggestions)
        assertTrue(next.selectedSuggestionIds.isEmpty())
        assertNull(next.errorReason)
        assertNull(next.saveRequest)
        assertNull(next.saveErrorReason)
    }

    @Test
    fun `applyGenerationOutcome on success with empty list transitions to EmptyResult`() {
        // AC: "결과가 비어 있으면 Empty 상태를 반환한다." (설계 문서 = Phase.EmptyResult)
        val state = CorrectionUiState(phase = CorrectionUiState.Phase.Generating)

        val next = state.applyGenerationOutcome(Result.success(emptyList()))

        assertEquals(CorrectionUiState.Phase.EmptyResult, next.phase)
        assertTrue(next.suggestions.isEmpty())
        assertNull(next.errorReason)
    }

    @Test
    fun `applyGenerationOutcome on failure with message transitions to Error`() {
        // AC: "AI 요청 실패, 응답 파싱 실패, 필수 필드 누락 시 Error 상태로 전환."
        val throwable = CorrectionSuggestionFixtures.generateFailure("candidateId mismatch")
        val state = CorrectionUiState(phase = CorrectionUiState.Phase.Generating)

        val next = state.applyGenerationOutcome(Result.failure(throwable))

        assertEquals(CorrectionUiState.Phase.Error, next.phase)
        assertEquals("candidateId mismatch", next.errorReason)
        assertTrue(next.suggestions.isEmpty())
        assertNull(next.saveRequest)
        assertNull(next.saveErrorReason)
    }

    @Test
    fun `applyGenerationOutcome on failure with null message falls back to class simpleName`() {
        // message == null 인 throwable 은 class simpleName 을 errorReason 으로 사용.
        val throwable = CorrectionSuggestionFixtures.generateFailure()
        // generateFailure 는 기본 메시지를 넣어주므로, message=null 케이스는 직접 만든다.
        val noMessageThrowable = object : Throwable() {
            override val message: String? = null
        }
        val state = CorrectionUiState(phase = CorrectionUiState.Phase.Generating)

        val next = state.applyGenerationOutcome(Result.failure(noMessageThrowable))

        assertEquals(CorrectionUiState.Phase.Error, next.phase)
        // simpleName 은 anonymous object 라 빈 문자열이 될 수 있으므로, null 이 아닌 것만 확인.
        assertNotNull(next.errorReason)
    }

    @Test
    fun `applyGenerationOutcome clears stale selectedIds saveRequest saveErrorReason on all outcomes`() {
        // 직전 Content 상태에서 selectedIds / saveRequest / saveErrorReason 이 채워져 있던 경우에도
        // outcome 적용 후 모두 비워지는 invariant — 정리 책임을 helper 로 통합했음을 검증.
        val staleState = CorrectionUiState(
            phase = CorrectionUiState.Phase.Generating,
            suggestions = CorrectionSuggestionFixtures.contentSuggestions(),
            selectedSuggestionIds = setOf("s-1", "s-2"),
            saveRequest = CorrectionSaveRequest(
                uid = "uid-1",
                lang = LangCode.EN,
                flashcards = emptyList(),
            ),
            saveErrorReason = "이전 저장 실패",
        )

        // 성공 케이스
        val afterContent = staleState.applyGenerationOutcome(
            Result.success(CorrectionSuggestionFixtures.contentSuggestions()),
        )
        assertTrue(afterContent.selectedSuggestionIds.isEmpty())
        assertNull(afterContent.saveRequest)
        assertNull(afterContent.saveErrorReason)

        // 빈 목록 케이스
        val afterEmptyResult = staleState.applyGenerationOutcome(Result.success(emptyList()))
        assertTrue(afterEmptyResult.selectedSuggestionIds.isEmpty())
        assertNull(afterEmptyResult.saveRequest)
        assertNull(afterEmptyResult.saveErrorReason)

        // 실패 케이스
        val afterError = staleState.applyGenerationOutcome(
            Result.failure(CorrectionSuggestionFixtures.generateFailure()),
        )
        assertTrue(afterError.selectedSuggestionIds.isEmpty())
        assertNull(afterError.saveRequest)
        assertNull(afterError.saveErrorReason)
    }

    // ─── COR-003-B: 카드 화면 상태 렌더링 invariant 회귀 ────────────────────────
    // 이슈 #134 엣지 케이스 "Empty / Error 상태인데 이전 카드가 남아 보이는 경우" 를 못 박는다.
    // [applyGenerationOutcome] 이 모든 비-Content outcome 에서 suggestions 를 비운다는
    // 계약을 COR-003-B SSOT 회귀로 명시한다. [applyGenerationOutcome clears stale ...] 테스트와
    // 의미가 겹치지만, 이 두 건은 *카드 화면 상태 구분* 관점의 invariant 를 독립적으로 못 박는다.
    // (하나가 깨져도 나머지가 살아 어느 invariant 가 깨졌는지 즉시 식별 가능하게 한다.)

    @Test
    fun `applyGenerationOutcome on EmptyResult clears suggestions to prevent leftover cards`() {
        // COR-003-B 엣지: "Empty 상태인데 이전 카드가 남아 보이는 경우".
        // Generating 직전까지 suggestions 가 채워진 Content 상태(=Retry 흐름)에서도
        // EmptyResult outcome 적용 후 suggestions 가 완전히 비워져야 한다.
        val staleContent = CorrectionUiState(
            phase = CorrectionUiState.Phase.Generating,
            suggestions = CorrectionSuggestionFixtures.contentSuggestions(),
        )

        val next = staleContent.applyGenerationOutcome(Result.success(emptyList()))

        // EmptyResult 로 전환됐는지 확인.
        assertEquals(CorrectionUiState.Phase.EmptyResult, next.phase)
        // 이전 카드가 남지 않아야 한다 — 카드 화면이 비어 있어야 하는 상태의 핵심 invariant.
        assertTrue(
            "EmptyResult 상태에서 이전 suggestions 가 잔존하면 안 됨",
            next.suggestions.isEmpty(),
        )
    }

    @Test
    fun `applyGenerationOutcome on Error clears suggestions to prevent leftover cards`() {
        // COR-003-B 엣지: "Error 상태인데 Content UI(카드 목록)가 함께 노출되는 경우".
        // AI 호출/파싱 실패로 Error 로 전환될 때, 직전 Content phase 의 suggestions 가
        // 남아 있으면 화면 분기는 CorrectionError 를 그리지만 stale 카드 데이터가 UiState 에
        // 잔존해 이후 phase 전환 시 렌더링 이상이 생길 수 있다.
        val staleContent = CorrectionUiState(
            phase = CorrectionUiState.Phase.Generating,
            suggestions = CorrectionSuggestionFixtures.contentSuggestions(),
        )

        val next = staleContent.applyGenerationOutcome(
            Result.failure(CorrectionSuggestionFixtures.generateFailure("candidateId mismatch")),
        )

        // Error 로 전환됐는지 확인.
        assertEquals(CorrectionUiState.Phase.Error, next.phase)
        assertEquals("candidateId mismatch", next.errorReason)
        // 이전 카드가 남지 않아야 한다 — Error UI 와 Content 카드 UI 가 동시에 노출되는 경우를 차단.
        assertTrue(
            "Error 상태에서 이전 suggestions 가 잔존하면 안 됨",
            next.suggestions.isEmpty(),
        )
    }

    /**
     * 테스트용 GlobalLangState 빌더.
     *
     * dashSummary 는 기본 initial(false) 로 둔다 — Ready 판정 로직이 DashSummary 를
     * 참조하지 않는다는 invariant 를 모든 시나리오에서 자연스럽게 강제한다.
     */
    private fun buildGlobal(
        lang: LangCode,
        sessionSummary: SessionSummary,
        dashSummary: DashSummary = DashSummary.initial(lang),
    ): GlobalLangState = GlobalLangState(
        userPref = UserLangPref.initial(primaryLang = LangCode.KO, selectedLang = lang),
        langStates = mapOf(lang to LangState.initial(lang)),
        dashSummaries = mapOf(lang to dashSummary),
        sessionSummaries = mapOf(lang to sessionSummary),
        flashcardSummaries = mapOf(lang to FlashcardSummary.initial(lang)),
    )

    // 완료 파이프라인 회귀에서만 사용하는 fixture. CorrectionSaveRequestOutcomeTest 의 동명 헬퍼와는
    // 클래스 경계가 다르므로 중복 정의를 허용한다(테스트 파일 간 공유 fixture 의 비용이 가독성 손해보다 큼).
    private fun sampleSaveRequest(): CorrectionSaveRequest = CorrectionSaveRequest(
        uid = "uid-1",
        lang = LangCode.EN,
        flashcards = listOf(
            CorrectionFlashcardSaveItem(
                suggestionId = "s-1",
                frontText = "나는 학교에 간다",
                backText = "I go to school.",
                explanation = "demo",
            ),
        ),
        requestedAt = 1_700_000_000_000L,
    )

    private fun sampleCompletionResult(savedIds: List<String>): CompleteCorrectionResult =
        CompleteCorrectionResult(
            savedFlashcardIds = savedIds,
            pendingSyncFlashcardIds = emptyList(),
            sessionMemoryKey = "",
            completedAt = 1_700_000_000_000L,
        )

    // ─── COR-FIX-06: areAllSuggestionsSelected 회귀 ──────────────────────────

    @Test
    fun `areAllSuggestionsSelected is false when no cards are selected`() {
        val suggestions = CorrectionSuggestionFixtures.contentSuggestions(LangCode.EN)
        val state = CorrectionUiState(
            phase = CorrectionUiState.Phase.Content,
            suggestions = suggestions,
            selectedSuggestionIds = emptySet(),
        )

        assertFalse(state.areAllSuggestionsSelected)
    }

    @Test
    fun `areAllSuggestionsSelected is false when only some cards are selected`() {
        // 픽스처 기본값이 후보 1개라 직접 2개짜리 리스트를 만든다.
        val suggestions = listOf(
            CorrectionSuggestion(
                id = "s-1", lang = LangCode.EN, sourceCandidateIds = listOf("c-1"),
                sourceTurnIndex = 0, beforeText = "a", nativeText = "a",
                afterText = "b", explanation = "e",
            ),
            CorrectionSuggestion(
                id = "s-2", lang = LangCode.EN, sourceCandidateIds = listOf("c-2"),
                sourceTurnIndex = 1, beforeText = "c", nativeText = "c",
                afterText = "d", explanation = "e",
            ),
        )
        val state = CorrectionUiState(
            phase = CorrectionUiState.Phase.Content,
            suggestions = suggestions,
            selectedSuggestionIds = setOf("s-1"),
        )

        assertFalse(state.areAllSuggestionsSelected)
    }

    @Test
    fun `areAllSuggestionsSelected is true when all cards are selected`() {
        val suggestions = CorrectionSuggestionFixtures.contentSuggestions(LangCode.EN)
        val state = CorrectionUiState(
            phase = CorrectionUiState.Phase.Content,
            suggestions = suggestions,
            selectedSuggestionIds = suggestions.map { it.id }.toSet(),
        )

        assertTrue(state.areAllSuggestionsSelected)
    }

    @Test
    fun `areAllSuggestionsSelected is false when suggestions list is empty`() {
        val state = CorrectionUiState(
            phase = CorrectionUiState.Phase.Content,
            suggestions = emptyList(),
            selectedSuggestionIds = emptySet(),
        )

        assertFalse(state.areAllSuggestionsSelected)
    }

    // ─── primaryLang 노출 회귀 ────────────────────────────────────────────────

    @Test
    fun `toCorrectionUiState exposes primaryLanguage from userPref`() {
        // primaryLanguage 는 Ready 게이트 조건이 아니라 UiState 필드로 노출만 한다.
        val lang = LangCode.EN
        val global = buildGlobal(
            lang = lang,
            sessionSummary = SessionSummary(
                lang = lang,
                correctionAvailable = true,
                recentMinutes = 30,
                recentTopic = null,
            )
        )

        val state = global.toCorrectionUiState()

        // buildGlobal 은 primaryLang = KO 로 UserLangPref 를 구성한다.
        assertEquals(LangCode.KO, state.primaryLanguage)
        // primaryLanguage 가 없어도 Ready 판정이 흔들리지 않는다.
        assertEquals(CorrectionUiState.Phase.Ready, state.phase)
    }

    @Test
    fun `toCorrectionUiState primaryLanguage is null when userPref is absent`() {
        // userPref 가 없으면 selectedLearningLanguage 와 primaryLanguage 모두 null 이어야 한다.
        val global = GlobalLangState.initial()

        val state = global.toCorrectionUiState()

        assertNull(state.selectedLearningLanguage)
        assertNull(state.primaryLanguage)
    }

    // ─── COR-TUNE-008: resolveCompletionBaseState 회귀 ──────────────────────────
    // 교정 완료 시 분석 base 로 동결 snapshot 대신 완료 직전 최신 langStates[lang] 을 쓰는지,
    // 최신 조회 실패/미존재 시 동결 snapshot 으로 fallback 하는지 못 박는다.

    @Test
    fun `resolveCompletionBaseState prefers fresh langState over frozen snapshot`() {
        // Generating 진입 시 동결된 snapshot(grammar=0.3) 이후, 완료 직전 최신 state(grammar=0.7)가 들어왔다면
        // 최신값을 base 로 써야 stale 덮어쓰기(lost update)를 막는다.
        val lang = LangCode.EN
        val frozen = LangState.initial(lang).copy(
            internal = LangState.initial(lang).internal.copy(grammarAccuracy = 0.3)
        )
        val fresh = LangState.initial(lang).copy(
            internal = LangState.initial(lang).internal.copy(grammarAccuracy = 0.7)
        )
        val freshGlobal = GlobalLangState.initial().copy(langStates = mapOf(lang to fresh))

        val base = resolveCompletionBaseState(freshGlobal, lang, frozen)

        assertEquals(0.7, base!!.internal.grammarAccuracy, 0.0001)
    }

    @Test
    fun `resolveCompletionBaseState falls back to frozen snapshot when fresh global is null`() {
        // 최신 조회(observeLearningState().first())가 실패해 null 이면 동결 snapshot 으로 완료를 이어간다.
        val lang = LangCode.EN
        val frozen = LangState.initial(lang).copy(
            internal = LangState.initial(lang).internal.copy(grammarAccuracy = 0.3)
        )

        val base = resolveCompletionBaseState(freshGlobal = null, lang = lang, frozenSnapshot = frozen)

        assertSame(frozen, base)
    }

    @Test
    fun `resolveCompletionBaseState falls back to frozen snapshot when fresh global lacks the language`() {
        // 최신 global 에 해당 언어 항목이 없으면(예: 다른 언어만 존재) 동결 snapshot 으로 fallback 한다.
        val lang = LangCode.EN
        val frozen = LangState.initial(lang).copy(
            internal = LangState.initial(lang).internal.copy(grammarAccuracy = 0.3)
        )
        val freshGlobalWithoutLang = GlobalLangState.initial().copy(langStates = emptyMap())

        val base = resolveCompletionBaseState(freshGlobalWithoutLang, lang, frozen)

        assertSame(frozen, base)
    }
}
