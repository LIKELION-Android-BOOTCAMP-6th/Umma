package com.app.umma.core.ui.modifier

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.app.umma.core.theme.ThemePrimary

/**
 * 클릭 유도를 위한 강조 테두리
 * animated=true 테두리가 은은하게 깜빡(펄스)
 */
@Composable
fun Modifier.attentionBorder(
    color: Color = ThemePrimary,
    width: Dp = 2.dp,
    shape: Shape = RoundedCornerShape(16.dp),
    animated: Boolean = true
): Modifier {
    // 애니메이션 필요 없으면 false로 색 고정
    if (!animated) {
        return this.border(width = width, color = color, shape = shape)
    }

    // rememberInfiniteTransition: 무한 반복 애니메이션
    val transition = rememberInfiniteTransition(label = "attentionBorder")
    // 테두리 투명도, 왕복(펄스 효과)
    // Reverse: targetValue 까지 도달 -> 역방향 initialValue 으로 되돌아옴
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "attentionBorderAlpha"
    )
    return this.border(width = width, color = color.copy(alpha = alpha), shape = shape)
}

//
@Preview(showBackground = true)
@Composable
private fun AttentionBorderPreview() {
    Box(
        modifier = Modifier
            .padding(16.dp)
            .size(200.dp, 160.dp)
            .attentionBorder(shape = RoundedCornerShape(16.dp), animated = true)
    )
}