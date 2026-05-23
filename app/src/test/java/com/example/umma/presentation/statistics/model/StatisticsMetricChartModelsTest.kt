package com.example.umma.presentation.statistics.model

import com.example.umma.domain.model.statistics.MetricHistoryPoint
import com.example.umma.domain.model.statistics.StatisticsMetricType
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsMetricChartModelsTest {

    @Test
    fun `returns empty chart state when points are less than two`() {
        // 한 점만 있는 history는 추세선을 만들 수 없으므로 Empty 상태로 내려야 한다.
        val state = listOf(
            MetricHistoryPoint(
                metricType = StatisticsMetricType.GrammarAccuracy,
                recordedAt = 1_000L,
                value = 61.0,
                displayValue = "61%"
            )
        ).toStatisticsMetricChartState(StatisticsMetricType.GrammarAccuracy)

        assertTrue(state is StatisticsMetricChartState.Empty)
    }

    @Test
    fun `returns ready chart state with sorted points`() {
        // presentation mapper도 recordedAt 정렬을 보장해야 preview/fake/real 입력이 흔들리지 않는다.
        val state = listOf(
            MetricHistoryPoint(
                metricType = StatisticsMetricType.GrammarAccuracy,
                recordedAt = 2_000L,
                value = 82.0,
                displayValue = "82%"
            ),
            MetricHistoryPoint(
                metricType = StatisticsMetricType.GrammarAccuracy,
                recordedAt = 1_000L,
                value = 61.0,
                displayValue = "61%"
            )
        ).toStatisticsMetricChartState(StatisticsMetricType.GrammarAccuracy)

        assertTrue(state is StatisticsMetricChartState.Ready)
        val ready = state as StatisticsMetricChartState.Ready
        // 늦게 들어온 point가 앞에 있어도 dialog에는 시간순으로 전달된다.
        assertEquals(1_000L, ready.points.first().recordedAt)
        assertEquals(2, ready.points.size)
    }
}
