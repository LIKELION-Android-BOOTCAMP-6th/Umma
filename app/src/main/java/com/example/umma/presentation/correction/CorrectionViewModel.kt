package com.example.umma.presentation.correction

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.umma.domain.model.correction.GenerateSuggestionsInput
import com.example.umma.domain.usecase.auth.GetCurrentUserUidUseCase
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
 *    실제 CompleteCorrectionUseCase 호출과 Done/Retry 상태 전이는 COR-006 에서 이 필드를 읽어 진행한다.
 *
 * 비범위:
 *  - Empty / Retry / 선택 언어 변경 재트리거 → COR-002-B.
 *  - 결과 카드 본격 UI → COR-003-A.
 *  - "전체 선택" 토글 → 후속 UI 백로그.
 *  - saveRequest 를 받아 CompleteCorrectionUseCase 호출 및 Done/Retry 전이 → COR-006.
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
) : ViewModel() {

    // 화면이 collect 하는 단일 진실. ViewModel 내부에서만 쓰기 가능.
    private val _uiState = MutableStateFlow(CorrectionUiState())
    // 외부(Composable)로 노출되는 read-only StateFlow. _uiState 를 그대로 비춘다.
    val uiState: StateFlow<CorrectionUiState> = _uiState.asStateFlow()

    // ensureObservation() 의 collect coroutine 핸들. 이미 active 면 재구독을 막아 중복 collect 를 방지한다.
    private var enterJob: Job? = null

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
     * 분기 결정은 [computeSaveRequestOutcome] 가 [SaveRequestOutcome] 으로 돌려주고,
     * ViewModel 은 그 결과를 logging / state 갱신 두 가지로만 적용한다. 모든 분기 가드는
     * pure function 쪽 회귀 테스트(`CorrectionSaveRequestOutcomeTest`)에서 검증된다.
     *
     * 비범위 (COR-006):
     *  - CompleteCorrectionUseCase 호출, Done/Retry 전이, 중복 클릭 방지 UI 가드.
     */
    fun onSaveClicked() {
        val snapshot = _uiState.value
        val outcome = snapshot.computeSaveRequestOutcome(
            uid = getCurrentUserUid.getCurrentUserUid(),
            prepare = { uid, selected ->
                prepareSaveRequest(uid = uid, selectedSuggestions = selected)
            },
        )
        logSaveOutcome(snapshot, outcome)
        _uiState.update { current -> current.applySaveRequestOutcome(outcome) }
    }

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

    private companion object {
        const val TAG = "CorrectionViewModel"
    }
}
