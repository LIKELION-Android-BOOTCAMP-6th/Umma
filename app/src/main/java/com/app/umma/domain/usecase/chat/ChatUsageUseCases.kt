package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.realtime.ChatUsageRecord
import com.app.umma.domain.repository.ChatUsageRepository
import javax.inject.Inject

/**
 * OpenAI Realtime usage 이벤트를 local-first 저장소에 기록합니다.
 */
class RecordChatUsageUseCase @Inject constructor(
    private val chatUsageRepository: ChatUsageRepository
) {
    suspend operator fun invoke(record: ChatUsageRecord): Result<Unit> {
        return chatUsageRepository.recordUsage(record)
    }
}

/**
 * 특정 AI Chat 세션의 pending usage를 Firestore aggregate로 동기화합니다.
 *
 * 화면 이탈 또는 세션 종료 시점에 호출해 Firestore write를 세션 단위로 줄입니다.
 */
class SyncChatSessionUsageUseCase @Inject constructor(
    private val chatUsageRepository: ChatUsageRepository
) {
    suspend operator fun invoke(userId: String, sessionId: String): Result<Int> {
        return chatUsageRepository.syncSessionUsage(userId, sessionId)
    }
}

/**
 * 이전 세션에서 남은 pending usage를 재시도합니다.
 *
 * 네트워크 실패나 앱 종료로 원격 sync가 밀린 경우, 다음 Chat 진입 시 사용자 단위로 복구합니다.
 */
class SyncPendingChatUsageUseCase @Inject constructor(
    private val chatUsageRepository: ChatUsageRepository
) {
    suspend operator fun invoke(userId: String): Result<Int> {
        return chatUsageRepository.syncPendingUsage(userId)
    }
}

/**
 * Firestore sync가 끝난 오래된 usage 원본 row를 정리합니다.
 *
 * PENDING row는 remote aggregate에 아직 반영되지 않은 원본이므로 삭제하지 않습니다.
 * 이 usecase는 local 저장공간 관리만 담당하며, 서버의 일/월 aggregate 보존 정책과 분리됩니다.
 */
class CleanupChatUsageUseCase @Inject constructor(
    private val chatUsageRepository: ChatUsageRepository
) {
    suspend operator fun invoke(userId: String): Result<Int> {
        return chatUsageRepository.cleanupSyncedUsage(userId)
    }
}
