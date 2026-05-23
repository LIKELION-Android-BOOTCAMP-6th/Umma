package com.example.umma.presentation.correction

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.umma.domain.model.correction.CompleteCorrectionResult
import com.example.umma.domain.model.correction.CorrectionSaveRequest
import com.example.umma.domain.model.correction.GenerateSuggestionsInput
import com.example.umma.domain.usecase.auth.GetCurrentUserUidUseCase
import com.example.umma.domain.usecase.correction.CompleteCorrectionUseCase
import com.example.umma.domain.usecase.correction.ExtractSessionCandidatesUseCase
import com.example.umma.domain.usecase.correction.GenerateSuggestionsUseCase
import com.example.umma.domain.usecase.correction.PrepareSaveRequestUseCase
import com.example.umma.domain.usecase.learningstate.ObserveLearningStateUseCase
import com.example.umma.domain.usecase.learningstate.PreloadLearningStateUseCase
import com.example.umma.domain.usecase.realtime.GetCorrectionContextUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Correction 화면의 ViewModel.
 *
 * SSOT: COR-001_Initial_State.md / COR-002_Suggestion_Generation.md
 *
 * 책임:
 *  - (COR-001-A) [GlobalLangState] 구독 → Ready / NotAvailable 분기.
 *  - (COR-002-A) Ready 첫 emit 시 사용자 추가 입력 없이 generateSuggestions 를 1회 자동 트리거.
 *    RT-003 correction context → 후보 추출 → AI 호출 → CorrectionSuggestion 목록 → Content / Error.
 *
 *  - (COR-004) 카드 선택/해제 토글과 저장 버튼 클릭 진입점을 노출한다.
 *    선택 상태는 [CorrectionUiState.selectedSuggestionIds] 가 SSOT 이고,
 *    저장 버튼 본문(실제 Flashcard 저장)은 후속 백로그 범위라 onSaveClicked 는 로그만 남긴다.
 *  - (COR-005-A) 저장 버튼 클릭 시 선택된 카드 → [CorrectionSaveRequest] 변환을
 *    [PrepareSaveRequestUseCase] 로 위임하고 결과를 [CorrectionUiState.saveRequest] 에 보관한다.
 *  - (COR-006-A) 변환 성공 직후 같은 클릭 흐름에서 완료 파이프라인까지 이어 호출하고, 성공 결과를
 *    [CorrectionUiState.completionResult] 에 보관하면서 [CorrectionUiState.Phase.Done] 으로 전환한다.
 *    완료 in-flight 윈도우([CorrectionUiState.isCompleting]) 와 [completionJob] 으로 중복 호출을 막는다.
 *    ⚠️ 본 단계에서는 [CompleteCorrectionUseCase] 입력의 `langStateUpdateInput` 필드가 필수라
 *    SYS-CORRECTION-INFRA 영역 보정이 선행되어야 실제 호출이 가능하다. 현재 [stubCompletion] 으로
 *    완료 결과만 흉내내며, 진짜 호출로 교체하는 PR 분리 계획은 `docs/handover/COR-006-A_LANGSTATE_INPUT_HANDOVER.md`
 *    에 인계되어 있다.
 *
 * 비범위:
 *  - Empty / Retry / 선택 언어 변경 재트리거 → COR-002-B.
 *  - 결과 카드 본격 UI → COR-003-A.
 *  - "전체 선택" 토글 → 후속 UI 백로그.
 *  - 완료 실패 → Retry 상태 유지 → COR-006-B.
 *  - 로컬 완료 성공 후 Dashboard 복귀 navigation, 1회성 이벤트 처리 → COR-007-A.
 *  - sync / compression pending 비차단 처리 → COR-007-B.
 */
@HiltViewModel
class CorrectionViewModel @Inject constructor(
    // 화면 진입 직후 1회 LearningState 적재. 네트워크 실패해도 NotAvailable 로 fallback 되므로 throw 하지 않는다.
    private val preloadLearningState: PreloadLearningStateUseCase,
    // GlobalLangState 변경을 Flow 로 구독. Ready / NotAvailable 분기의 단일 입력원.
    private val observeLearningState: ObserveLearningStateUseCase,
    // RT-003 read model. 현재 세션의 user turn 목록을 Flow 첫 emit 으로 가져온다.
    private val getCorrectionContext: GetCorrectionContextUseCase,
    // 세션 turn 목록 → CorrectionCandidate 목록. 빈 결과면 generateSuggestions 가 early-return.
    private val extractSessionCandidates: ExtractSessionCandidatesUseCase,
    // 후보 + LangState → AI 호출 → CorrectionSuggestion 목록. Result 로 success/failure 가 갈린다.
    private val generateSuggestions: GenerateSuggestionsUseCase,
    // COR-005-A: Flashcard 저장 요청의 uid 출처. Room 저장이 uid+cardId 복합키라 화면에서 누락되면 안 된다.
    private val getCurrentUserUid: GetCurrentUserUidUseCase,
    // COR-005-A: 선택된 CorrectionSuggestion 목록을 SYS-CORRECTION-INFRA 저장 계약으로 변환한다.
    private val prepareSaveRequest: PrepareSaveRequestUseCase,
    // COR-006-A: 완료 파이프라인 진입점. SYS-CORRECTION-INFRA 보정 전까지는 stubCompletion 으로 흉내내고
    // 실제 호출은 인계 PR 에서 끼운다. 주입을 미리 받아 두는 이유는 인프라 보정 PR 에서 ViewModel 시그니처가
    // 다시 흔들리지 않도록 하기 위함이다. handover 문서: docs/handover/COR-006-A_LANGSTATE_INPUT_HANDOVER.md
    @Suppress("UnusedPrivateProperty")
    private val completeCorrection: CompleteCorrectionUseCase,
) : ViewModel() {

    // 화면이 collect 하는 단일 진실. ViewModel 내부에서만 쓰기 가능.
    private val _uiState = MutableStateFlow(CorrectionUiState())
    // 외부(Composable)로 노출되는 read-only StateFlow. _uiState 를 그대로 비춘다.
    val uiState: StateFlow<CorrectionUiState> = _uiState.asStateFlow()

    // ensureObservation() 의 collect coroutine 핸들. 이미 active 면 재구독을 막아 중복 collect 를 방지한다.
    private var enterJob: Job? = null

    // COR-006-A: 완료 파이프라인 호출의 coroutine 핸들. isCompleting 플래그와 함께 이중으로 중복 호출을 막는다.
    // 플래그(state) 가 ViewModel 외부 회귀의 SSOT 이고, 이 Job 은 ViewModel 내부에서 같은 호출 스택 두 번
    // 진입을 더 빠르게 끊기 위한 보조 가드다.
    private var completionJob: Job? = null

    /**
     * Ready 진입 시 generateSuggestions 트리거를 한 번만 실행하기 위한 가드.
     *
     * 1-1.COR-001-A 범위에서는 한 번 생성이 시작된 뒤 GlobalLangState 가 다시 Ready 를 흘려보내도 무시한다.
     * 선택 언어 변경 / Retry 정책은 4-1.COR-001-B 에서 이 가드를 푸는 방향으로 확장한다.
     */
    private var generationLaunched = false

    fun onEnter() {
        ensureObservation()
    }

    private fun ensureObservation() {
        if (enterJob?.isActive == true) {
            Log.d(TAG, "ensureObservation() skipped — already collecting")
            return
        }
        enterJob = viewModelScope.launch {
            Log.d(TAG, "ensureObservation() — COR-001-A preload start")

            preloadLearningState().exceptionOrNull()?.let { e ->
                Log.w(TAG, "preload failed — falling back to NotAvailable", e)
            }

            observeLearningState().collect { global ->
                // -A 가드: generate 가 시작된 뒤에는 LangState refresh 로 Ready 가 다시 떨어져도
                // Generating/Content/Error 를 덮어쓰지 않는다. Retry 정책은 -B 에서 다룬다.
                if (generationLaunched) {
                    return@collect
                }

                val next = global.toCorrectionUiState()
                val reason = global.notAvailableReason()
                if (reason != null) {
                    Log.d(TAG, "NotAvailable — $reason")
                } else {
                    Log.d(
                        TAG,
                        "Ready — lang=${next.selectedLearningLanguage}, " +
                                "recentTopic=${next.sessionSummary?.recentTopic}, " +
                                "langState.updatedAt=${next.langStateSnapshot?.updatedAt}"
                    )
                }
                _uiState.value = next

                if (next.phase == CorrectionUiState.Phase.Ready) {
                    generationLaunched = true
                    triggerGeneration(next)
                }
            }
        }
    }

    /**
     * Ready 게이트 통과 직후의 자동 교정 흐름.
     *
     * 1) RT-003 correction context 조회 (Flow 첫 emit).
     * 2) ExtractSessionCandidatesUseCase 로 후보 추출.
     * 3) GenerateSuggestionsInput 구성 → GenerateSuggestionsUseCase.
     * 4) success → Content, failure → Error.
     */
    private fun triggerGeneration(ready: CorrectionUiState) {
        val lang = ready.selectedLearningLanguage ?: return
        val langState = ready.langStateSnapshot ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                phase = CorrectionUiState.Phase.Generating,
                errorReason = null,
            )

            val result = runCatching {
                // RT-003 read model 은 Flow 라 추가 emit 이 흘러도 -A 범위에서는 첫 snapshot 만 본다.
                val sessionTurns = getCorrectionContext(lang).first()
                val candidates = extractSessionCandidates(
                    selectedLang = lang,
                    sessionLang = lang,
                    sessionTurns = sessionTurns,
                )
                Log.d(TAG, "Generating — candidates=${candidates.size}")

                val input = GenerateSuggestionsInput(
                    candidates = candidates,
                    langState = langState,
                )
                generateSuggestions(input).getOrThrow()
            }

            result.fold(
                onSuccess = { suggestions ->
                    Log.d(TAG, "Content — suggestions=${suggestions.size}")
                    _uiState.value = _uiState.value.copy(
                        phase = CorrectionUiState.Phase.Content,
                        suggestions = suggestions,
                        // 새 suggestions 로 교체되는 시점에 stale 한 selectedSuggestionIds 가 남아 있으면
                        // 새 목록에 존재하지 않는 id 가 canSave 를 거짓 양성으로 띄울 수 있어 함께 비운다.
                        selectedSuggestionIds = emptySet(),
                        errorReason = null,
                        // 직전 저장 시도가 만들어둔 saveRequest 도 새 목록 기준에서는 stale 이므로 함께 비운다.
                        saveRequest = null,
                        saveErrorReason = null,
                    )
                },
                onFailure = { e ->
                    val reason = e.message ?: e.javaClass.simpleName
                    Log.w(TAG, "Error — reason=$reason", e)
                    _uiState.value = _uiState.value.copy(
                        phase = CorrectionUiState.Phase.Error,
                        suggestions = emptyList(),
                        // 에러 진입 시점에도 동일하게 비워 다음 Content 진입의 출발점을 깔끔하게 둔다.
                        selectedSuggestionIds = emptySet(),
                        errorReason = reason,
                        // suggestions 가 사라진 상태에서 직전 saveRequest 만 살아 있으면 COR-006 호출 근거가 흔들린다.
                        saveRequest = null,
                        saveErrorReason = null,
                    )
                }
            )
        }
    }

    /**
     * 카드 한 장의 선택 상태를 뒤집는다.
     *
     * AC:
     *  - 같은 id 를 다시 누르면 선택 해제 → Set 의 `-` 연산.
     *  - 선택 항목은 `CorrectionSuggestion.id` 기준으로 관리.
     *  - 같은 카드를 빠르게 여러 번 누르는 엣지케이스는 Set 의 add/remove 가 멱등성과 무관하게
     *    "현재 상태 기준 토글" 시그니처라 매 호출이 결정적이다.
     */
    fun toggleSuggestionSelection(id: String) {
        _uiState.update { current ->
            val next = if (id in current.selectedSuggestionIds) {
                current.selectedSuggestionIds - id
            } else {
                current.selectedSuggestionIds + id
            }
            current.copy(selectedSuggestionIds = next)
        }
    }

    /**
     * 저장 버튼 진입점.
     *
     * COR-005-A 범위:
     *  - 선택된 카드 → [com.example.umma.domain.model.correction.CorrectionSaveRequest] 변환만 책임진다.
     *  - 결과(또는 변환 실패 사유)는 [CorrectionUiState.saveRequest] / [CorrectionUiState.saveErrorReason]
     *    필드에 보관해 COR-006 이 집어가도록 한다.
     *
     * COR-006-A 범위 (이번 단계 추가):
     *  - 변환이 [SaveRequestOutcome.Prepared] 로 끝나면 같은 클릭 흐름에서 [launchCompletion] 으로 이어가
     *    완료 파이프라인까지 호출한다. ViewModel 외부에서 보면 "저장" 버튼 한 번 누름이 변환 + 완료를
     *    직렬로 끝낸다.
     *
     * 분기 결정은 [computeSaveRequestOutcome] 가 [SaveRequestOutcome] 으로 돌려주고,
     * ViewModel 은 그 결과를 logging / state 갱신 두 가지로만 적용한다. 모든 분기 가드는
     * pure function 쪽 회귀 테스트(`CorrectionSaveRequestOutcomeTest`)에서 검증된다.
     *
     * 비범위 (COR-006-B / COR-007):
     *  - 완료 실패 → Retry 상태 유지 (COR-006-B).
     *  - Done 진입 후 Dashboard 복귀 navigation (COR-007-A).
     */
    fun onSaveClicked() {
        // 가드 판단용 snapshot 은 _uiState 갱신 이전 값으로 잡는다.
        // 첫 호출은 isSavePreparing == false 인 snapshot 으로 compute 가드를 통과하고,
        // 같은 함수가 두 번 동시에 들어오는 경우 두 번째 호출은 첫 호출이 emit 해둔
        // isSavePreparing == true 를 새 snapshot 으로 읽어 AlreadyInFlight 분기로 막힌다.
        // (현재 onSaveClicked 는 동기 흐름이라 사실상 같은 콜스택 두 번 진입이 불가능하지만,
        // COR-006-A 의 suspend 한 완료 호출이 합류하면서 이 가드가 실제 race 차단의 핵심이 된다.)
        val snapshot = _uiState.value
        _uiState.update { it.copy(isSavePreparing = true) }
        val outcome = snapshot.computeSaveRequestOutcome(
            uid = getCurrentUserUid.getCurrentUserUid(),
            prepare = { uid, selected ->
                prepareSaveRequest(uid = uid, selectedSuggestions = selected)
            },
        )
        logSaveOutcome(snapshot, outcome)
        // applySaveRequestOutcome 가 AlreadyInFlight 외 모든 분기에서 in-flight 윈도우를 닫아준다.
        _uiState.update { current -> current.applySaveRequestOutcome(outcome) }

        // COR-006-A: Prepared 분기에서 즉시 완료 파이프라인으로 이어 호출.
        // 변환 윈도우를 먼저 닫은 뒤 완료 윈도우를 여는 이유는 canSave 가 두 플래그를 모두 가드하기 때문에
        // 사용자 입장에서 윈도우가 끊기지 않는다는 점이고, 분기별로 어느 단계에서 in-flight 였는지 진단 가능하게
        // 의미를 분리해 둔 것이다.
        if (outcome is SaveRequestOutcome.Prepared) {
            launchCompletion(outcome.request)
        }
    }

    /**
     * 완료 파이프라인 호출 진입점.
     *
     * [onSaveClicked] 의 Prepared 분기 또는 COR-006-B 가 채울 Retry 액션이 호출한다.
     * 분기 결정은 [computeCompletionLaunch] 가 [CompletionLaunchOutcome] 으로 돌려주고, 본 함수는
     * (a) state 갱신, (b) viewModelScope.launch 진입, (c) [stubCompletion] 결과 적용 세 가지만 책임진다.
     *
     * ⚠️ 본 단계에서는 SYS-CORRECTION-INFRA 의 `langStateUpdateInput` 필수 필드 충돌로 실제
     * [CompleteCorrectionUseCase] 호출이 막혀 있어 [stubCompletion] 으로 성공 결과만 흉내낸다.
     * 인계 문서: `docs/handover/COR-006-A_LANGSTATE_INPUT_HANDOVER.md`.
     */
    private fun launchCompletion(request: CorrectionSaveRequest) {
        val snapshot = _uiState.value
        val launch = snapshot.computeCompletionLaunch(request)
        logCompletionLaunch(launch)
        if (launch !is CompletionLaunchOutcome.Launched) return

        // 완료 in-flight 윈도우를 먼저 열어 두 번째 클릭이 canSave 가드와 computeCompletionLaunch
        // 두 곳에서 모두 차단되게 한다.
        _uiState.update { current -> current.openCompletionWindow() }

        completionJob = viewModelScope.launch {
            // TODO(SCI-001): SYS-CORRECTION-INFRA 의 CompleteCorrectionInput.langStateUpdateInput 필드가
            //  옵셔널화 / UseCase 내부 조립 / 헬퍼 UseCase 신설 중 하나로 보정되는 즉시, 아래 stubCompletion 을
            //  실제 completeCorrection(input) 호출로 교체한다. 입력 조립 책임은 본 PR 의 범위가 아니므로
            //  ViewModel 은 saveRequest 만 그대로 넘기는 형태가 되어야 한다. 자세한 결정 옵션은
            //  docs/handover/COR-006-A_LANGSTATE_INPUT_HANDOVER.md 참조.
            val result = stubCompletion(launch.saveRequest)
            logCompletionResult(result)
            _uiState.update { current -> current.applyCompletionOutcome(result) }
        }
    }

    /**
     * SYS-CORRECTION-INFRA 보정 전까지 사용하는 임시 완료 결과 생성기.
     *
     * 실제 [CompleteCorrectionUseCase] 호출이 막혀 있어, 변환된 [CorrectionSaveRequest] 의 suggestionId 들을
     * 그대로 `savedFlashcardIds` 로 흉내내 화면 흐름(Phase.Done / completionResult.savedFlashcardIds.size) 을
     * 끝까지 검증할 수 있게 한다. Firestore sync / Session compression / Statistics 등 후속 상태는 모두 기본값.
     *
     * 부팀장이 인프라 보정 후 [launchCompletion] 의 TODO 자리만 실제 호출로 교체하면 본 함수는 제거된다.
     */
    private fun stubCompletion(request: CorrectionSaveRequest): Result<CompleteCorrectionResult> =
        Result.success(
            CompleteCorrectionResult(
                savedFlashcardIds = request.flashcards.map { it.suggestionId },
                pendingSyncFlashcardIds = emptyList(),
                sessionMemoryKey = "",
                completedAt = request.requestedAt,
            )
        )

    /**
     * onSaveClicked 의 분기별 진단 로그.
     *
     * UiState 에 사유를 담지 않는 분기(NotSavable / NoMatchingSuggestions) 도 logcat 에는 남겨야
     * 사용자 화면에서 "버튼을 눌렀는데 아무 일도 안 일어남" 케이스를 디버깅할 수 있다.
     */
    private fun logSaveOutcome(snapshot: CorrectionUiState, outcome: SaveRequestOutcome) {
        when (outcome) {
            SaveRequestOutcome.NotSavable -> Log.d(
                TAG,
                "onSaveClicked — guard: !canSave (phase=${snapshot.phase}, selected=${snapshot.selectedSuggestionIds.size})",
            )
            SaveRequestOutcome.AlreadyInFlight -> Log.d(
                TAG,
                "onSaveClicked — guard: already preparing, ignoring duplicate click",
            )
            SaveRequestOutcome.NoMatchingSuggestions -> Log.d(
                TAG,
                "onSaveClicked — guard: no matching suggestions for selected ids",
            )
            is SaveRequestOutcome.UidUnavailable -> Log.w(
                TAG,
                "onSaveClicked — uid unavailable, blocking save request",
            )
            is SaveRequestOutcome.Prepared -> Log.d(
                TAG,
                "onSaveClicked — saveRequest 준비 — flashcards=${outcome.request.flashcards.size}",
            )
            is SaveRequestOutcome.Failed -> Log.w(
                TAG,
                "onSaveClicked — 변환 실패 — reason=${outcome.reason}",
            )
        }
    }

    /**
     * COR-006-A: 완료 파이프라인 트리거 분기 진단 로그.
     *
     * AlreadyInFlight / NoSaveRequest 분기는 사용자 입장에서 "버튼을 한 번 더 눌렀는데 아무 일도 안 일어남"
     * 으로 보이므로 logcat 단서가 필요하다.
     */
    private fun logCompletionLaunch(outcome: CompletionLaunchOutcome) {
        when (outcome) {
            CompletionLaunchOutcome.AlreadyInFlight -> Log.d(
                TAG,
                "launchCompletion — guard: already completing, ignoring duplicate trigger",
            )
            CompletionLaunchOutcome.NoSaveRequest -> Log.d(
                TAG,
                "launchCompletion — guard: saveRequest is null, nothing to complete",
            )
            is CompletionLaunchOutcome.Launched -> Log.d(
                TAG,
                "launchCompletion — flashcards=${outcome.saveRequest.flashcards.size}",
            )
        }
    }

    /**
     * COR-006-A: 완료 파이프라인 결과 진단 로그.
     *
     * 본 단계에서는 [stubCompletion] 이 항상 success 를 돌려주지만, 인프라 보정 후 실제 호출에서는
     * failure 분기가 의미를 가지므로 미리 두 갈래로 나눠 둔다.
     */
    private fun logCompletionResult(result: Result<CompleteCorrectionResult>) {
        result.fold(
            onSuccess = { value ->
                Log.d(
                    TAG,
                    "completion success — savedFlashcardIds=${value.savedFlashcardIds.size}, " +
                            "pendingSync=${value.pendingSyncFlashcardIds.size}",
                )
            },
            onFailure = { e ->
                Log.w(TAG, "completion failure — reason=${e.message ?: e.javaClass.simpleName}", e)
            },
        )
    }

    private companion object {
        const val TAG = "CorrectionViewModel"
    }
}
