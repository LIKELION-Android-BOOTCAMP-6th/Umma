package com.example.umma.presentation.correction

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.umma.domain.model.correction.CompleteCorrectionInput
import com.example.umma.domain.model.correction.CompleteCorrectionResult
import com.example.umma.domain.model.correction.CorrectionSaveRequest
import com.example.umma.domain.model.correction.GenerateSuggestionsInput
import com.example.umma.domain.usecase.auth.GetCurrentUserUidUseCase
import com.example.umma.domain.usecase.correction.CompleteCorrectionUseCase
import com.example.umma.domain.usecase.correction.ExtractSessionCandidatesUseCase
import com.example.umma.domain.usecase.correction.GenerateSuggestionsUseCase
import com.example.umma.domain.usecase.correction.PrepareSaveRequestUseCase
import com.example.umma.domain.usecase.learningstate.BuildLangStateUpdateInputCommand
import com.example.umma.domain.usecase.learningstate.BuildLangStateUpdateInputUseCase
import com.example.umma.domain.usecase.learningstate.ObserveLearningStateUseCase
import com.example.umma.domain.usecase.learningstate.PreloadLearningStateUseCase
import com.example.umma.domain.usecase.realtime.GetCorrectionContextUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
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
 *    [BuildLangStateUpdateInputUseCase] 로 LangStateUpdateInput 을 조립한 뒤 [CompleteCorrectionUseCase]
 *    에 전달하는 실제 호출이 연결되어 있다. 인계 문서: `docs/handover/LS-008_LANGSTATE_INPUT_READY.md`.
 *  - (COR-007-A) 완료 파이프라인 성공 직후 [events] 채널로 [CorrectionEvent.NavigateToDashboard] 를
 *    정확히 한 번 방출한다. State(`Phase.Done`) 결정 책임은 [applyCompletionOutcome] 에 그대로 두고,
 *    1회성 navigation 신호만 Channel 로 분리해 회전/recomposition/재진입에 의한 재발화를 막는다.
 *
 * 비범위:
 *  - Empty / Retry / 선택 언어 변경 재트리거 → COR-002-B.
 *  - 결과 카드 본격 UI → COR-003-A.
 *  - "전체 선택" 토글 → 후속 UI 백로그.
 *  - 완료 실패 → Retry 상태 유지 → COR-006-B.
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
    // COR-006-A: 완료 파이프라인 진입점. LS-008 에서 제공된 BuildLangStateUpdateInputUseCase 로 입력을
    // 조립한 뒤 실제 호출한다. 인계 문서: docs/handover/LS-008_LANGSTATE_INPUT_READY.md
    private val completeCorrection: CompleteCorrectionUseCase,
    // COR-006-A: LS-008 조립 UseCase. caller 확보 domain 값 → LangStateUpdateInput 변환 정책 고정.
    // @Inject constructor() 라 Hilt 모듈 추가 없이 자동 주입된다.
    private val buildLangStateUpdateInput: BuildLangStateUpdateInputUseCase,
) : ViewModel() {

    // 화면이 collect 하는 단일 진실. ViewModel 내부에서만 쓰기 가능.
    private val _uiState = MutableStateFlow(CorrectionUiState())
    // 외부(Composable)로 노출되는 read-only StateFlow. _uiState 를 그대로 비춘다.
    val uiState: StateFlow<CorrectionUiState> = _uiState.asStateFlow()

    // COR-007-A: 1회성 navigation/effect 전달 채널. StateFlow 와 분리한 이유는 navigation 같이
    // "정확히 한 번만 일어나야 하는 effect" 가 recomposition 마다 상태로 재소비되면 중복 이동이
    // 발생하기 때문이다. Channel 의 element 는 단일 collector 에 한 번만 전달되므로 회전/재진입
    // 사이에 동일 이벤트가 두 번 발화되지 않는다. BUFFERED — Compose 측 collect 시점과 emit 시점이
    // 어긋날 가능성을 흡수한다(Done 직후 화면이 활성이라 보통은 즉시 소비됨).
    private val _events = Channel<CorrectionEvent>(Channel.BUFFERED)
    val events: Flow<CorrectionEvent> = _events.receiveAsFlow()

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
     * (a) state 갱신, (b) viewModelScope.launch 진입, (c) 두 UseCase 직렬 호출 결과 적용 세 가지만 책임진다.
     *
     * ViewModel 이 [BuildLangStateUpdateInputCommand] 조립을 직접 수행하는 이유: LS-008 설계에서
     * [BuildLangStateUpdateInputUseCase] 는 caller 가 이미 확보한 domain 값만 받고, uid 와 RT-003
     * context 의 출처가 ViewModel 의존성이라 화면 레이어가 가장 가까운 caller 이기 때문이다.
     * 인계 문서: `docs/handover/LS-008_LANGSTATE_INPUT_READY.md`.
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
            // 1. uid — sessionMemoryKey / analysisEventId 의 최상위 키. 없으면 이후 조립 전체가 무의미하다.
            val uid = getCurrentUserUid.getCurrentUserUid()?.takeIf { it.isNotBlank() }
            if (uid == null) {
                val err = Result.failure<CompleteCorrectionResult>(
                    IllegalStateException("uid unavailable at completion")
                )
                logCompletionResult(err)
                _uiState.update { current -> current.applyCompletionOutcome(err) }
                return@launch
            }

            // 2. 선택 카드 — Command 의 stableEventParts(analysisEventId fingerprint 재료) 이자
            //    CompleteCorrectionInput 의 selectedSuggestions 이다. snapshot 은 launch 진입 시점에 고정한다.
            val stateSnapshot = _uiState.value
            val lang = launch.saveRequest.lang
            val selectedSuggestions = stateSnapshot.suggestions.filter { it.id in stateSnapshot.selectedSuggestionIds }

            // 3. RT-003 correction context — BuildLangStateUpdateInputCommand 의 correctionContextTurns 출처.
            //    Flow 의 첫 emit 만 사용하며, 조회 실패는 완료 실패로 처리한다.
            val contextTurns = runCatching { getCorrectionContext(lang).first() }.getOrElse { e ->
                val err = Result.failure<CompleteCorrectionResult>(e)
                logCompletionResult(err)
                _uiState.update { current -> current.applyCompletionOutcome(err) }
                return@launch
            }

            // 4. LangStateUpdateInput 조립 — LS-008 정책에 따라 sessionMemoryKey / analysisEventId /
            //    recentUserTurns 를 결정한다. 조립 실패는 완료 실패로 처리한다(Done 으로 보내지 않음).
            val command = BuildLangStateUpdateInputCommand(
                uid = uid,
                lang = lang,
                selectedLang = stateSnapshot.selectedLearningLanguage ?: lang,
                currentState = stateSnapshot.langStateSnapshot,
                correctionContextTurns = contextTurns,
                stableEventParts = selectedSuggestions.map { it.id },
                analyzedAt = launch.saveRequest.requestedAt,
                correctionResult = null,
                correctionAvailableOverride = false,
            )
            val langStateInput = buildLangStateUpdateInput(command).getOrElse { e ->
                val err = Result.failure<CompleteCorrectionResult>(e)
                logCompletionResult(err)
                _uiState.update { current -> current.applyCompletionOutcome(err) }
                return@launch
            }

            // 5. 완료 파이프라인 호출 — Flashcard 저장 + LangState 갱신 + Statistics + Session compression.
            val result = completeCorrection(
                CompleteCorrectionInput(
                    selectedSuggestions = selectedSuggestions,
                    langStateUpdateInput = langStateInput,
                    requestedAt = launch.saveRequest.requestedAt,
                )
            )
            logCompletionResult(result)
            _uiState.update { current -> current.applyCompletionOutcome(result) }

            // COR-007-A: 완료 성공 경로에서만 Dashboard 복귀 1회성 이벤트를 발화한다.
            // 1~4단계 실패와 5단계 호출 실패 분기는 각자 위에서 applyCompletionOutcome(err) + return@launch
            // 로 이미 빠져 나갔으므로, 여기 도달 자체가 "Phase.Done 으로 전환되었다" 의 동의어다.
            // Channel 이라 회전/recomposition 으로 collector 가 재구성되어도 동일 이벤트가 두 번 전달되지 않는다.
            if (result.isSuccess) {
                _events.send(CorrectionEvent.NavigateToDashboard)
            }
        }
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
     * uid 미확보 / RT-003 context 조회 실패 / [BuildLangStateUpdateInputUseCase] 조립 실패 /
     * [CompleteCorrectionUseCase] 자체 실패 가 모두 failure 로 흘러온다.
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
