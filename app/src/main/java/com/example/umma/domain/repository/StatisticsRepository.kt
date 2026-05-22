package com.example.umma.domain.repository

import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.statistics.StatisticsHistory
import com.example.umma.domain.model.statistics.StatisticsHistoryState
import com.example.umma.domain.model.statistics.StatisticsHistoryRecordResult
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
}
