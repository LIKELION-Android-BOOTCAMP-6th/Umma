package com.example.umma.presentation.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.umma.domain.model.learningstate.currentDashSummary
import com.example.umma.domain.model.learningstate.isEffectivelyEmpty
import com.example.umma.domain.model.learningstate.selectedLang
import com.example.umma.domain.usecase.learningstate.ObserveLearningStateUseCase
import com.example.umma.domain.usecase.learningstate.PreloadLearningStateUseCase
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
 * 현재 단계 완료 항목 (AC 2, 3, 4, 5, 12):
 *  - PreloadLearningStateUseCase 로 DataStore → 메모리 스냅샷 복원
 *  - ObserveLearningStateUseCase 로 GlobalLangState 구독
 *  - selectedLearningLanguage / summary / isEmpty 분기 채움
 *
 * 직후 단계에서 할 일 (AC 1, 6, 7, 10, 11):
 *  - Firebase background sync (Repo 에 sync 메서드 추가 필요)
 *  - Summary fetch 실패 시 fallback + errorMessage 활용 + Screen Error 분기
 *  - 재진입 refresh, fetchJob 별도 관리
 *
 * DASH-006 본 구현은 별도 PR — 여기선 hook 만 유지.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val preloadLearningState: PreloadLearningStateUseCase,
    private val observeLearningState: ObserveLearningStateUseCase,
//    private val changeSelectedLang: ChangeSelectedLangUseCase // 검증용 임시 주입 (DASH-006 본 PR 에서 정식 적용 예정)
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /**
     * 중복 진입 방지.
     *
     * 현재 단계부터 observeLearningState() 가 Flow 라서 enterJob 은 사실상
     * ViewModel 생애 내내 active. LaunchedEffect 재실행 / 회전에서 onEnter() 가
     * 다시 호출돼도 isActive 체크에서 skip 됨 — 이게 의도된 동작.
     * ViewModel onCleared() 시 viewModelScope 와 함께 자동 cancel.
     */
    private var enterJob: Job? = null

    /**
     * 화면 진입 시 1 회 호출. (DASH-001)
     *
     * 흐름:
     *  1. isLoading = true 로 Skeleton 트리거
     *  2. preload() — DataStore 의 마지막 스냅샷을 메모리(_state) 로 복원 (idempotent)
     *  3. observeLearningState() collect 시작
     *     - 첫 emit 도착 시 isLoading = false 로 전환
     *     - selectedLang 이 바뀌면 (DASH-006 본 구현 후) Flow 가 알아서 새 summary emit
     *     - 즉 selectedLearningLanguage 변경 시 별도 분기 없이 자동 반영
     */
    fun onEnter() {
        if (enterJob?.isActive == true) {
            Log.d(TAG, "onEnter() skipped — already in flight")
            return
        }
        enterJob = viewModelScope.launch {
            Log.d(TAG, "onEnter() — DASH-001 preload start")
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }

            // 1. Local Cache preload.
            //    이미 다른 곳에서 preload 됐다면 _state 가 그대로 유지되니 idempotent.
            //    실패해도 observeLearningState() 가 GlobalLangState.initial() 을 emit 해서
            //    Empty 분기로 자연스럽게 fallthrough.
            //    에러 fallback 정책 / errorMessage 활용은 다음 단게 에서 본 처리.
            preloadLearningState().exceptionOrNull()?.let { e ->
                Log.w(TAG, "preload failed — falling back to Empty (DASH-001 마지막 단계에서 errorMessage 처리)", e)
            }

            // 2. 전역 학습 상태 구독.
            //    GlobalLangState.selectedLang / currentDashSummary() extension 으로
            //    selectedLearningLanguage + summary 를 한 번에 뽑는다.
            //    AC 12 (오래된 cache 우선 렌더링): TTL(Time To Live) 체크 없이 _state 의 값 그대로 흘려보냄.
            //    최신화는 다음 단계의 Firebase sync 가 담당.
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
                // AC 5: '현재 선택 언어 기준 Dashboard 카드 데이터가 정상 출력된다.' 검증을 위한
                //   verbose 로그. (DASH-002 카드 본 구현 전까지 시각 검증 대체)
                // TODO: AC 12 검증 — Firebase background sync 가 들어오면
                //   "오래된 cache 우선 렌더 → sync 후 갱신" 사이클이 이 collect 에서 두 번
                //   emit 되는 형태로 흘러야 한다. logcat 에 두 번 찍히는지로 검증.
                Log.d(
                    TAG,
                    "state emit: lang=$lang, " +
                            "summary=[recentTopic=${summary?.recentTopic}, " +
                            "recentMinutes=${summary?.recentMinutes}, " +
                            "dueFlashcards=${summary?.dueFlashcards}, " +
                            "savedFlashcards=${summary?.savedFlashcards}], " +
                            "isEmpty=$empty"
                )
            }
        }
    }

    /**
     * 학습 언어 변경 시 호출. (DASH-006)
     *
     * 본 구현은 DASH-006 PR 에서:
     *  1. ChangeSelectedLangUseCase(LangCode) 호출
     *  2. 위 onEnter() 의 observeLearningState() collect 가 새 selectedLang / summary 자동 emit
     *     → _uiState 갱신은 자동, 여기서 별도로 손댈 필요 없음
     */
    fun onChangeLearningLanguage(langCode: String) {
        Log.d(
            TAG,
            "onChangeLearningLanguage(langCode=$langCode) — DASH-006 hook (not implemented yet)"
        )
        // 검증용 임시 주입 (DASH-006 본 PR 에서 정식 적용 예정)
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