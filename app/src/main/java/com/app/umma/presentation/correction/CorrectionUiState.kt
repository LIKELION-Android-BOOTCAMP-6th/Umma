package com.app.umma.presentation.correction

import com.app.umma.domain.model.correction.CompleteCorrectionResult
import com.app.umma.domain.model.correction.CorrectionSaveRequest
import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.learningstate.GlobalLangState
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.currentLangState
import com.app.umma.domain.model.learningstate.currentSessionSummary
import com.app.umma.domain.model.learningstate.selectedLang

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
 *  - (COR-006-A) 저장 요청 변환 성공 후 CompleteCorrectionUseCase 완료 파이프라인 호출까지 이어가고,
 *    완료 in-flight 윈도우([isCompleting])와 완료 결과 보관([completionResult])을 추가한다.
 *    완료 성공 시 [Phase.Done] 으로 전환되지만, COR-007 복귀 이벤트가 즉시 발화되므로 사용자가 별도 완료
 *    안내 화면에 머물지 않는 정책을 따른다.
 *  - (COR-006-B) 로컬 완료 실패 결과를 받으면 [Phase.Retry] 로 전환한다. 카드 목록 / 선택 /
 *    [saveRequest] 는 그대로 보존해 사용자가 같은 입력으로 다시 저장 버튼을 누르면 [PrepareSaveRequestUseCase]
 *    가 deterministic 하게 같은 [CorrectionSaveRequest] 를 재생성해 같은 완료 요청으로 재진입한다.
 *    실패 사유는 [completionErrorReason] 에 보관하고 화면 배너로 노출한다.
 *    Firestore sync / Session compression / Statistics history 의 pending 만 발생한 경우는
 *    [CompleteCorrectionUseCase] 가 `Result.success` 로 흘려보내므로 Retry 가 아니라 Done 으로 이어간다.
 *  - (COR-001-B) 결손 케이스(선택 언어 없음 / SessionSummary 없음 / LangState 없음 / correctionAvailable=false)를
 *    [Phase.Empty] 한 분기로 묶고, 화면 레이어가 AI Chat 이동 CTA 와 함께 노출할 수 있도록 한다.
 *    설계 문서의 `Empty(reason)` 표기와 정합. 결손 사유 자체는 [notAvailableReason] 의 logcat 단서로만 추적하고
 *    UiState 표면에는 별도 `reason` 필드를 두지 않는다. 또한 중복 초기화 방지를 위한 generate 트리거 가드는
 *    [shouldTriggerGeneration] pure helper 로 분리해 ViewModel 본문과 동치인 invariant 를 회귀 테스트한다.
 *  - (COR-002-B) AI 응답이 0 건일 때의 [Phase.EmptyResult] 분기, AI 호출/파싱/필수 필드 누락 실패의 [Phase.Error]
 *    분기를 [applyGenerationOutcome] pure helper 로 일원화한다. Error 에서는 같은 Session Memory/선택 언어 기준의
 *    Retry 액션을 [CorrectionViewModel.onRetryClicked] 로 제공한다. 터미널 phase(Empty/EmptyResult/Error) 진입 시
 *    generationLaunched 가드를 해제해, 다른 경로로 학습 언어가 바뀌면 새 Ready emit 에서 자동 재시도된다.
 *
 * 비범위:
 *  - "전체 선택" 토글은 후속 UI 백로그 범위.
 *  - 로컬 완료 성공 후 Dashboard 복귀 navigation 은 COR-007-A 가 1회성 이벤트로 다룬다.
 *
 * COR-007-B (pending 비차단 정책):
 *  - Firestore sync / Session compression / Statistics history 의 pending 상태가 채워진 success 결과도
 *    [applyCompletionOutcome] 의 onSuccess 분기 하나로 [Phase.Done] 으로 전환한다.
 *  - pending 정보는 [completionResult] 안의 필드(`pendingSyncFlashcardIds` /
 *    `sessionCompressionPending` / `statisticsHistoryPending`) 로 보관만 하고 화면에는 노출하지 않는다.
 *    사용자 노출은 [CorrectionViewModel.logCompletionResult] 진단 로그가 전담한다.
 *  - 따라서 본 UiState 는 pending 을 위한 별도 표면(필드/Phase) 을 추가하지 않는다.
 */
data class CorrectionUiState(
    // 첫 emit 전 (preload 대기) → Ready / Empty / Generating / Content / Error / Done 중 하나로 수렴.
    // (COR-001-B 에서 NotAvailable → Empty 로 rename. 설계 문서 표기 `Empty(reason)` 정합.)
    val phase: Phase = Phase.Loading,
    // 현재 선택 학습 언어. Empty 진입 사유 디버깅(어느 단계가 결손인지 식별) 에도 사용.
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
    // COR-005-A 에서 추가된 변환 실패 사유(예: uid 미확보, blank 필수 필드).
    // COR-005-B 부터 Content phase 카드 목록 상단 배너로 사용자에게 노출된다.
    // 다음 저장 시도(Prepared/Failed/UidUnavailable apply) 시 함께 갱신된다.
    val saveErrorReason: String? = null,
    // COR-005-B: 저장 요청 준비 in-flight 가드.
    // true 동안 [canSave] 가 false 가 되어 저장 버튼이 비활성화된다.
    // 현재 onSaveClicked 는 동기 흐름이라 윈도우가 짧지만, COR-006 가 suspend 한 저장 호출을 얹으면
    // 이 윈도우가 실효를 가진다. 같은 프레임 두 번 디스패치도 함께 막는다.
    val isSavePreparing: Boolean = false,
    // COR-006-A: 완료 파이프라인 in-flight 가드.
    // 변환 in-flight([isSavePreparing]) 와 별개로 둔 이유는 두 단계의 의미가 다르기 때문이다.
    // 저장 버튼 한 번 클릭이 (1) 변환 → (2) 완료 두 단계를 직렬로 진행하는데, 변환 윈도우가 닫힌 뒤
    // 완료 윈도우가 열리는 짧은 구간에 두 번째 클릭이 들어와도 [canSave] 가 false 가 되도록 분리해 둔다.
    // 같은 이유로 [canSave] 가 두 플래그를 모두 가드한다.
    val isCompleting: Boolean = false,
    // COR-006-A: CompleteCorrectionUseCase 성공 결과 보관.
    // COR-007 복귀 이벤트가 성공 직후 지연 없이 발화되므로 화면은 이 값을 완료 안내로 노출하지 않는다.
    // pending sync / compression 진단과 회귀 테스트를 위해 결과 객체 자체는 상태에 보관한다.
    val completionResult: CompleteCorrectionResult? = null,
    // COR-006-B: CompleteCorrectionUseCase 가 로컬 완료 실패 결과를 돌려준 사유.
    // [Phase.Retry] 화면 상단 배너로 노출하기 위한 진단 텍스트로, 같은 saveRequest 로 재시도가 성공하면
    // [applyCompletionOutcome] 의 onSuccess 분기에서 null 로 초기화된다.
    // saveErrorReason 과 의미가 다르다 — saveErrorReason 은 "저장 요청 변환 단계 실패", 본 필드는
    // "완료 파이프라인 단계 실패" 사유다. 두 필드가 동시에 채워지는 일은 정상 흐름에서는 없다.
    val completionErrorReason: String? = null,
) {
    /**
     * Correction 화면이 가질 수 있는 진행 단계.
     *
     * 결손 케이스(선택 언어 없음 / SessionSummary 없음 / LangState 없음 /
     * correctionAvailable=false)는 사용자 입장에서 "지금은 교정할 게 없음" 으로
     * 동일하게 보이므로 [Empty] 하나로 합친다. 어느 필드가 비어 있는지는
     * logcat 의 ViewModel 로그([notAvailableReason]) 로 추적한다.
     *
     * COR-001-B: 결손 사유는 logcat 단서로만 노출하고 화면 표면에는 별도 필드를 두지 않는다.
     * UI 가 분기할 수 있는 정보는 phase 자체로 충분하며, AI Chat 이동 CTA 도 phase=Empty 한 조건만 본다.
     */
    enum class Phase {
        // preload 가 끝나기 전 또는 첫 collect emit 전.
        Loading,

        // selectedLang + sessionSummary + langState 모두 채워졌고
        // sessionSummary.correctionAvailable == true 인 상태.
        // ViewModel 이 이 phase 를 보면 즉시 generateSuggestions 를 1회 트리거한다.
        Ready,

        // 위 조건 중 하나라도 누락된 상태. (COR-001-B: 설계 문서의 `Empty(reason)` 표기와 정합.)
        // 화면 레이어가 AI Chat 이동 CTA 와 함께 노출한다.
        // ※ Ready 게이트 미통과 케이스만 의미한다 — AI 응답 0건은 [EmptyResult] 로 별도 분기한다.
        Empty,

        // AI 호출 in-flight. 중복 트리거 방지에도 사용된다.
        Generating,

        // 비어 있지 않은 suggestions 가 있는 정상 상태.
        // (COR-002-B: 0건은 [EmptyResult] 로 빠지므로 Content 는 suggestions.isNotEmpty() 를 보장한다.)
        Content,

        // COR-002-B: Ready 게이트를 통과해 generate 가 호출됐고, AI 가 0건을 돌려준 경우.
        // [Empty] (Ready 게이트 미통과) 와 다른 의미 — 화면 레이어가 "다시 시도" + "AI 대화하기" CTA 를 제공한다.
        EmptyResult,

        // candidateId 매칭 실패 / JSON 파싱 실패 / 필수 필드 누락 / AI 호출 자체 실패.
        // errorReason 필드에 사유 보관. COR-002-B: 화면 레이어가 Retry 버튼을 제공한다.
        Error,

        // COR-006-A: CompleteCorrectionUseCase 가 로컬 완료 성공 결과를 돌려준 직후의 종착 단계.
        // 카드 목록 / 저장 버튼이 사라지고 안내 텍스트만 노출된다. Dashboard 복귀는 COR-007-A 에서
        // 이 단계 진입 시점을 1회성 이벤트로 소비해 navigation 으로 잇는다.
        Done,

        // COR-006-B: CompleteCorrectionUseCase 가 로컬 완료 실패 결과를 돌려준 직후의 재시도 대기 단계.
        // 카드 목록 / 선택 상태 / saveRequest 는 그대로 유지되어 사용자가 같은 저장 버튼을 다시 누르면
        // 같은 saveRequest 로 완료 파이프라인이 재진입된다.
        // 사용자 입장에서 [Content] 와 거의 동일한 화면이지만, [completionErrorReason] 배너가 상단에
        // 함께 노출된다.
        // ※ Firestore sync / compression / statistics 의 pending 만 발생한 경우는 [Done] 으로 빠진다 —
        //   [CompleteCorrectionUseCase] 가 `Result.success` 로 흘려보내므로 본 분기에 들어오지 않는다.
        Retry,
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

    /**
     * COR-005-B: 직전 저장 시도가 아직 in-flight 라 새 시도를 거절한 경우. state 변화 없음.
     *
     * canSave 가드보다 먼저 분기되어 "버튼 비활성인데도 같은 프레임에서 들어온 두 번째 호출"
     * 을 진단 로그와 회귀 테스트에서 식별 가능하게 한다. COR-006 가 suspend 한 저장 호출을
     * 얹기 시작하면 이 분기가 실제 race 차단의 핵심이 된다.
     */
    object AlreadyInFlight : SaveRequestOutcome

    /** 선택 id 중 현재 [CorrectionUiState.suggestions] 와 매칭되는 것이 없는 경우. state 변화 없음. */
    object NoMatchingSuggestions : SaveRequestOutcome

    /** uid 가 null/blank 라 도메인 require 전에 화면 계층이 차단한 경우. */
    data class UidUnavailable(val reason: String = "uid unavailable") : SaveRequestOutcome

    /** [com.app.umma.domain.usecase.correction.PrepareSaveRequestUseCase] 성공. */
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
    // COR-005-B: in-flight 가드는 canSave 보다 먼저 본다.
    // canSave 도 isSavePreparing 을 참조하기 때문에, 가드 순서가 뒤집히면 "중복 클릭" 과
    // "원래부터 저장 불가" 분기가 모두 NotSavable 로 합쳐져 진단이 모호해진다.
    if (isSavePreparing) return SaveRequestOutcome.AlreadyInFlight
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
        // AlreadyInFlight 는 원래 in-flight owner 가 아직 진행 중인 분기이므로
        // 두 번째 호출이 윈도우를 함부로 닫지 않도록 같은 인스턴스를 그대로 돌려준다.
        SaveRequestOutcome.AlreadyInFlight -> this
        // 시도조차 하지 않은 분기 — saveRequest / saveErrorReason 은 그대로 두지만,
        // ViewModel 이 onSaveClicked 진입에서 열어둔 in-flight 윈도우는 닫아 다음 클릭을 허용해야 한다.
        // isSavePreparing 이 이미 false 면 인스턴스를 새로 만들지 않아 MutableStateFlow.update 가 emit 을 생략한다.
        SaveRequestOutcome.NotSavable,
        SaveRequestOutcome.NoMatchingSuggestions ->
            if (isSavePreparing) copy(isSavePreparing = false) else this
        // COR-005-B: 실제 시도가 일어난 분기는 모두 in-flight 윈도우를 함께 닫는다.
        // 클리어 책임을 apply 한 곳에 응집해 ViewModel 본문이 finally 블록을 들지 않아도 된다.
        is SaveRequestOutcome.UidUnavailable -> copy(
            saveRequest = null,
            saveErrorReason = outcome.reason,
            isSavePreparing = false,
        )
        is SaveRequestOutcome.Prepared -> copy(
            saveRequest = outcome.request,
            saveErrorReason = null,
            isSavePreparing = false,
        )
        is SaveRequestOutcome.Failed -> copy(
            saveRequest = null,
            saveErrorReason = outcome.reason,
            isSavePreparing = false,
        )
    }

/**
 * 저장 버튼 활성 조건.
 *
 * Content 또는 Retry 단계이며 한 개 이상 선택되었고, 직전 시도가 어느 단계든 in-flight 가 아닌 경우에만 true.
 * - phase 가드: 생성 중 / 에러 / Empty / Done 등에서는 카드 자체가 안 보이므로
 *   잔존 selectedSuggestionIds 가 있어도 저장이 가능해선 안 된다.
 *   특히 [CorrectionUiState.Phase.Done] 진입 후에는 같은 화면에서 재저장이 일어나지 않아야 한다.
 * - COR-006-B: [CorrectionUiState.Phase.Retry] 도 허용한다. 완료 실패 후 카드 목록 / 선택 / saveRequest 가
 *   보존된 상태에서 사용자가 같은 저장 버튼을 다시 눌렀을 때 같은 완료 요청으로 재진입할 수 있어야 한다.
 *   ([PrepareSaveRequestUseCase] 가 같은 입력에 대해 같은 saveRequest 를 deterministic 하게 만들어 주므로
 *   COR-006 AC "Retry 시 같은 저장 요청으로 완료 파이프라인을 다시 호출" 이 자연스럽게 충족된다.)
 * - 0개 가드: AC "선택 항목이 0개이면 저장 버튼은 비활성화" 의 직접 반영.
 * - 변환 in-flight 가드 (COR-005-B): 저장 요청 준비 중에는 같은 버튼이 한 번 더 활성화되지 않도록 한다.
 * - 완료 in-flight 가드 (COR-006-A): 변환 윈도우가 닫힌 직후 완료 윈도우가 열리는 짧은 구간에도
 *   두 번째 클릭이 들어오지 않도록 [isCompleting] 도 함께 가드한다.
 */
val CorrectionUiState.canSave: Boolean
    get() = (phase == CorrectionUiState.Phase.Content || phase == CorrectionUiState.Phase.Retry) &&
            selectedSuggestionIds.isNotEmpty() &&
            !isSavePreparing &&
            !isCompleting

/**
 * COR-006-A 완료 파이프라인 트리거 분기.
 *
 * 저장 버튼 한 번의 클릭이 (1) 변환 → (2) 완료 두 단계를 직렬로 진행하므로, 변환이 [SaveRequestOutcome.Prepared]
 * 로 끝났을 때 즉시 완료 단계로 넘어갈지 / 거부할지를 결정하는 가드를 [SaveRequestOutcome] 와 같은 결로
 * 추출해 둔다. ViewModel 인스턴스 없이도 모든 분기를 회귀할 수 있다는 장점이 그대로 유지된다.
 */
internal sealed interface CompletionLaunchOutcome {
    /** 직전 완료 호출이 아직 진행 중이라 새 호출을 거절한 경우. state 변화 없음. */
    object AlreadyInFlight : CompletionLaunchOutcome

    /** 변환된 saveRequest 가 없거나 stale 이라 완료를 시작할 근거가 없는 경우. state 변화 없음. */
    object NoSaveRequest : CompletionLaunchOutcome

    /** 완료를 시작해도 된다는 결정. ViewModel 은 이 분기에서 viewModelScope.launch 로 실제 호출에 진입한다. */
    data class Launched(val saveRequest: CorrectionSaveRequest) : CompletionLaunchOutcome
}

/**
 * 현재 UiState 스냅샷이 완료 파이프라인을 시작할 수 있는 상태인지 계산한다.
 *
 * [CorrectionViewModel.onSaveClicked] 가 변환 직후 같은 호출 스택에서 이 함수를 호출하고,
 * [CompletionLaunchOutcome.Launched] 분기에서만 실제 [CompleteCorrectionUseCase] 호출에 진입한다.
 * compute → apply 분리 패턴을 유지하기 위해 state 갱신은 [openCompletionWindow] / [applyCompletionOutcome]
 * 가 책임진다.
 */
internal fun CorrectionUiState.computeCompletionLaunch(
    request: CorrectionSaveRequest?,
): CompletionLaunchOutcome {
    // 완료 in-flight 윈도우는 가장 먼저 본다. 두 번째 클릭이 우연히 같은 request 를 가지고 들어와도 막힌다.
    if (isCompleting) return CompletionLaunchOutcome.AlreadyInFlight
    // 변환이 실패했거나 stale 한 경우. canSave 가드와는 별개로, 호출자 쪽에서 request 가 비어 있으면 막는다.
    val saveRequest = request ?: return CompletionLaunchOutcome.NoSaveRequest
    return CompletionLaunchOutcome.Launched(saveRequest)
}

/**
 * 완료 in-flight 윈도우를 연다. [CompletionLaunchOutcome.Launched] 직후 ViewModel 이 viewModelScope.launch 안에서
 * 실제 호출에 들어가기 직전에 적용한다.
 */
internal fun CorrectionUiState.openCompletionWindow(): CorrectionUiState =
    if (isCompleting) this else copy(isCompleting = true)

/**
 * [CompleteCorrectionUseCase] 호출 결과를 UiState 에 반영한다.
 *
 * 성공 → [Phase.Done] 으로 전환하고 [completionResult] 에 결과를 보관한다.
 *        직전 Retry 진입에서 채워진 [completionErrorReason] 은 명시적으로 null 로 비워 stale 사유가 남지 않게 한다.
 *
 * 실패 → COR-006-B: [Phase.Retry] 로 전환하고 [completionErrorReason] 에 진단 메시지를 보관한다.
 *        카드 목록 / 선택 / [saveRequest] 는 그대로 유지해 사용자가 같은 입력으로 재시도할 수 있게 한다
 *        (AC "선택 상태와 카드 목록을 유지" 의 직접 반영). [completionResult] 는 명시적으로 null 로
 *        비워 화면이 stale Done 결과를 잘못 읽지 않도록 한다.
 *
 * COR-007-B: pending 필드(`pendingSyncFlashcardIds` / `sessionCompressionPending` /
 * `statisticsHistoryPending`) 가 채워져 있어도 사용자 실패가 아니므로 동일한 onSuccess 분기로
 * [Phase.Done] 에 진입한다. pending 은 *후속 재시도 대상*이며, 화면 노출 책임은 본 함수가 갖지 않고
 * [CorrectionViewModel.logCompletionResult] 의 진단 로그가 단일 SSOT 다. 회귀는
 * `CorrectionUiStateTest` 의 pending 3건이 보장한다.
 */
internal fun CorrectionUiState.applyCompletionOutcome(
    result: Result<CompleteCorrectionResult>,
): CorrectionUiState = result.fold(
    onSuccess = { value ->
        copy(
            phase = CorrectionUiState.Phase.Done,
            completionResult = value,
            // 직전 Retry 진입으로 채워졌을 수 있는 사유를 명시적으로 비운다.
            // Done 화면에서는 안내 텍스트만 노출되므로 stale 메시지가 어디서도 읽히지 않게 한다.
            completionErrorReason = null,
            isCompleting = false,
        )
    },
    onFailure = { e ->
        // COR-006-B: Retry 분기. 카드 목록 / 선택 / saveRequest 는 그대로 둔다.
        // applyGenerationOutcome 의 reason 채움 패턴(message ?: javaClass.simpleName) 을 그대로 따른다.
        val reason = e.message ?: e.javaClass.simpleName
        copy(
            phase = CorrectionUiState.Phase.Retry,
            completionErrorReason = reason,
            // 실패 사유가 도착했으므로 stale Done 결과가 보관되어 있다면 함께 비운다.
            // (정상 흐름에서 Retry 진입 시 completionResult 는 이미 null 이지만, 명시적으로 못 박는다.)
            completionResult = null,
            isCompleting = false,
        )
    },
)

/**
 * [GlobalLangState] 스냅샷을 Correction 화면의 UiState 로 환산한다.
 *
 * 이 함수는 Loading/Ready/Empty 만 결정한다.
 * Generating/Content/Error 로의 전이는 ViewModel 의 generateCorrection 흐름에서만 이뤄지며,
 * 한 번 그 phase 에 진입한 뒤에는 GlobalLangState 의 추가 emit 이 이 함수를 다시 통과하더라도
 * ViewModel 이 _uiState 를 덮어쓰지 않도록 가드를 둔다.
 * Done/Retry 도 마찬가지로 ViewModel 의 완료 파이프라인 결과 적용([applyCompletionOutcome]) 에서만
 * 진입한다 — GlobalLangState 의 refresh 가 본 함수를 다시 통과해도 완료 결과가 덮이지 않는다.
 *
 * AC 매핑:
 *  - "selectedLearningLanguage 확인"        → [GlobalLangState.selectedLang]
 *  - "SessionSummary 로드"                  → [GlobalLangState.currentSessionSummary]
 *  - "correctionAvailable 기준 판단"        → SessionSummary.correctionAvailable
 *  - "LangState snapshot 로드"              → [GlobalLangState.currentLangState]
 *  - "Ready → COR-002 자동 진행"            → phase = Ready (ViewModel 측 LaunchedEffect hook)
 *  - "결손 → Empty 분기"                    → COR-001-B (구 `NotAvailable` 의 rename 결과)
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
        // COR-001-B: 결손 케이스는 단일 Empty 분기로 묶고, 어떤 필드가 비었는지는 notAvailableReason 의 logcat 으로 추적.
        phase = if (ready) CorrectionUiState.Phase.Ready else CorrectionUiState.Phase.Empty,
        selectedLearningLanguage = lang,
        sessionSummary = sessionSummary,
        langStateSnapshot = langState,
    )
}

/**
 * Empty 진입 사유(=구 `NotAvailable` 진입 사유)를 사람이 읽을 수 있는 한 줄로 환산한다. logcat 디버깅 전용.
 *
 * Ready 면 null. UI 는 결손 사유를 단일 [CorrectionUiState.Phase.Empty] 로 합쳐 보여주므로,
 * 실 운영 시점에는 이 로그가 어느 단계에서 떨어졌는지 식별하는 유일한 단서가 된다.
 *
 * 함수명은 의도적으로 `notAvailableReason` 을 유지한다 — logcat grep / 기존 인계 문서 참조 일관성을 위해서이며,
 * 의미는 "Empty(Ready-gate 미통과) 진입 사유" 이다. 본 KDoc 이 그 매핑의 SSOT 다.
 * AI 응답이 0건인 [CorrectionUiState.Phase.EmptyResult] 는 별도 분기이므로 이 함수와 무관하다.
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

/**
 * COR-002-B: generate 흐름 결과를 UiState 에 반영한다.
 *
 * [CorrectionViewModel.triggerGeneration] 의 result.fold 본문을 pure function 으로 분리해
 * ViewModel 인스턴스 / viewModelScope 셋업 없이 모든 분기를 회귀 테스트할 수 있도록 한다.
 * [applySaveRequestOutcome] / [applyCompletionOutcome] 과 동일한 compute-apply 패턴.
 *
 * 분기 표:
 *  - success([])           → [CorrectionUiState.Phase.EmptyResult] : AI 가 0 건 응답.
 *  - success(non-empty)    → [CorrectionUiState.Phase.Content]     : 정상 카드 목록.
 *  - failure(t)            → [CorrectionUiState.Phase.Error]       : 호출/파싱/필수 필드 누락 실패.
 *
 * 세 분기 공통으로 정리하는 필드:
 *  - [CorrectionUiState.selectedSuggestionIds] = ∅  (stale id 잔존 방지)
 *  - [CorrectionUiState.saveRequest]           = null (직전 시도 stale 방지)
 *  - [CorrectionUiState.saveErrorReason]       = null
 */
internal fun CorrectionUiState.applyGenerationOutcome(
    result: Result<List<CorrectionSuggestion>>,
): CorrectionUiState = result.fold(
    onSuccess = { suggestions ->
        // 0건 → EmptyResult, 1건 이상 → Content.
        val nextPhase = if (suggestions.isEmpty()) {
            CorrectionUiState.Phase.EmptyResult
        } else {
            CorrectionUiState.Phase.Content
        }
        copy(
            phase = nextPhase,
            suggestions = suggestions,
            selectedSuggestionIds = emptySet(),
            errorReason = null,
            saveRequest = null,
            saveErrorReason = null,
        )
    },
    onFailure = { e ->
        // message 가 null 이면 class 이름으로 fallback — 현 ViewModel 패턴과 동치.
        val reason = e.message ?: e.javaClass.simpleName
        copy(
            phase = CorrectionUiState.Phase.Error,
            suggestions = emptyList(),
            selectedSuggestionIds = emptySet(),
            errorReason = reason,
            saveRequest = null,
            saveErrorReason = null,
        )
    },
)

/**
 * COR-001-B: Ready 게이트 통과 직후의 generateSuggestions 1회 트리거 가드.
 *
 * 의도:
 *  - [CorrectionViewModel] 의 collect 본문이 `phase==Ready && !generationLaunched` 분기에서
 *    [com.app.umma.domain.usecase.correction.GenerateSuggestionsUseCase] 를 호출한다.
 *    그 분기 결정을 pure function 으로 분리해 ViewModel 인스턴스 / viewModelScope 셋업 없이
 *    회귀 테스트가 가능하도록 만든다. ViewModel 본문과 항상 동치여야 한다는 invariant 가 핵심.
 *  - 한 번 launched=true 가 되면 GlobalLangState refresh 로 Ready 가 다시 emit 되어도 false 를
 *    돌려 두 번째 generate 가 일어나지 않도록 가드한다(COR-001-B AC "중복 요청과 중복 초기화 방지").
 *  - Ready 이외의 phase(특히 [CorrectionUiState.Phase.Empty]) 에서는 launched 와 무관하게 false 를
 *    돌려, Empty 진입 후 LangState 가 refresh 되어도 generate 가 시작되지 않도록 한다.
 *
 * 함수 분리 이유: ViewModel 본문에서 한 줄 if 로 같이 처리해도 동일하게 동작하지만, 가드 변경 시
 * "어떤 케이스가 깨지는가" 를 [CorrectionUiStateTest] 한 곳에서 표 형태로 회귀할 수 있다는 점이 크다.
 */
internal fun shouldTriggerGeneration(
    phase: CorrectionUiState.Phase,
    alreadyLaunched: Boolean,
): Boolean = phase == CorrectionUiState.Phase.Ready && !alreadyLaunched
