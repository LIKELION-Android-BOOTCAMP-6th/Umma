package com.app.umma.domain.usecase.auth

import com.app.umma.domain.repository.SessionRepository
import javax.inject.Inject

/**
 * 현재 기기의 로컬 sessionId가 서버의 activeSession.sessionId와 일치하는지 확인한다.
 * FCM 유실 등으로 force_logout을 받지 못한 경우의 fallback 검증에 사용한다.
 */
class IsSessionStillActiveUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(uid: String): Result<Boolean> = sessionRepository.isCurrentSessionActive(uid)
}
