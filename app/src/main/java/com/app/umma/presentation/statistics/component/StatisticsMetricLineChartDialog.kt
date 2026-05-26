package com.app.umma.presentation.statistics.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.app.umma.core.theme.BackgroundPrimary
import com.app.umma.core.theme.BackgroundSecondary
import com.app.umma.core.theme.CardCornerRadius
import com.app.umma.core.theme.ChipCornerRadius
import com.app.umma.core.theme.ChipPaddingHorizontal
import com.app.umma.core.theme.ChipPaddingVertical
import com.app.umma.core.theme.SpacingL
import com.app.umma.core.theme.SpacingM
import com.app.umma.core.theme.SpacingS
import com.app.umma.core.theme.TextPrimary
import com.app.umma.core.theme.TextExplanationR
import com.app.umma.core.theme.TextPrimaryR
import com.app.umma.core.theme.TextSecondaryR
import com.app.umma.core.theme.ThemePrimary
import com.app.umma.domain.model.statistics.StatisticsMetricType
import com.app.umma.presentation.statistics.model.StatisticsMetricChartDisplayModel
import com.app.umma.presentation.statistics.model.StatisticsMetricChartState
import com.app.umma.presentation.statistics.model.StatisticsChartAxisPolicy
import com.app.umma.presentation.statistics.model.StatisticsChartSummary
import com.app.umma.presentation.statistics.model.formatMetricChange
import com.app.umma.presentation.statistics.model.isVisible
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.abs
import kotlin.math.roundToInt

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(SpacingM),
            contentAlignment = Alignment.Center
        ) {
            val isLandscape = maxWidth > maxHeight
            // 세로/가로에서 같은 비율을 쓰면 한쪽 화면에서 과하게 비거나 잘린다.
            // 그래서 dialog 폭과 chart 높이를 방향별로 따로 계산한다.
            val dialogWidthFraction = if (isLandscape) {
                LANDSCAPE_DIALOG_WIDTH_FRACTION
            } else {
                PORTRAIT_DIALOG_WIDTH_FRACTION
            }
            val dialogMaxWidth = if (isLandscape) {
                LANDSCAPE_DIALOG_MAX_WIDTH
            } else {
                PORTRAIT_DIALOG_MAX_WIDTH
            }
            val chartHeight = if (isLandscape) {
                // 가로 화면에서는 상단 텍스트와 칩 영역을 먼저 확보하고,
                // 차트는 남는 높이의 일부만 차지하도록 제한한다.
                maxHeight * LANDSCAPE_CHART_HEIGHT_FRACTION
            } else {
                // 세로 화면에서는 폭 기반으로 차트 높이를 계산해 답답하지 않게 만든다.
                maxWidth / PORTRAIT_CHART_ASPECT_RATIO
            }

            Surface(
                modifier = modifier
                    .fillMaxWidth(dialogWidthFraction)
                    .widthIn(max = dialogMaxWidth),
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

                        is StatisticsMetricChartState.Ready -> Column(
                            verticalArrangement = Arrangement.spacedBy(SpacingM)
                        ) {
                            ChartSummaryGrid(summary = chartState.displayModel.summary)
                            StatisticsMetricLineChart(
                                displayModel = chartState.displayModel,
                                chartHeight = chartHeight,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        StatisticsMetricChartState.Hidden -> Unit
                    }
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
    displayModel: StatisticsMetricChartDisplayModel,
    chartHeight: Dp,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // modelProducer는 chart 데이터만 보관하고, Composable 재구성 자체와는 분리한다.
        // 실제 series 주입은 아래 LaunchedEffect에서 transaction으로 처리한다.
        val modelProducer = remember { CartesianChartModelProducer() }
        val displayPoints = displayModel.displayPoints
        val yAxisPolicy = displayModel.yAxisPolicy
        val yTickStep = remember(yAxisPolicy) { yAxisPolicy.tickStep() }
        val pointProvider = remember {
            // 점선만 있으면 point 단위 변화를 읽기 어렵기 때문에 작은 dot을 같이 그린다.
            LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    component = ShapeComponent(
                        fill = Fill(ThemePrimary),
                        shape = CircleShape
                    ),
                    size = 5.dp
                )
            )
        }
        val markerLabel = remember {
            // marker는 차트 위에 올라오는 정보창이므로, chip보다 조금 더 여유 있는 패딩을 둔다.
            TextComponent(
                textStyle = TextSecondaryR.copy(color = TextPrimary),
                lineCount = 3,
                textOverflow = TextOverflow.Clip,
                margins = Insets(horizontal = 4.dp, vertical = 4.dp),
                padding = Insets(horizontal = 16.dp, vertical = 12.dp),
                background = ShapeComponent(
                    fill = Fill(BackgroundSecondary),
                    shape = RoundedCornerShape(ChipCornerRadius),
                    strokeFill = Fill(ThemePrimary.copy(alpha = 0.64f)),
                    strokeThickness = 1.dp
                )
            )
        }
        val chartMarker = rememberDefaultCartesianMarker(
            label = markerLabel,
            valueFormatter = DefaultCartesianMarker.ValueFormatter { _, targets ->
                // marker는 현재 point와 바로 이전 point를 비교해서 변화량을 보여준다.
                // x축은 index 좌표이므로 entry.x를 다시 displayPoints index로 되돌린다.
                val lineTarget = targets.firstOrNull() as? LineCartesianLayerMarkerTarget
                val point = lineTarget?.points?.firstOrNull()?.entry?.x
                    ?.roundToInt()
                    ?.let(displayPoints::getOrNull)
                if (point == null) return@ValueFormatter ""

                val currentIndex = point.x.toInt()
                val previousPoint = displayPoints.getOrNull(currentIndex - 1)
                val changeLabel = previousPoint?.let {
                    formatMetricChange(
                        metricType = displayModel.metricType,
                        diff = point.y - it.y
                    )
                } ?: "첫 기록"

                buildString {
                    append(point.xLabel)
                    append('\n')
                    append("기록값 ")
                    append(point.displayValue)
                    append('\n')
                    append("변화량 ")
                    append(changeLabel)
                }
            },
            // 마커는 차트 바깥 고정이 아니라, 라인 기준 위/아래 공간에 따라 차트 내부에서 배치한다.
            labelPosition = DefaultCartesianMarker.LabelPosition.AroundPoint,
            indicator = { color ->
                ShapeComponent(
                    fill = Fill(color),
                    shape = CircleShape,
                    strokeFill = Fill(BackgroundSecondary),
                    strokeThickness = 1.dp
                )
            },
            indicatorSize = 12.dp,
            guideline = LineComponent(
                fill = Fill(ThemePrimary.copy(alpha = 0.24f)),
                thickness = 1.dp
            )
        )
        val xLabelByValue = remember(displayPoints) {
            // bottom axis는 index 좌표를 받으므로, 좌표 -> 날짜 label lookup table을 만든다.
            displayPoints.associate { point -> point.x to point.xLabel }
        }
        val yAxisFormatter = remember(yAxisPolicy) {
            // y축은 metric별 정책 label만 사용한다.
            CartesianValueFormatter { _, value, _ -> yAxisPolicy.labelFor(value) }
        }
        val xAxisFormatter = remember(xLabelByValue) {
            // x축은 point index를 그대로 노출하지 않고, 가장 가까운 point의 날짜 label로 바꾼다.
            CartesianValueFormatter { _, value, _ ->
                xLabelByValue.labelForNearestAxisValue(value) ?: value.roundToInt().toString()
            }
        }

        LaunchedEffect(displayModel) {
            // Vico에는 presentation model이 만든 index 좌표만 전달한다.
            // recordedAt은 label 생성에만 쓰고, domain/storage 계약은 이 Composable에서 다시 해석하지 않는다.
            modelProducer.runTransaction {
                lineSeries {
                    series(
                        x = displayPoints.map { it.x },
                        y = displayPoints.map { it.y }
                    )
                }
            }
        }

        key(displayModel.metricType, displayPoints.size, displayPoints.lastOrNull()?.recordedAt) {
            // metric이나 마지막 point가 바뀌면 scroll/zoom state도 새 차트 기준으로 다시 잡는다.
            val scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End)
            val zoomState = rememberVicoZoomState()

            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(
                            LineCartesianLayer.Companion.rememberLine(
                                pointProvider = pointProvider
                            )
                        ),
                        rangeProvider = CartesianLayerRangeProvider.fixed(
                            minX = displayPoints.firstOrNull()?.x,
                            maxX = displayPoints.lastOrNull()?.x,
                            minY = yAxisPolicy.minY,
                            maxY = yAxisPolicy.maxY
                        )
                    ),
                    startAxis = VerticalAxis.rememberStart(
                        valueFormatter = yAxisFormatter,
                        itemPlacer = VerticalAxis.ItemPlacer.step(step = { yTickStep })
                    ),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        valueFormatter = xAxisFormatter,
                        itemPlacer = HorizontalAxis.ItemPlacer.aligned()
                    ),
                    marker = chartMarker,
                    markerController = com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController.rememberToggleOnTap()
                ),
                modelProducer = modelProducer,
                scrollState = scrollState,
                zoomState = zoomState,
                // line 이 위로 뜨는 초기 진입 애니메이션은 끄고,
                // 현재 history snapshot 을 바로 읽을 수 있게 고정 렌더링한다.
                animateIn = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
            )
        }
    }
}

@Composable
private fun ChartSummaryGrid(
    summary: StatisticsChartSummary,
    modifier: Modifier = Modifier
) {
    // Dialog 내부 요약은 별도 카드가 아니라 Dashboard의 chip 패턴을 따른다.
    // 네 칩이 모두 들어가면 한 줄, 아니면 3+1이 되지 않도록 2개씩 나눈다.
    ChartSummaryChipLayout(
        texts = listOf(
            "처음 ${summary.startValue}",
            "지금 ${summary.currentValue}",
            "총성장 ${summary.changeValue}",
            "히스토리 ${summary.pointCountLabel}"
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = SpacingS)
    )
}

@Composable
private fun ChartSummaryChipLayout(
    texts: List<String>,
    modifier: Modifier = Modifier
) {
    Layout(
        modifier = modifier,
        content = {
            texts.forEach { text ->
                ChartSummaryChip(text = text)
            }
        }
    ) { measurables, constraints ->
        val gap = SpacingM.roundToPx()
        val relaxedConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { measurable ->
            measurable.measure(relaxedConstraints)
        }
        // 4개 칩이 한 줄에 들어가면 그대로 한 줄로 유지하고,
        // 그렇지 않으면 3+1 같은 어색한 분할 대신 2개씩 2줄로 고정한다.
        val oneRowWidth = placeables.sumOf { it.width } + gap * (placeables.size - 1).coerceAtLeast(0)
        val useSingleRow = oneRowWidth <= constraints.maxWidth
        val rows = if (useSingleRow) {
            listOf(placeables)
        } else {
            placeables.chunked(2)
        }
        val rowHeights = rows.map { row -> row.maxOfOrNull { it.height } ?: 0 }
        val layoutHeight = rowHeights.sum() + gap * (rows.size - 1).coerceAtLeast(0)

        layout(width = constraints.maxWidth, height = layoutHeight) {
            var y = 0
            rows.forEachIndexed { rowIndex, row ->
                var x = 0
                row.forEach { placeable ->
                    placeable.placeRelative(x = x, y = y)
                    x += placeable.width + gap
                }
                y += rowHeights[rowIndex] + gap
            }
        }
    }
}

@Composable
private fun ChartSummaryChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = ThemePrimary,
                    shape = RoundedCornerShape(ChipCornerRadius)
                )
                .padding(
                    horizontal = ChipPaddingHorizontal + SpacingS,
                    vertical = ChipPaddingVertical + 2.dp
                )
        ) {
            Text(
                text = text,
                style = TextExplanationR,
                color = BackgroundSecondary
            )
        }
    }
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
            // loading 상태도 실제 chart와 비슷한 밀도를 유지해서 레이아웃 점프를 줄인다.
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
            // Empty 상태는 chart 대신 metric 이름과 안내만 보여준다.
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
            // retry 가능할 때만 버튼을 노출한다. 단순 조회 실패는 안내만 남겨도 된다.
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

private fun StatisticsChartAxisPolicy.tickStep(): Double {
    // Vico의 vertical item placer는 step 값으로 tick과 guideline 위치를 결정한다.
    // 정책 label 간격을 그대로 쓰면 y축 label과 grid 기준이 함께 맞춰진다.
    return labels
        .zipWithNext()
        .firstOrNull()
        ?.let { (previous, next) -> next.value - previous.value }
        ?: 1.0
}

private fun Map<Double, String>.labelForNearestAxisValue(value: Double): String? {
    // Vico가 전달하는 axis 값은 Double이므로, index 좌표 근처의 label을 안정적으로 찾는다.
    val nearestEntry = entries.minByOrNull { (axisValue, _) -> abs(axisValue - value) }
    return nearestEntry
        ?.takeIf { (axisValue, _) -> abs(axisValue - value) < 0.001 }
        ?.value
}

private const val PORTRAIT_DIALOG_WIDTH_FRACTION = 0.96f
private const val LANDSCAPE_DIALOG_WIDTH_FRACTION = 0.78f
private val PORTRAIT_DIALOG_MAX_WIDTH = 760.dp
private val LANDSCAPE_DIALOG_MAX_WIDTH = 680.dp
private const val PORTRAIT_CHART_ASPECT_RATIO = 1.16f
private const val LANDSCAPE_CHART_HEIGHT_FRACTION = 0.48f
