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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.example.umma.core.theme.CardCornerRadius
import com.example.umma.core.theme.SpacingL
import com.example.umma.core.theme.SpacingM
import com.example.umma.core.theme.SpacingXS

/**
 * Dashboard Loading 상태에서 표시되는 Skeleton UI.
 *
 * SSOT: DASH-001 — "Loading 상태 중 Skeleton UI 가 표시된다".
 *
 * 본 [DashboardContent] 의 2x2 카드 그리드와 동일한 골격을 placeholder 로 표시.
 * Loading → Content 전환 시 레이아웃 점프가 없도록 padding / spacing 을
 * DashboardScreen 의 DashboardCardGrid 와 정확히 맞춘다.
 * 상단 인사 텍스트 자리에는 짧은 placeholder bar 를 두어 본 콘텐츠 정렬 위치를
 * 미리 잡아둔다.
 */
@Composable
fun DashboardSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = SpacingL)
    ) {
        Spacer(modifier = Modifier.height(SpacingXS))

        // 상단 인사 텍스트 ("편안한 대화, 즐거운 학습!") 자리 placeholder.
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(rememberShimmerBrush())
        )

        Spacer(modifier = Modifier.height(SpacingL))

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(SpacingM)
            ) {
                SkeletonCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                SkeletonCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            Spacer(modifier = Modifier.height(SpacingM))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(SpacingM)
            ) {
                SkeletonCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                SkeletonCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            Spacer(modifier = Modifier.height(SpacingL))
        }
    }
}

@Composable
private fun SkeletonCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(CardCornerRadius))
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
