package com.app.umma.domain.repository

import com.app.umma.domain.model.realtime.ChatUsageRecord

/**
 * AI Chat usage의 local-first 저장과 remote aggregate sync를 담당하는 repository 계약입니다.
 *
 * SessionMemory는 대화 내용 source of truth이고, 이 repository는 비용 분석용 운영 데이터를 다룹니다.
 * 두 책임을 분리해야 usage 저장 실패가 대화 저장/교정 신호 흐름을 막지 않습니다.
 */
interface ChatUsageRepository {
    suspend fun recordUsage(record: ChatUsageRecord): Result<Unit>
    suspend fun syncSessionUsage(userId: String, sessionId: String): Result<Int>
    suspend fun syncPendingUsage(userId: String): Result<Int>
    suspend fun cleanupSyncedUsage(userId: String): Result<Int>

    /**
     * 회원탈퇴 시 Chat usage local 원본 row를 모두 비운다.
     *
     * usage row는 비용 집계용 보조 데이터지만 userId/sessionId를 포함하므로,
     * 탈퇴 후 같은 설치에서 다음 계정이 이전 사용자의 pending usage를 재시도하지 않게 정리한다.
     */
    suspend fun clearLocal(): Result<Unit> = Result.success(Unit)
}
