package com.example.umma.presentation.dashboard

import com.example.umma.core.ui.UiText
import com.example.umma.domain.model.learningstate.DashSummary
import com.example.umma.domain.model.learningstate.LangCode

data class DashboardUiState(
    // 진입 직후 첫 프레임에서 Skeleton 이 즉시 보이도록 true 로 시작.
    // onEnter() 가 끝나면 Loading 해제하며 isEmpty / summary 로 분기.
    val isLoading: Boolean = true,
    val selectedLearningLanguage: LangCode? = null,
    val summary: DashSummary? = null,
    val errorMessage: UiText? = null,
    val isEmpty: Boolean = false,
    val learningLanguages: List<LangCode> = emptyList(),
    // DASH-006 AC 7: 언어 변경 저장 중 중복 요청이 방지된다.
    //   true 인 동안 selector 비활성화 + onChangeLearningLanguage 진입 가드.
    //   isLoading(preload 중) 과 의미 분리 — 사용자 선택 후 persist 진행 중.
    val isChangingLanguage: Boolean = false,
    // DASH-006 AC 10: learningLanguages가 비어 있거나 로드 실패 시 Error 또는 Empty 상태가 표시된다.
    //   true 면 DashboardError 화면 분기. 재시도 버튼은 onEnter() 재호출.
    //   userPref==null 인 신규 사용자는 여기 해당 안 됨 (Empty 분기로).
    val hasFatalError: Boolean = false,
)