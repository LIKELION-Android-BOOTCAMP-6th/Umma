package com.app.umma.core.navigation

import kotlinx.serialization.Serializable

sealed interface Route {
    // Auth Nested Graph(구성 요소: 로그인, 온보딩)
    @Serializable
    data object AuthGraph : Route
    // Main Graph (구성 요소: 통계, 교정, 챗, 피드백)
    @Serializable data object HomeGraph: Route

    @Serializable data object SrsStudyGraph: Route

    @Serializable data object CorrectionGraph: Route

    @Serializable data object ChatGraph: Route

    @Serializable data object StatisticsGraph: Route
    // 스플래시 (앱 진입)
    @Serializable
    data object AppEntry:Route
    // 1. 온보딩
    @Serializable
    data object OnBoarding : Route

    // 3. 통계
    @Serializable
    data object Statistics : Route

    // 4. 대시보드
    @Serializable
    data object Dashboard : Route

    // 5. 대화
    @Serializable
    data object Chat : Route

    // 6. 마이페이지
    @Serializable
    data object MyPage : Route

    // 7. 교정
    @Serializable
    data object CorrectionList : Route

    // 8. SRS 반복학습
    @Serializable
    data object SrsStudy : Route

    // 9. SRS 저장 카드 목록
    @Serializable
    data object SrsCardList : Route

}
