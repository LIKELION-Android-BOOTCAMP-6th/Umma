package com.example.umma.presentation.statistics.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.umma.core.theme.BackgroundSecondary
import com.example.umma.core.theme.CardCornerRadius
import com.example.umma.core.theme.CardElevation
import com.example.umma.core.theme.SpacingL
import com.example.umma.core.theme.SpacingM
import com.example.umma.core.theme.SpacingS
import com.example.umma.core.theme.TextCorrect
import com.example.umma.core.theme.TextExplanationR
import com.example.umma.core.theme.TextPrimary
import com.example.umma.core.theme.TextPrimaryR
import com.example.umma.core.theme.ThemePrimary
import com.example.umma.core.theme.ThemeSecondary
import com.example.umma.core.theme.TitleColor
import com.example.umma.domain.model.statistics.StatisticsMetricType
import com.example.umma.presentation.statistics.model.StatisticsMetricSummaryItem

/**
 * Statistics의 5개 요약 지표 카드를 읽기 쉬운 2열 레이아웃으로 배치한다.
 *
 * 레퍼런스처럼 카드 표면은 흰색으로 두고, metric별 포인트 컬러만 얹어서
 * 한눈에 구분되도록 만든다.
 */
@Composable
fun StatisticsMetricSummaryGrid(
    items: List<StatisticsMetricSummaryItem>,
    onMetricClick: (StatisticsMetricType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SpacingM)
    ) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SpacingM)
            ) {
                rowItems.forEach { item ->
                    StatisticsMetricSummaryCard(
                        item = item,
                        onClick = { onMetricClick(item.metricType) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatisticsMetricSummaryCard(
    item: StatisticsMetricSummaryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 카드별 포인트 컬러를 metricType에 묶어두면,
    // 화면이 데이터를 읽는 순간 어떤 카드인지 빠르게 구분된다.
    val accent = item.metricType.accentColor()
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 132.dp),
        shape = RoundedCornerShape(CardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = BackgroundSecondary),
        elevation = CardDefaults.cardElevation(defaultElevation = CardElevation),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.16f))
    ) {
        Column(
            modifier = Modifier.padding(SpacingL),
            verticalArrangement = Arrangement.spacedBy(SpacingS)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = accent.copy(alpha = 0.10f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.metricType.icon(),
                    contentDescription = item.title,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }

            // 제목과 값 사이를 비워 두면 레퍼런스처럼 시선이 아이콘 → 라벨 → 값으로 흐른다.
            Text(
                text = item.title,
                style = TextExplanationR,
                color = accent
            )
            Text(
                text = item.valueText,
                style = TextPrimaryR.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = accent
            )
        }
    }
}

private fun StatisticsMetricType.accentColor(): Color {
    return when (this) {
        StatisticsMetricType.VocabularyLevel -> ThemePrimary
        StatisticsMetricType.GrammarAccuracy -> ThemeSecondary
        StatisticsMetricType.ExpressionRange -> TextCorrect
        StatisticsMetricType.FluencyScore -> TitleColor
        StatisticsMetricType.NaturalnessScore -> TextPrimary
    }
}

private fun StatisticsMetricType.icon(): ImageVector {
    return when (this) {
        StatisticsMetricType.VocabularyLevel -> Icons.AutoMirrored.Outlined.MenuBook
        StatisticsMetricType.GrammarAccuracy -> Icons.Outlined.EditNote
        StatisticsMetricType.ExpressionRange -> Icons.Outlined.ChatBubbleOutline
        StatisticsMetricType.FluencyScore -> Icons.Outlined.Speed
        StatisticsMetricType.NaturalnessScore -> Icons.Outlined.AutoAwesome
    }
}
