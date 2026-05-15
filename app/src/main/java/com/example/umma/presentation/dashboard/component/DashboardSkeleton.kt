package com.example.umma.presentation.dashboard.component

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.umma.core.theme.SpacingS

/**
 * Dashboard Loading 상태에서 표시되는 Skeleton UI.
 *
 * SSOT: DASH-001 — "Loading 상태 중 Skeleton UI 가 표시된다".
 *
 * 카드 4 개(대화 / 학습 / 교정 / 통계, DASH-002) 자리에 동일한 크기의 placeholder 를 렌더한다.
 * DASH-002 본 카드 컴포저블이 들어오면 placeholder 크기를 그에 맞춰 조정한다.
 */
@Composable
fun DashboardSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(SpacingS),
        verticalArrangement = Arrangement.spacedBy(SpacingS)
    ) {
        repeat(4) {
            SkeletonCard()
        }
    }
}

@Composable
private fun SkeletonCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(rememberShimmerBrush())
    )
}

@Composable
private fun rememberShimmerBrush(): Brush {
    val infiniteTransition = rememberInfiniteTransition(label = "dashboard-skeleton")
    val translate by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dashboard-skeleton-translate"
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