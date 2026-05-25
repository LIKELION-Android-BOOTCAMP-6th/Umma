package com.example.umma.domain.usecase.statistics

import com.example.umma.domain.model.statistics.StatisticsHistoryQueryState
import com.example.umma.domain.model.statistics.StatisticsHistoryState
import com.example.umma.domain.repository.StatisticsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Statistics 화면이 history observe 경로를 안전하게 구독하도록 감싼다.
 *
 * query state가 준비되지 않았으면 repository를 억지로 호출하지 않고,
 * 화면이 Error/Empty 분기로 정리할 수 있는 상태를 돌려준다.
 */
class ObserveStatisticsHistoryUseCase @Inject constructor(
    private val statisticsRepository: StatisticsRepository
) {
    operator fun invoke(queryState: StatisticsHistoryQueryState): Flow<StatisticsHistoryState> {
        // queryState가 아직 준비되지 않았으면 repository를 호출하지 않고,
        // 화면이 Error/Retry 분기로 끝나도록 즉시 돌려준다.
        val readyState = queryState as? StatisticsHistoryQueryState.Ready
            ?: return flowOf(
                StatisticsHistoryState.Error(
                    IllegalStateException(
                        // 화면은 준비되지 않은 query state를 history 없음이 아니라 명시적 실패로 다룬다.
                        when (queryState) {
                            is StatisticsHistoryQueryState.Unavailable -> queryState.reason
                            else -> "statistics history query state is unavailable"
                        }
                    )
                )
            )

        return statisticsRepository.observeHistory(readyState.userId, readyState.language)
    }
}

/**
 * Statistics 화면이 Firestore refresh를 local cache에 보정하도록 요청하는 얇은 UseCase다.
 */
class RefreshStatisticsHistoryUseCase @Inject constructor(
    private val statisticsRepository: StatisticsRepository
) {
    suspend operator fun invoke(queryState: StatisticsHistoryQueryState): Result<Unit> {
        // refresh도 observe와 같은 준비 조건을 요구한다.
        // 준비되지 않은 상태에서 Firestore refresh를 시도하면 잘못된 uid/language를 갱신할 수 있다.
        val readyState = queryState as? StatisticsHistoryQueryState.Ready
            ?: return Result.failure(
                IllegalStateException(
                    // refresh도 같은 guard를 써야 uid/language가 섞인 잘못된 보정 요청을 막을 수 있다.
                    when (queryState) {
                        is StatisticsHistoryQueryState.Unavailable -> queryState.reason
                        else -> "statistics history query state is unavailable"
                    }
                )
            )

        return statisticsRepository.refreshHistory(readyState.userId, readyState.language)
    }
}

/**
 * local에 남은 StatisticsHistory pending write-back을 재시도하는 UseCase다.
 *
 * 화면은 이미 local history를 볼 수 있으므로 실패를 fatal error로 다루지 않는다.
 * 성공한 row만 SYNCED로 정리되고, 실패 row는 PENDING으로 남아 다음 재진입 때 다시 시도된다.
 */
class SyncPendingStatisticsHistoriesUseCase @Inject constructor(
    private val statisticsRepository: StatisticsRepository
) {
    suspend operator fun invoke(userId: String): Result<Int> {
        // userId 단위로 pending을 정리해 현재 화면 언어가 아닌 이전 기록도 함께 복구한다.
        return statisticsRepository.syncPendingHistories(userId)
    }
}
