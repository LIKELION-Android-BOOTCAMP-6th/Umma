package com.example.umma.presentation.statistics

import com.example.umma.domain.model.statistics.StatisticsHistoryQueryState
import com.example.umma.domain.model.statistics.StatisticsMetricType
import com.example.umma.domain.model.statistics.StatisticsOverview
import com.example.umma.presentation.statistics.model.StatisticsMetricSummaryItem
import com.example.umma.presentation.statistics.model.StatisticsMetricChartState

/**
 * Statistics 화면이 바로 렌더링할 수 있는 상태다.
 *
 * STAT-001에서는 카드/차트보다 먼저 현재 선택 언어와 query 준비 상태를 보여준다.
 */
data class StatisticsUiState(
    // 진입 직후 local cache preload + overview 조립 중에는 true.
    // 로딩 중에도 화면이 깨지지 않도록 StatisticsScreen이 이 값을 먼저 본다.
    val isLoading: Boolean = true,
    // 초기 컨텍스트 조립 결과. 성공 시 화면은 이 스냅샷을 기준으로 표시한다.
    val overview: StatisticsOverview? = null,
    // STAT-002에서 사용할 5개 요약 카드 state.
    val metricSummaryCards: List<StatisticsMetricSummaryItem> = emptyList(),
    // 지금 선택된 metric. STAT-003에서 chart 입력으로 이어받을 값이다.
    val selectedMetricType: StatisticsMetricType? = null,
    // 선택 metric에 대한 chart dialog 상태.
    val metricChartState: StatisticsMetricChartState = StatisticsMetricChartState.Hidden,
    // 실패 시 사용자에게 보여줄 메시지.
    val errorMessage: String? = null,
    // 실패나 네트워크 문제 등으로 다시 시도할 수 있는지 여부.
    // STAT-001은 retry만 제공하고, 실제 history/chart data 재계산은 후속 이슈가 담당한다.
    val isRetryable: Boolean = false
) {
    // UI가 자주 꺼내 쓰는 값은 state 내부에서 바로 접근할 수 있게 계산 프로퍼티로 둔다.
    val selectedLearningLanguage = overview?.selectedLearningLanguage
    val currentExternalMetrics = overview?.currentExternalMetrics
    val historyQueryState: StatisticsHistoryQueryState? = overview?.historyQueryState
    val selectedChartMetricType = when (val state = metricChartState) {
        is StatisticsMetricChartState.Loading -> state.metricType
        is StatisticsMetricChartState.Ready -> state.metricType
        is StatisticsMetricChartState.Empty -> state.metricType
        is StatisticsMetricChartState.Error -> state.metricType
        StatisticsMetricChartState.Hidden -> null
    }
}
