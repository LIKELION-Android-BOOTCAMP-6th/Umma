package com.app.umma.presentation.statistics.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.app.umma.core.theme.UmmaTheme
import com.app.umma.domain.model.chat.ConversationConsistencyEvidence
import com.app.umma.domain.model.chat.ConversationSustainabilityEvidence
import com.app.umma.domain.model.chat.LanguageDependenceEvidence
import com.app.umma.domain.model.chat.ResponseDifficultyFitEvidence
import com.app.umma.domain.model.chat.TargetLanguageComprehensionEvidence
import com.app.umma.domain.model.chat.TargetLanguageProductionEvidence
import com.app.umma.domain.model.learningstate.EvidenceDirection
import com.app.umma.domain.model.learningstate.ExternalMetrics
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LearningMetricKey
import com.app.umma.domain.model.learningstate.MetricEvidence
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.InternalMetrics
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LangStateAnalysisMeta
import com.app.umma.domain.model.learningstate.ChatEvidenceSummary
import com.app.umma.domain.model.learningstate.toLangAbilityStats
import com.app.umma.domain.model.statistics.StatisticsHistoryQueryState
import com.app.umma.domain.model.statistics.StatisticsOverview
import com.app.umma.domain.model.statistics.StatisticsMetricType
import com.app.umma.presentation.statistics.StatisticsContent
import com.app.umma.presentation.statistics.StatisticsUiState
import com.app.umma.presentation.statistics.component.StatisticsMetricLineChartDialog
import com.app.umma.presentation.statistics.model.toMetricSummaryItems
import com.app.umma.presentation.statistics.model.StatisticsMetricChartState

/**
 * Statistics 화면을 실제 데이터처럼 채워서 보는 Preview다.
 *
 * FakeRepository를 실제 DI에 끼우는 것보다, Preview 전용 상태를 직접 만들어서
 * 화면 조립 결과를 보는 편이 훨씬 가볍고 안전하다.
 */
@Preview(showBackground = true, showSystemUi = true, backgroundColor = 0xFFF8F2E5)
@Composable
fun StatisticsScreenPreview() {
    UmmaTheme {
        StatisticsContent(
            uiState = previewUiState(),
            onRetry = {},
            onMetricClick = {}
        )
    }
}

/**
 * 로딩 상태의 스켈레톤이 어떻게 보이는지 확인하는 Preview다.
 */
@Preview(showBackground = true, showSystemUi = true, backgroundColor = 0xFFF8F2E5)
@Composable
fun StatisticsSkeletonPreview() {
    UmmaTheme {
        StatisticsContent(
            uiState = StatisticsUiState(isLoading = true),
            onRetry = {},
            onMetricClick = {}
        )
    }
}

/**
 * 차트 dialog와 line chart 상태를 함께 확인하는 Preview다.
 */
@Preview(showBackground = true, showSystemUi = true, backgroundColor = 0xFFF8F2E5)
@Composable
fun StatisticsChartDialogPreview() {
    UmmaTheme {
        StatisticsMetricLineChartDialog(
            chartState = StatisticsMetricChartState.Ready(
                metricType = StatisticsMetricType.GrammarAccuracy,
                points = previewPoints()
            ),
            onDismiss = {},
            onRetry = {}
        )
    }
}

/**
 * 통계 카드에 실제 값이 들어온 상태를 보여주는 샘플 상태다.
 *
 * Preview에서만 쓰는 값이므로, 런타임 ViewModel이나 Repository에는 연결하지 않는다.
 * 날짜를 흩어 놓은 이유는 x축 label, summary, marker가 한 화면에서 같이 보이도록 하기 위해서다.
 */
private fun previewUiState(): StatisticsUiState {
    val language = LangCode.EN
    val langState = LangState.initial(language).copy(
        internal = InternalMetrics.initial().copy(
            speechRate = 0.78,
            pauseFrequency = 0.18,
            spokenNaturalness = 0.72,
            naturalExpressionUsage = 0.77,
            grammarAccuracy = 0.81
        ),
        analysisMeta = LangStateAnalysisMeta(
            metricEvidence = mapOf(
                LearningMetricKey.GrammarAccuracy to MetricEvidence(
                    observedCount = 3,
                    confidence = 0.82,
                    sourceTypes = setOf(com.app.umma.domain.model.learningstate.LearningSignalSource.CorrectionSignal),
                    direction = EvidenceDirection.Up,
                    directionCount = 3,
                    lastObservedAt = 1_740_326_400_000L
                )
            ),
            activeFocus = emptyList(),
            lastSignalAt = 1_740_326_400_000L,
            lastChatAnalysisEventId = "preview-chat",
            chatEvidenceSummary = ChatEvidenceSummary(
                targetLanguageComprehension = TargetLanguageComprehensionEvidence.SimpleSentence,
                targetLanguageProduction = TargetLanguageProductionEvidence.SimpleSentences,
                supportLanguageDependence = LanguageDependenceEvidence.Low,
                aiScaffoldingDependence = LanguageDependenceEvidence.Low,
                conversationSustainability = ConversationSustainabilityEvidence.SustainedSimple,
                consistency = ConversationConsistencyEvidence.Stable,
                responseDifficultyFit = ResponseDifficultyFitEvidence.Fits,
                confidence = ProfileConfidence.Medium,
                observedCount = 3,
                lastObservedAt = 1_740_326_400_000L
            )
        ),
        updatedAt = 1_740_326_400_000L
    )
    val overview = StatisticsOverview(
        userId = "preview-user",
        selectedLearningLanguage = language,
        currentLangState = langState,
        currentExternalMetrics = ExternalMetrics(
            vocabularyLevel = com.app.umma.domain.model.learningstate.VocabLevel.B2,
            grammarAccuracy = 0.82,
            expressionRange = 8,
            fluencyScore = 0.74,
            naturalnessScore = 0.69
        ),
        currentLangAbilityStats = langState.toLangAbilityStats(
            com.app.umma.domain.usecase.learningstate.BuildLearnerAdaptationProfileUseCase().invoke(langState)
        ),
        availableMetricTypes = listOf(
            StatisticsMetricType.ConversationBand,
            StatisticsMetricType.FluencyScore,
            StatisticsMetricType.GrammarAccuracy,
            StatisticsMetricType.NaturalnessScore,
            StatisticsMetricType.VocabularyLevel,
            StatisticsMetricType.ExpressionRange
        ),
        historyQueryState = StatisticsHistoryQueryState.Ready(
            userId = "preview-user",
            language = language
        )
    )

    return StatisticsUiState(
        isLoading = false,
        overview = overview,
        metricSummaryCards = overview.toMetricSummaryItems(),
        selectedMetricType = StatisticsMetricType.GrammarAccuracy
    )
}

private fun previewPoints() = listOf(
    // preview에서는 1/1 반복 대신 실제 날짜 흐름이 보이도록 샘플을 분산시킨다.
    com.app.umma.domain.model.statistics.MetricHistoryPoint(
        metricType = StatisticsMetricType.GrammarAccuracy,
        recordedAt = 1_735_657_200_000L,
        value = 61.0,
        displayValue = "61%"
    ),
    com.app.umma.domain.model.statistics.MetricHistoryPoint(
        metricType = StatisticsMetricType.GrammarAccuracy,
        recordedAt = 1_736_260_800_000L,
        value = 73.0,
        displayValue = "73%"
    ),
    com.app.umma.domain.model.statistics.MetricHistoryPoint(
        metricType = StatisticsMetricType.GrammarAccuracy,
        recordedAt = 1_737_465_600_000L,
        value = 69.0,
        displayValue = "69%"
    ),
    com.app.umma.domain.model.statistics.MetricHistoryPoint(
        metricType = StatisticsMetricType.GrammarAccuracy,
        recordedAt = 1_740_326_400_000L,
        value = 78.0,
        displayValue = "78%"
    ),
    com.app.umma.domain.model.statistics.MetricHistoryPoint(
        metricType = StatisticsMetricType.GrammarAccuracy,
        recordedAt = 1_743_019_200_000L,
        value = 82.0,
        displayValue = "82%"
    )
)
