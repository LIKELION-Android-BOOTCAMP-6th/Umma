package com.app.umma.domain.model.statistics

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.SyncStatus
import com.app.umma.domain.model.learningstate.VocabLevel
import kotlin.math.roundToInt

/**
 * Statistics 화면이 사용하는 시간별 지표 snapshot 원본 모델이다.
 *
 * 이 모델은 현재 상태를 다시 계산하는 용도가 아니라,
 * 특정 시점의 Language State 결과를 누적 저장하고 line chart로 읽기 위한 계약이다.
 */
data class StatisticsHistory(
    // history 식별자. local/remote 저장 경계를 따라가며 중복 판정에도 활용한다.
    val id: String,
    // 사용자 단위로 history를 분리하기 위한 기준.
    val userId: String,
    // 이 snapshot이 어떤 학습 언어에 속하는지 나타낸다.
    val language: LangCode,
    // line chart의 x축 기준이 되는 저장 시각.
    val recordedAt: Long,
    // 외부 화면에 보여주는 CEFR 등급.
    val vocabularyLevel: VocabLevel,
    // 문법 정확도 원본 값. current state의 원본 스케일을 유지한다.
    val grammarAccuracy: Double,
    // 표현 폭을 단순화한 값.
    val expressionRange: Int,
    // 유창성 점수 원본 값.
    val fluencyScore: Double,
    // 자연스러움 점수 원본 값.
    val naturalnessScore: Double,
    // 같은 분석 이벤트의 중복 기록 방지용 id.
    val sourceEventId: String,
    // local/remote sync 상태.
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)

/**
 * Statistics history 기록 UseCase 가 저장 결과를 UI/상위 흐름에 돌려줄 때 쓰는 최소 결과다.
 *
 * local-first 저장이 성공했는지, remote sync 가 아직 pending 인지만 분리해서 보여준다.
 */
data class StatisticsHistoryRecordResult(
    // 기록된 history 식별자.
    val historyId: String,
    // 중복 방지/연결 추적용 source event.
    val sourceEventId: String,
    // 실제로 새 기록이 반영되었는지 여부.
    val applied: Boolean,
    // Firestore sync 가 아직 남아있는지 여부.
    val isSyncPending: Boolean,
    // 기록 기준 시각.
    val recordedAt: Long
)

/**
 * line chart에 바로 넣을 수 있는 단일 점 모델이다.
 */
data class MetricHistoryPoint(
    // 어떤 metric의 점인지 구분한다.
    val metricType: StatisticsMetricType,
    // x축 기준 시각.
    val recordedAt: Long,
    // 차트 계산용 숫자 값.
    val value: Double,
    // 사용자가 읽을 라벨 값.
    val displayValue: String
)

/**
 * Statistics MVP에서 노출할 metric 식별자다.
 */
enum class StatisticsMetricType(
    // 기존 ExternalMetrics 필드와 1:1로 매핑되는 key.
    val metricKey: String,
    // UI에서 사용할 읽기 쉬운 이름.
    val displayName: String
) {
    VocabularyLevel("vocabularyLevel", "어휘 레벨"),
    GrammarAccuracy("grammarAccuracy", "문법 정확도"),
    ExpressionRange("expressionRange", "표현 폭"),
    FluencyScore("fluencyScore", "유창성"),
    NaturalnessScore("naturalnessScore", "자연스러움");

    companion object {
        fun fromMetricKey(metricKey: String): StatisticsMetricType? {
            return entries.firstOrNull { it.metricKey == metricKey }
        }
    }
}

/**
 * Statistics history 조회 결과를 UI 친화적으로 표현한 상태다.
 *
 * Flashcard의 ReviewDeckState처럼, 실제 조회 경계와 UI 분기를 함께 전달한다.
 */
sealed interface StatisticsHistoryState {
    // 아직 history가 없거나 선택 언어에 기록이 없는 경우.
    data object Empty : StatisticsHistoryState

    // local cache 혹은 현재 조회 결과가 있는 경우.
    data class Content(val histories: List<StatisticsHistory>) : StatisticsHistoryState

    // 잠시 다시 시도할 수 있는 조회 실패 상태.
    data class Retry(val cause: Throwable? = null) : StatisticsHistoryState

    // 복구가 어려운 조회 실패 상태.
    data class Error(val cause: Throwable? = null) : StatisticsHistoryState
}

/**
 * 한 history를 차트 포인트로 바꾸는 공통 변환 함수다.
 *
 * Statistics 화면은 current LangState를 다시 계산하지 않고,
 * 이 snapshot과 UseCase를 통해 파생 point만 읽는다.
 */
fun StatisticsHistory.toMetricPoints(): List<MetricHistoryPoint> {
    return listOf(
        MetricHistoryPoint(
            metricType = StatisticsMetricType.VocabularyLevel,
            recordedAt = recordedAt,
            value = vocabularyLevel.toChartValue(),
            displayValue = vocabularyLevel.name
        ),
        MetricHistoryPoint(
            metricType = StatisticsMetricType.GrammarAccuracy,
            recordedAt = recordedAt,
            value = grammarAccuracy.toPercentageValue(),
            displayValue = grammarAccuracy.toPercentageLabel()
        ),
        MetricHistoryPoint(
            metricType = StatisticsMetricType.ExpressionRange,
            recordedAt = recordedAt,
            value = expressionRange.toDouble(),
            displayValue = expressionRange.toString()
        ),
        MetricHistoryPoint(
            metricType = StatisticsMetricType.FluencyScore,
            recordedAt = recordedAt,
            value = fluencyScore.toPercentageValue(),
            displayValue = fluencyScore.toPercentageLabel()
        ),
        MetricHistoryPoint(
            metricType = StatisticsMetricType.NaturalnessScore,
            recordedAt = recordedAt,
            value = naturalnessScore.toPercentageValue(),
            displayValue = naturalnessScore.toPercentageLabel()
        )
    )
}

/**
 * 선택한 metric 타입 하나만 차트 포인트로 바꿀 때 사용하는 보조 함수다.
 */
fun StatisticsHistory.toMetricPoint(metricType: StatisticsMetricType): MetricHistoryPoint {
    return when (metricType) {
        StatisticsMetricType.VocabularyLevel -> MetricHistoryPoint(
            metricType = metricType,
            recordedAt = recordedAt,
            value = vocabularyLevel.toChartValue(),
            displayValue = vocabularyLevel.name
        )
        StatisticsMetricType.GrammarAccuracy -> MetricHistoryPoint(
            metricType = metricType,
            recordedAt = recordedAt,
            value = grammarAccuracy.toPercentageValue(),
            displayValue = grammarAccuracy.toPercentageLabel()
        )
        StatisticsMetricType.ExpressionRange -> MetricHistoryPoint(
            metricType = metricType,
            recordedAt = recordedAt,
            value = expressionRange.toDouble(),
            displayValue = expressionRange.toString()
        )
        StatisticsMetricType.FluencyScore -> MetricHistoryPoint(
            metricType = metricType,
            recordedAt = recordedAt,
            value = fluencyScore.toPercentageValue(),
            displayValue = fluencyScore.toPercentageLabel()
        )
        StatisticsMetricType.NaturalnessScore -> MetricHistoryPoint(
            metricType = metricType,
            recordedAt = recordedAt,
            value = naturalnessScore.toPercentageValue(),
            displayValue = naturalnessScore.toPercentageLabel()
        )
    }
}

private fun VocabLevel.toChartValue(): Double {
    // A1=1, A2=2 ... C2=6 으로 line chart에서 바로 비교할 수 있게 바꾼다.
    return ordinal + 1.0
}

private fun Double.toPercentageValue(): Double {
    // 원본 스케일은 0.0~1.0 이고, 차트/카드 표시용은 0~100 이다.
    return (coerceIn(0.0, 1.0) * 100.0)
}

private fun Double.toPercentageLabel(): String {
    // 카드/차트 label은 소수점보다 한눈에 보이는 정수 퍼센트가 더 읽기 쉽다.
    return "${toPercentageValue().roundToInt()}%"
}
