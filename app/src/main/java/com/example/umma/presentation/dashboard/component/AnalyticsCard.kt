package com.example.umma.presentation.dashboard.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.example.umma.R
import com.example.umma.core.theme.BackgroundSecondary
import com.example.umma.core.theme.CardCornerRadius
import com.example.umma.core.theme.CardElevation
import com.example.umma.core.theme.IconSizeLarge
import com.example.umma.core.theme.SpacingM
import com.example.umma.core.theme.SpacingS
import com.example.umma.core.theme.SpacingXL
import com.example.umma.core.theme.SpacingXXL
import com.example.umma.core.theme.TextExplanationR
import com.example.umma.core.theme.TextPrimary
import com.example.umma.core.theme.TitleCardR

/**
 * 대시보드 - 대표 언어 성취율 카드.
 *
 * SSOT: DASH-001 "Dashboard 카드 구성 → 4. 대표 언어 성취율 카드" / DASH-005 본 구현 대상.
 *
 * "절대 점수보다 최근 성장량(delta)을 우선 노출한다." — SSOT
 *
 * Phase 1: 진입점 카드 형태(아이콘 + 타이틀 + 부제) 만 렌더.
 *   delta 4 종을 카드 표면(미니 칩/바)에 노출하는 디자인은 후속 Phase 또는
 *   디자인 확정 시 추가.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsCard(
    grammarScoreDelta: Int,
    vocabularyScoreDelta: Int,
    fluencyScoreDelta: Int,
    naturalnessScoreDelta: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = TextPrimary

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(CardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = BackgroundSecondary),
        elevation = CardDefaults.cardElevation(defaultElevation = CardElevation)
    ) {
        Column(
            // 좌우 padding 을 다른 카드와 동일하게 SpacingS 로 통일 —
            // 4 개 카드 외곽 여백 일관성. 상하는 SpacingXL 유지.
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = SpacingS, vertical = SpacingXL),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(SpacingM))

            Icon(
                painter = painterResource(id = R.drawable.leaderboard_24),
                contentDescription = "통계",
                tint = accent,
                modifier = Modifier.size(IconSizeLarge)
            )

            Spacer(modifier = Modifier.height(SpacingXXL))
            Text(text = "통계", color = accent, style = TitleCardR)

            Spacer(modifier = Modifier.height(SpacingXXL))
            Text(
                text = "성취도를 확인해봐요!",
                style = TextExplanationR,
                color = accent
            )

            Spacer(modifier = Modifier.weight(1f))
            // delta 4 종은 후속 Phase 에서 미니 칩 등으로 노출 검토.
        }
    }
}
