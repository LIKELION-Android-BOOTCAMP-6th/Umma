package com.app.umma.presentation.statistics.model

import com.app.umma.domain.model.learningstate.AbilityReadinessState
import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.statistics.StatisticsMetricType
import com.app.umma.domain.model.statistics.StatisticsOverview
import kotlin.math.roundToInt

/**
 * Statistics 요약 카드에서 바로 렌더링할 수 있는 UI 전용 모델이다.
 *
 * Domain의 LangAbilityStats를 화면이 읽기 쉬운 텍스트로 바꿔주는 역할만 맡는다.
 * 값이 아직 준비되지 않았을 때는 Empty에 준하는 상태 문구를 내려서 카드가
 * "데이터 없음" 상태로 일관되게 보이도록 한다.
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
 * StatisticsOverview를 6개 요약 카드 목록으로 변환한다.
 *
 * 여기서는 `LangAbilityStats`가 가진 현재 측정 상태를 사용자-facing 카드 텍스트로 바꾼다.
 * 실제 카드 디자인이나 클릭 반응은 presentation/statistics/component 쪽에서 담당한다.
 */
fun StatisticsOverview.toMetricSummaryItems(): List<StatisticsMetricSummaryItem> {
    return listOf(
        StatisticsMetricSummaryItem(
            metricType = StatisticsMetricType.ConversationBand,
            title = StatisticsMetricType.ConversationBand.displayName,
            valueText = currentLangAbilityStats.conversation.currentBand?.toDisplayText()
                ?: "측정 준비 중",
            isAvailable = currentLangAbilityStats.conversation.currentBand != null
        ),
        StatisticsMetricSummaryItem(
            metricType = StatisticsMetricType.FluencyScore,
            title = StatisticsMetricType.FluencyScore.displayName,
            valueText = currentLangAbilityStats.speaking.score.toReadabilityText(
                fallbackText = "측정 중"
            ),
            isAvailable = currentLangAbilityStats.speaking.score != null
        ),
        StatisticsMetricSummaryItem(
            metricType = StatisticsMetricType.GrammarAccuracy,
            title = StatisticsMetricType.GrammarAccuracy.displayName,
            valueText = currentLangAbilityStats.grammar.score.toReadabilityText(
                fallbackText = "측정 중"
            ),
            isAvailable = currentLangAbilityStats.grammar.score != null
        ),
        StatisticsMetricSummaryItem(
            metricType = StatisticsMetricType.NaturalnessScore,
            title = StatisticsMetricType.NaturalnessScore.displayName,
            valueText = currentLangAbilityStats.comprehension.score.toReadabilityText(
                fallbackText = "측정 중"
            ),
            isAvailable = currentLangAbilityStats.comprehension.score != null
        ),
        StatisticsMetricSummaryItem(
            metricType = StatisticsMetricType.VocabularyLevel,
            title = StatisticsMetricType.VocabularyLevel.displayName,
            valueText = if (currentLangAbilityStats.vocabulary == AbilityReadinessState.Ready) {
                currentExternalMetrics.vocabularyLevel.name
            } else {
                "측정 준비 중"
            },
            isAvailable = currentLangAbilityStats.vocabulary == AbilityReadinessState.Ready
        ),
        StatisticsMetricSummaryItem(
            metricType = StatisticsMetricType.ExpressionRange,
            title = StatisticsMetricType.ExpressionRange.displayName,
            valueText = if (currentLangAbilityStats.expression == AbilityReadinessState.Ready) {
                currentExternalMetrics.expressionRange.toString()
            } else {
                "측정 준비 중"
            },
            isAvailable = currentLangAbilityStats.expression == AbilityReadinessState.Ready
        )
    )
}

private fun ConversationAbilityBand.toDisplayText(): String {
    return when (this) {
        // Statistics 화면은 내부 band 이름이나 숫자만 노출하지 않고,
        // 사용자가 자신의 현재 말하기 단계를 직관적으로 이해할 수 있는 이름을 보여준다.
        ConversationAbilityBand.IntentOnly -> "시작 Level"
        ConversationAbilityBand.PhraseEmerging -> "단어 Level"
        ConversationAbilityBand.SimpleSentence -> "문장 Level"
        ConversationAbilityBand.BasicConversation -> "대화 Level"
        ConversationAbilityBand.ConnectedExpression -> "표현 Level"
        ConversationAbilityBand.NuanceControl -> "능숙 Level"
    }
}

private fun Double?.toReadabilityText(fallbackText: String): String {
    return this?.toReadablePercent() ?: fallbackText
}

private fun Double.toReadablePercent(): String {
    return "${(coerceIn(0.0, 1.0) * 100.0).roundToInt()}%"
}
