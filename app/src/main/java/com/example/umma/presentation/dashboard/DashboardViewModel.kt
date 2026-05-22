package com.example.umma.presentation.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.umma.R
import com.example.umma.core.ui.UiText
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.isEffectivelyEmpty
import com.example.umma.domain.usecase.learningstate.ChangeSelectedLangUseCase
import com.example.umma.domain.usecase.learningstate.ObserveLearningStateUseCase
import com.example.umma.domain.usecase.learningstate.PreloadLearningStateUseCase
import com.example.umma.domain.usecase.learningstate.SyncLearningStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Dashboard 화면의 ViewModel.
 *
 * SSOT: DASH-001_Dashboard_Entry.md
 *
 * 책임 (전체):
 *  - DASH-001: 진입 시 Local Cache preload → UserLangPref / DashSummary 노출,
 *              Firebase background sync
 *  - DASH-002: 카드별 데이터 상태 관리 ([DashboardUiState.summary] 안의 필드들)
 *  - DASH-006: 학습 언어 변경 시 새 언어 기준으로 DashSummary 재 fetch
 *
 * DASH-006 본 구현은 별도 PR — 여기선 hook 만 유지.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val preloadLearningState: PreloadLearningStateUseCase,
    private val observeLearningState: ObserveLearningStateUseCase,
    private val syncLearningState: SyncLearningStateUseCase,
    private val changeSelectedLang: ChangeSelectedLangUseCase,
) : ViewModel() {

    // UI state 의 단일 source of truth (쓰기 가능). _ prefix = 외부 비공개 컨벤션.
    // View 가 직접 못 건들고 ViewModel 내부에서 update() 로만 변경.
    private val _uiState = MutableStateFlow(DashboardUiState())

    // 위 _uiState 의 읽기 전용 노출. Screen 이 collectAsStateWithLifecycle 로 구독.
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /**
     * 전역 학습 상태 구독 job.
     *
     * observeLearningState() 가 Flow 라서 ViewModel 생애 내내 active.
     * LaunchedEffect 재실행 / 회전에서 onEnter() 가 다시 호출돼도
     * isActive 체크에서 collect 셋업은 skip 됨 — 이게 의도된 동작.
     * ViewModel onCleared() 시 viewModelScope 와 함께 자동 cancel.
     */
    // observeLearningState() Flow 를 collect 하는 background job 핸들.
    // null = 아직 셋업 안 됨. ensureObservation() 에서 1 회만 셋업, ViewModel 사망 시 자동 cancel.
    private var enterJob: Job? = null

    /**
     * Firebase background sync job. (AC 11 중복 방지)
     * AC 11: Summary fetch 중 중복 요청이 방지된다.
     *
     * onEnter() 가 호출될 때마다 sync 를 트리거하되, 직전 호출이 아직 진행 중이면
     * skip. 재진입 (AC 10) 도 같은 경로로 처리.
     * AC 10: Dashboard 재진입 시 최신 Summary 데이터가 반영된다.
     */
    // triggerSync() (Firebase background sync) 의 in-flight job 핸들.
    // active 면 새 sync 호출 skip — AC 11 dedup 의 근거.
    private var fetchJob: Job? = null

    // sync 중 새 local-first 변경이 들어오면 현재 sync 종료 후 한 번 더 실행하기 위한 예약 플래그.
    private var pendingSyncAfterCurrent: Boolean = false

    /**
     * Setup 완료 여부 추적.
     *
     * null  = 첫 Flow emit 전(미결정)
     * false = 신규 사용자 — userPref 없음, Firestore 문서 미생성
     * true  = Setup 완료 확인 — createInitial() 실행 이후 userPref non-null
     *
     * ensureObservation() collect 에서 userPref 유무로 갱신.
     * sync 진입 게이트(onEnter / collect) 와 Snackbar 발화 게이트(triggerSync) 로 사용.
     */
    private var setupConfirmed: Boolean? = null

    /**
     * 화면 진입 시 호출. (DASH-001)
     *
     * 두 가지 일을 분리 트리거:
     *  1. enterJob — Cache preload + observeLearningState() collect (1 회만 셋업)
     *  2. fetchJob — Firebase sync (재진입마다 트리거, 중복 방지)
     *
     * AC 12 (오래된 cache 우선 렌더링): 1) 의 첫 emit 으로 stale cache 가 즉시 UI 에 뜨고,
     * 2) 가 비동기로 끝나면서 repo 의 _state 가 갱신되면 1) 의 collect 가 새 emit 을 받음.
     * 즉 같은 collect 가 두 번 emit → UI 가 자연스럽게 stale → fresh 로 교체된다.
     */
    fun onEnter() {
        ensureObservation()
        // setupConfirmed == true → 재진입(기존 사용자) 즉시 sync.
        // null / false → 첫 진입 또는 신규 사용자. ensureObservation collect 에서 처리.
        if (setupConfirmed == true) {
            triggerSync()
        }
    }

    /**
     * (1) Cache preload + Flow collect 셋업. 최초 1 회만 실행.
     */
    private fun ensureObservation() {
        if (enterJob?.isActive == true) {
            Log.d(TAG, "ensureObservation() skipped — already collecting")
            return
        }
        enterJob = viewModelScope.launch {
            Log.d(TAG, "ensureObservation() — DASH-001 preload start")
            // DASH-001: 의도된 skeleton 최소 표시 지연 (SKELETON_MIN_DISPLAY_MS).
            //   cache hit 시 skeleton 이 1 프레임만 깜빡이고 사라지는 문제 방지용.
            //   첫 emit 직전에 한 번만 잔여 시간 delay. 조정 시 SKELETON_MIN_DISPLAY_MS 만 변경.
            val startedAtMs = System.currentTimeMillis()
            var skeletonGateApplied = false
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }

            // Local Cache preload.
            //   idempotent. 실패해도 observeLearningState() 가 GlobalLangState.initial() 을
            //   emit 해서 Empty 분기로 자연스럽게 fallthrough.
            //   사용자 노출 errorMessage 는 sync 실패 쪽에서만 다룬다 — preload 실패는
            //   "캐시 없음" 일 뿐 사용자 입장에선 신규 진입과 구분 불가하니까.
            preloadLearningState().exceptionOrNull()?.let { e ->
                Log.w(TAG, "preload failed — falling back to Empty", e)
            }

            // 전역 학습 상태 구독.
            //   AC 12: 오래된 cache 데이터가 존재하더라도 우선 렌더링된다.
            //   TTL(Time To Live) 체크 없이 _state 의 값 그대로 흘려보냄. 최신화는 triggerSync() 담당.
            //   sync 가 _state 를 갱신하면 여기 collect 가 새 emit 을 한 번 더 받는다.
            observeLearningState().collect { global ->
                // 이번 emit 의 사용자 학습 설정 스냅샷. 신규 사용자 / preload 직후엔 null.
                val userPref = global.userPref
                // 학습 중인 언어 목록. userPref null 이면 빈 리스트로 안전 처리.
                val learningLangs = userPref?.learningLangs.orEmpty()

                // [DASH-001 후속] Setup 완료 추적 + 첫 확인 시 sync 트리거.
                // userPref non-null = createInitial() 이 실행된 이후 → Setup 완료 기준.
                // triggerSync() 는 자체 dedup(fetchJob?.isActive) 을 갖고 있어 중복 호출 안전.
                val isConfirmedNow = userPref != null
                val wasConfirmed = setupConfirmed
                setupConfirmed = isConfirmedNow
                if (isConfirmedNow && wasConfirmed != true) {
                    // 기존 사용자 첫 진입 또는 신규→완료 전환 시 한 번만 트리거.
                    triggerSync()
                }

                // AC 10: userPref 가 채워졌는데 learningLangs 가 비어있으면 Fatal.
                //   userPref==null 은 preload 직후/신규 사용자 — Empty 분기에서 처리하므로 여기선 패스.
                // (AC 10: learningLanguages가 비어 있거나 로드 실패 시 Error 또는 Empty 상태가 표시된다.)
                if (userPref != null && learningLangs.isEmpty()) {
                    Log.w(TAG, "DASH-006 AC 10 fatal — userPref present but learningLangs empty")
                    if (!skeletonGateApplied) {
                        skeletonGateApplied = true
                        val remaining = SKELETON_MIN_DISPLAY_MS - (System.currentTimeMillis() - startedAtMs)
                        if (remaining > 0) delay(remaining)
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            hasFatalError = true,
                            isEmpty = false,
                            summary = null
                        )
                    }
                    return@collect
                }

                // AC 9: selectedLang ∉ learningLangs 인 데이터 오염 케이스 → primaryLang fallback.
                //   복구 저장도 시도 (fire-and-forget). 다음 emit 에선 정합 상태로 들어옴.
                // (AC 9: selectedLearningLanguage가 없는 경우 primaryLearningLanguage로 fallback된다.)

                // UI 가 실제로 쓸 lang. selectedLang 을 그대로 쓰지 않고 정합성 가드 한 번 거친 값.
                //  - null : userPref 자체 없음 (신규/preload 직후)
                //  - selectedLang : 정상 케이스
                //  - primaryLang : selectedLang ∉ learningLangs 인 오염 케이스 (AC 9 fallback)
                val effectiveLang: LangCode? = when {
                    userPref == null -> null
                    userPref.selectedLang in learningLangs -> userPref.selectedLang
                    else -> {
                        Log.w(
                            TAG,
                            "AC 9 fallback — selectedLang=${userPref.selectedLang} " +
                                    "not in learningLangs=$learningLangs, using primary=${userPref.primaryLang}"
                        )
                        // 복구 저장. 실패해도 다음 emit 까진 effectiveLang 으로 계속 동작.
                        launch { changeSelectedLang(userPref.primaryLang) }
                        userPref.primaryLang
                    }
                }

                // effectiveLang 기준 카드 데이터. null 가능 (effectiveLang null 또는 해당 lang summary 없음).
                val summary = effectiveLang?.let { global.dashSummaries[it] }
                // "보여줄 게 없는" 상태 — Empty 분기 판정용.
                val empty = summary == null || summary.isEffectivelyEmpty

                // 실제 학습 데이터가 있는 언어 집합 — DashSummary 중 isEffectivelyEmpty=false.
                //   selector 다이얼로그의 "이전에 학습 중이던 언어" 체크 아이콘 기준으로 쓰인다.
                //   userPref.learningLangs 는 selector 단순 선택만으로도 자동 확장되지만,
                //   여기는 실제 대화/카드/통계 데이터가 쌓인 언어만 포함 → 시각적 구분.
                val activeLangs = global.dashSummaries
                    .filterValues { !it.isEffectivelyEmpty }
                    .keys

                if (!skeletonGateApplied) {
                    skeletonGateApplied = true
                    val remaining = SKELETON_MIN_DISPLAY_MS - (System.currentTimeMillis() - startedAtMs)
                    if (remaining > 0) delay(remaining)
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedLearningLanguage = effectiveLang,
                        summary = summary,
                        isEmpty = empty,
                        learningLanguages = learningLangs,
                        activeLearningLanguages = activeLangs,
                        hasFatalError = false
                    )
                }
                Log.d(
                    TAG,
                    "state emit: lang=$effectiveLang, " +
                            "learningLangs=$learningLangs, " +
                            "summary=[recentTopic=${summary?.recentTopic}, " +
                            "recentMinutes=${summary?.recentMinutes}, " +
                            "dueFlashcards=${summary?.dueFlashcards}, " +
                            "savedFlashcards=${summary?.savedFlashcards}, " +
                            "updatedAt=${summary?.updatedAt}], " +
                            "isEmpty=$empty"
                )
            }
        }
    }

    // DASH-001: Firebase background sync.
    private fun triggerSync() {
        if (fetchJob?.isActive == true) {
            pendingSyncAfterCurrent = true
            Log.d(TAG, "triggerSync() skipped — AC 11 dedup, in-flight job exists")
            return
        }
        fetchJob = viewModelScope.launch {
            try {
                Log.d(TAG, "triggerSync() — Firebase background sync start")
                // 재진입 시 이전 에러 클리어. 새 시도니까.
                _uiState.update { it.copy(errorMessage = null) }

                syncLearningState()
                    .onSuccess {
                        Log.d(TAG, "sync success — observe collect 가 새 emit 처리")
                        // _state 갱신은 repo 가 하고, observe collect 가 받아서 UI 반영.
                        // 여기선 별도 작업 없음.
                    }
                    .onFailure { e ->
                        // DASH-001 AC 7: cache 유지. errorMessage 만 세팅해서 UI 가 알릴 수 있게.
                        Log.w(TAG, "sync failed — keeping cache (AC 7 fallback)", e)
                        // setupConfirmed != true(신규·미결정) → Snackbar 억제.
                        // users/{uid} 미생성 분기의 실패를 사용자에게 노출하지 않는다.
                        if (setupConfirmed == true) {
                            _uiState.update {
                                it.copy(errorMessage = UiText.Resource(R.string.dashboard_err_sync_failed))
                            }
                        }
                    }
            } finally {
                val shouldRunQueuedSync = pendingSyncAfterCurrent
                pendingSyncAfterCurrent = false
                fetchJob = null
                if (shouldRunQueuedSync) {
                    // 진행 중이던 fetch가 local 변경보다 먼저 시작됐을 수 있으므로 한 번 더 수렴시킨다.
                    triggerSync()
                }
            }
        }
    }

    /**
     * 학습 언어 변경 시 호출. (DASH-006 Phase 2 본 구현)
     *
     * AC 4: 언어 선택 시 selectedLearningLanguage가 갱신된다.
     * AC 5: selectedLearningLanguage 변경 후 해당 언어의 Dashboard Summary가 로드된다.
     * AC 6: Dashboard의 모든 카드가 변경된 언어 기준으로 다시 렌더링된다.
     *
     * 흐름 (SSOT "변경 정책" 매핑):
     *  1. [changeSelectedLang] 호출 — Repo 의 _state.userPref.selectedLang 갱신
     *     → observeLearningState() collect 가 새 emit 받아 _uiState.selectedLearningLanguage,
     *       _uiState.summary 자동 갱신 (AC 4, 5)
     *     → Compose recomposition 으로 카드 재렌더링 (AC 6)
     *     ※ SSOT 의 "DashSummary Local Cache fetch" 단계는 별도 호출 불필요.
     *       cache(_state) 가 모든 언어 summary 를 들고 있고 currentDashSummary() 가
     *       새 selectedLang 기준으로 자동 추출.
     *  2. [triggerSync] 호출 — UserLangPref + DashSummary Firebase background sync.
     *     onEnter() 직후라 이미 fetchJob 이 active 면 dedup 으로 skip — 의도된 동작.
     *
     * 동일 언어 재선택은 no-op. happy path 의 일부지만 불필요한 sync 트리거를 막는
     * 의미도 있음.
     *
     * Phase 3 에서 추가될 사항:
     *  - isChangingLanguage 중복 방지 (AC 7)
     *  - 실패 시 rollback + Snackbar (AC 8)
     *  - fallback / Error UI (AC 9, 10)
     *
     * @param langCode UI 에서 전달되는 언어 코드 문자열 (예: "en", "ja").
     *                 LangCode 도메인 타입으로 매핑 후 처리.
     */
    fun onChangeLearningLanguage(langCode: String) {
        // UI 에서 받은 문자열 코드를 도메인 enum 으로 매핑. 매핑 실패 시 무시.
        val lang = LangCode.fromCode(langCode) ?: run {
            Log.w(TAG, "unknown langCode=$langCode, skip")
            return
        }
        // 현재 UI 가 보여주고 있는 lang. 같은 값이면 변경할 게 없으니 no-op.
        val current = _uiState.value.selectedLearningLanguage
        if (lang == current) {
            Log.d(TAG, "onChangeLearningLanguage skipped — same lang ($lang)")
            return
        }
        // DASH-006 AC 7: 가드 + flag set 을 같은 동기 블록에서 atomic 하게.
        //   MutableStateFlow.update 가 lock 기반이라 동시 호출 시 직렬화됨.
        //   compareAndSet 으로 "false → true" 전이를 한 번만 성공시키고,
        //   실패하면 다른 호출이 이미 in-flight 인 것.
        // (AC 7: 언어 변경 저장 중 중복 요청이 방지된다.)

        // AC 7 가드의 "락 획득" 결과. true = 이 호출이 변경 처리권을 잡음.
        //   false = 다른 호출이 이미 in-flight, 이 호출은 skip.
        val acquired = run {
            // 직전 state 스냅샷. compareAndSet 의 "예상값" 으로 사용.
            val prev = _uiState.value
            if (prev.isChangingLanguage) {
                false
            } else {
                _uiState.compareAndSet(
                    prev,
                    prev.copy(isChangingLanguage = true, errorMessage = null)
                )
            }
        }
        if (!acquired) {
            Log.d(TAG, "onChangeLearningLanguage skipped — AC 7 dedup, change in-flight")
            return
        }

        viewModelScope.launch {
            Log.d(TAG, "onChangeLearningLanguage(lang=$lang) — local update start")

            changeSelectedLang(lang)
                .onSuccess {
                    Log.d(TAG, "changeSelectedLang success — observe collect 가 새 emit 처리")
                    // Repo.sync() 가 pending write를 먼저 처리하므로 언어 변경 직후에도 안전하게 원격 반영을 예약할 수 있다.
                    // 이미 sync 중이면 triggerSync() 가 pendingSyncAfterCurrent 로 한 번 더 실행을 예약한다.
                    triggerSync()
                }
                .onFailure { e ->
                    Log.w(TAG, "changeSelectedLang failed — AC 8 auto-rollback via observe", e)
                    _uiState.update {
                        it.copy(errorMessage = UiText.Resource(R.string.dashboard_err_lang_change_failed))
                    }
                }

            _uiState.update { it.copy(isChangingLanguage = false) }
        }
    }

    private companion object {
        const val TAG = "DashboardViewModel"

        /**
         * DASH-001: skeleton 최소 표시 시간(ms).
         *
         * Local Cache hit 시 observeLearningState() 첫 emit 이 거의 즉시 도착해
         * skeleton 이 한 프레임만 깜빡이는 문제를 막기 위한 의도된 지연.
         * 이 값만 조정하면 전체 skeleton 노출 시간이 바뀐다 — 검색 키워드:
         * "의도된 skeleton 최소 표시 지연".
         */
        const val SKELETON_MIN_DISPLAY_MS = 600L
    }
}
