package com.app.umma.domain.usecase.auth

import com.app.umma.core.logging.AuthFlowLog
import com.app.umma.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * 회원탈퇴 서버 요청과 로컬 정리를 순서대로 수행한다.
 */
class DeleteAccountUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val clearUserLocalDataUseCase: ClearUserLocalDataUseCase,
    private val signOutUseCase: SignOutUseCase
) {

    /**
     * Cloud Function이 계정 삭제를 완료한 뒤 로컬 사용자 데이터를 정리한다.
     */
    suspend operator fun invoke(): Result<Unit> {
        val remoteDeleteResult = authRepository.deleteAccount()
        if (remoteDeleteResult.isFailure) {
            return remoteDeleteResult
        }

        // 원격 삭제가 끝난 뒤에는 로컬 정리가 일부 실패해도 로그인 해제 상태로 복귀시키는 것이 우선이다.
        clearUserLocalDataUseCase()
            .onFailure { error ->
                AuthFlowLog.w("Local cleanup failed after remote account deletion.", error)
            }
        signOutUseCase()
            .onFailure { error ->
                AuthFlowLog.w("Sign-out cleanup failed after remote account deletion.", error)
            }
        return Result.success(Unit)
    }
}
