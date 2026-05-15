package com.example.umma.presentation.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.umma.R
import com.example.umma.core.ui.UiText
import com.example.umma.domain.model.learningstate.currentDashSummary
import com.example.umma.domain.model.learningstate.isEffectivelyEmpty
import com.example.umma.domain.model.learningstate.selectedLang
import com.example.umma.domain.usecase.learningstate.ObserveLearningStateUseCase
import com.example.umma.domain.usecase.learningstate.PreloadLearningStateUseCase
import com.example.umma.domain.usecase.learningstate.SyncLearningStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
//    private val changeSelectedLang: ChangeSelectedLangUseCase // DASH-001 검증용 임시 주입 (DASH-006 본 PR 에서 정식 적용 예정)
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /**
     * 전역 학습 상태 구독 job.
     *
     * observeLearningState() 가 Flow 라서 ViewModel 생애 내내 active.
     * LaunchedEffect 재실행 / 회전에서 onEnter() 가 다시 호출돼도
     * isActive 체크에서 collect 셋업은 skip 됨 — 이게 의도된 동작.
     * ViewModel onCleared() 시 viewModelScope 와 함께 자동 cancel.
     */
    private var enterJob: Job? = null

    /**
     * Firebase background sync job. (AC 11 중복 방지)
     * AC 11: Summary fetch 중 중복 요청이 방지된다.
     *
     * onEnter() 가 호출될 때마다 sync 를 트리거하되, 직전 호출이 아직 진행 중이면
     * skip. 재진입 (AC 10) 도 같은 경로로 처리.
     * AC 10: Dashboard 재진입 시 최신 Summary 데이터가 반영된다.
     */
    private var fetchJob: Job? = null

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
        triggerSync()
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
                val lang = global.selectedLang
                val summary = global.currentDashSummary()
                val empty = summary == null || summary.isEffectivelyEmpty

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedLearningLanguage = lang,
                        summary = summary,
                        isEmpty = empty
                    )
                }
                Log.d(
                    TAG,
                    "state emit: lang=$lang, " +
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

    /**
     * (2) Firebase background sync. (AC 1, 6, 10, 11)
     * AC 1: Dashboard 진입 시 DashSummary fetch가 수행된다.
     * AC 6: Firebase background sync가 수행된다.
     * AC 10: Dashboard 재진입 시 최신 Summary 데이터가 반영된다.
     * AC 11: Summary fetch 중 중복 요청이 방지된다.
     *
     *  - AC 1, 6: 진입 시 fetch.
     *  - AC 10: 재진입 시 fetch 재트리거 → 최신 emit 으로 UI 갱신.
     *  - AC 11: 직전 호출이 active 면 skip — 중복 네트워크 요청 방지.
     *  - AC 7: 실패 시 cache 그대로 두고 errorMessage 만 세팅.
     * (AC 7: Summary fetch 실패 시 fallback 데이터가 사용된다.)
     *
     * isLoading 은 건드리지 않는다. cache 가 이미 UI 에 떠 있는 상태(AC 12)에서
     * 백그라운드로 갱신하는 동작이라 Skeleton 깜빡임이 있으면 안 됨.
     * (AC 12: 오래된 cache 데이터가 존재하더라도 우선 렌더링된다.)
     */
    private fun triggerSync() {
        if (fetchJob?.isActive == true) {
            Log.d(TAG, "triggerSync() skipped — AC 11 dedup, in-flight job exists")
            return
        }
        fetchJob = viewModelScope.launch {
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
                    // AC 7: cache 유지. errorMessage 만 세팅해서 UI 가 알릴 수 있게.
                    Log.w(TAG, "sync failed — keeping cache (AC 7 fallback)", e)
                    _uiState.update {
                        it.copy(errorMessage = UiText.Resource(R.string.dashboard_err_sync_failed))
                    }
                }
        }
    }

    /**
     * 학습 언어 변경 시 호출. (DASH-006)
     *
     * 본 구현은 DASH-006 PR 에서:
     *  1. ChangeSelectedLangUseCase(LangCode) 호출
     *  2. observeLearningState() collect 가 새 selectedLang / summary 자동 emit
     *     → _uiState 갱신은 자동, 여기서 별도로 손댈 필요 없음
     */
    fun onChangeLearningLanguage(langCode: String) {
        Log.d(
            TAG,
            "onChangeLearningLanguage(langCode=$langCode) — DASH-006 hook (not implemented yet)"
        )
//        val lang = LangCode.fromCode(langCode) ?: run {
//            Log.w(TAG, "unknown langCode=$langCode, skip")
//            return
//        }
//        viewModelScope.launch {
//            changeSelectedLang(lang)
//        }
    }

    private companion object {
        const val TAG = "DashboardViewModel"
    }
}