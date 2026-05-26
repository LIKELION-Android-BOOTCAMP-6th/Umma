package com.app.umma.presentation.dashboard.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.app.umma.core.theme.BackgroundSecondary
import com.app.umma.core.theme.BadgeDotSize
import com.app.umma.core.theme.CardCornerRadius
import com.app.umma.core.theme.CardElevation
import com.app.umma.core.theme.IconSizeLarge
import com.app.umma.core.theme.SpacingL
import com.app.umma.core.theme.SpacingM
import com.app.umma.core.theme.SpacingS
import com.app.umma.core.theme.SpacingXL
import com.app.umma.core.theme.SpacingXXL
import com.app.umma.core.theme.TextExplanationR
import com.app.umma.core.theme.TextLogout
import com.app.umma.core.theme.TitleCardR

/**
 * 대시보드 - 교정 대기 카드.
 *
 * SSOT: DASH-003_Correction_Pending_Card.md
 *  (선반영 메모: DASH-001 "Dashboard 카드 구성 → 2. 교정 대기 카드" 에서 골격을 먼저 잡았다.)
 *
 * 충족 AC:
 *  - AC 1 카드 정상 출력
 *  - AC 2 최근 대화 기록 존재 여부 — 우상단 빨간 점 ([correctionAvailable] 기반.
 *         correctionAvailable=true 이면 재사용 가능한 최근 대화 존재가 함의되므로
 *         AC 2 / AC 5 가 동일 시각 시그널로 충족된다)
 *  - AC 4 최근 대화 시간 표시 — 하단 "대화 기록" 칩 ([recentConversationMinutes] > 0)
 *  - AC 5 현재 선택 언어의 재사용 Session Memory 에 교정 가능한 turn 존재 표시
 *         — 동일 빨간 점
 *  - AC 6 카드 클릭 → Correction 화면 이동 (호출자 [onClick] 람다가 navigate 담당,
 *         UmmaNavHost 에서 Route.FeedbackList 로 wiring 됨)
 *  - AC 8 recentFullContext 가 없을 경우 Empty 표시 — 점/칩 모두 자동 hide.
 *         본문 텍스트는 영구 CTA ("더 좋은 표현을 배워봐요!") 로 유지
 *         (ConversationCard 와 동일 패턴)
 *  - AC 9 카드 클릭 중 중복 Navigation 방지 — [rememberDashboardCardClick] 500ms throttle
 *
 * 스킵 AC:
 *  - AC 3 현재 선택 언어가 카드에 표시 — 화면 우측 상단 LearningLanguageSelector
 *         (DASH-006) 가 이미 현재 언어를 노출하므로 카드 본문에 중복 표시하지 않는다.
 *  - AC 7 selectedLearningLanguage 가 Correction 화면으로 전달 — 팀 정책상 Correction
 *         화면이 [GlobalLangState] 를 직접 구독하므로 Dashboard 측 인자 전달 불필요.
 *
 * @param correctionAvailable 현재 선택 언어의 재사용 Session Memory 에 교정 가능한 turn 존재 여부.
 *                            true 일 때 우상단 배지 dot 노출.
 * @param recentConversationMinutes 최근 대화 누적 분. null 또는 0 이면 칩 미표시.
 * @param onClick 카드 클릭 시 호출. throttle 은 카드 내부에서 처리되므로 호출자는
 *                단순히 navigate 만 수행하면 된다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackCard(
    correctionAvailable: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    val accent = accentColor ?: TextLogout
    // AC 9: 카드 onClick 을 throttle 로 감싸 연타 → 중복 navigate 차단.
    //   DASH-002 ConversationCard 와 동일 헬퍼 (DashboardCardCommon.rememberDashboardCardClick)
    //   재사용. 500ms 윈도우 안의 추가 클릭은 silently drop.
    val throttledOnClick = rememberDashboardCardClick(onClick)

    Card(
        onClick = throttledOnClick,
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

                // 교정 가능 여부는 우측 상단 빨간 점(correctionAvailable) 이 이미 표현하므로,
                // 하단 "대화 기록: ##분" 칩은 중복 시그널 — 제거.
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
