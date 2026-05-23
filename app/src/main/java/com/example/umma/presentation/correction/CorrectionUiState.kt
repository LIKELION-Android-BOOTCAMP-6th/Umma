package com.example.umma.presentation.correction

import com.example.umma.domain.model.correction.CorrectionSuggestion
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
 * SSOT: COR-001_Initial_State.md / COR-002_Suggestion_Generation.md / COR-004_Card_Selection.md
 *
 * 책임:
 *  - (COR-001-A) Global Learning State 의 selectedLearningLanguage / SessionSummary / LangState snapshot 을
 *    한데 모아 "교정 결과 생성 가능" 여부를 [phase] 한 필드로 노출한다.
 *  - (COR-002-A) Ready 게이트 통과 이후 ViewModel 이 자동으로 generateSuggestions 를 호출하면
 *    [Phase.Generating] → [Phase.Content] 또는 [Phase.Error] 로 진행한다.
 *  - (COR-004) 사용자가 저장 대상으로 고른 카드 목록을 [selectedSuggestionIds] 로 보관한다.
 *    저장 버튼 활성/비활성은 [canSave] 확장 속성으로 파생한다.
 *
 * 비범위:
 *  - Empty 분리 / Retry 액션은 COR-002-B 에서 [Phase.Empty] 와 함께 추가한다.
 *    그래서 [Phase.Content] 는 suggestions 가 비어 있어도 그대로 유지된다.
 *  - "전체 선택" 토글과 실제 Flashcard 저장 호출은 COR-004 다음 백로그 범위.
 *    이번 단계의 저장 버튼은 ViewModel 메서드만 호출하고 본문은 placeholder 이다.
 */
data class CorrectionUiState(
    // 첫 emit 전 (preload 대기) → Ready / NotAvailable / Generating / Content / Error 중 하나로 수렴.
    val phase: Phase = Phase.Loading,
    // 현재 선택 학습 언어. NotAvailable 사유 디버깅에도 사용.
    val selectedLearningLanguage: LangCode? = null,
    // 현재 선택 언어 기준 SessionSummary. correctionAvailable 판정 근거.
    val sessionSummary: SessionSummary? = null,
    // 현재 선택 언어 기준 LangState snapshot. generateSuggestions 입력으로 사용된다.
    val langStateSnapshot: LangState? = null,
    // Content 상태에서 화면이 표시할 교정 결과 목록. 그 외 phase 에서는 빈 리스트.
    val suggestions: List<CorrectionSuggestion> = emptyList(),
    // 사용자가 저장 대상으로 고른 CorrectionSuggestion.id 집합. 순서 무관 + 중복 자동 방어 목적으로 Set 사용.
    // Content 가 새 suggestions 로 교체될 때 stale id 잔존을 막기 위해 ViewModel 이 함께 비운다.
    val selectedSuggestionIds: Set<String> = emptySet(),
    // Error 상태에서 logcat / 화면 디버깅 텍스트로 노출할 짧은 사유. 그 외에는 null.
    val errorReason: String? = null,
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
        // ViewModel 이 이 phase 를 보면 즉시 generateSuggestions 를 1회 트리거한다.
        Ready,

        // 위 조건 중 하나라도 누락된 상태.
        NotAvailable,

        // AI 호출 in-flight. 중복 트리거 방지에도 사용된다.
        Generating,

        // suggestions 가 채워진 정상 상태. -A 범위에서는 빈 리스트도 Content 로 둔다.
        // (Empty UX 는 COR-002-B 에서 Phase.Empty 로 분리.)
        Content,

        // candidateId 매칭 실패 / JSON 파싱 실패 / 필수 필드 누락 / AI 호출 자체 실패.
        // errorReason 필드에 사유 보관.
        Error,
    }
}

/** Ready 진입 여부를 한 줄로 확인할 수 있는 편의 속성. */
val CorrectionUiState.isReady: Boolean
    get() = phase == CorrectionUiState.Phase.Ready

/**
 * 저장 버튼 활성 조건.
 *
 * Content 단계이며 한 개 이상 선택된 경우에만 true.
 * - phase 가드: 생성 중 / 에러 / NotAvailable 등에서는 카드 자체가 안 보이므로
 *   잔존 selectedSuggestionIds 가 있어도 저장이 가능해선 안 된다.
 * - 0개 가드: AC "선택 항목이 0개이면 저장 버튼은 비활성화" 의 직접 반영.
 */
val CorrectionUiState.canSave: Boolean
    get() = phase == CorrectionUiState.Phase.Content && selectedSuggestionIds.isNotEmpty()

/**
 * [GlobalLangState] 스냅샷을 Correction 화면의 UiState 로 환산한다.
 *
 * 이 함수는 Loading/Ready/NotAvailable 만 결정한다.
 * Generating/Content/Error 로의 전이는 ViewModel 의 generateCorrection 흐름에서만 이뤄지며,
 * 한 번 그 phase 에 진입한 뒤에는 GlobalLangState 의 추가 emit 이 이 함수를 다시 통과하더라도
 * ViewModel 이 _uiState 를 덮어쓰지 않도록 가드를 둔다.
 *
 * AC 매핑:
 *  - "selectedLearningLanguage 확인"        → [GlobalLangState.selectedLang]
 *  - "SessionSummary 로드"                  → [GlobalLangState.currentSessionSummary]
 *  - "correctionAvailable 기준 판단"        → SessionSummary.correctionAvailable
 *  - "LangState snapshot 로드"              → [GlobalLangState.currentLangState]
 *  - "Ready → COR-002 자동 진행"            → phase = Ready (ViewModel 측 LaunchedEffect hook)
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
