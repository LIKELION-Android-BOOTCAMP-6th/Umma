package com.example.umma.core.navigation

import kotlinx.serialization.Serializable

interface Route {
    // 1. 온보딩
    @Serializable data object OnBoarding: Route

    // 2. 로그인
    @Serializable data object SignIn: Route

    // 3. 통계
    @Serializable data object Analytics: Route

    // 4. 대시보드
    @Serializable data object Home: Route

    // 5. 대화
    @Serializable data object Chat: Route

    // 6. 마이페이지
    @Serializable data object MyPage: Route

    // 7. 교정
    @Serializable data object FeedbackList: Route

    // 8. 교정 상세
    @Serializable data object FeedbackDetail: Route


    // 9. 학습
    @Serializable data object StudyList: Route

    // 10. 학습 상세
    @Serializable data object StudyDetail: Route
}