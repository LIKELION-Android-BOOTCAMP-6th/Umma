package com.example.umma.domain.usecase.auth

import com.example.umma.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * 구글 ID 토큰을 사용하여 Firebase 로그인을 수행하는 UseCase.
 */
class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(idToken: String): Result<String> = authRepository.signInWithGoogle(idToken)
}
