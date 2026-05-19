package com.example.umma.presentation.dashboard.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.example.umma.R
import com.example.umma.core.theme.BackgroundSecondary
import com.example.umma.core.theme.BadgeDotSize
import com.example.umma.core.theme.CardCornerRadius
import com.example.umma.core.theme.CardElevation
import com.example.umma.core.theme.IconSizeLarge
import com.example.umma.core.theme.SpacingL
import com.example.umma.core.theme.SpacingM
import com.example.umma.core.theme.SpacingS
import com.example.umma.core.theme.SpacingXL
import com.example.umma.core.theme.SpacingXXL
import com.example.umma.core.theme.TextExplanationR
import com.example.umma.core.theme.TextLogout
import com.example.umma.core.theme.TitleCardR

/**
 * 대시보드 - 교정 대기 카드.
 *
 * SSOT: DASH-001 "Dashboard 카드 구성 → 2. 교정 대기 카드" / DASH-003 본 구현 대상.
 *
 * @param correctionAvailable 현재 선택 언어의 재사용 Session Memory 에 교정 가능한 turn 존재 여부.
 *                            true 일 때 우상단 배지 dot 노출.
 * @param recentConversationMinutes 최근 대화 누적 분. null 또는 0 이면 칩 미표시.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackCard(
    correctionAvailable: Boolean = false,
    recentConversationMinutes: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = TextLogout

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(CardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = BackgroundSecondary),
        elevation = CardDefaults.cardElevation(defaultElevation = CardElevation)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                // 좌우 padding 을 ConversationCard 와 동일하게 SpacingS 로 통일 —
                // 4 개 카드 외곽 여백 일관성. 상하는 SpacingXL 유지.
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = SpacingS, vertical = SpacingXL),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(SpacingM))

                Icon(
                    painter = painterResource(id = R.drawable.wand_shine_24),
                    contentDescription = "교정",
                    tint = accent,
                    modifier = Modifier.size(IconSizeLarge)
                )

                Spacer(modifier = Modifier.height(SpacingXXL))
                Text(text = "교정", color = accent, style = TitleCardR)

                Spacer(modifier = Modifier.height(SpacingXXL))
                Text(
                    text = "더 좋은 표현을 배워봐요!",
                    style = TextExplanationR,
                    color = accent
                )

                Spacer(modifier = Modifier.weight(1f))

                if (recentConversationMinutes != null && recentConversationMinutes > 0) {
                    Row(
                        // 좌우 padding 축소 후 offset 보정 불필요.
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        CardInfoChip(
                            text = "대화 기록: ${recentConversationMinutes}분",
                            accent = accent
                        )
                    }
                }
            }

            if (correctionAvailable) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(SpacingL)
                        .size(BadgeDotSize)
                        .background(color = accent, shape = CircleShape)
                )
            }
        }
    }
}
