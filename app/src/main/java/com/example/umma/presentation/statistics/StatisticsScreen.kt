package com.example.umma.presentation.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.umma.core.ui.component.UmmaAppBar
import com.example.umma.domain.model.learningstate.ExternalMetrics
import com.example.umma.domain.model.statistics.StatisticsHistoryQueryState

/**
 * Statistics 화면의 첫 진입 화면이다.
 *
 * STAT-001은 지표 카드/차트보다 먼저 현재 선택 언어와 LangState.external
 * 준비 상태를 안전하게 조립해서 후속 화면이 쓸 수 있는 컨텍스트를 만든다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage = uiState.errorMessage
    val overview = uiState.overview

    Scaffold(
        topBar = {
            UmmaAppBar(
                title = "통계",
                isCenterTitle = true
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                // 진입 직후에는 아직 current language context가 준비되지 않았을 수 있으므로
                // 카드/차트 대신 loading panel 을 먼저 보여준다.
                uiState.isLoading -> LoadingPanel()
                // 컨텍스트를 만들 수 없는 경우에는 retry 가능한 error panel 로 빠진다.
                errorMessage != null -> ErrorPanel(
                    message = errorMessage,
                    isRetryable = uiState.isRetryable,
                    onRetry = viewModel::retry
                )
                // overview 가 준비되면 STAT-002/003이 사용할 최소 컨텍스트를 보여준다.
                overview != null -> OverviewPanel(
                    selectedLanguage = uiState.selectedLearningLanguage?.code?.uppercase().orEmpty(),
                    currentExternalMetrics = uiState.currentExternalMetrics,
                    historyQueryState = uiState.historyQueryState,
                    availableMetricLabels = overview.availableMetricTypes.joinToString(
                        separator = " · "
                    ) { it.displayName }
                )
                else -> ErrorPanel(
                    message = "Statistics 초기 상태를 만들 수 없습니다.",
                    isRetryable = true,
                    onRetry = viewModel::retry
                )
            }
        }
    }
}

@Composable
private fun LoadingPanel() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 아직 컨텍스트 조립 중임을 짧게 알려주는 최소 피드백이다.
            CircularProgressIndicator()
            Text(text = "현재 선택 언어와 통계 컨텍스트를 불러오는 중입니다.")
        }
    }
}

@Composable
private fun ErrorPanel(
    message: String,
    isRetryable: Boolean,
    onRetry: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 실패 사유를 숨기지 않고 바로 보여주되, retry 가능 여부만 따로 제어한다.
            Text(text = message)
            if (isRetryable) {
                Button(onClick = onRetry) {
                    Text(text = "다시 시도")
                }
            }
        }
    }
}

@Composable
private fun OverviewPanel(
    selectedLanguage: String,
    currentExternalMetrics: ExternalMetrics?,
    historyQueryState: StatisticsHistoryQueryState?,
    availableMetricLabels: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // STAT-001에서 준비한 현재 언어 컨텍스트를 사람이 읽을 수 있는 형태로만 보여준다.
            Text(text = "현재 선택 언어: $selectedLanguage")
            Text(text = "history 조회 준비: ${historyQueryState.toStatusText()}")
            Text(text = "준비된 지표: $availableMetricLabels")
            currentExternalMetrics?.let { metrics ->
                // 실제 카드 렌더링은 STAT-002지만, 001에서는 원본 snapshot 이 준비되었는지 확인한다.
                Text(text = "LangState.external")
                Text(text = "어휘 레벨: ${metrics.vocabularyLevel.name}")
                Text(text = "문법 정확도: ${(metrics.grammarAccuracy * 100).toInt()}%")
                Text(text = "표현 폭: ${metrics.expressionRange}")
                Text(text = "유창성: ${(metrics.fluencyScore * 100).toInt()}%")
                Text(text = "자연스러움: ${(metrics.naturalnessScore * 100).toInt()}%")
            }
            // 이 화면은 요약 카드/차트의 기반이 되는 컨텍스트만 준비하고 끝난다.
            Text(
                text = "이 화면은 2~4단계에서 사용할 current language context를 준비하는 역할만 담당합니다.",
                textAlign = TextAlign.Start
            )
        }
    }
}

private fun StatisticsHistoryQueryState?.toStatusText(): String {
    return when (this) {
        is StatisticsHistoryQueryState.Ready ->
            "Ready(userId=${userId}, language=${language.code})"
        is StatisticsHistoryQueryState.Unavailable -> "Unavailable($reason)"
        null -> "Unavailable"
    }
}
