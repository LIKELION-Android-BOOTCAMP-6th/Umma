package com.app.umma.domain.usecase.realtime

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.repository.SessionMemoryRepository
import javax.inject.Inject

/**
 * 네트워크 연결 상태 복구 시점 등에 로컬에 잔존하는 미동기화 턴(PENDING, FAILED)을 일괄 동기화하는 비즈니스 유스케이스입니다.
 */
class SyncPendingTurnsUseCase @Inject constructor(
    private val repository: SessionMemoryRepository
) {
    suspend operator fun invoke(language: LangCode): Result<Unit> {
        return repository.syncPendingTurns(language)
    }
}
