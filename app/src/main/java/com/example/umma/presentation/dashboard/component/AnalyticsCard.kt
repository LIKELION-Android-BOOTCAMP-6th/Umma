package com.example.umma.presentation.dashboard.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.example.umma.R
import com.example.umma.core.theme.BackgroundSecondary
import com.example.umma.core.theme.CardCornerRadius
import com.example.umma.core.theme.CardElevation
import com.example.umma.core.theme.IconSizeLarge
import com.example.umma.core.theme.SpacingM
import com.example.umma.core.theme.SpacingS
import com.example.umma.core.theme.SpacingXL
import com.example.umma.core.theme.SpacingXS
import com.example.umma.core.theme.SpacingXXL
import com.example.umma.core.theme.TextExplanationR
import com.example.umma.core.theme.TextPrimary
import com.example.umma.core.theme.TitleCardR

/**
 * 대시보드 - 대표 언어 성취율 카드.
 *
 * SSOT: DASH-005_Language_Progress_Card.md
 *  (선반영 메모: DASH-001 "Dashboard 카드 구성 → 4. 대표 언어 성취율 카드" 에서
 *   진입점 카드 골격을 먼저 잡았다.)
 *
 * "절대 점수보다 최근 성장량(delta)을 우선 노출한다." — SSOT
 *
 * 충족 AC:
 *  - AC 1 카드 정상 출력
 *  - AC 2 대표 학습 성장 지표(delta) 표시 — 하단 FlowRow 에 4 종 delta 칩 노출.
 *         값이 0 인 항목은 칩 자체를 hide ("성장 없음" 노이즈 제거).
 *  - AC 3 현재 선택 언어 기준 데이터 렌더링 — 호출자(DashboardScreen)가
 *         DashSummary[selectedLearningLanguage] 의 delta 4 종을 매핑 전달.
 *  - AC 4 카드 클릭 → Statistics 화면 이동 (호출자 [onClick] 람다가 navigate 담당,
 *         UmmaNavHost 에서 Route.Analytics 로 wiring 됨)
 *  - AC 6 통계 데이터 부족 시 Empty — delta 4 종 모두 0 일 때 FlowRow 전체 hide.
 *         본문 텍스트는 영구 CTA ("성취도를 확인해봐요!") 로 유지
 *         (ConversationCard / FeedbackCard / StudyCard 와 동일 패턴).
 *         별도 Empty 메시지("아직 충분한 학습 데이터가 없습니다…")는 Statistics
 *         화면 진입 후 책임. Dashboard 카드 표면은 시각 일관성 우선.
 *  - AC 7 카드 클릭 중 중복 Navigation 방지 — [rememberDashboardCardClick] 500ms throttle
 *
 * 스킵 AC:
 *  - AC 5 selectedLearningLanguage 가 Statistics 초기 상태에 반영 — 팀 정책상
 *         Statistics 화면이 [GlobalLangState] 를 직접 구독하므로 Dashboard 측
 *         인자 전달 불필요. (DASH-002 / DASH-003 / DASH-004 와 동일)
 *
 * 칩 라벨 단축 근거: SSOT 예시("문법 정확도 +8" 등) 보다 짧은 "문법 +8" 등을 사용.
 *   카드 폭(~184dp) 안에서 4 개 칩이 FlowRow wrap 으로 들어가야 하기 때문.
 *   부호는 Kotlin "%+d" 포맷으로 자동 처리 — 양수 "+8", 음수 "-3"
 *   (0 은 어차피 칩 자체가 hide).
 *
 * @param grammarScoreDelta 문법 성취 변화량. 0 이면 칩 미표시.
 * @param vocabularyScoreDelta 어휘 성취 변화량. 0 이면 칩 미표시.
 * @param fluencyScoreDelta 유창성 성취 변화량. 0 이면 칩 미표시.
 * @param naturalnessScoreDelta 자연스러움 성취 변화량. 0 이면 칩 미표시.
 * @param onClick 카드 클릭 시 호출. throttle 은 카드 내부에서 처리되므로 호출자는
 *                단순히 navigate 만 수행하면 된다.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AnalyticsCard(
    grammarScoreDelta: Int,
    vocabularyScoreDelta: Int,
    fluencyScoreDelta: Int,
    naturalnessScoreDelta: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    val accent = accentColor ?: TextPrimary
    // AC 7: 카드 onClick 을 throttle 로 감싸 연타 → 중복 navigate 차단.
    //   DASH-002 / DASH-003 / DASH-004 카드와 동일 헬퍼
    //   (DashboardCardCommon.rememberDashboardCardClick) 재사용. 500ms 윈도우
    //   안의 추가 클릭은 silently drop.
    val throttledOnClick = rememberDashboardCardClick(onClick)

    Card(
        onClick = throttledOnClick,
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

            // AC 2 / AC 6: delta 4 종 칩 노출.
            //   하나라도 비-0 일 때만 FlowRow 진입 — 모두 0 인 신규 사용자 케이스
            //   에서는 영역 전체 hide 되어 본문 CTA 만 남는다 (다른 3 카드 Empty 패턴 동일).
            //   값이 충분히 많이 들어오면 FlowRow 가 자동 wrap — 좁은 카드 폭에서도
            //   짧은 라벨("문법 +8") 4 개가 2 줄에 걸쳐 자연스럽게 배치된다.
            if (grammarScoreDelta != 0 ||
                vocabularyScoreDelta != 0 ||
                fluencyScoreDelta != 0 ||
                naturalnessScoreDelta != 0
            ) {
                FlowRow(
                    modifier = Modifier.align(Alignment.Start),
                    horizontalArrangement = Arrangement.spacedBy(SpacingXS),
                    verticalArrangement = Arrangement.spacedBy(SpacingXS)
                ) {
                    if (grammarScoreDelta != 0) {
                        CardInfoChip(
                            text = "문법 ${"%+d".format(grammarScoreDelta)}",
                            accent = accent
                        )
                    }
                    if (vocabularyScoreDelta != 0) {
                        CardInfoChip(
                            text = "어휘 ${"%+d".format(vocabularyScoreDelta)}",
                            accent = accent
                        )
                    }
                    if (fluencyScoreDelta != 0) {
                        CardInfoChip(
                            text = "유창성 ${"%+d".format(fluencyScoreDelta)}",
                            accent = accent
                        )
                    }
                    if (naturalnessScoreDelta != 0) {
                        CardInfoChip(
                            text = "자연스러움 ${"%+d".format(naturalnessScoreDelta)}",
                            accent = accent
                        )
                    }
                }
            }
        }
    }
}
