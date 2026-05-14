package com.example.umma.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.umma.core.theme.BackgroundDeactivated
import com.example.umma.core.theme.BackgroundPrimary
import com.example.umma.core.theme.ThemePrimary

/**
 * 페이지 전환 위치를 시각적으로 나타내는 애니메이션 페이지 인디케이터 컴포저블입니다.
 *
 * 현재 선택된 페이지의 점은 [ThemePrimary] 색상으로 가로 32dp로 늘어나고,
 * 나머지 점은 [BackgroundDeactivated] 색상의 8dp 원형으로 표시됩니다.
 * 너비 변화는 [Spring.DampingRatioMediumBouncy] 스프링 애니메이션,
 * 색상 변화는 300ms [tween] 애니메이션으로 처리됩니다.
 *
 * 사용 예시:
 * ```
 * PageIndicator(
 *     totalCount = 4,
 *     currentIndex = pagerState.currentPage
 * )
 * ```
 *
 * @param modifier 이 컴포저블에 적용할 [Modifier].
 * @param totalCount 전체 페이지 수. 해당 수만큼 점이 렌더링됨. 기본값은 `3`.
 * @param currentIndex 현재 활성화된 페이지의 인덱스 (0부터 시작).
 *   [totalCount] 범위를 벗어나지 않도록 주의해야 합니다.
 */
@Composable
fun PageIndicator(
    modifier: Modifier = Modifier,
    totalCount: Int = 3,
    currentIndex: Int,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalCount) { index ->
            val isSelected = index == currentIndex

            val width by animateDpAsState(
                targetValue = if (isSelected) 32.dp else 8.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "width"
            )

            val color by animateColorAsState(
                targetValue = if (isSelected) ThemePrimary else BackgroundDeactivated,
                animationSpec = tween(durationMillis = 300),
                label = "color"
            )
            Box(
              modifier = Modifier
                  .height(8.dp)
                  .width(width)
                  .clip(CircleShape)
                  .background(color = color)
            )
        }
    }
}

// 애니메이션 테스트 Preview
@Preview(showBackground = true, backgroundColor = 0xFFF8F2E5) //
@Composable
fun UmmaPagerIndicatorPreview() {

    var currentIndex by remember { mutableIntStateOf(0) }
    val totalCount = 3

    Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        PageIndicator(
            totalCount = totalCount,
            currentIndex = currentIndex
        )

        // 인덱스 순환 버튼
        Button(
            onClick = {
                currentIndex = (currentIndex + 1) % totalCount
            },
            colors = ButtonDefaults.buttonColors(containerColor = ThemePrimary)
        ) {
            Text("다음 페이지 (애니메이션 테스트)")
        }
    }
}


