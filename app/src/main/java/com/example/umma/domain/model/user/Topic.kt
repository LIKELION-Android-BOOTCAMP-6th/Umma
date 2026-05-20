package com.example.umma.domain.model.user

/**
 * 사용자가 선택하는 관심 대화 주제 5개
 * 정확히 5개 선택해야 가능
 *
 * 대화 첫 진입 시 사용자에게 다이얼로그 표시
 * DB 저장될 때는 name이 저장 ("TRAVEL")
 */
enum class Topic(val displayName: String) {
    TRAVEL("여행"),
    FOOD("음식"),
    MOVIE("영화"),
    MUSIC("음악"),
    GAME("게임"),
    DAILY_CONVERSATION("일상"),
    BUSINESS("비즈니스"),
    STUDY("학업")
}