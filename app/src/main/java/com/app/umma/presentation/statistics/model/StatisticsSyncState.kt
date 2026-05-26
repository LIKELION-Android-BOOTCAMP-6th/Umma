package com.app.umma.presentation.statistics.model

import com.app.umma.domain.model.learningstate.SyncStatus
import com.app.umma.domain.model.statistics.StatisticsHistoryState

/**
 * Statistics 화면이 local-first history와 background refresh 상태를 사용자에게 보여주기 위한 상태다.
 *
 * refresh는 화면 전체 실패와 분리하고, pending sync는 local history가 사라지지 않게
 * 작은 보조 상태로만 표현한다.
 */
sealed interface StatisticsSyncState {
    data object Idle : StatisticsSyncState

    data object Refreshing : StatisticsSyncState

    data class Pending(val message: String = "동기화 대기 중") : StatisticsSyncState

    data class Error(
        val message: String,
        val isRetryable: Boolean = true
    ) : StatisticsSyncState
}

fun resolveStatisticsSyncState(historyState: StatisticsHistoryState): StatisticsSyncState {
    return when (historyState) {
        // 데이터가 없으면 동기화 보조 상태도 숨기고 기본 화면만 유지한다.
        StatisticsHistoryState.Empty -> StatisticsSyncState.Idle
        is StatisticsHistoryState.Content -> {
            // local cache 안에 pending row가 하나라도 있으면, 사용자에게는 "동기화 대기"만 보인다.
            if (historyState.histories.any { it.syncStatus != SyncStatus.SYNCED }) {
                StatisticsSyncState.Pending()
            } else {
                StatisticsSyncState.Idle
            }
        }
        // 조회 실패는 chart/summary의 local first 화면과 분리된 보조 에러 상태로만 노출한다.
        is StatisticsHistoryState.Retry -> StatisticsSyncState.Error(
            message = historyState.cause?.message ?: "history 조회에 실패했습니다."
        )
        // repository가 명시적으로 error를 준 경우도 UI 전체 fatal error가 아니라 동기화 보조 에러로 취급한다.
        is StatisticsHistoryState.Error -> StatisticsSyncState.Error(
            message = historyState.cause?.message ?: "history 조회에 실패했습니다."
        )
    }
}
