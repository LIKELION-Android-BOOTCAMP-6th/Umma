package com.example.umma.presentation.statistics.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.umma.core.theme.BackgroundPrimary
import com.example.umma.core.theme.BackgroundSecondary
import com.example.umma.core.theme.CardCornerRadius
import com.example.umma.core.theme.SpacingL
import com.example.umma.core.theme.SpacingM
import com.example.umma.core.theme.SpacingS
import com.example.umma.core.theme.TextPrimary
import com.example.umma.core.theme.TextPrimaryR
import com.example.umma.core.theme.TextSecondaryR
import com.example.umma.core.theme.ThemePrimary
import com.example.umma.domain.model.statistics.MetricHistoryPoint
import com.example.umma.domain.model.statistics.StatisticsMetricType
import com.example.umma.presentation.statistics.model.StatisticsMetricChartState
import com.example.umma.presentation.statistics.model.isVisible
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart

/**
 * 선택한 metric의 차트를 dialog로 보여준다.
 *
 * 카드 영역을 차트로 대체하지 않고, 같은 화면 위에 독립적인 dialog로 띄운다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsMetricLineChartDialog(
    chartState: StatisticsMetricChartState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!chartState.isVisible) return

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CardCornerRadius),
            color = BackgroundPrimary,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(SpacingL),
                verticalArrangement = Arrangement.spacedBy(SpacingM)
            ) {
                ChartDialogHeader(
                    metricType = chartState.metricType(),
                    onDismiss = onDismiss
                )

                when (chartState) {
                    is StatisticsMetricChartState.Loading -> ChartLoading(
                        message = "${chartState.metricType.displayName} 차트를 불러오는 중이에요."
                    )

                    is StatisticsMetricChartState.Empty -> EmptyChartState(
                        message = chartState.message,
                        metricType = chartState.metricType
                    )

                    is StatisticsMetricChartState.Error -> ChartError(
                        message = chartState.message,
                        isRetryable = chartState.isRetryable,
                        onRetry = onRetry
                    )

                    is StatisticsMetricChartState.Ready -> StatisticsMetricLineChart(
                        points = chartState.points,
                        metricType = chartState.metricType,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    )

                    StatisticsMetricChartState.Hidden -> Unit
                }
            }
        }
    }
}

@Composable
private fun ChartDialogHeader(
    metricType: StatisticsMetricType,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SpacingS)
        ) {
            Text(
                text = metricType.displayName,
                style = TextPrimaryR,
                color = ThemePrimary
            )
            Text(
                text = "선택한 지표의 변화를 시간순으로 확인합니다.",
                style = TextSecondaryR,
                color = TextPrimary.copy(alpha = 0.72f)
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "차트 닫기",
                tint = TextPrimary
            )
        }
    }
}

@Composable
fun StatisticsMetricLineChart(
    points: List<MetricHistoryPoint>,
    metricType: StatisticsMetricType,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(points, metricType) {
        // Vico는 ViewModel이 준비한 point 목록만 받아서 차트 모델로 변환한다.
        // 여기서는 렌더링용 model producer만 갱신하고, 데이터 해석은 하지 않는다.
        modelProducer.runTransaction {
            lineSeries {
                series(
                    // x축은 문서와 domain 모델 계약대로 recordedAt 을 사용한다.
                    // 간격이 일정하지 않은 history도 실제 기록 시점 기준으로 그려진다.
                    x = points.map { it.recordedAt.toDouble() },
                    y = points.map { it.value }
                )
            }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom()
        ),
        modelProducer = modelProducer,
        // line 이 위로 뜨는 초기 진입 애니메이션은 끄고,
        // 현재 history snapshot 을 바로 읽을 수 있게 고정 렌더링한다.
        animateIn = false,
        modifier = modifier
    )
}

@Composable
fun ChartLoading(
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BackgroundSecondary)
    ) {
        Column(
            modifier = Modifier.padding(SpacingL),
            verticalArrangement = Arrangement.spacedBy(SpacingM)
        ) {
            Text(
                text = message,
                style = TextSecondaryR,
                color = TextPrimary
            )
            ChartSkeletonBlock(modifier = Modifier.fillMaxWidth().height(220.dp))
        }
    }
}

@Composable
fun EmptyChartState(
    metricType: StatisticsMetricType,
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BackgroundSecondary)
    ) {
        Column(
            modifier = Modifier.padding(SpacingL),
            verticalArrangement = Arrangement.spacedBy(SpacingS)
        ) {
            Text(
                text = metricType.displayName,
                style = TextPrimaryR,
                color = ThemePrimary
            )
            Text(
                text = message,
                style = TextSecondaryR,
                color = TextPrimary.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
fun ChartError(
    message: String,
    isRetryable: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BackgroundSecondary)
    ) {
        Column(
            modifier = Modifier.padding(SpacingL),
            verticalArrangement = Arrangement.spacedBy(SpacingM)
        ) {
            Text(
                text = "차트를 불러오지 못했어요.",
                style = TextPrimaryR,
                color = ThemePrimary
            )
            Text(
                text = message,
                style = TextSecondaryR,
                color = TextPrimary.copy(alpha = 0.72f)
            )
            if (isRetryable) {
                Button(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null
                    )
                    Text(text = "다시 시도")
                }
            }
        }
    }
}

@Composable
private fun ChartSkeletonBlock(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SpacingS)
    ) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                        shape = RoundedCornerShape(12.dp)
                    )
            )
        }
    }
}

private fun StatisticsMetricChartState.metricType(): StatisticsMetricType {
    // Header는 Loading/Ready/Empty/Error 모두 같은 metric 이름을 보여줘야 한다.
    // Hidden은 실제로 dialog가 열리지 않으므로 기본값만 반환한다.
    return when (this) {
        is StatisticsMetricChartState.Loading -> metricType
        is StatisticsMetricChartState.Ready -> metricType
        is StatisticsMetricChartState.Empty -> metricType
        is StatisticsMetricChartState.Error -> metricType
        StatisticsMetricChartState.Hidden -> StatisticsMetricType.VocabularyLevel
    }
}
