package com.app.umma.presentation.statistics.model

import com.app.umma.domain.model.statistics.MetricHistoryPoint
import com.app.umma.domain.model.statistics.StatisticsMetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `ready chart state converts recordedAt to index based x labels`() {
        // timestamp 자체를 x축으로 쓰면 label이 읽기 어렵고 간격이 과도해지므로 index 좌표로 분리한다.
        val state = grammarPoints().toStatisticsMetricChartState(StatisticsMetricType.GrammarAccuracy)

        assertTrue(state is StatisticsMetricChartState.Ready)
        val ready = state as StatisticsMetricChartState.Ready

        assertEquals(0.0, ready.displayModel.displayPoints[0].x, 0.0)
        assertEquals(1.0, ready.displayModel.displayPoints[1].x, 0.0)
        assertEquals("1/1", ready.displayModel.displayPoints[0].xLabel)
        assertEquals("1/2", ready.displayModel.displayPoints[1].xLabel)
    }

    @Test
    fun `duplicates same date labels with compact sequence`() {
        // 같은 날짜의 기록은 정렬 순서를 유지하면서도 축 label에서 서로 구분되어야 한다.
        val displayModel = listOf(
            grammarPoint(recordedAt = JAN_1_2025_KST, value = 61.0, displayValue = "61%"),
            grammarPoint(recordedAt = JAN_1_2025_KST + ONE_HOUR_MILLIS, value = 64.0, displayValue = "64%")
        ).toStatisticsMetricChartDisplayModel(StatisticsMetricType.GrammarAccuracy)

        assertEquals("1/1 #1", displayModel.displayPoints[0].xLabel)
        assertEquals("1/1 #2", displayModel.displayPoints[1].xLabel)
    }

    @Test
    fun `score metric uses fixed percent axis and summary values`() {
        // 점수형 metric은 domain 변환 단계에서 이미 0~100 값이므로 dialog 축도 같은 단위로 고정한다.
        val displayModel = grammarPoints().toStatisticsMetricChartDisplayModel(
            StatisticsMetricType.GrammarAccuracy
        )

        assertEquals(0.0, displayModel.yAxisPolicy.minY, 0.0)
        assertEquals(100.0, displayModel.yAxisPolicy.maxY, 0.0)
        assertEquals(listOf("0%", "25%", "50%", "75%", "100%"), displayModel.yAxisPolicy.labels.map { it.label })
        assertEquals("61%", displayModel.summary.startValue)
        assertEquals("82%", displayModel.summary.currentValue)
        assertEquals("+21%", displayModel.summary.changeValue)
        assertEquals("3개", displayModel.summary.pointCountLabel)
    }

    @Test
    fun `display model handles empty points without crashing`() {
        // Ready 상태는 빈 목록으로 생성되지 않지만, public mapper 직접 호출은 안전한 빈 표시 모델을 반환한다.
        val displayModel = emptyList<MetricHistoryPoint>().toStatisticsMetricChartDisplayModel(
            StatisticsMetricType.GrammarAccuracy
        )

        assertTrue(displayModel.displayPoints.isEmpty())
        assertEquals("-", displayModel.summary.startValue)
        assertEquals("-", displayModel.summary.currentValue)
        assertEquals("-", displayModel.summary.changeValue)
        assertEquals("0개", displayModel.summary.pointCountLabel)
        assertEquals(listOf("0%", "25%", "50%", "75%", "100%"), displayModel.yAxisPolicy.labels.map { it.label })
    }

    @Test
    fun `vocabulary metric keeps ordinal values and CEFR labels`() {
        // VocabularyLevel은 chart 좌표는 1~6 ordinal이지만 사용자는 A1~C2 label로 읽어야 한다.
        val displayModel = listOf(
            vocabularyPoint(recordedAt = JAN_1_2025_KST, value = 2.0, displayValue = "A2"),
            vocabularyPoint(recordedAt = JAN_2_2025_KST, value = 4.0, displayValue = "B2")
        ).toStatisticsMetricChartDisplayModel(StatisticsMetricType.VocabularyLevel)

        assertEquals(1.0, displayModel.yAxisPolicy.minY, 0.0)
        assertEquals(6.0, displayModel.yAxisPolicy.maxY, 0.0)
        assertEquals(listOf("A1", "A2", "B1", "B2", "C1", "C2"), displayModel.yAxisPolicy.labels.map { it.label })
        assertEquals("+2단계", displayModel.summary.changeValue)
    }

    @Test
    fun `expression range uses default scale and expands by even step when needed`() {
        // 기본 표현 범위는 0~10이지만, 기록 값이 넘으면 차트 선이 잘리지 않도록 상한만 확장한다.
        val defaultModel = listOf(
            expressionPoint(recordedAt = JAN_1_2025_KST, value = 4.0, displayValue = "4"),
            expressionPoint(recordedAt = JAN_2_2025_KST, value = 8.0, displayValue = "8")
        ).toStatisticsMetricChartDisplayModel(StatisticsMetricType.ExpressionRange)
        val expandedModel = listOf(
            expressionPoint(recordedAt = JAN_1_2025_KST, value = 9.0, displayValue = "9"),
            expressionPoint(recordedAt = JAN_2_2025_KST, value = 13.0, displayValue = "13")
        ).toStatisticsMetricChartDisplayModel(StatisticsMetricType.ExpressionRange)

        assertEquals(10.0, defaultModel.yAxisPolicy.maxY, 0.0)
        assertEquals(listOf("0", "2", "4", "6", "8", "10"), defaultModel.yAxisPolicy.labels.map { it.label })
        assertEquals(14.0, expandedModel.yAxisPolicy.maxY, 0.0)
        assertEquals("14", expandedModel.yAxisPolicy.labels.last().label)
        assertEquals("+4", expandedModel.summary.changeValue)
    }

    private fun grammarPoints(): List<MetricHistoryPoint> = listOf(
        grammarPoint(recordedAt = JAN_1_2025_KST, value = 61.0, displayValue = "61%"),
        grammarPoint(recordedAt = JAN_2_2025_KST, value = 73.0, displayValue = "73%"),
        grammarPoint(recordedAt = JAN_3_2025_KST, value = 82.0, displayValue = "82%")
    )

    private fun grammarPoint(
        recordedAt: Long,
        value: Double,
        displayValue: String
    ): MetricHistoryPoint = MetricHistoryPoint(
        metricType = StatisticsMetricType.GrammarAccuracy,
        recordedAt = recordedAt,
        value = value,
        displayValue = displayValue
    )

    private fun vocabularyPoint(
        recordedAt: Long,
        value: Double,
        displayValue: String
    ): MetricHistoryPoint = MetricHistoryPoint(
        metricType = StatisticsMetricType.VocabularyLevel,
        recordedAt = recordedAt,
        value = value,
        displayValue = displayValue
    )

    private fun expressionPoint(
        recordedAt: Long,
        value: Double,
        displayValue: String
    ): MetricHistoryPoint = MetricHistoryPoint(
        metricType = StatisticsMetricType.ExpressionRange,
        recordedAt = recordedAt,
        value = value,
        displayValue = displayValue
    )

    private companion object {
        private const val ONE_HOUR_MILLIS = 60 * 60 * 1_000L
        private const val ONE_DAY_MILLIS = 24 * ONE_HOUR_MILLIS
        private const val JAN_1_2025_KST = 1_735_657_200_000L
        private const val JAN_2_2025_KST = JAN_1_2025_KST + ONE_DAY_MILLIS
        private const val JAN_3_2025_KST = JAN_2_2025_KST + ONE_DAY_MILLIS
    }
}
