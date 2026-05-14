package com.example.umma.presentation.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Dashboard 화면의 ViewModel.
 *
 * SSOT: DASH-001_Dashboard_Entry.md "권장 ViewModel 역할"
 *
 * 책임 (전체):
 *  - DASH-001: Dashboard 진입 시 UserLangPref / DashSummary preload, selectedLearningLanguage 확인,
 *              Local Cache 우선 렌더, Firebase background sync
 *  - DASH-002: 카드별 데이터 상태 관리 (현재 [DashboardUiState.summary] 안의 필드들)
 *  - DASH-006: 학습 언어 변경 시 새 언어 기준으로 DashSummary 재 fetch
 *
 * 현재 파일은 **placeholder 상태**이다:
 *  - 의존성 미주입(LearningStateRepo / UseCase) — 다음 작업자가 추가
 *  - 메서드들은 진입/언어 변경 시그널을 log 로만 남긴다
 *  - 실제 데이터 흐름과 분기 처리는 후속 Phase 에서 구현
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    // 후속: LearningStateRepo, FetchDashSummaryUseCase 등 주입 예정
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /**
     * 화면 진입 시 1 회 호출. (DASH-001)
     *
     * 본 구현에서 해야 할 일:
     *  1. UserLangPref Local preload
     *  2. selectedLearningLanguage 확인 후 _uiState 에 반영
     *  3. DashSummary[selectedLearningLanguage] Local Cache preload → _uiState.summary 갱신
     *  4. Firebase background sync → 변경분 있으면 _uiState.summary 재갱신
     *  5. 중복 호출 방지(fetchJob 관리), 에러 시 fallback / Snackbar
     *
     * 현재는 placeholder 로 log 만 남긴다.
     */
    fun onEnter() {
        Log.d(TAG, "onEnter() called — DASH-001 preload hook (not implemented yet)")
    }

    /**
     * 학습 언어 변경 시 호출. (DASH-006)
     *
     * 본 구현에서 해야 할 일:
     *  1. UserLangPref.selectedLearningLanguage 갱신 (persist)
     *  2. _uiState.selectedLearningLanguage 갱신
     *  3. 새 언어 기준으로 DashSummary 재 fetch (Local cache → Firebase sync)
     *
     * @param langCode ISO 639-1 같은 언어 코드 (예: "en", "ja"). 도메인 타입(LangCode) 으로 매핑은 본 구현에서.
     */
    fun onChangeLearningLanguage(langCode: String) {
        Log.d(
            TAG,
            "onChangeLearningLanguage(langCode=$langCode) — DASH-006 hook (not implemented yet)"
        )
    }

    private companion object {
        const val TAG = "DashboardViewModel"
    }
}