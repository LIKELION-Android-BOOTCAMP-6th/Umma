package com.example.umma.presentation.statistics.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.umma.core.theme.UmmaTheme
import com.example.umma.domain.model.learningstate.ExternalMetrics
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.LangState
import com.example.umma.domain.model.statistics.StatisticsHistoryQueryState
import com.example.umma.domain.model.statistics.StatisticsOverview
import com.example.umma.domain.model.statistics.StatisticsMetricType
import com.example.umma.presentation.statistics.StatisticsContent
import com.example.umma.presentation.statistics.StatisticsUiState
import com.example.umma.presentation.statistics.model.toMetricSummaryItems

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
 * 통계 카드에 실제 값이 들어온 상태를 보여주는 샘플 상태다.
 *
 * Preview에서만 쓰는 값이므로, 런타임 ViewModel이나 Repository에는 연결하지 않는다.
 */
private fun previewUiState(): StatisticsUiState {
    val language = LangCode.EN
    val overview = StatisticsOverview(
        userId = "preview-user",
        selectedLearningLanguage = language,
        currentLangState = LangState.initial(language),
        currentExternalMetrics = ExternalMetrics(
            vocabularyLevel = com.example.umma.domain.model.learningstate.VocabLevel.B2,
            grammarAccuracy = 0.82,
            expressionRange = 8,
            fluencyScore = 0.74,
            naturalnessScore = 0.69
        ),
        availableMetricTypes = StatisticsMetricType.entries,
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
