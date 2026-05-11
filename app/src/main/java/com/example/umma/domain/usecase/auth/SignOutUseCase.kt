package com.example.umma.domain.usecase.auth

import com.example.umma.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * 로그아웃을 수행하는 UseCase.
 */
class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Result<Unit> = authRepository.signOut()
}
