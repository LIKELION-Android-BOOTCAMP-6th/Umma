package com.app.umma.presentation.correction

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.umma.BuildConfig
import com.app.umma.devtools.correctionpromptreview.CorrectionPromptReviewContextTurn
import com.app.umma.devtools.correctionpromptreview.CorrectionPromptReviewSnapshot
import com.app.umma.devtools.correctionpromptreview.CorrectionPromptReviewSuggestion
import com.app.umma.devtools.correctionpromptreview.ReportCorrectionPromptReviewUseCase
import com.app.umma.domain.model.correction.CompleteCorrectionInput
import com.app.umma.domain.model.correction.CompleteCorrectionResult
import com.app.umma.domain.model.correction.CorrectionContextTurn
import com.app.umma.domain.model.correction.CorrectionSaveRequest
import com.app.umma.domain.model.correction.CorrectionSessionContext
import com.app.umma.domain.model.correction.GenerateSuggestionsInput
import com.app.umma.domain.model.flashcard.Flashcard
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.realtime.SessionTurn
import com.app.umma.domain.usecase.auth.GetCurrentUserUidUseCase
import com.app.umma.domain.usecase.correction.CompleteCorrectionUseCase
import com.app.umma.domain.usecase.correction.ExtractSessionCandidatesUseCase
import com.app.umma.domain.usecase.correction.FilterCorrectionCandidatesForSafetyUseCase
import com.app.umma.domain.usecase.correction.GenerateSuggestionsUseCase
import com.app.umma.domain.usecase.correction.PrepareSaveRequestUseCase
import com.app.umma.domain.usecase.flashcardreview.GetFlashcardsUseCase
import com.app.umma.domain.usecase.learningstate.BuildLangStateUpdateInputCommand
import com.app.umma.domain.usecase.learningstate.BuildLangStateUpdateInputUseCase
import com.app.umma.domain.usecase.learningstate.BuildLearnerAdaptationProfileUseCase
import com.app.umma.domain.usecase.learningstate.ObserveLearningStateUseCase
import com.app.umma.domain.usecase.learningstate.PreloadLearningStateUseCase
import com.app.umma.domain.usecase.realtime.GetCorrectionContextUseCase
import com.app.umma.domain.usecase.realtime.GetSessionMemoryUseCase
import com.app.umma.presentation.correction.CorrectionViewModel.Companion.STEP_MIN_DURATION_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
 *  - (COR-001-A) [GlobalLangState] 구독 → Ready / Empty 분기. (구 `NotAvailable` 의 rename — COR-001-B 정합화 결과.)
 *  - (COR-001-B) 결손 4종(선택 언어 없음 / SessionSummary 없음 / LangState snapshot 없음 / correctionAvailable=false)
 *    을 [CorrectionUiState.Phase.Empty] 한 분기로 묶고, 중복 collect / 중복 generate 두 가드를
 *    각각 [enterJob] 활성 체크 + [com.app.umma.presentation.correction.shouldTriggerGeneration] pure helper 로
 *    보장한다. 화면 레이어가 Empty 상태에서 AI Chat 이동 CTA 를 노출하는 책임은 [CorrectionScreen] 이 진다.
 *  - (COR-002-A) Ready 첫 emit 시 사용자 추가 입력 없이 generateSuggestions 를 1회 자동 트리거.
 *    RT-003 correction context → 후보 추출 → AI 호출 → CorrectionSuggestion 목록 → Content / Error.
 *    로딩 단계 안내는 [triggerGeneration] 이 [CorrectionUiState.loadingStep] 으로 직접 구동하며(COR-UX-001),
 *    5단계 안내가 모두 보이도록 생성 결과 반영 전 단계별 최소 [STEP_MIN_DURATION_MS](3.5초) × 5 ≈ 17.5초
 *    로딩 시간을 보장한다(AI 호출이 길어지면 4단계가 그만큼 늘어난다).
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
 *  - (COR-006-B) [CompleteCorrectionUseCase] 가 로컬 완료 실패 결과를 돌려주면
 *    [CorrectionUiState.Phase.Retry] 로 전환된다. 카드 목록 / 선택 / saveRequest 는 그대로 유지되어
 *    사용자가 같은 저장 버튼을 다시 누르면 [onSaveClicked] → [launchCompletion] 흐름이 같은 입력으로
 *    재진입한다 — [PrepareSaveRequestUseCase] 가 deterministic 하게 같은 [CorrectionSaveRequest] 를
 *    재생성하므로 AC "Retry 시 같은 저장 요청으로 완료 파이프라인을 다시 호출" 이 자연스럽게 충족된다.
 *    실패 사유는 [CorrectionUiState.completionErrorReason] 에 보관되어 화면 배너로 노출된다.
 *    Firestore sync / compression / statistics 의 pending 만 발생한 경우는 [CompleteCorrectionUseCase]
 *    가 `Result.success` 로 흘려보내므로 Retry 가 아니라 Done 으로 이어진다 (회귀: COR-007-B pending 3건).
 *  - (COR-007-A) 완료 파이프라인 성공 직후 [events] 채널로 [CorrectionEvent.NavigateToDashboard] 를
 *    정확히 한 번 방출한다. State(`Phase.Done`) 결정 책임은 [applyCompletionOutcome] 에 그대로 두되,
 *    성공 직후 지연 없이 navigation 신호를 보내 별도 완료 안내 화면이 사용자에게 노출되지 않게 한다.
 *  - (COR-007-B) Firestore sync / Session compression / Statistics history 의 pending 상태는
 *    사용자 흐름을 막지 않는다. [CompleteCorrectionUseCase] 가 pending 케이스에서도 `Result.success`
 *    로 흘려보내므로 [applyCompletionOutcome] 의 success 분기 하나로 [Phase.Done] 진입과
 *    [CorrectionEvent.NavigateToDashboard] 발화가 동일하게 이뤄진다. pending 자체는 사용자에게
 *    어떤 UI 로도 노출하지 않고, [logCompletionResult] 진단 로그가 개발 확인의 SSOT 다.
 *    회귀는 `CorrectionUiStateTest` 의 pending 비차단 3건 + `CompleteCorrectionUseCaseTest` 의
 *    compression/statistics 실패 비롤백 테스트가 함께 보장한다.
 *
 *  - (COR-002-B) AI 응답 0건 → [CorrectionUiState.Phase.EmptyResult] 분기.
 *    AI 호출/파싱/필수 필드 누락 실패 → [CorrectionUiState.Phase.Error] + [onRetryClicked] 액션.
 *    [triggerGeneration] 결과 적용은 [com.app.umma.presentation.correction.applyGenerationOutcome] pure helper 로 위임.
 *    터미널 phase([CorrectionUiState.Phase.Empty]/[CorrectionUiState.Phase.EmptyResult]/[CorrectionUiState.Phase.Error])
 *    진입 시 [generationLaunched] 가드를 해제해, 다른 경로로 학습 언어가 바뀌면 새 Ready emit 에서 자동 재시도된다.
 *
 * 비범위:
 *  - 결과 카드 본격 UI → COR-003-A.
 *  - "전체 선택" 토글 → 후속 UI 백로그.
 */
@HiltViewModel
class CorrectionViewModel @Inject constructor(
    // 화면 진입 직후 1회 LearningState 적재. 네트워크 실패해도 Empty 로 fallback 되므로 throw 하지 않는다.
    // (COR-001-B: 구 표기 `NotAvailable` → `Empty` 로 rename. 의미는 동일.)
    private val preloadLearningState: PreloadLearningStateUseCase,
    // GlobalLangState 변경을 Flow 로 구독. Ready / Empty 분기의 단일 입력원.
    private val observeLearningState: ObserveLearningStateUseCase,
    // RT-003 read model. 현재 세션의 user turn 목록을 Flow 첫 emit 으로 가져온다.
    private val getCorrectionContext: GetCorrectionContextUseCase,
    // COR-TUNE-010: 의도 파악용 이전 세션 기억(topicSummaries/topicKeySentences/recentTopics) 1회성 조회.
    // 새 저장소 메서드를 만들지 않고 기존 SessionMemoryRepository.getSessionMemory(lang) 를 그대로 재사용한다.
    // 실패해도 빈 맥락으로 폴백하므로 throw 하지 않는다(완료 흐름을 막지 않음).
    private val getSessionMemory: GetSessionMemoryUseCase,
    // 세션 turn 목록 → CorrectionCandidate 목록. 빈 결과면 generateSuggestions 가 early-return.
    private val extractSessionCandidates: ExtractSessionCandidatesUseCase,
    private val filterCorrectionCandidatesForSafety: FilterCorrectionCandidatesForSafetyUseCase,
    // 후보 + LangState → AI 호출 → CorrectionSuggestion 목록. Result 로 success/failure 가 갈린다.
    private val generateSuggestions: GenerateSuggestionsUseCase,
    // COR-TUNE-01: langState snapshot → 교정 적응 정책(LearnerAdaptationProfile). raw metric 해석은 이 domain UseCase 가 전담하고
    // 프롬프트 빌더는 숫자를 모르게 한다. @Inject constructor() 라 Hilt 모듈 추가 없이 자동 주입된다.
    private val buildLearnerAdaptationProfile: BuildLearnerAdaptationProfileUseCase,
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
    // COR-UX-001: 로딩 화면 복습 영역에 노출할 현재 학습 언어의 로컬 Flashcard 조회 전용 재사용.
    // SRS 카드 목록(SrsCardListViewModel)과 동일한 진입점이며, 새 저장소 메서드를 신설하지 않는다
    // (읽기 전용 — 스케줄/평가/저장 등 SRS 책임은 일절 건드리지 않는다).
    private val getFlashcards: GetFlashcardsUseCase,
    private val reportCorrectionPromptReviewUseCase: ReportCorrectionPromptReviewUseCase,
) : ViewModel() {

    // 화면이 collect 하는 단일 진실. ViewModel 내부에서만 쓰기 가능.
    @Suppress("KotlinConstantConditions")
    private val shouldShowCorrectionReviewReportButton =
        BuildConfig.DEBUG &&
            BuildConfig.FLAVOR == "dev" &&
            BuildConfig.CORRECTION_PROMPT_REVIEW_ENABLED

    private val _uiState = MutableStateFlow(
        CorrectionUiState(showCorrectionReviewReportButton = shouldShowCorrectionReviewReportButton)
    )
    // 외부(Composable)로 노출되는 read-only StateFlow. _uiState 를 그대로 비춘다.
    val uiState: StateFlow<CorrectionUiState> = _uiState.asStateFlow()

    // COR-007-A: 1회성 navigation/effect 전달 채널. StateFlow 와 분리한 이유는 navigation 같이
    // "정확히 한 번만 일어나야 하는 effect" 가 recomposition 마다 상태로 재소비되면 중복 이동이
    // 발생하기 때문이다. Channel 의 element 는 단일 collector 에 한 번만 전달되므로 회전/재진입
    // 사이에 동일 이벤트가 두 번 발화되지 않는다. BUFFERED — Compose 측 collect 시점과 emit 시점이
    // 어긋날 가능성을 흡수한다(Done 직후 화면이 활성이라 보통은 즉시 소비됨).
    private val _events = Channel<CorrectionEvent>(Channel.BUFFERED)
    // 외부(Composable)가 collect 하는 read-only 이벤트 스트림. _events 를 receiveAsFlow 로 단방향 expose 해서
    // 1회성 navigation/effect 신호(NavigateToDashboard 등) 만 화면 레이어로 흘려보낸다.
    val events: Flow<CorrectionEvent> = _events.receiveAsFlow()

    // ensureObservation() 의 collect coroutine 핸들. 이미 active 면 재구독을 막아 중복 collect 를 방지한다.
    // (COR-001-B AC "중복 요청과 중복 초기화 방지" 의 1차 가드 — 화면 재진입으로 onEnter 가 다시 불려도
    //  같은 인스턴스에서는 새 collect 가 시작되지 않는다. ViewModel 인스턴스가 새로 만들어진 경우에는
    //  자연스럽게 새 collect 가 시작되며, 이는 의도된 동작.)
    private var enterJob: Job? = null

    /**
     * COR-002-B: 터미널 phase 집합.
     *
     * [triggerGeneration] 결과가 이 집합 중 하나로 전환되면 [generationLaunched] 를 false 로 리셋해,
     * 학습 언어가 바뀌어 [GlobalLangState][com.app.umma.domain.model.learningstate.GlobalLangState] 가
     * 새 Ready 를 emit 하면 자동으로 새 generate 가 다시 시작될 수 있게 한다.
     * 자동 재시도가 아닌 명시적 버튼 클릭([onRetryClicked]) 도 같은 흐름([triggerGeneration]) 으로 진입한다.
     *
     * COR-006-B: [CorrectionUiState.Phase.Retry] 는 의식적으로 본 집합에 포함시키지 않는다.
     * Retry 는 "완료 실패 후 사용자가 같은 입력으로 명시적 재시도를 기다리는 상태" 이므로 학습 언어 변경
     * 같은 외부 이벤트로 자동 generate 가 다시 일어나선 안 된다. 사용자가 저장 버튼을 다시 눌렀을 때만
     * [launchCompletion] 으로 재진입한다.
     */
    private val terminalPhases = setOf(
        CorrectionUiState.Phase.Empty,
        CorrectionUiState.Phase.EmptyResult,
        CorrectionUiState.Phase.Error,
    )

    // 플래그(state) 가 ViewModel 외부 회귀의 SSOT 이고, 이 Job 은 ViewModel 내부에서 같은 호출 스택 두 번
    // 진입을 더 빠르게 끊기 위한 보조 가드다.
    private var completionJob: Job? = null

    /**
     * Ready 진입 시 generateSuggestions 트리거를 한 번만 실행하기 위한 가드.
     *
     * COR-001-A/B 범위에서는 한 번 생성이 시작된 뒤 GlobalLangState 가 다시 Ready 를 흘려보내도 무시한다.
     *
     * COR-002-B: [terminalPhases] 중 하나로 진입하는 시점에 false 로 리셋한다. 이후 학습 언어 변경으로
     * GlobalLangState 가 새 Ready 를 emit 하면 자동으로 generate 가 다시 시작된다.
     * 분기 결정 자체는 [shouldTriggerGeneration] pure helper 가 가지고 있고, 본 플래그는
     * 그 helper 의 두 번째 인자(`alreadyLaunched`) 와 1:1 대응한다. ViewModel 본문은 helper 호출 결과만
     * 보고, 회귀 테스트는 helper 단위에서 phase × launched 조합을 모두 못 박는다.
     */
    private var generationLaunched = false

    fun onEnter() {
        ensureObservation()
    }

    /**
     * GlobalLangState 구독 셋업.
     *
     * COR-001-B 중복 방어 가드 (두 겹):
     *  1. [enterJob] 활성 체크 — 같은 ViewModel 인스턴스에서 [onEnter] 가 여러 번 호출되어도
     *     새 collect 가 시작되지 않는다. 화면 재진입 / recomposition 시 [CorrectionScreen]
     *     의 LaunchedEffect 가 다시 발화되어도 안전.
     *  2. [shouldTriggerGeneration] — Ready 가 emit 된 직후 generate 트리거 분기를 결정하는
     *     pure helper. 한 번 launched=true 가 되면 GlobalLangState refresh 로 Ready 가 다시
     *     흘러와도 false 를 돌려 두 번째 generate 를 막는다. 동시에 Empty / 진행 단계에서는
     *     phase 자체로 막혀 generate 가 시작되지 않는다.
     */
    private fun ensureObservation() {
        // 가드 1: 이미 collect 중이면 새 coroutine 을 띄우지 않는다. (COR-001-B AC "중복 초기화 방지")
        if (enterJob?.isActive == true) {
            Log.d(TAG, "ensureObservation() skipped — already collecting")
            return
        }
        enterJob = viewModelScope.launch {
            Log.d(TAG, "ensureObservation() — COR-001-A preload start")

            preloadLearningState().exceptionOrNull()?.let { e ->
                // 실패해도 NotAvailable(=Empty) 분기로 fallback 되어 화면 흐름은 막히지 않는다.
                Log.w(TAG, "preload failed — falling back to Empty", e)
            }

            observeLearningState().collect { global ->
                // -A 가드: generate 가 시작된 뒤에는 LangState refresh 로 Ready 가 다시 떨어져도
                // Generating/Content/Error 를 덮어쓰지 않는다. Retry 정책은 후속에서 다룬다.
                if (generationLaunched) {
                    return@collect
                }

                val next = global.toCorrectionUiState()
                // logcat 진단용 — UI 는 Empty 한 분기로 합쳐 보여주므로 사유 식별은 이 로그가 단일 SSOT.
                val reason = global.notAvailableReason()
                if (reason != null) {
                    Log.d(TAG, "Empty — $reason")
                } else {
                    Log.d(
                        TAG,
                        "Ready — lang=${next.selectedLearningLanguage}, " +
                                "recentTopic=${next.sessionSummary?.recentTopic}, " +
                                "langState.updatedAt=${next.langStateSnapshot?.updatedAt}"
                    )
                }
                val previous = _uiState.value
                _uiState.value = next.mergeReviewButtonState(
                    previous = previous,
                    shouldShowButton = shouldShowCorrectionReviewReportButton,
                )

                // 가드 2 (COR-001-B): generate 트리거 분기는 pure helper 가 결정한다.
                // helper 가 false 를 돌리는 모든 경우(Empty / 이미 launched / Generating 등) 가
                // CorrectionUiStateTest 의 shouldTriggerGeneration 표 회귀로 못 박혀 있다.
                if (shouldTriggerGeneration(next.phase, generationLaunched)) {
                    generationLaunched = true
                    triggerGeneration(next)
                }
            }
        }
    }

    /**
     * Ready 게이트 통과 직후의 자동 교정 흐름. [onRetryClicked] 도 같은 진입점을 사용한다.
     *
     * COR-UX-001: 5단계 로딩 안내를 실제 작업 흐름에 동기화한다 — 화면 자체 타이머가 아니라
     * 본 함수가 [CorrectionUiState.loadingStep] 을 직접 구동하고, 화면은 렌더링만 담당한다.
     *
     * 1) [runPipeline] 을 `async` 로 백그라운드 시작한다 — 진입 즉시 RT-003 context 조회 →
     *    candidate 추출 → 세션 맥락/적응 프로파일 조립 → AI 호출까지 한 번에 묶어 실행한다
     *    ("백그라운드 선행": 4단계를 기다리지 않고 1단계 진입 시점부터 미리 시작).
     * 2) 1~3단계(인덱스 0~2): 입력 수집 단계를 체감 안내하며 각 [STEP_MIN_DURATION_MS] 동안
     *    순차 노출한다. 실제로는 거의 동시에 끝나는 작업들이라 시간 기반 연출이지만, 위 파이프라인과
     *    겹쳐 돌기 때문에 전체 대기 시간이 늘어나지 않는다.
     * 3) 4단계(인덱스 3): [runPipeline] 결과를 `await` 한다 — 시간의 대부분을 차지하는 실제 AI
     *    호출이 끝날 때까지 유지되며(최소 [STEP_MIN_DURATION_MS] 보장), AI가 1~3단계 연출보다
     *    먼저 끝나도 문구가 스쳐 지나가지 않게 한다.
     * 4) 5단계(인덱스 4): 결과 매핑은 사실상 즉시 끝나지만, 마찬가지로 최소 [STEP_MIN_DURATION_MS]
     *    노출한 뒤 다음 단계로 넘어간다.
     * 5) [applyGenerationOutcome] pure helper 로 결과 적용:
     *    - success([])        → [CorrectionUiState.Phase.EmptyResult]
     *    - success(non-empty) → [CorrectionUiState.Phase.Content]
     *    - failure            → [CorrectionUiState.Phase.Error]
     * 6) 터미널 phase 진입 시 [generationLaunched] 가드 해제 (COR-002-B 자동 재시도 정책).
     *
     * [loadLoadingFlashcards] 가 로딩 화면 복습 영역의 카드 목록을 별도로 병렬 적재한다 —
     * 본 파이프라인과 독립적이며 실패해도 교정 흐름을 막지 않는다.
     */
    private fun triggerGeneration(ready: CorrectionUiState) {
        val lang = ready.selectedLearningLanguage ?: return
        val langState = ready.langStateSnapshot ?: return

        viewModelScope.launch {
            // Generating 전환 + 단계 인디케이터 초기화. COR-UX-001: loadingStep 의 단일 진실은
            // 본 함수이고, 화면(CorrectionLoading)은 이 값을 그대로 렌더링만 한다.
            _uiState.value = _uiState.value.copy(
                phase = CorrectionUiState.Phase.Generating,
                errorReason = null,
                loadingStep = 0,
            )

            // COR-UX-001: 로딩 화면 복습 카드 적재 — 본 파이프라인과 독립적으로 병렬 진행한다.
            loadLoadingFlashcards(lang)

            // COR-UX-001: 백그라운드 선행 — AI 호출을 포함한 실제 파이프라인을 진입 즉시 시작해
            // 1~3단계 연출과 겹쳐 돌린다. AI가 빠르면 전체 체감 대기도 그만큼 짧아진다(합의 사항).
            val pipeline = async {
                runPipeline(lang = lang, langState = langState, primaryLanguage = ready.primaryLanguage)
            }

            // 1~3단계: 입력 수집 단계의 체감 안내. 실제로는 거의 동시에 끝나는 작업들이라
            // 각 STEP_MIN_DURATION_MS 만큼 시간 기반으로 순차 노출한다.
            for (step in 0..2) {
                _uiState.update { it.copy(loadingStep = step) }
                delay(STEP_MIN_DURATION_MS)
            }

            // 4단계: 실제 AI 호출이 끝날 때까지 유지한다. pipeline 이 1~3단계 연출보다 먼저
            // 끝났더라도 문구가 스쳐 지나가지 않도록 최소 STEP_MIN_DURATION_MS 를 보장한다.
            _uiState.update { it.copy(loadingStep = 3) }
            val aiStartedAt = SystemClock.elapsedRealtime()
            val result = pipeline.await()
            val aiElapsedMs = SystemClock.elapsedRealtime() - aiStartedAt
            val aiRemainingMs = STEP_MIN_DURATION_MS - aiElapsedMs
            if (aiRemainingMs > 0) {
                delay(aiRemainingMs)
            }

            // 5단계: 결과 매핑은 사실상 즉시 끝나지만, 안내 문구가 최소한의 시간은 보이도록 한다.
            _uiState.update { it.copy(loadingStep = 4) }
            delay(STEP_MIN_DURATION_MS)

            // COR-002-B: 분기 결정(EmptyResult/Content/Error) 과 필드 정리는 pure helper 에 위임.
            result.exceptionOrNull()?.let { error ->
                Log.w(TAG, "generation failure — reason=${error.toDiagnosticReason()}", error)
            }
            _uiState.value = _uiState.value.applyGenerationOutcome(result)
            Log.d(
                TAG,
                "applyGenerationOutcome → phase=${_uiState.value.phase}, " +
                        "suggestions=${_uiState.value.suggestions.size}, " +
                        "errorReason=${_uiState.value.errorReason}",
            )

            // COR-002-B: 터미널 phase 진입 시 가드 해제 — 학습 언어 변경 후 새 Ready emit 에서 자동 재시도.
            if (_uiState.value.phase in terminalPhases) {
                generationLaunched = false
            }
        }
    }

    /**
     * COR-UX-001: 실제 교정 파이프라인 본문을 [triggerGeneration] 의 단계 타임라인에서 분리한다.
     *
     * RT-003 correction context 조회 → candidate 추출 → 세션 맥락/적응 프로파일 조립 → AI 호출까지
     * 묶어 한 [Result] 로 반환한다. [triggerGeneration] 이 본 함수를 `async` 로 백그라운드 실행해
     * 1~3단계 로딩 연출과 겹쳐 돌리고, 4단계에서 `await` 해 실제 AI 응답 시점에 안내를 동기화한다.
     *
     * 내용 자체는 기존 흐름과 동일하다 — RT-003 read model 첫 snapshot 조회, candidate 추출,
     * COR-TUNE-010 세션 맥락 조립, COR-TUNE-01 적응 프로파일 산출, AI 호출 순.
     */
    private suspend fun runPipeline(
        lang: LangCode,
        langState: LangState,
        primaryLanguage: LangCode?,
    ): Result<GenerationOutcomePayload> = runCatching {
        // RT-003 read model 은 Flow 라 추가 emit 이 흘러도 첫 snapshot 만 본다.
        val sessionTurns = getCorrectionContext(lang).first()
        Log.d(
            TAG,
            "correction context loaded lang=${lang.code}, turns=${sessionTurns.size}, turnIds=${sessionTurns.joinToString(separator = ",") { it.turnId }}"
        )
        val candidates = extractSessionCandidates(
            selectedLang = lang,
            sessionLang = lang,
            sessionTurns = sessionTurns,
        )
        val filteredCandidates = filterCorrectionCandidatesForSafety(candidates)
        Log.d(
            TAG,
            "correction candidates extracted lang=${lang.code}, candidates=${candidates.size}, blocked=${filteredCandidates.blockedCount}, candidateTurnIds=${candidates.mapNotNull { it.sourceTurnId }}"
        )
        if (filteredCandidates.allowedCandidates.isEmpty()) {
            return@runCatching GenerationOutcomePayload(
                suggestions = emptyList(),
                emptyReason = com.app.umma.domain.model.correction.CorrectionEmptyResultReason.SAFETY_BLOCKED
            )
        }

        // COR-TUNE-010: 의도 파악용 세션 맥락을 함께 확보한다.
        // - 이전 세션 기억(topicSummaries/topicKeySentences/recentTopics)은 getSessionMemory(lang) 로 읽는다
        //   (새 저장소 메서드 신설 금지 — 기존 SessionMemoryRepository.getSessionMemory 재사용).
        // - 현재 세션 흐름은 위에서 이미 가져온 sessionTurns 를 그대로 매핑해 candidate 1문장 +
        //   assistantContext 한마디보다 넓은 범위로 제공한다.
        // - 조회 실패는 빈 맥락으로 폴백한다. CorrectionPromptBuilder 가 빈 맥락이면 블록을 생략하므로
        //   기존 candidate 기반 교정 흐름이 그대로 유지되고 완료를 막지 않는다.
        val sessionContext = buildCorrectionSessionContext(lang, sessionTurns)

        // COR-TUNE-01: langState snapshot 을 교정 적응 정책으로 해석해 입력에 싣는다.
        // 근거 부족/null 이면 UseCase 가 보수적 profile 을 돌려주므로 프롬프트도 안전한 최소 교정으로 떨어진다.
        val profile = buildLearnerAdaptationProfile(langState)
        val input = GenerateSuggestionsInput(
            candidates = filteredCandidates.allowedCandidates,
            langState = langState,
            // primaryLanguage 가 없으면 한국어로 fallback — Chat 의 UNKNOWN→영어 fallback 패턴과 동일.
            primaryLang = primaryLanguage ?: LangCode.KO,
            profile = profile,
            sessionContext = sessionContext,
        )
        val suggestions = generateSuggestions(input).getOrThrow()
        GenerationOutcomePayload(
            suggestions = suggestions,
            emptyReason = if (suggestions.isEmpty()) {
                com.app.umma.domain.model.correction.CorrectionEmptyResultReason.NO_CORRECTION_NEEDED
            } else {
                null
            }
        )
    }

    /**
     * COR-UX-001: 로딩 화면 복습 영역에 노출할 플래시카드를 별도로 적재한다.
     *
     * 교정 파이프라인([runPipeline])과 독립적으로 동작한다 — 조회가 실패하거나 늦게 끝나도
     * [triggerGeneration] 의 단계 타임라인을 막지 않으며, 빈 상태로 남으면 화면이 안내 카드로
     * 폴백한다([CorrectionUiState.loadingFlashcards] 기본값이 emptyList).
     *
     * 오래된 카드부터 복습되도록 [Flashcard.createdAt] 오름차순으로 정렬한다 — 기존
     * [GetFlashcardsUseCase] 의 DAO 정렬은 newest-first(SrsCardListScreen 용)라 새 쿼리/정렬을
     * 추가하지 않고(신설 금지 원칙) 화면 책임으로 메모리에서 재정렬한다.
     */
    private fun loadLoadingFlashcards(lang: LangCode) {
        viewModelScope.launch {
            val uid = getCurrentUserUid.getCurrentUserUid()?.takeIf { it.isNotBlank() } ?: return@launch
            val cards = getFlashcards(uid, lang).getOrNull() ?: return@launch
            val loadingCards = cards
                .sortedBy { it.createdAt }
                .map { CorrectionLoadingCard(front = it.frontText, back = it.backText) }
            _uiState.update { it.copy(loadingFlashcards = loadingCards) }
        }
    }

    /**
     * COR-TUNE-010: 의도 파악용 [CorrectionSessionContext] 를 조립한다.
     *
     * - 이전 세션 기억은 [getSessionMemory] 로 1회성 조회한다(새 저장소 메서드 신설 금지 — AC 준수).
     * - 현재 세션 흐름은 같은 트리거에서 이미 가져온 [sessionTurns] (RT-003 read model) 를 그대로 매핑한다.
     *   별도 조회를 추가하면 같은 세션을 두 번 읽게 되므로, 이미 보유한 snapshot 을 재사용한다.
     * - 조회 실패/예외는 빈 [CorrectionSessionContext] 로 폴백한다. [CorrectionPromptBuilder] 가 빈 맥락이면
     *   "Conversation context / intent" 블록을 생략하므로 기존 candidate 기반 교정이 그대로 유지되고
     *   완료 흐름을 막지 않는다(예외 처리 정책: 세션 기억 조회 실패해도 교정은 계속한다).
     */
    private suspend fun buildCorrectionSessionContext(
        lang: LangCode,
        sessionTurns: List<SessionTurn>,
    ): CorrectionSessionContext {
        val currentSessionTurns = sessionTurns.map { turn ->
            CorrectionContextTurn(
                speaker = if (turn.role == TurnSpeaker.USER) "user" else "assistant",
                text = turn.text,
            )
        }

        return runCatching { getSessionMemory(lang).getOrThrow() }
            .fold(
                onSuccess = { memory ->
                    CorrectionSessionContext(
                        recentTopics = memory.recentTopics,
                        topicSummaries = memory.topicSummaries,
                        topicKeySentences = memory.topicKeySentences,
                        currentSessionTurns = currentSessionTurns,
                    )
                },
                onFailure = { error ->
                    Log.w(TAG, "session memory lookup failed lang=${lang.code} — falling back to empty context", error)
                    CorrectionSessionContext(currentSessionTurns = currentSessionTurns)
                },
            )
    }

    /**
     * COR-002-B: Error 상태에서 사용자가 호출하는 Retry 액션.
     *
     * 같은 Session Memory(= 현재 선택 언어 기준 SessionMemory) 와 _uiState 의 langStateSnapshot 으로
     * [triggerGeneration] 흐름을 다시 진입한다. [CorrectionUiState.Phase.Error] 외 상태에서 호출되면 no-op.
     *
     * 언어 변경 자동 재시도와의 관계:
     *  - [terminalPhases] 진입 시 [generationLaunched]=false 리셋으로 GlobalLangState refresh 가
     *    새 Ready 를 흘려보내면 자동으로 generate 가 다시 시작된다.
     *  - 본 함수는 사용자가 같은 언어로 명시적으로 다시 시도하는 경로다.
     */
    fun onRetryClicked() {
        val current = _uiState.value
        // 방어 가드: UI 가 Error phase 에서만 버튼을 노출하지만 방어적으로 체크한다.
        if (current.phase != CorrectionUiState.Phase.Error) return
        Log.d(TAG, "onRetryClicked — lang=${current.selectedLearningLanguage}")
        triggerGeneration(current)
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
     * 전체 선택/해제 토글.
     *
     * AC:
     *  - 현재 suggestions 의 id 전부가 selectedSuggestionIds 에 포함된 상태라면 빈 Set 으로 교체(전체 해제).
     *  - 그렇지 않으면 모든 id 를 Set 으로 교체(전체 선택).
     *  - suggestions 에 없는 stale id 는 allIds 로 덮어쓰며 함께 정리된다.
     *  - suggestions 가 비어있으면 아무런 상태 변화가 없다.
     */
    fun toggleSelectAll() {
        _uiState.update { current ->
            val allIds = current.suggestions.map { it.id }.toSet()
            if (allIds.isEmpty()) return@update current
            val next = if (current.selectedSuggestionIds.containsAll(allIds)) {
                emptySet()
            } else {
                allIds
            }
            current.copy(selectedSuggestionIds = next)
        }
    }

    /**
     * 저장 버튼 진입점.
     *
     * COR-005-A 범위:
     *  - 선택된 카드 → [com.app.umma.domain.model.correction.CorrectionSaveRequest] 변환만 책임진다.
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
     * COR-006-B 추가 흐름:
     *  - [CorrectionUiState.Phase.Retry] 에서도 [CorrectionUiState.canSave] 가 true 이므로 이 진입점이
     *    그대로 재사용된다. 같은 사용자 선택이 보존되어 있으면 [PrepareSaveRequestUseCase] 가
     *    deterministic 하게 같은 saveRequest 를 만들어 [launchCompletion] 으로 흘려보낸다.
     */
    /**
     * Stores the current Correction result as a developer review report.
     *
     * This path is intentionally isolated from production save/completion behavior. A report
     * failure only affects the report button state and never blocks correction learning flows.
     */
    fun reportCorrectionPromptReview(reportNote: String? = null) {
        val current = _uiState.value
        if (!current.showCorrectionReviewReportButton ||
            current.isCorrectionReviewReporting ||
            current.hasCorrectionReviewReported
        ) {
            return
        }

        _uiState.update { it.copy(isCorrectionReviewReporting = true) }
        viewModelScope.launch {
            val uid = getCurrentUserUid.getCurrentUserUid()?.takeIf { it.isNotBlank() }
            val lang = current.selectedLearningLanguage ?: current.suggestions.firstOrNull()?.lang
            if (uid == null || lang == null) {
                Log.w(TAG, "correction prompt review report skipped: uid or language unavailable")
                _uiState.update { it.copy(isCorrectionReviewReporting = false) }
                return@launch
            }

            val contextTurns = runCatching { getCorrectionContext(lang).first() }
                .getOrDefault(emptyList())
                .map {
                    CorrectionPromptReviewContextTurn(
                        speaker = it.role.name.lowercase(),
                        text = it.text,
                    )
                }
            val sourceKey = current.suggestions.firstOrNull()?.id
                ?: current.errorReason
                ?: current.phase.name
            val snapshot = CorrectionPromptReviewSnapshot(
                uid = uid,
                language = lang,
                primaryLanguage = current.primaryLanguage,
                phase = current.phase.name,
                reportNote = reportNote,
                sourceKey = sourceKey,
                suggestions = current.suggestions.map {
                    CorrectionPromptReviewSuggestion(
                        id = it.id,
                        sourceCandidateIds = it.sourceCandidateIds,
                        sourceTurnIndex = it.sourceTurnIndex,
                        beforeText = it.beforeText,
                        nativeText = it.nativeText,
                        afterText = it.afterText,
                        explanation = it.explanation,
                        sourceLang = it.sourceLang,
                    )
                },
                selectedSuggestionIds = current.selectedSuggestionIds,
                contextTurns = contextTurns,
                errorReason = current.errorReason,
                saveErrorReason = current.saveErrorReason,
                completionErrorReason = current.completionErrorReason,
            )

            reportCorrectionPromptReviewUseCase(snapshot)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isCorrectionReviewReporting = false,
                            hasCorrectionReviewReported = true,
                        )
                    }
                }
                .onFailure { error ->
                    Log.w(TAG, "correction prompt review report failed: ${error.message}", error)
                    _uiState.update { it.copy(isCorrectionReviewReporting = false) }
                }
        }
    }

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
            launchCompletion(outcome.result.request)
        }
    }

    /**
     * 완료 파이프라인 호출 진입점.
     *
     * [onSaveClicked] 의 Prepared 분기에서 호출된다. COR-006-B 에서는 별도 Retry 액션을 두지 않고,
     * [CorrectionUiState.Phase.Retry] 에서도 사용자가 같은 저장 버튼을 누르면 [onSaveClicked] 가
     * 다시 변환 → Prepared → 본 함수로 흘러 들어와 같은 saveRequest 로 재진입한다.
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
            //
            // [COR-TUNE-008] 분석 base는 화면이 동결한 stateSnapshot.langStateSnapshot 대신
            // 완료 직전의 최신 langStates[lang]을 사용한다(stale snapshot 덮어쓰기 방지).
            // observeLearningState().first()는 preload가 이미 완료된 StateFlow라 캐시를 즉시 반환한다.
            // 최신 조회(IO)만 여기서 하고, base 선택/fallback 결정은 순수 helper resolveCompletionBaseState로 분리한다.
            val freshGlobal = runCatching { observeLearningState().first() }.getOrNull()
            val freshLangState = resolveCompletionBaseState(
                freshGlobal = freshGlobal,
                lang = lang,
                frozenSnapshot = stateSnapshot.langStateSnapshot,
            )
            val command = BuildLangStateUpdateInputCommand(
                uid = uid,
                lang = lang,
                selectedLang = stateSnapshot.selectedLearningLanguage ?: lang,
                currentState = freshLangState,
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
            result.getOrNull()?.let { completion ->
                _events.send(
                    CorrectionEvent.NavigateToDashboard(
                        message = completion.toDashboardToastMessage(),
                    )
                )
            }
        }
    }

    private fun CompleteCorrectionResult.toDashboardToastMessage(): String {
        val savedCount = savedFlashcardIds.size
        return when {
            savedCount > 0 && safetyBlockedSuggestionCount > 0 ->
                "일부 문장은 학습 카드로 저장할 수 없어 제외했어요"
            savedCount > 0 ->
                "학습 카드 ${savedCount}개가 저장되었어요"
            zeroSaveReason == com.app.umma.domain.model.correction.CorrectionSaveZeroReason.SAFETY_BLOCKED ->
                "이번 교정 결과에는 저장 가능한 학습 문장이 없어요"
            else ->
                "저장할 학습 카드가 없어 정리만 완료했어요!"
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
                "onSaveClicked — saveRequest 준비 — flashcards=${outcome.result.request.flashcards.size}, safetyBlocked=${outcome.result.safetyBlockedSuggestionIds.size}, zeroReason=${outcome.result.zeroReason}",
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
     *
     * COR-007-B: success 분기에는 Firestore sync / Session compression / Statistics history 의
     * pending 3종이 포함되어 흘러올 수 있다. pending 은 사용자 실패가 아니라 *후속 재시도 대상*
     * 이므로 화면에는 노출하지 않고, 본 로그가 개발 확인의 단일 SSOT 다. 각 단계의 errorMessage
     * 도 함께 남겨 어떤 단계가 pending 으로 떨어졌는지 logcat 만으로 식별 가능하게 한다.
     */
    private fun logCompletionResult(result: Result<CompleteCorrectionResult>) {
        result.fold(
            onSuccess = { value ->
                // COR-007-B: pending 3종(sync / compression / statistics) 와 진단 메시지를 한 줄에 모은다.
                // 한 줄로 모으는 이유: "어느 단계가 pending 인가"를 grep 한 번으로 식별하기 위함.
                Log.d(
                    TAG,
                    buildString {
                        append("completion success — saved=${value.savedFlashcardIds.size}")
                        append(", pendingSync=${value.pendingSyncFlashcardIds.size}")
                        append(", compressionApplied=${value.sessionCompressionApplied}")
                        append(", compressionPending=${value.sessionCompressionPending}")
                        value.sessionCompressionErrorMessage?.let {
                            append(", compressionErr=$it")
                        }
                        append(", statsApplied=${value.statisticsHistoryApplied}")
                        append(", statsPending=${value.statisticsHistoryPending}")
                        value.statisticsHistoryErrorMessage?.let {
                            append(", statsErr=$it")
                        }
                    },
                )
            },
            onFailure = { e ->
                Log.w(TAG, "completion failure — reason=${e.toDiagnosticReason()}", e)
            },
        )
    }

    // 클래스 내부 진단 로그 전용 상수 묶음. ViewModel 외부에서 참조할 일이 없어 private companion 으로 격리한다.
    private companion object {
        // logcat 필터 식별자. 모든 Log.d/Log.w 호출이 이 태그를 공유해 한 화면 흐름의 로그를 한 번에 grep 할 수 있게 한다.
        const val TAG = "CorrectionViewModel"
        // COR-UX-001: 기존 일괄 MIN_LOADING_GUIDE_DURATION_MS(20초)를 대체하는 단계별 최소 노출 시간.
        // [triggerGeneration] 의 5단계(loadingStep 0~4) 각각이 이 시간만큼은 노출되도록 보장한다 —
        // 0~2단계는 단순 delay, 3단계(AI 호출)는 SystemClock.elapsedRealtime 기반 잔여시간 보정,
        // 4단계는 결과 매핑이 즉시 끝나도 문구가 스쳐 지나가지 않도록 동일하게 적용한다.
        const val STEP_MIN_DURATION_MS = 3_500L
    }
}

internal fun CorrectionUiState.mergeReviewButtonState(
    previous: CorrectionUiState,
    shouldShowButton: Boolean,
): CorrectionUiState {
    val isSameReviewTarget = previous.reviewTargetKey() == reviewTargetKey()
    return copy(
        showCorrectionReviewReportButton = shouldShowButton,
        isCorrectionReviewReporting = if (isSameReviewTarget) {
            previous.isCorrectionReviewReporting
        } else {
            false
        },
        hasCorrectionReviewReported = if (isSameReviewTarget) {
            previous.hasCorrectionReviewReported
        } else {
            false
        },
    )
}

internal fun CorrectionUiState.reviewTargetKey(): CorrectionReviewTargetKey =
    CorrectionReviewTargetKey(
        selectedLearningLanguage = selectedLearningLanguage,
        phase = phase,
        suggestionIds = suggestions.map { it.id },
        errorReason = errorReason,
        saveErrorReason = saveErrorReason,
        completionErrorReason = completionErrorReason,
    )

internal data class CorrectionReviewTargetKey(
    val selectedLearningLanguage: LangCode?,
    val phase: CorrectionUiState.Phase,
    val suggestionIds: List<String>,
    val errorReason: String?,
    val saveErrorReason: String?,
    val completionErrorReason: String?,
)
