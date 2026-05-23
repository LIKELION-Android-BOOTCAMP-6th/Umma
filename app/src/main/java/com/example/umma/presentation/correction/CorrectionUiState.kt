package com.example.umma.presentation.correction

import com.example.umma.domain.model.correction.CorrectionSaveRequest
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
 *  - (COR-005-A) 저장 버튼 클릭 시 ViewModel 이 만들어둔 Flashcard 저장 요청 모델을
 *    [saveRequest] 에 보관한다. 변환 실패 사유는 [saveErrorReason] 에 남긴다.
 *    실제 저장 파이프라인 호출과 Done/Retry UI 전이는 COR-006 범위라 여기서는 다루지 않는다.
 *
 * 비범위:
 *  - Empty 분리 / Retry 액션은 COR-002-B 에서 [Phase.Empty] 와 함께 추가한다.
 *    그래서 [Phase.Content] 는 suggestions 가 비어 있어도 그대로 유지된다.
 *  - "전체 선택" 토글은 후속 UI 백로그 범위.
 *  - [saveRequest] 를 받아 CompleteCorrectionUseCase 를 호출하고 Done/Retry 상태로 전환하는 흐름은 COR-006.
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
    // COR-005-A: 변환에 성공한 Flashcard 저장 요청 모델. COR-006 이 이 값을 읽어
    // CompleteCorrectionUseCase 로 넘기므로 화면 계층에서는 보관만 한다.
    // 새 suggestions 가 들어오거나 변환이 다시 시도될 때 ViewModel 이 함께 갱신/초기화한다.
    val saveRequest: CorrectionSaveRequest? = null,
    // COR-005-A: 변환 실패 사유(예: uid 미확보, blank 필수 필드). UI 노출은 COR-006 범위라
    // 현재는 logcat 보조와 회귀 테스트의 단서로만 사용한다.
    val saveErrorReason: String? = null,
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
 * COR-005-A 저장 요청 변환 시도의 분기 결과.
 *
 * ViewModel 의 `onSaveClicked` 안에 분기 if/else 를 늘어놓는 대신, "어떤 분기로 끝났는지" 를
 * 값으로 들고 다닌다. 덕분에 logging 책임과 state 갱신 책임을 한 곳에 묶지 않을 수 있고,
 * 모든 분기를 ViewModel 인스턴스 없이 [computeSaveRequestOutcome] 단위로 회귀 테스트할 수 있다.
 */
internal sealed interface SaveRequestOutcome {
    /** [CorrectionUiState.canSave] 가 false 라 변환을 시도조차 하지 않은 경우. state 변화 없음. */
    object NotSavable : SaveRequestOutcome

    /** 선택 id 중 현재 [CorrectionUiState.suggestions] 와 매칭되는 것이 없는 경우. state 변화 없음. */
    object NoMatchingSuggestions : SaveRequestOutcome

    /** uid 가 null/blank 라 도메인 require 전에 화면 계층이 차단한 경우. */
    data class UidUnavailable(val reason: String = "uid unavailable") : SaveRequestOutcome

    /** [com.example.umma.domain.usecase.correction.PrepareSaveRequestUseCase] 성공. */
    data class Prepared(val request: CorrectionSaveRequest) : SaveRequestOutcome

    /** UseCase require 실패 등 변환이 실제로 시도됐지만 실패한 경우. */
    data class Failed(val reason: String) : SaveRequestOutcome
}

/**
 * 현재 UiState 스냅샷과 외부 의존성(uid, prepare 함수)을 받아 어떤 분기로 끝나야 할지 계산한다.
 *
 * 이 함수가 [SaveRequestOutcome] 만 돌려주고 직접 state 를 바꾸지 않는 이유는
 * (1) logging 분기와 state 갱신 분기를 ViewModel 한 곳에서만 결정하기 위함이고,
 * (2) ViewModel 의 viewModelScope/Main dispatcher 셋업 없이도 모든 가드를 회귀할 수 있게 하기 위함이다.
 */
internal fun CorrectionUiState.computeSaveRequestOutcome(
    uid: String?,
    prepare: (uid: String, selected: List<CorrectionSuggestion>) -> Result<CorrectionSaveRequest>,
): SaveRequestOutcome {
    if (!canSave) return SaveRequestOutcome.NotSavable

    // AC 엣지: 선택된 id 중 현재 결과 목록에 없는 것은 stale 이므로 변환 대상에서 제외한다.
    // 새 suggestions 로 교체된 직후 화면 race 로 들어온 id 가 require 까지 흘러 들어가지 않게 막는다.
    val selected = suggestions.filter { it.id in selectedSuggestionIds }
    if (selected.isEmpty()) return SaveRequestOutcome.NoMatchingSuggestions

    if (uid.isNullOrBlank()) return SaveRequestOutcome.UidUnavailable()

    return prepare(uid, selected).fold(
        onSuccess = { SaveRequestOutcome.Prepared(it) },
        onFailure = { e -> SaveRequestOutcome.Failed(e.message ?: e.javaClass.simpleName) }
    )
}

/**
 * [computeSaveRequestOutcome] 결과를 UiState 에 반영한다.
 *
 * NotSavable / NoMatchingSuggestions 는 사용자가 이미 알고 있는 상태(버튼 비활성, 선택 변화 없음)
 * 라 굳이 새 객체를 만들지 않는다. MutableStateFlow.update 는 같은 인스턴스면 emit 을 생략하므로
 * 불필요한 collector 깨움도 함께 막을 수 있다.
 */
internal fun CorrectionUiState.applySaveRequestOutcome(outcome: SaveRequestOutcome): CorrectionUiState =
    when (outcome) {
        SaveRequestOutcome.NotSavable, SaveRequestOutcome.NoMatchingSuggestions -> this
        is SaveRequestOutcome.UidUnavailable -> copy(saveRequest = null, saveErrorReason = outcome.reason)
        is SaveRequestOutcome.Prepared -> copy(saveRequest = outcome.request, saveErrorReason = null)
        is SaveRequestOutcome.Failed -> copy(saveRequest = null, saveErrorReason = outcome.reason)
    }

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
