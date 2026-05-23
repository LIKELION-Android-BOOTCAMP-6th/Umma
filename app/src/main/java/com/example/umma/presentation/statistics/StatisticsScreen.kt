package com.example.umma.presentation.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.umma.core.theme.BackgroundPrimary
import com.example.umma.core.theme.BackgroundSecondary
import com.example.umma.core.theme.SpacingL
import com.example.umma.core.theme.SpacingM
import com.example.umma.core.theme.TextPrimary
import com.example.umma.core.theme.TextSecondaryR
import com.example.umma.core.theme.ThemePrimary
import com.example.umma.core.theme.TitleB
import com.example.umma.core.theme.TitleColor
import com.example.umma.core.ui.component.UmmaAppBar
import com.example.umma.domain.model.statistics.StatisticsMetricType
import com.example.umma.presentation.statistics.component.StatisticsMetricLineChartDialog
import com.example.umma.presentation.statistics.component.StatisticsMetricSummaryGrid
import com.example.umma.presentation.statistics.component.StatisticsSkeleton
import com.example.umma.presentation.statistics.model.isVisible
import com.example.umma.presentation.statistics.model.StatisticsMetricSummaryItem

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

    Box {
        Scaffold(
            containerColor = BackgroundPrimary,
            topBar = {
                UmmaAppBar(
                    title = "통계",
                    isCenterTitle = true
                )
            }
        ) { paddingValues ->
            StatisticsContent(
                uiState = uiState,
                modifier = Modifier.padding(paddingValues),
                onRetry = viewModel::retry,
                onMetricClick = viewModel::onMetricClick
            )
        }

        if (uiState.metricChartState.isVisible) {
            StatisticsMetricLineChartDialog(
                chartState = uiState.metricChartState,
                onDismiss = viewModel::dismissMetricChart,
                onRetry = viewModel::retryMetricChart
            )
        }
    }
}

@Composable
internal fun StatisticsContent(
    uiState: StatisticsUiState,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
    onMetricClick: (StatisticsMetricType) -> Unit
) {
    // Preview와 실제 화면이 같은 분기/레이아웃을 공유하도록,
    // Scaffold 바깥의 순수 UI 조립 로직을 따로 분리해 둔다.
    Column(
        modifier = modifier
            .padding(horizontal = SpacingL, vertical = SpacingL)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SpacingL)
    ) {
        when {
            // 진입 직후에는 아직 current language context가 준비되지 않았을 수 있으므로
            // 카드/차트 대신 loading panel을 먼저 보여준다.
            uiState.isLoading -> StatisticsSkeleton(
                modifier = Modifier.padding(top = SpacingM)
            )

            // 컨텍스트를 만들 수 없는 경우에는 retry 가능한 error panel 로 빠진다.
            uiState.errorMessage != null -> ErrorPanel(
                message = uiState.errorMessage,
                isRetryable = uiState.isRetryable,
                onRetry = onRetry
            )

            // overview 가 준비되면 STAT-002/003이 사용할 최소 컨텍스트를 보여준다.
            // 현재 화면은 차트 그리기보다는 "준비된 상태를 눈으로 확인"하는 역할에 가깝다.
            uiState.overview != null -> OverviewPanel(
                selectedLanguage = uiState.selectedLearningLanguage?.code?.uppercase().orEmpty(),
                metricSummaryCards = uiState.metricSummaryCards,
                onMetricClick = onMetricClick
            )

            else -> ErrorPanel(
                message = "Statistics 초기 상태를 만들 수 없습니다.",
                isRetryable = true,
                onRetry = onRetry
            )
        }
    }
}

@Composable
private fun ErrorPanel(
    message: String,
    isRetryable: Boolean,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BackgroundSecondary)
    ) {
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
    metricSummaryCards: List<StatisticsMetricSummaryItem>,
    onMetricClick: (StatisticsMetricType) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(SpacingM)
    ) {
        // 현재 화면이 어떤 언어 기준인지 먼저 보여준다.
        Text(
            text = if (selectedLanguage.isBlank()) {
                "현재 선택 언어"
            } else {
                "현재 선택 언어 · ${selectedLanguage.uppercase()}"
            },
            style = TextSecondaryR,
            color = ThemePrimary
        )
        // 카드 그리드의 시선을 받쳐주는 큰 문구. 상태가 비어 있어도 화면 구조는 유지된다.
        Text(
            text = "꾸준한 학습으로 실력이 쑥쑥 늘고 있어요!",
            style = TitleB,
            color = TitleColor
        )
        // 카드가 보여주는 값이 "현재 대비 얼마나 나아졌는지"를 한 줄로 설명한다.
        Text(
            text = "첫 학습 대비 실력 증진율",
            style = TextSecondaryR,
            color = TextPrimary.copy(alpha = 0.82f)
        )
        StatisticsMetricSummaryGrid(
            items = metricSummaryCards,
            onMetricClick = onMetricClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
