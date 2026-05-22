package com.example.umma.presentation.correction

import com.example.umma.domain.model.learningstate.GlobalLangState
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.LangState
import com.example.umma.domain.model.learningstate.SessionSummary
import com.example.umma.domain.model.learningstate.currentLangState
import com.example.umma.domain.model.learningstate.currentSessionSummary
import com.example.umma.domain.model.learningstate.selectedLang

/**
 * Correction 화면의 단일 UI 상태.
 *
 * SSOT: COR-001_Initial_State.md
 *
 * 책임 (COR-001-A):
 *  - Global Learning State 의 selectedLearningLanguage / SessionSummary / LangState snapshot 을
 *    한데 모아 "교정 결과 생성 가능" 여부를 [phase] 한 필드로 노출한다.
 *  - Ready 게이트 도달 자체가 COR-002 (교정 결과 생성) 의 트리거 hook 이다.
 *    별도 사용자 버튼 없이 다음 흐름으로 이어진다 — AC "Ready 상태가 되면
 *    사용자 버튼 없이 COR-002 교정 결과 생성 흐름으로 이어질 수 있다." 매핑.
 *
 * 비범위:
 *  - generateSuggestions 호출 결과(Content/Empty/Error)는 COR-002 백로그에서 별도 phase 로 추가.
 *  - sync / network 실패 분기 또한 이 백로그 AC 에 없어 Error phase 를 두지 않는다.
 */
data class CorrectionUiState(
    // 첫 emit 전 (preload 대기) → Ready 또는 NotAvailable 중 하나로 수렴.
    val phase: Phase = Phase.Loading,
    // 현재 선택 학습 언어. NotAvailable 사유 디버깅에도 사용.
    val selectedLearningLanguage: LangCode? = null,
    // 현재 선택 언어 기준 SessionSummary. correctionAvailable 판정 근거.
    val sessionSummary: SessionSummary? = null,
    // 현재 선택 언어 기준 LangState snapshot. COR-002 prompt 구성에 쓰일 예정.
    val langStateSnapshot: LangState? = null,
) {
    /**
     * Correction 화면이 가질 수 있는 진행 단계.
     *
     * 결손 케이스(선택 언어 없음 / SessionSummary 없음 / LangState 없음 /
     * correctionAvailable=false)는 사용자 입장에서 "지금은 교정할 게 없음" 으로
     * 동일하게 보이므로 [NotAvailable] 하나로 합친다. 어느 필드가 비어 있는지는
     * logcat 의 ViewModel 로그로 추적한다.
     */
    enum class Phase {
        // preload 가 끝나기 전 또는 첫 collect emit 전.
        Loading,

        // selectedLang + sessionSummary + langState 모두 채워졌고
        // sessionSummary.correctionAvailable == true 인 상태.
        // COR-002 자동 진행 트리거 hook.
        Ready,

        // 위 조건 중 하나라도 누락된 상태.
        NotAvailable,
    }
}

/** Ready 진입 여부를 한 줄로 확인할 수 있는 편의 속성. COR-002 LaunchedEffect 게이트 등에 쓰인다. */
val CorrectionUiState.isReady: Boolean
    get() = phase == CorrectionUiState.Phase.Ready

/**
 * [GlobalLangState] 스냅샷을 Correction 화면의 UiState 로 환산한다.
 *
 * AC 매핑:
 *  - "selectedLearningLanguage 확인"        → [GlobalLangState.selectedLang]
 *  - "SessionSummary 로드"                  → [GlobalLangState.currentSessionSummary]
 *  - "correctionAvailable 기준 판단"        → SessionSummary.correctionAvailable
 *  - "LangState snapshot 로드"              → [GlobalLangState.currentLangState]
 *  - "RT-003 correction context 조회 준비"  → SessionSummary.correctionAvailable 로 갈음
 *  - "Ready → COR-002 자동 진행"            → phase = Ready (Screen 측 LaunchedEffect hook)
 *
 * AC 6 (DashSummary 아닌 SessionSummary 기준) 매핑: 본 함수는 [GlobalLangState.dashSummaries]
 * 를 일절 참조하지 않는다. Ready 판정은 오로지 SessionSummary 의 correctionAvailable 만 본다.
 *
 * 순수 함수로 추출한 의도: [CorrectionViewModel] 의 collect 콜백 본체를 비우고 단위 테스트
 * 가능성을 끌어올린다 — 분기 로직만 검증하면 viewModelScope / Main dispatcher 셋업 없이도
 * 모든 AC 시나리오를 빠르게 회귀할 수 있다.
 */
internal fun GlobalLangState.toCorrectionUiState(): CorrectionUiState {
    val lang = selectedLang
    val sessionSummary = currentSessionSummary()
    val langState = currentLangState()

    val ready = lang != null &&
            sessionSummary != null &&
            langState != null &&
            sessionSummary.correctionAvailable

    return CorrectionUiState(
        phase = if (ready) CorrectionUiState.Phase.Ready else CorrectionUiState.Phase.NotAvailable,
        selectedLearningLanguage = lang,
        sessionSummary = sessionSummary,
        langStateSnapshot = langState,
    )
}

/**
 * NotAvailable 진입 사유를 사람이 읽을 수 있는 한 줄로 환산한다. logcat 디버깅 전용.
 *
 * Ready 면 null. UI 는 결손 사유를 단일 NotAvailable 로 합쳐 보여주므로, 실 운영 시점에는
 * 이 로그가 어느 단계에서 떨어졌는지 식별하는 유일한 단서가 된다.
 */
internal fun GlobalLangState.notAvailableReason(): String? {
    val lang = selectedLang ?: return "selectedLang == null (userPref absent)"
    val sessionSummary = currentSessionSummary() ?: return "sessionSummary == null for lang=$lang"
    currentLangState() ?: return "langState snapshot == null for lang=$lang"
    if (!sessionSummary.correctionAvailable) {
        return "sessionSummary.correctionAvailable == false for lang=$lang"
    }
    return null
}
