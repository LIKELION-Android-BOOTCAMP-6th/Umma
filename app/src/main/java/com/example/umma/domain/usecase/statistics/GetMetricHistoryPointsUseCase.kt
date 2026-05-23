package com.example.umma.domain.usecase.statistics

import com.example.umma.domain.model.statistics.MetricHistoryPoint
import com.example.umma.domain.model.statistics.StatisticsHistoryState
import com.example.umma.domain.model.statistics.StatisticsHistoryQueryState
import com.example.umma.domain.model.statistics.StatisticsMetricType
import com.example.umma.domain.model.statistics.toMetricPoint
import com.example.umma.domain.repository.StatisticsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * StatisticsHistory를 선택 metric의 chart point 목록으로 바꾼다.
 *
 * 이 UseCase는 chart를 직접 그리지 않고, 화면이 읽기 쉬운 point 목록만 돌려준다.
 * point 개수 2개 미만 여부에 따른 Empty/Ready 분기는 presentation 계층이 맡는다.
 */
class GetMetricHistoryPointsUseCase @Inject constructor(
    private val statisticsRepository: StatisticsRepository
) {
    suspend operator fun invoke(
        queryState: StatisticsHistoryQueryState,
        metricType: StatisticsMetricType
    ): Result<List<MetricHistoryPoint>> = runCatching {
        // Statistics 화면 진입에서 userId/language가 준비된 경우에만 history를 조회한다.
        val readyState = queryState as? StatisticsHistoryQueryState.Ready
            ?: throw IllegalStateException(
                when (queryState) {
                    is StatisticsHistoryQueryState.Unavailable -> queryState.reason
                    else -> "history query state is unavailable"
                }
            )

        // Repository는 원본 history 상태만 제공하고, 선택 metric 변환은 domain에서 수행한다.
        when (val historyState = statisticsRepository
            .observeHistory(readyState.userId, readyState.language)
            .first()
        ) {
            // history가 없으면 presentation에서 Empty chart로 바꿀 수 있게 빈 목록을 반환한다.
            StatisticsHistoryState.Empty -> emptyList()
            is StatisticsHistoryState.Content -> historyState.histories
                // 차트 입력은 항상 시간순이어야 하므로 UseCase에서 정렬을 고정한다.
                .sortedBy { it.recordedAt }
                .map { it.toMetricPoint(metricType) }

            is StatisticsHistoryState.Retry -> {
                // Retry/Error는 chart dialog의 Error 상태로 이어질 수 있게 실패로 올린다.
                throw historyState.cause ?: IllegalStateException("statistics history query failed")
            }

            is StatisticsHistoryState.Error -> {
                // 복구 불가 에러도 호출자에게 동일하게 Result.failure로 전달한다.
                throw historyState.cause ?: IllegalStateException("statistics history query failed")
            }
        }
    }
}
