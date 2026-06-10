package com.app.umma.domain.usecase.auth

import com.app.umma.domain.repository.LearningStateRepo
import com.app.umma.domain.repository.ChatConversationAnalysisJobRepository
import com.app.umma.domain.repository.SessionRepository
import com.app.umma.domain.usecase.notification.UnregisterNotificationDeviceUseCase
import javax.inject.Inject

/**
 * 순서
 * 1. SignOutUseCase 는 Firebase/Google 세션 해제만 담당
 * 2. 로그아웃 시 필요한 메모리(RAM) 초기화
 *
 * 1번 실패 -> 즉시 중단, 실패 반환
 * 2번 실패 -> 로그아웃은 이미 완료된 상태 성공으로 판단
 *
 * Firebase/Google 세션 해제(SignOutUseCase)
 * GlobalLangState 인메모리 초기화 (LearningStateRepo.clear)
 */
class LogoutUseCase @Inject constructor(
    private val signOutUseCase: SignOutUseCase,
    private val learningStateRepo: LearningStateRepo,
    private val chatConversationAnalysisJobRepository: ChatConversationAnalysisJobRepository,
    private val unregisterNotificationDeviceUseCase: UnregisterNotificationDeviceUseCase,
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        unregisterNotificationDeviceUseCase()
        val signOutResult = signOutUseCase()
        // 1단계: Firebase + Google 세션 해제
        // 실패하면 로그인 상태이므로 즉시 중단
        if (signOutResult.isFailure) {
            return signOutResult
        }

        // 2단계: 앱 메모리에 남아있는 사용자 데이터 초기화
        // DataStore - createInitial() 로 저장했던 모든 데이터 삭제
        // (UserLangPref, LangState, DashSummary, SessionSummary, FlashcardSummary)
        // 다음 사용자가 로그인했을 때 이전 사용자 데이터가 보이지 않도록
        learningStateRepo.clear()
        // Chat 분석 pending job은 로그인 사용자 기준으로 재시도되므로 로그아웃 시 같이 제거한다.
        // 실패해도 sign-out은 완료된 상태라 로그아웃 흐름을 되돌리지는 않는다.
        chatConversationAnalysisJobRepository.clearAll()
        // 다음 로그인 시 새로 claim 받을 수 있도록 로컬 sessionId를 제거한다.
        // (강제 로그아웃 안내 플래그는 여기서 건드리지 않는다 - FCM force_logout 핸들러만 설정한다)
        sessionRepository.clearLocalSessionId()
        return Result.success(Unit)

    }
}
