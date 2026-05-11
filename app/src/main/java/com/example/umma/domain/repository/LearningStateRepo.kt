package com.example.umma.domain.repository

import com.example.umma.domain.model.learningstate.DashSummary
import com.example.umma.domain.model.learningstate.LangStateUpdateInput
import com.example.umma.domain.model.learningstate.FlashcardSummary
import com.example.umma.domain.model.learningstate.GlobalLangState
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.LangState
import com.example.umma.domain.model.learningstate.SessionSummary
import com.example.umma.domain.model.learningstate.UserLangPref
import kotlinx.coroutines.flow.Flow

/**
 * 학습 상태의 로컬/원격 저장과 동기화 경계를 숨기는 저장소 계약.
 *
 * updateLanguageState는 UseCase가 계산한 preparedState를 원자적으로 저장한다.
 */
interface LearningStateRepo {
    // 전역 상태 스냅샷을 한번에 구독한다.
    fun observeLearningState(): Flow<GlobalLangState>
    // 사용자의 언어 선택을 구독한다.
    fun observeUserPref(): Flow<UserLangPref?>
    // 현재 언어의 장기 상태를 구독한다.
    fun observeLangState(lang: LangCode): Flow<LangState?>
    // 대시보드 요약을 구독한다.
    fun observeDashSummary(lang: LangCode): Flow<DashSummary?>
    // 세션 요약을 구독한다.
    fun observeSessionSummary(lang: LangCode): Flow<SessionSummary?>
    // 플래시카드 요약을 구독한다.
    fun observeFlashcardSummary(lang: LangCode): Flow<FlashcardSummary?>

    // 앱 시작 시 local cache를 채운다.
    suspend fun preload(): Result<Unit>
    // 현재 선택 언어를 바꾼다.
    suspend fun changeSelectedLang(lang: LangCode): Result<Unit>
    // UseCase가 계산한 preparedState를 저장한다.
    suspend fun updateLanguageState(input: LangStateUpdateInput): Result<Unit>
    // 신규 사용자 첫 상태를 만든다.
    suspend fun createInitial(
        userUid: String,
        userPref: UserLangPref,
        langState: LangState,
        dashSummary: DashSummary,
        sessionSummary: SessionSummary,
        flashcardSummary: FlashcardSummary
    ): Result<Unit>
    // 로그아웃 시 상태를 비운다.
    suspend fun clear(): Result<Unit>
}
