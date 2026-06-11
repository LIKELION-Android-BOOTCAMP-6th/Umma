package com.app.umma.domain.repository

import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.FlashcardSummary
import com.app.umma.domain.model.learningstate.GlobalLangState
import com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateInput
import com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateResult
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateInput
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateResult
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.OnboardingGuideStage
import com.app.umma.domain.model.learningstate.LangStateUpdateInput
import com.app.umma.domain.model.learningstate.LearningStateUpdateResult
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.UserLangPref
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

    // 사용자 언어 변경
    suspend fun changePrimaryLang(lang: LangCode): Result<Unit>

    // UseCase가 계산한 preparedState를 저장하고, 후속 기록 흐름이 사용할 완료 결과를 돌려준다.
    suspend fun updateLanguageState(input: LangStateUpdateInput): Result<LearningStateUpdateResult>

    // SRS가 계산한 복습 요약 수치를 전역 Summary에 반영한다.
    suspend fun updateFlashcardSummary(
        input: FlashcardSummaryUpdateInput
    ): Result<FlashcardSummaryUpdateResult>

    // AI Chat final turn 이후 correctionAvailable 신호만 가볍게 반영한다.
    suspend fun updateCorrectionSignal(
        input: CorrectionSignalUpdateInput
    ): Result<CorrectionSignalUpdateResult> = Result.failure(
        UnsupportedOperationException("updateCorrectionSignal is not implemented")
    )

    // 신규 사용자 첫 상태를 만든다.
    suspend fun createInitial(
        userUid: String,
        userPref: UserLangPref,
        langState: LangState,
        dashSummary: DashSummary,
        sessionSummary: SessionSummary,
        flashcardSummary: FlashcardSummary
    ): Result<Unit>

    // 온보딩 가이드 단계를 저장한다. 언어별 독립 진행.
    suspend fun setOnboardingGuideStage(lang: LangCode, stage: OnboardingGuideStage): Result<Unit>

    /**
     * 온보딩 stage 전이를 원자적으로 적용한다.
     *
     * [transition] 은 현재 stage 를 받아 다음 stage 를 돌려주는 순수 정책 함수다(전이 없으면 null).
     * 구현체는 "현재 stage read → transition 계산 → 저장" 을 단일 임계구역에서 수행해,
     * 동시에 발생한 두 전이(예: CorrectionAvailable 와 CorrectionSaved)가 같은 stale stage 를
     * 각자 읽고 서로의 결과를 덮어쓰는 lost update 를 막는다.
     *
     * 전이표(정책)는 호출 UseCase 가 [transition] 으로 주입한다 — Repository 는 원자적 저장만 책임진다.
     * 미구현 test double 호환을 위해 기본 구현은 실패를 돌려준다(updateCorrectionSignal 과 동일 패턴).
     */
    suspend fun advanceOnboardingGuideStage(
        lang: LangCode,
        transition: (current: OnboardingGuideStage) -> OnboardingGuideStage?,
    ): Result<Unit> = Result.failure(
        UnsupportedOperationException("advanceOnboardingGuideStage is not implemented")
    )

    // 로그아웃 시 상태를 비운다.
    suspend fun clear(): Result<Unit>

    /**
     * Firebase 에서 최신 LearningState 를 fetch 해서 local cache(_state) 를 갱신.
     *
     * - 성공 시: _state 가 새 값으로 업데이트되면서 observe* Flow 들이 자동 emit.
     *   VM(ViewModel)은 별도 처리 없이 collect 안에서 새 값 받음.
     * - 실패 시: cache 를 건드리지 않음. 호출자가 Result.failure 보고 errorMessage 처리.
     *
     * Idempotent. 동시 호출은 호출자(VM 의 fetchJob)가 막음.
     */
    suspend fun sync(): Result<Unit>
}
