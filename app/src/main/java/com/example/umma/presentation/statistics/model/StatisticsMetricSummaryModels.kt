package com.example.umma.presentation.statistics.model

import com.example.umma.domain.model.learningstate.ExternalMetrics
import com.example.umma.domain.model.statistics.StatisticsMetricType
import com.example.umma.domain.model.statistics.StatisticsOverview
import kotlin.math.roundToInt

/**
 * Statistics 요약 카드에서 바로 렌더링할 수 있는 UI 전용 모델이다.
 *
 * Domain의 ExternalMetrics를 화면이 읽기 쉬운 텍스트로 바꿔주는 역할만 맡는다.
 * 값이 아직 준비되지 않았을 때는 Empty를 내려서 카드가 "데이터 없음" 상태로
 * 일관되게 보이도록 한다.
 */
data class StatisticsMetricSummaryItem(
    val metricType: StatisticsMetricType,
    val title: String,
    val valueText: String,
    // 실제 데이터가 들어온 상태인지, 아니면 Empty placeholder인지 구분한다.
    // UI는 이 값으로 렌더링 여부를 달리하지 않고, 읽는 사람에게만 상태를 알린다.
    val isAvailable: Boolean
)

/**
 * StatisticsOverview를 5개 요약 카드 목록으로 변환한다.
 *
 * 여기서는 "무엇을 보여줄지"만 정하고, 실제 카드 디자인이나 클릭 반응은
 * presentation/statistics/component 쪽에서 담당한다.
 */
fun StatisticsOverview.toMetricSummaryItems(): List<StatisticsMetricSummaryItem> {
    val isAvailable = currentExternalMetrics != ExternalMetrics.initial()
    return listOf(
        StatisticsMetricSummaryItem(
            metricType = StatisticsMetricType.VocabularyLevel,
            title = StatisticsMetricType.VocabularyLevel.displayName,
            valueText = currentExternalMetrics.vocabularyLevel.name,
            isAvailable = isAvailable
        ),
        StatisticsMetricSummaryItem(
            metricType = StatisticsMetricType.GrammarAccuracy,
            title = StatisticsMetricType.GrammarAccuracy.displayName,
            valueText = currentExternalMetrics.grammarAccuracy.toReadablePercent(),
            isAvailable = isAvailable
        ),
        StatisticsMetricSummaryItem(
            metricType = StatisticsMetricType.ExpressionRange,
            title = StatisticsMetricType.ExpressionRange.displayName,
            valueText = if (currentExternalMetrics.expressionRange == 0 && !isAvailable) {
                "Empty"
            } else {
                currentExternalMetrics.expressionRange.toString()
            },
            isAvailable = isAvailable
        ),
        StatisticsMetricSummaryItem(
            metricType = StatisticsMetricType.FluencyScore,
            title = StatisticsMetricType.FluencyScore.displayName,
            valueText = currentExternalMetrics.fluencyScore.toReadablePercent(),
            isAvailable = isAvailable
        ),
        StatisticsMetricSummaryItem(
            metricType = StatisticsMetricType.NaturalnessScore,
            title = StatisticsMetricType.NaturalnessScore.displayName,
            valueText = currentExternalMetrics.naturalnessScore.toReadablePercent(),
            isAvailable = isAvailable
        )
    ).map { item ->
        if (!item.isAvailable) {
            item.copy(valueText = "Empty")
        } else {
            item
        }
    }
}

private fun Double.toReadablePercent(): String {
    return "${(coerceIn(0.0, 1.0) * 100.0).roundToInt()}%"
}
