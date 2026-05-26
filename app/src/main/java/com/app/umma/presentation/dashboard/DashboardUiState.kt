package com.app.umma.presentation.dashboard

import com.app.umma.core.ui.UiText
import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.LangCode

data class DashboardUiState(
    // 진입 직후 첫 프레임에서 Skeleton 이 즉시 보이도록 true 로 시작.
    val isLoading: Boolean = true,

    // 현재 선택된 학습 언어. selector 칩 라벨 + summary 분기의 기준.
    //   null = 신규 사용자 / preload 직후. ViewModel 의 effectiveLang 결과를 그대로 받음.
    val selectedLearningLanguage: LangCode? = null,

    // selectedLearningLanguage 기준 카드 데이터.
    //   null 이거나 isEffectivelyEmpty 면 isEmpty=true 로 분기.
    val summary: DashSummary? = null,

    // Snackbar 로 표면화할 에러 메시지. null 이면 표시 안 함.
    //   localization 을 위해 String 이 아니라 UiText 타입.
    val errorMessage: UiText? = null,

    // Empty 분기 여부. 신규 / 활동 0 회 사용자에게 Empty UI 노출.
    val isEmpty: Boolean = false,

    // 사용자가 학습 중인 모든 언어. selector 의 dropdown 목록 출처.
    val learningLanguages: List<LangCode> = emptyList(),

    // 실제 학습 데이터가 있는 언어 집합 — DashSummary 중 isEffectivelyEmpty=false 인 것.
    //   selector 다이얼로그에서 "이전에 학습 중이던 언어" 체크 아이콘 표시 기준.
    //   docs LS-003 의 learningLanguages (=userPref 의 학습 가능 목록) 와는 의미가 다르다:
    //   여기는 "실제로 대화/카드/통계 데이터가 쌓인 언어" 만 포함한다. selector 클릭만으로는
    //   learningLanguages 에는 자동 추가되지만 activeLearningLanguages 에는 안 들어간다 →
    //   "선택만 한 언어" 와 "정말 학습 중인 언어" 를 시각적으로 분리.
    val activeLearningLanguages: Set<LangCode> = emptySet(),

    // DASH-006 AC 7: 언어 변경 저장 중 중복 요청이 방지된다.
    //   true 인 동안 selector 비활성화 + onChangeLearningLanguage 진입 가드.
    //   isLoading(preload 중) 과 의미 분리 — 사용자 선택 후 persist 진행 중.
    val isChangingLanguage: Boolean = false,

    // DASH-006 AC 10: learningLanguages가 비어 있거나 로드 실패 시 Error 또는 Empty 상태가 표시된다.
    //   true 면 DashboardError 화면 분기. 재시도 버튼은 onEnter() 재호출.
    //   userPref==null 인 신규 사용자는 여기 해당 안 됨 (Empty 분기로).
    val hasFatalError: Boolean = false,
)