package com.app.umma.domain.repository

import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.FlashcardSummary
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.UserLangPref
import com.app.umma.domain.model.user.UserProfile

interface UserProfileRepository {
    /**
     * 최초 로그인 사용자 여부 확인
     * 판단 기준 : users/{uid} 존재 여부
     */
    suspend fun isNewUser(uid: String): Boolean

    /** AUTH-004: 초기 설정 데이터 저장 */
    suspend fun saveInitialSetup(
        profile: UserProfile,
        langPref: UserLangPref,
        initialLangState: LangState,
        dashSummary: DashSummary,
        sessionSummary: SessionSummary,
        flashcardSummary: FlashcardSummary
    ): Result<Unit>

    /**
     * 사용자 프로필 전체 조회
     * @param uid 사용자 UID
     * @return 조회한 유저의 문서, 문서가 없다면 null
     */
    suspend fun getUserProfile(uid: String): UserProfile?

    /**
     * @param uid 사용자 UID
     * @param topics 문자열 리스트 (예: "Travel", "FOOD")
     */
    suspend fun saveInterestTopics(uid: String, topics: List<String>): Result<Unit>
}