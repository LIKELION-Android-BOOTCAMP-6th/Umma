package com.example.umma.domain.usecase.auth

import com.example.umma.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 현재 로그인된 유저의 UID를 실시간으로 관찰하는 UseCase.
 * 로그아웃 시 null을 반환합니다.
 */
class GetCurrentUserUidUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Flow<String?> = authRepository.currentUserUid

    /** 동기, 즉시 조회*/
    fun getCurrentUserUid(): String? = authRepository.getCurrentUserUid()
    /** 동기, 즉시 조회*/
    fun getCurrentUserEmail(): String? = authRepository.getCurrentUserEmail()
}
