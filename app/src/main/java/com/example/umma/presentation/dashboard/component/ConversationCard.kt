package com.example.umma.presentation.dashboard.component

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
import androidx.compose.ui.text.style.TextOverflow
import com.example.umma.R
import com.example.umma.core.theme.BackgroundSecondary
import com.example.umma.core.theme.BadgeDotSize
import com.example.umma.core.theme.CardCornerRadius
import com.example.umma.core.theme.CardElevation
import com.example.umma.core.theme.ChipCornerRadius
import com.example.umma.core.theme.ChipPaddingHorizontal
import com.example.umma.core.theme.ChipPaddingVertical
import com.example.umma.core.theme.IconSizeLarge
import com.example.umma.core.theme.SpacingL
import com.example.umma.core.theme.SpacingM
import com.example.umma.core.theme.SpacingS
import com.example.umma.core.theme.SpacingXL
import com.example.umma.core.theme.SpacingXS
import com.example.umma.core.theme.SpacingXXL
import com.example.umma.core.theme.TextCardR
import com.example.umma.core.theme.TextExplanationR
import com.example.umma.core.theme.ThemePrimary
import com.example.umma.core.theme.TitleCardR

/**
 * 대시보드 - 최근 AI 대화 카드.
 *
 * SSOT: DASH-002_Recent_AI_Conversation_Card.md
 *
 * 충족 AC:
 *  - AC 1 카드 정상 출력
 *  - AC 3 최근 대화 주제 표시 (하단 "주제" 칩)
 *  - AC 4 최근 대화 시간 표시 (하단 "대화 기록" 칩)
 *  - AC 5 카드 클릭 → AI Chat 이동 (호출자 [onClick] 람다가 navigate 담당)
 *  - AC 7 최근 대화 데이터가 없을 경우 Empty 상태 (시간/주제 칩 자동 hide)
 *  - AC 8 카드 클릭 중 중복 Navigation 방지 ([rememberDashboardCardClick] 500ms throttle)
 *
 * 스킵 AC:
 *  - AC 2 현재 선택 언어가 카드에 표시 — 화면 우측 상단 LearningLanguageSelector
 *         (DASH-006) 가 이미 현재 언어를 노출하므로 카드 본문에 중복 표시하지 않는다.
 *  - AC 6 AI Chat 초기 상태에 selectedLanguage 반영 — 팀 정책상 AI Chat 이 [GlobalLangState]
 *         를 직접 구독하므로 Dashboard 측 인자 전달 불필요.
 *
 * @param recentConversationTopic 최근 대화 주제. null 이면 주제 칩 미표시.
 * @param recentConversationMinutes 최근 대화 누적 분. null 이면 시간 칩 미표시.
 * @param onClick 카드 클릭 시 호출. throttle 은 카드 내부에서 처리되므로 호출자는 단순히
 *                navigate 만 수행하면 된다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationCard(
    recentConversationTopic: String?,
    recentConversationMinutes: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEmpty: Boolean = false
) {
    val accent = ThemePrimary
    // AC 8: 카드 onClick 을 throttle 로 감싸 연타 → 중복 navigate 차단.
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
                // 좌우 padding 을 작게 가져가 칩 컨테이너의 가용 폭을 최대한 확보.
                //   카드 폭(184dp 추정) 안에서 시간 칩 + 주제 칩 가로 배치가 가능하려면
                //   가용 폭이 ~160dp 이상 필요. 좌우 SpacingS(8dp) 로 ~168dp 확보.
                //   상하는 기존 그대로 유지해 아이콘/타이틀/본문 시각 위치 보존.
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = SpacingS, vertical = SpacingXL),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(SpacingM))

                Icon(
                    painter = painterResource(id = R.drawable.record_voice_over_24),
                    contentDescription = "대화",
                    tint = accent,
                    modifier = Modifier.size(IconSizeLarge)
                )

                Spacer(modifier = Modifier.height(SpacingXXL))
                Text(text = "대화", color = accent, style = TitleCardR)

                Spacer(modifier = Modifier.height(SpacingXXL))

                Text(
                    text = "대화를 시작해 볼까요?",
                    style = TextExplanationR,
                    color = accent
                )

                Spacer(modifier = Modifier.weight(1f))

                if (recentConversationMinutes != null || recentConversationTopic != null) {
                    // 두 칩을 FlowRow 로 배치 — 가용 폭이 충분하면 한 줄에 가로 정렬,
                    // 부족하면 주제 칩이 자동으로 다음 줄로 떨어진다. 짧은 주제(Travel /
                    // 일상 등) 는 시간 칩과 한 줄에 나란히, 긴 주제는 자연스럽게 줄바꿈.
                    //
                    // verticalArrangement 는 줄바꿈이 발생했을 때 두 줄 사이 간격.
                    FlowRow(
                        // padding 이 SpacingS 로 줄어 칩이 카드 좌측에 적절히 붙으므로
                        // 이전의 -SpacingM offset 보정은 불필요.
                        modifier = Modifier.align(Alignment.Start),
                        horizontalArrangement = Arrangement.spacedBy(SpacingXS),
                        verticalArrangement = Arrangement.spacedBy(SpacingXS)
                    ) {
                        if (recentConversationMinutes != null) {
                            CardInfoChip(
                                text = "대화 기록: ${recentConversationMinutes}분",
                                accent = accent
                            )
                        }
                        if (recentConversationTopic != null) {
                            // 다음 줄로 떨어진 뒤에도 자기 폭이 카드 폭을 초과하는 극단 케이스
                            // 안전망 — 2줄까지 wrap, 초과분은 ellipsis.
                            CardInfoChip(
                                text = "주제: $recentConversationTopic",
                                accent = accent,
                                maxLines = 2,
                                softWrap = true
                            )
                        }
                    }
                }
            }

            // Empty 상태 시각 표시: FeedbackCard 의 빨간 점 패턴과 동일한 위치/크기.
            //   대화 카드는 색까지 회색 처리하지 않고 ThemePrimary 를 유지해 CTA 성격을
            //   살린다. 점만 우측 상단에 부착해 "이전 대화 없음" 을 가볍게 표시.
            if (isEmpty) {
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

/**
 * 카드 하단 정보 칩 공용 컴포저블.
 *
 * 칩 배경(Box) 은 자식 Text 의 측정 폭/높이를 그대로 따라간다.
 *  - 기존에는 overflow=Visible 로 인해 Text 만 박스 밖으로 흘러나가 배경이 늘어나지
 *    않는 시각 버그가 있었음 → Visible 제거하고 호출자가 줄바꿈 정책을 결정한다.
 *
 * @param maxLines 표시할 최대 줄 수. 단일 정보 칩(시간/카드 수 등) 은 1 유지.
 *                 ConversationCard 의 "주제" 칩처럼 긴 텍스트가 들어오는 경우 2~3 사용.
 * @param softWrap maxLines > 1 일 때 단어 단위 줄바꿈 허용 여부.
 */
@Composable
internal fun CardInfoChip(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    softWrap: Boolean = false
) {
    Box(
        modifier = modifier
            .background(
                color = accent,
                shape = RoundedCornerShape(ChipCornerRadius)
            )
            .padding(
                horizontal = ChipPaddingHorizontal,
                vertical = ChipPaddingVertical
            )
    ) {
        Text(
            text = text,
            style = TextCardR,
            color = BackgroundSecondary,
            maxLines = maxLines,
            softWrap = softWrap,
            // maxLines 초과 시 …로 마무리. 단일 라인 칩에서는 사실상 발생할 일 없음
            // (텍스트 길이가 박스를 정의하므로) — multiline 칩의 안전망 용도.
            overflow = TextOverflow.Ellipsis
        )
    }
}
