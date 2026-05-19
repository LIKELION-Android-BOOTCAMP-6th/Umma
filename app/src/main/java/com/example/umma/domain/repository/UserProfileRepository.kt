package com.example.umma.domain.repository

import com.example.umma.domain.model.learningstate.DashSummary
import com.example.umma.domain.model.learningstate.FlashcardSummary
import com.example.umma.domain.model.learningstate.LangState
import com.example.umma.domain.model.learningstate.SessionSummary
import com.example.umma.domain.model.learningstate.UserLangPref
import com.example.umma.domain.model.user.UserProfile

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
}