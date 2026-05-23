package com.example.umma.presentation.statistics.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.umma.core.theme.CardCornerRadius
import com.example.umma.core.theme.SpacingL
import com.example.umma.core.theme.SpacingM
import com.example.umma.core.theme.SpacingS

/**
 * Statistics Loading 상태에서 표시되는 Skeleton UI.
 *
 * 레퍼런스의 Statistics 화면 구조를 미리 보여주는 목적이므로,
 * 상단 텍스트 자리와 2x2 카드 그리드 자리를 같은 비율로 잡아둔다.
 */
@Composable
fun StatisticsSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpacingM)
    ) {
        // 언어 표시 줄 자리.
        Box(
            modifier = Modifier
                .fillMaxWidth(0.38f)
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(rememberSkeletonBrush())
        )

        // 큰 헤드라인 자리.
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .height(28.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(rememberSkeletonBrush())
        )

        // 서브 카피 자리.
        Box(
            modifier = Modifier
                .fillMaxWidth(0.56f)
                .height(14.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(rememberSkeletonBrush())
        )

        Spacer(modifier = Modifier.height(SpacingL))

        Column(verticalArrangement = Arrangement.spacedBy(SpacingM)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SpacingM)
            ) {
                SkeletonMetricCard(modifier = Modifier.weight(1f))
                SkeletonMetricCard(modifier = Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SpacingM)
            ) {
                SkeletonMetricCard(modifier = Modifier.weight(1f))
                SkeletonMetricCard(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SkeletonMetricCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CardCornerRadius))
            .background(MaterialTheme.colorScheme.surface)
            .padding(SpacingL),
        verticalArrangement = Arrangement.spacedBy(SpacingS)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(rememberSkeletonBrush())
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.48f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(rememberSkeletonBrush())
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(22.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(rememberSkeletonBrush())
        )
    }
}

@Composable
private fun rememberSkeletonBrush(): Brush {
    val infiniteTransition = rememberInfiniteTransition(label = "statistics-skeleton")
    val translate by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "statistics-skeleton-translate"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    return Brush.linearGradient(
        colors = listOf(
            base.copy(alpha = 0.55f),
            base.copy(alpha = 0.25f),
            base.copy(alpha = 0.55f)
        ),
        start = Offset(translate - 400f, 0f),
        end = Offset(translate, 0f)
    )
}
