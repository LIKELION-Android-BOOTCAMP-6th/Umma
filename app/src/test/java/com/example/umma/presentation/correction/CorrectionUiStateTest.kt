package com.example.umma.presentation.correction

import com.example.umma.domain.model.learningstate.DashSummary
import com.example.umma.domain.model.learningstate.FlashcardSummary
import com.example.umma.domain.model.learningstate.GlobalLangState
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.LangState
import com.example.umma.domain.model.learningstate.SessionSummary
import com.example.umma.domain.model.learningstate.UserLangPref
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * COR-001-A 분기 로직 회귀 테스트.
 *
 * [GlobalLangState.toCorrectionUiState] 는 [CorrectionViewModel] 의 collect 본체에서 분리한
 * 순수 함수다. ViewModel 자체는 viewModelScope/Main dispatcher 셋업이 필요해 무겁지만,
 * 이 함수는 [GlobalLangState] 만 받으면 동일한 결과를 돌려주므로 모든 AC 시나리오를
 * coroutines-test 의존성 없이 즉시 회귀할 수 있다.
 */
class CorrectionUiStateTest {

    @Test
    fun `returns NotAvailable when userPref is missing`() {
        // emptyDataStore 와 동일 — 신규 사용자, 아직 onboarding 도 안 함.
        val global = GlobalLangState.initial()

        val state = global.toCorrectionUiState()

        assertEquals(CorrectionUiState.Phase.NotAvailable, state.phase)
        assertNull(state.selectedLearningLanguage)
        assertEquals(
            "selectedLang == null (userPref absent)",
            global.notAvailableReason()
        )
    }

    @Test
    fun `returns NotAvailable when SessionSummary is missing for the selected language`() {
        // userPref / langState 는 있지만 sessionSummaries 가 비어있는 케이스.
        val lang = LangCode.EN
        val global = GlobalLangState(
            userPref = UserLangPref.initial(nativeLang = LangCode.KO, primaryLang = lang),
            langStates = mapOf(lang to LangState.initial(lang)),
            dashSummaries = emptyMap(),
            sessionSummaries = emptyMap(),
            flashcardSummaries = emptyMap(),
        )

        val state = global.toCorrectionUiState()

        assertEquals(CorrectionUiState.Phase.NotAvailable, state.phase)
        assertEquals(lang, state.selectedLearningLanguage)
        assertNull(state.sessionSummary)
        assertEquals(
            "sessionSummary == null for lang=$lang",
            global.notAvailableReason()
        )
    }

    @Test
    fun `returns NotAvailable when LangState snapshot is missing`() {
        // sessionSummary 는 살아있고 correctionAvailable=true 인데 langStates 만 비어 있는 케이스.
        val lang = LangCode.EN
        val global = GlobalLangState(
            userPref = UserLangPref.initial(nativeLang = LangCode.KO, primaryLang = lang),
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

        assertEquals(CorrectionUiState.Phase.NotAvailable, state.phase)
        assertNotNull(state.sessionSummary)
        assertNull(state.langStateSnapshot)
        assertEquals(
            "langState snapshot == null for lang=$lang",
            global.notAvailableReason()
        )
    }

    @Test
    fun `returns NotAvailable when correctionAvailable is false`() {
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

        assertEquals(CorrectionUiState.Phase.NotAvailable, state.phase)
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
    fun `returns NotAvailable when SessionSummary correctionAvailable is false even if DashSummary is true`() {
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

        assertEquals(CorrectionUiState.Phase.NotAvailable, state.phase)
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
        userPref = UserLangPref.initial(nativeLang = LangCode.KO, primaryLang = lang),
        langStates = mapOf(lang to LangState.initial(lang)),
        dashSummaries = mapOf(lang to dashSummary),
        sessionSummaries = mapOf(lang to sessionSummary),
        flashcardSummaries = mapOf(lang to FlashcardSummary.initial(lang)),
    )
}
