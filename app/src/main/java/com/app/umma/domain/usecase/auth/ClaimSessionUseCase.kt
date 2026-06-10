package com.app.umma.domain.usecase.auth

import com.app.umma.domain.repository.SessionRepository
import javax.inject.Inject

/**
 * 로그인 성공 직후 호출하여 서버에 현재 기기를 활성 세션으로 claim한다.
 */
class ClaimSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(): Result<Unit> = sessionRepository.claimSession()
}
