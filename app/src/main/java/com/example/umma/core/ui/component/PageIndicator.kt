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

// 움마 애니메이션 페이지 인디케이터 위젯
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


