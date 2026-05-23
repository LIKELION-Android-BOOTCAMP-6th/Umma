package com.example.umma.presentation.statistics.model

import com.example.umma.domain.model.statistics.MetricHistoryPoint
import com.example.umma.domain.model.statistics.StatisticsMetricType

/**
 * Statistics 화면에서 선택한 metric의 chart dialog 상태다.
 *
 * 카드 선택은 유지하되, 실제 chart 데이터는 이 별도 상태로 열고 닫는다.
 */
sealed interface StatisticsMetricChartState {
    /**
     * 아직 차트 dialog를 열지 않은 상태다.
     */
    data object Hidden : StatisticsMetricChartState

    /**
     * 선택 metric에 대한 history point를 불러오는 중이다.
     */
    data class Loading(
        val metricType: StatisticsMetricType
    ) : StatisticsMetricChartState

    /**
     * 최소 2개 이상의 point가 준비되어 실제 line chart를 그릴 수 있는 상태다.
     */
    data class Ready(
        val metricType: StatisticsMetricType,
        val points: List<MetricHistoryPoint>
    ) : StatisticsMetricChartState

    /**
     * history가 너무 적어서 chart 대신 안내만 보여줘야 하는 상태다.
     */
    data class Empty(
        val metricType: StatisticsMetricType,
        val message: String = "차트를 그릴 만큼의 기록이 아직 충분하지 않아요."
    ) : StatisticsMetricChartState

    /**
     * history 조회 실패로 chart를 못 그리는 상태다.
     */
    data class Error(
        val metricType: StatisticsMetricType,
        val message: String,
        val isRetryable: Boolean = true
    ) : StatisticsMetricChartState
}

val StatisticsMetricChartState.isVisible: Boolean
    // Dialog 표시 여부는 상태 타입만 보면 되도록 Composable 밖에 둔다.
    get() = this !is StatisticsMetricChartState.Hidden

/**
 * line chart에 바로 넣을 수 있는 point 목록을 dialog 상태로 바꾼다.
 *
 * point가 2개 미만이면 line chart 대신 Empty 안내를 유지한다.
 */
fun List<MetricHistoryPoint>.toStatisticsMetricChartState(
    metricType: StatisticsMetricType
): StatisticsMetricChartState {
    // Repository가 정렬된 값을 주더라도 presentation 경계에서 한 번 더 정렬해
    // preview/test/real 흐름 모두 같은 chart 입력 순서를 보장한다.
    val sortedPoints = sortedBy { it.recordedAt }
    return if (sortedPoints.size < 2) {
        // line chart는 최소 두 점이 있어야 의미가 있으므로 더미 선을 만들지 않는다.
        StatisticsMetricChartState.Empty(metricType = metricType)
    } else {
        StatisticsMetricChartState.Ready(
            metricType = metricType,
            points = sortedPoints
        )
    }
}
