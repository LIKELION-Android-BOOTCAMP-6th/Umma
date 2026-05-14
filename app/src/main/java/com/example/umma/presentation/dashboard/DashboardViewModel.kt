package com.example.umma.presentation.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * SSOT: DASH-001_Dashboard_Entry.md "권장 ViewModel 역할"
 *
 * 책임 (전체):
 *  - DASH-001: 진입 시 UserLangPref / DashSummary preload, selectedLearningLanguage 확인,
 *              Local Cache 우선 렌더, Firebase background sync
 *  - DASH-002: 카드별 데이터 상태 관리 ([DashboardUiState.summary] 안의 필드들)
 *  - DASH-006: 학습 언어 변경 시 새 언어 기준으로 DashSummary 재 fetch
 *
 * 현재 진행 상태:
 *  - Loading(Skeleton) → Empty(신규 사용자) 흐름이 placeholder 로 구현돼 있다.
 *  - 의존성(LearningStateRepo / FetchDashSummaryUseCase) 은 아직 주입 안 됨.
 *
 * 직후 해야 할 일 (USER_FLOW_MOCK_REAL_DATA_GUIDE.md 의 fake/real 전환 패턴):
 *  - [LearningStateRepo] 주입. 개발 중에는 FakeLearningStateRepo, release 에서는 LearningStateRepoImpl.
 *  - onEnter() 의 simulated delay 블록을 실제 repo 호출로 교체:
 *      1. UserLangPref Local preload → _uiState.selectedLearningLanguage 반영
 *      2. DashSummary[selectedLearningLanguage] Local Cache preload → _uiState.summary 갱신
 *      3. Firebase background sync → 변경분 있으면 _uiState.summary 재갱신
 *      4. summary 가 비어있으면 isEmpty = true, 있으면 false
 *      5. 에러 시 errorMessage 세팅 (Error 분기는 Screen 의 when 에도 추가 필요)
 *  - onChangeLearningLanguage() 본 구현 (persist + 재 fetch)
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    // 직후 단계: LearningStateRepo, FetchDashSummaryUseCase 등 주입 예정.
    //   가이드의 fake/real 전환은 di/RepositoryModule.kt 의 bindLearningStateRepo() 에서.
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /**
     * 중복 진입 방지.
     * 회전이나 LaunchedEffect 재실행으로 인한 중복 fetch 차단.
     */
    private var enterJob: Job? = null

    /**
     * 화면 진입 시 1 회 호출. (DASH-001)
     *
     * 현재 단계 임시 동작:
     *  1. isLoading = true 로 Skeleton 트리거
     *  2. simulated delay (직후 단계 에서 실제 repo 호출로 교체)
     *  3. 신규 사용자 가정 → isEmpty = true 로 Empty 표시
     */
    fun onEnter() {
        if (enterJob?.isActive == true) {
            Log.d(TAG, "onEnter() skipped — already in flight")
            return
        }
        enterJob = viewModelScope.launch {
            Log.d(TAG, "onEnter() — DASH-001 placeholder fetch start")
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }

            // ====== 직후 단계 교체 대상 (START) ======
            // 실제 구현 예시:
            //   val langPref = learningStateRepo.getUserLangPref()
            //   val summary  = learningStateRepo.getDashSummary(langPref.selectedLearningLanguage)
            //   viewModelScope.launch { learningStateRepo.syncDashSummaryFromRemote(...) }
            delay(SIMULATED_LOAD_DELAY_MS)
            // ====== 직후 단계 교체 대상 (END) ======

            // 현재 단계: 신규 사용자 흐름 가정 → Empty 표시.
            //   직후 단계 에서는 summary == null/empty 여부로 isEmpty 판정.
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isEmpty = true,
                    summary = null
                )
            }
            Log.d(TAG, "onEnter() — placeholder fetch done, isEmpty=true")
        }
    }

    /**
     * 학습 언어 변경 시 호출. (DASH-006)
     *
     * 직후 할 일:
     *  1. UserLangPref.selectedLearningLanguage persist
     *  2. _uiState.selectedLearningLanguage 갱신
     *  3. 새 언어 기준으로 DashSummary 재 fetch (Local cache → Firebase sync)
     */
    fun onChangeLearningLanguage(langCode: String) {
        Log.d(
            TAG,
            "onChangeLearningLanguage(langCode=$langCode) — DASH-006 hook (not implemented yet)"
        )
    }

    private companion object {
        const val TAG = "DashboardViewModel"

        /**
         * 현재 단계 용 placeholder 용 시뮬레이션 지연.
         * 다음 단계에서 실제 repo 호출로 교체할 때 함께 제거한다.
         */
        const val SIMULATED_LOAD_DELAY_MS = 1200L
    }
}