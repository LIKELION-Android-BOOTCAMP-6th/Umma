package com.app.umma.domain.usecase.auth

import com.app.umma.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * 구글 ID 토큰을 사용하여 Firebase 로그인을 수행하는 UseCase.
 */
class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val claimSessionUseCase: ClaimSessionUseCase
) {
    suspend operator fun invoke(idToken: String): Result<String> {
        val result = authRepository.signInWithGoogle(idToken)
        if (result.isSuccess) {
            // 세션 claim은 best-effort: 실패해도 로그인 결과 자체는 성공으로 유지한다.
            claimSessionUseCase()
        }
        return result
    }
}
