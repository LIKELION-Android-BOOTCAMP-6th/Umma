package com.app.umma.presentation.statistics.model

import com.app.umma.domain.model.statistics.MetricHistoryPoint
import com.app.umma.domain.model.statistics.StatisticsMetricType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val SCORE_AXIS_MIN = 0.0
private const val SCORE_AXIS_MAX = 100.0
private const val SCORE_AXIS_TICK_STEP = 10.0
private const val EXPRESSION_AXIS_MIN = 0.0
private const val EXPRESSION_DEFAULT_AXIS_MAX = 10.0
private const val EXPRESSION_TICK_STEP = 2
private const val VOCABULARY_AXIS_MIN = 1.0
private const val VOCABULARY_AXIS_MAX = 6.0
private const val CONVERSATION_AXIS_MIN = 1.0
private const val CONVERSATION_AXIS_MAX = 6.0
private const val AXIS_VALUE_EPSILON = 0.001

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
     * metric별로 요구되는 최소 point가 준비되어 실제 chart를 그릴 수 있는 상태다.
     */
    data class Ready(
        val metricType: StatisticsMetricType,
        val points: List<MetricHistoryPoint>,
        val displayModel: StatisticsMetricChartDisplayModel =
            points.toStatisticsMetricChartDisplayModel(metricType)
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
 * line chart는 최소 두 점이 있어야 변화 추세를 보여줄 수 있으므로,
 * point가 2개 미만이면 Empty 안내를 유지한다.
 */
fun List<MetricHistoryPoint>.toStatisticsMetricChartState(
    metricType: StatisticsMetricType
): StatisticsMetricChartState {
    if (metricType == StatisticsMetricType.ConversationBand) {
        // 종합 레벨은 차트보다 단계 정의 안내가 사용자에게 더 유용하다.
        // ViewModel에서 먼저 guide dialog로 분기하지만, mapper도 방어해 accidental chart를 막는다.
        return StatisticsMetricChartState.Empty(
            metricType = metricType,
            message = "종합 레벨은 차트 대신 단계 설명으로 확인할 수 있어요."
        )
    }

    // Repository가 정렬된 값을 주더라도 presentation 경계에서 한 번 더 정렬해
    // preview/test/real 흐름 모두 같은 chart 입력 순서를 보장한다.
    val sortedPoints = sortedBy { it.recordedAt }
    return if (sortedPoints.size < 2) {
        // 최소 point 수가 부족하면 더미 선을 만들지 않고 Empty 안내를 유지한다.
        StatisticsMetricChartState.Empty(metricType = metricType)
    } else {
        StatisticsMetricChartState.Ready(
            metricType = metricType,
            points = sortedPoints,
            displayModel = sortedPoints.toStatisticsMetricChartDisplayModel(metricType)
        )
    }
}

/**
 * Vico에 전달할 좌표와 화면에 보여줄 label을 분리한 presentation 전용 point다.
 *
 * Domain의 `MetricHistoryPoint.recordedAt`은 source of truth로 보존하되, timestamp를
 * x축 좌표로 직접 쓰면 label과 간격이 과도하게 벌어지므로 dialog 렌더링에서는 index 좌표를 사용한다.
 */
data class StatisticsChartDisplayPoint(
    val x: Double,
    val y: Double,
    val xLabel: String,
    val displayValue: String,
    val recordedAt: Long
)

/**
 * y축 tick 하나와 화면 label을 묶은 값이다.
 *
 * `VocabularyLevel`처럼 chart value와 사람이 읽는 label이 다른 metric을 domain 모델 변경 없이
 * 처리하기 위해 presentation 모델에만 둔다.
 */
data class StatisticsChartAxisLabel(
    val value: Double,
    val label: String
)

/**
 * metric별 y축 범위와 tick/grid 기준이다.
 *
 * 이 정책은 chart를 읽기 쉽게 만드는 presentation 책임이며, history 저장/동기화 계약에는 영향을 주지 않는다.
 */
data class StatisticsChartAxisPolicy(
    val minY: Double,
    val maxY: Double,
    val labels: List<StatisticsChartAxisLabel>
) {
    /**
     * Vico axis formatter가 넘기는 실수값을 미리 정의한 tick label로 변환한다.
     */
    fun labelFor(value: Double): String =
        labels.firstOrNull { abs(it.value - value) < AXIS_VALUE_EPSILON }?.label
            ?: value.roundToInt().toString()
}

/**
 * chart dialog 상단에 보여줄 요약 정보다.
 *
 * 카드의 현재값과 별개로, 선택한 history point 목록 안에서 시작/현재/변화만 계산한다.
 */
data class StatisticsChartSummary(
    val startValue: String,
    val currentValue: String,
    val changeValue: String,
    val pointCount: Int,
    val pointCountLabel: String
)

/**
 * `StatisticsMetricLineChartDialog`가 필요한 표시 정보를 한 번에 전달하는 모델이다.
 */
data class StatisticsMetricChartDisplayModel(
    val metricType: StatisticsMetricType,
    val displayPoints: List<StatisticsChartDisplayPoint>,
    val yAxisPolicy: StatisticsChartAxisPolicy,
    val summary: StatisticsChartSummary
)

/**
 * domain point 목록을 chart 표시 모델로 바꾼다.
 *
 * 이 변환은 chart 축/summary 표시만 책임지므로 repository, usecase, sync 상태를 참조하지 않는다.
 */
fun List<MetricHistoryPoint>.toStatisticsMetricChartDisplayModel(
    metricType: StatisticsMetricType
): StatisticsMetricChartDisplayModel {
    val sortedPoints = sortedBy { it.recordedAt }
    if (sortedPoints.isEmpty()) {
        // Ready 상태는 최소 2개 point에서만 만들어지지만, public mapper 직접 호출도 crash 없이 다룬다.
        return StatisticsMetricChartDisplayModel(
            metricType = metricType,
            displayPoints = emptyList(),
            yAxisPolicy = emptyList<MetricHistoryPoint>().toYAxisPolicy(metricType),
            summary = StatisticsChartSummary(
                startValue = "-",
                currentValue = "-",
                changeValue = "-",
                pointCount = 0,
                pointCountLabel = "0개"
            )
        )
    }
    return StatisticsMetricChartDisplayModel(
        metricType = metricType,
        displayPoints = sortedPoints.toDisplayPoints(),
        yAxisPolicy = sortedPoints.toYAxisPolicy(metricType),
        summary = sortedPoints.toChartSummary(metricType)
    )
}

private fun List<MetricHistoryPoint>.toDisplayPoints(): List<StatisticsChartDisplayPoint> {
    // x축은 timestamp를 그대로 쓰지 않는다.
    // 차트의 좌우 간격은 index로 고정하고, 날짜는 label로만 표현해야 읽기 쉽다.
    val baseLabels = map { it.recordedAt.toChartDateLabel() }
    val labelCounts = baseLabels.groupingBy { it }.eachCount()
    val seenLabelCounts = mutableMapOf<String, Int>()

    return mapIndexed { index, point ->
        val baseLabel = baseLabels[index]
        val label = if ((labelCounts[baseLabel] ?: 0) > 1) {
            // 같은 날짜에 여러 기록이 있으면 날짜 label만으로는 point를 구분할 수 없다.
            val nextIndex = (seenLabelCounts[baseLabel] ?: 0) + 1
            seenLabelCounts[baseLabel] = nextIndex
            "$baseLabel #$nextIndex"
        } else {
            baseLabel
        }

        StatisticsChartDisplayPoint(
            x = index.toDouble(),
            y = point.value,
            xLabel = label,
            displayValue = point.displayValue,
            recordedAt = point.recordedAt
        )
    }
}

private fun List<MetricHistoryPoint>.toYAxisPolicy(
    metricType: StatisticsMetricType
): StatisticsChartAxisPolicy = when (metricType) {
    StatisticsMetricType.VocabularyLevel -> StatisticsChartAxisPolicy(
        minY = VOCABULARY_AXIS_MIN,
        maxY = VOCABULARY_AXIS_MAX,
        labels = listOf("A1", "A2", "B1", "B2", "C1", "C2").mapIndexed { index, label ->
            StatisticsChartAxisLabel(value = (index + 1).toDouble(), label = label)
        }
    )

    StatisticsMetricType.ConversationBand -> StatisticsChartAxisPolicy(
        minY = CONVERSATION_AXIS_MIN,
        maxY = CONVERSATION_AXIS_MAX,
        labels = listOf(
            "시작",
            "단어",
            "문장",
            "대화",
            "표현",
            "능숙"
        ).mapIndexed { index, label ->
            // y축은 1~6 숫자 좌표를 쓰지만, 화면에는 Umma가 정의한 레벨명을 짧게 보여준다.
            StatisticsChartAxisLabel(value = (index + 1).toDouble(), label = label)
        }
    )

    StatisticsMetricType.ExpressionRange -> {
        // 표현 범위는 기본 0~10으로 보되, 실제 값이 넘치면 선이 잘리지 않도록 짝수 상한으로 확장한다.
        // 숫자 자체가 label이므로 Vocabulary처럼 별도 의미 매핑은 없다.
        val observedMax = maxOfOrNull { it.value } ?: EXPRESSION_DEFAULT_AXIS_MAX
        val maxY = if (observedMax <= EXPRESSION_DEFAULT_AXIS_MAX) {
            EXPRESSION_DEFAULT_AXIS_MAX
        } else {
            ceil(observedMax / EXPRESSION_TICK_STEP).toInt() * EXPRESSION_TICK_STEP.toDouble()
        }
        val tickValues = (EXPRESSION_AXIS_MIN.roundToInt()..maxY.roundToInt())
            .step(EXPRESSION_TICK_STEP)
            .map { it.toDouble() }

        StatisticsChartAxisPolicy(
            minY = EXPRESSION_AXIS_MIN,
            maxY = maxY,
            labels = tickValues.map { value ->
                StatisticsChartAxisLabel(value = value, label = value.roundToInt().toString())
            }
        )
    }

    StatisticsMetricType.GrammarAccuracy,
    StatisticsMetricType.FluencyScore,
    StatisticsMetricType.NaturalnessScore -> StatisticsChartAxisPolicy(
        minY = SCORE_AXIS_MIN,
        maxY = SCORE_AXIS_MAX,
        labels = (SCORE_AXIS_MIN.toInt()..SCORE_AXIS_MAX.toInt())
            .step(SCORE_AXIS_TICK_STEP.toInt())
            .map { value ->
                value.toDouble()
            }
            .map { value ->
            StatisticsChartAxisLabel(value = value, label = "${value.roundToInt()}%")
            }
    )
}

private fun List<MetricHistoryPoint>.toChartSummary(
    metricType: StatisticsMetricType
): StatisticsChartSummary {
    val firstPoint = first()
    val lastPoint = last()
    return StatisticsChartSummary(
        startValue = firstPoint.displayValue,
        currentValue = lastPoint.displayValue,
        changeValue = formatMetricChange(metricType, lastPoint.value - firstPoint.value),
        pointCount = size,
        pointCountLabel = "${size}개"
    )
}

/**
 * Chart summary와 marker가 같은 변화량 표기 정책을 쓰도록 하는 presentation formatter다.
 *
 * 점수형 metric은 이미 domain에서 0~100 값으로 변환된 `MetricHistoryPoint.value`를 받으므로
 * 여기서는 화면에 보여줄 단위만 붙인다.
 */
fun formatMetricChange(
    metricType: StatisticsMetricType,
    diff: Double
): String {
    val sign = when {
        diff > 0.0 -> "+"
        diff < 0.0 -> "-"
        else -> ""
    }
    val absoluteDiff = abs(diff).roundToInt()
    return when (metricType) {
        // Vocabulary는 ordinal 간 차이가 곧 몇 단계가 바뀌었는지이므로 단계 단위로 보여준다.
        StatisticsMetricType.VocabularyLevel -> "$sign${absoluteDiff}단계"
        StatisticsMetricType.ConversationBand -> "$sign${absoluteDiff}단계"
        // Expression은 정수 스케일 차이만 보여주면 된다.
        StatisticsMetricType.ExpressionRange -> "$sign$absoluteDiff"
        // 점수형 metric은 percent 표기만 붙이고 p는 붙이지 않는다.
        StatisticsMetricType.GrammarAccuracy,
        StatisticsMetricType.FluencyScore,
        StatisticsMetricType.NaturalnessScore -> "$sign${absoluteDiff}%"
    }
}

private fun Long.toChartDateLabel(): String {
    // Statistics history는 epoch millis를 저장하므로, 사용자에게는 앱 기준 날짜만 짧게 보여준다.
    val formatter = SimpleDateFormat("M/d", Locale.KOREA).apply {
        timeZone = TimeZone.getTimeZone("Asia/Seoul")
    }
    return formatter.format(Date(this))
}
