package com.example.umma.presentation.dashboard

import com.example.umma.core.ui.UiText
import com.example.umma.domain.model.learningstate.DashSummary
import com.example.umma.domain.model.learningstate.LangCode

/**
 * Dashboard 화면의 UI 상태.
 *
 * SSOT: DASH-001_Dashboard_Entry.md "권장 상태 구조"
 *
 * 후속 작업자가 채울 항목:
 *  - selectedLearningLanguage: UserLangPref preload 결과 / 변경
 *  - summary: DashSummary[selectedLearningLanguage] preload + Firebase sync 결과
 *  - isLoading / errorMessage / isEmpty: Loading / Empty / Error 분기
 */

data class DashboardUiState(
    // 진입 직후 첫 프레임에서 Skeleton 이 즉시 보이도록 true 로 시작.
    // onEnter() 가 끝나면 Loading 해제하며 isEmpty / summary 로 분기.
    val isLoading: Boolean = true,
    val selectedLearningLanguage: LangCode? = null,
    val summary: DashSummary? = null,
    val errorMessage: UiText? = null,
    val isEmpty: Boolean = false
)