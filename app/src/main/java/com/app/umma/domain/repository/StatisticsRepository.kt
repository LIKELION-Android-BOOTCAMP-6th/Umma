package com.app.umma.domain.repository

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.statistics.StatisticsHistory
import com.app.umma.domain.model.statistics.StatisticsHistoryRecordResult
import com.app.umma.domain.model.statistics.StatisticsHistoryState
import kotlinx.coroutines.flow.Flow

/**
 * Statistics history의 조회 경계를 숨기는 저장소 계약이다.
 *
 * STI-001에서는 우선 query 계약과 fake/real 교체 기준을 고정하고,
 * 실제 local persistence write는 후속 STI-002에서 이어 붙인다.
 */
interface StatisticsRepository {
    /**
     * 사용자와 언어 기준으로 history를 관찰한다.
     *
     * 구현체는 local cache를 먼저 내보내고, 필요한 경우 stale cache 보정을 뒤에 붙인다.
     */
    fun observeHistory(userId: String, language: LangCode): Flow<StatisticsHistoryState>

    /**
     * Correction 완료 후 만들어진 history snapshot 을 local-first로 기록한다.
     *
     * 구현체는 local 저장 성공을 우선 기준으로 삼고, remote sync 실패는 pending 으로 남긴다.
     */
    suspend fun recordHistory(history: StatisticsHistory): Result<StatisticsHistoryRecordResult>

    /**
     * Firestore의 최신 history를 local cache에 보정한다.
     *
     * 화면은 이 결과를 직접 그리지 않고 local observe 결과를 다시 읽는다.
     * 기존 usecase 테스트의 단순 test double은 refresh를 사용하지 않으므로 기본 실패 구현을 둔다.
     * 실제 STAT-004 경로에서 쓰는 real/fake repository는 반드시 override한다.
     */
    suspend fun refreshHistory(userId: String, language: LangCode): Result<Unit> {
        return Result.failure(UnsupportedOperationException("refreshHistory is not implemented"))
    }

    /**
     * local에 PENDING으로 남은 StatisticsHistory를 Firestore mirror로 다시 올린다.
     *
     * refreshHistory가 remote -> local 보정이라면, 이 함수는 local -> remote write-back이다.
     * 실제 STAT-004 경로의 real/fake repository는 반드시 override한다.
     */
    suspend fun syncPendingHistories(userId: String): Result<Int> {
        return Result.failure(UnsupportedOperationException("syncPendingHistories is not implemented"))
    }

    /**
     * 회원탈퇴 시 이 Repository 가 소유한 로컬 영속 데이터를 모두 비운다.
     * domain UseCase 가 Room 구현체를 알지 않도록 정리 계약을 추상화 경계에 둔다.
     * 실제 정리는 data 레이어 Impl 이 override 하며, fake/test 더블은 no-op 으로 둔다.
     */
    suspend fun clearLocal(): Result<Unit> = Result.success(Unit)
}
