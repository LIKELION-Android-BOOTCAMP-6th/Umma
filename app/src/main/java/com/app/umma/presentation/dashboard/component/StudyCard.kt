package com.app.umma.presentation.dashboard.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.app.umma.R
import com.app.umma.core.theme.BackgroundSecondary
import com.app.umma.core.theme.CardCornerRadius
import com.app.umma.core.theme.CardElevation
import com.app.umma.core.theme.IconSizeLarge
import com.app.umma.core.theme.PercentageDialogB
import com.app.umma.core.theme.SpacingL
import com.app.umma.core.theme.SpacingM
import com.app.umma.core.theme.SpacingS
import com.app.umma.core.theme.SpacingXL
import com.app.umma.core.theme.SpacingXXL
import com.app.umma.core.theme.TextCorrect
import com.app.umma.core.theme.TextExplanationR
import com.app.umma.core.theme.TitleCardR

/**
 * 대시보드 - Flashcard 학습 카드.
 *
 * SSOT: DASH-004_Flashcard_Study_Card.md
 *  (선반영 메모: DASH-001 "Dashboard 카드 구성 → 3. Flashcard 학습 카드" 에서 골격을 먼저 잡았다.)
 *
 * 충족 AC:
 *  - AC 1 카드 정상 출력
 *  - AC 2 복습 예정 Flashcard 수 표시 — 우상단 "+N" ([dueFlashcards] > 0).
 *         "오늘 학습해야 할 카드 수" 의미로 노출.
 *  - AC 3 최근 저장된 Flashcard 수 표시 — 하단 "총 학습 카드 수" 칩 ([savedFlashcards] > 0).
 *         현재 DashSummary 의 savedFlashcards 가 LS-002 doc 상 Firestore `recentSavedFlashcards`
 *         로 매핑되는 "누적/최근 저장" 의미 — 사용자에게는 "총 학습 카드 수" 로 노출하여
 *         학습 진척의 누적치를 보여준다.
 *  - AC 4 카드 클릭 → Flashcard 학습 화면 이동 (호출자 [onClick] 람다가 navigate 담당,
 *         UmmaNavHost 에서 Route.StudyList 로 wiring 됨)
 *  - AC 6 복습 카드가 없을 경우 Empty — [dueFlashcards] 0 일 때 우상단 "+N" 자동 hide.
 *         [savedFlashcards] 0 일 때 하단 칩 자동 hide. 본문 텍스트는 영구 CTA
 *         ("좋은 표현을 배워봐요!") 로 유지 (ConversationCard / FeedbackCard 와 동일 패턴)
 *  - AC 7 카드 클릭 중 중복 Navigation 방지 — [rememberDashboardCardClick] 500ms throttle
 *
 * 스킵 AC:
 *  - AC 5 selectedLearningLanguage 가 Flashcard 학습 초기 상태에 반영 — 팀 정책상
 *         Flashcard 학습 화면이 [GlobalLangState] 를 직접 구독하므로 Dashboard 측
 *         인자 전달 불필요. (DASH-002 ConversationCard / DASH-003 FeedbackCard 와 동일)
 *
 * @param dueFlashcards 오늘 복습 대상 카드 수. > 0 일 때만 우상단 "+N" 노출.
 * @param savedFlashcards 누적 저장 카드 수. > 0 일 때만 "총 학습 카드 수" 칩 노출.
 * @param onClick 카드 클릭 시 호출. throttle 은 카드 내부에서 처리되므로 호출자는 단순히
 *                navigate 만 수행하면 된다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyCard(
    dueFlashcards: Int,
    savedFlashcards: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    val accent = accentColor ?: TextCorrect
    // AC 7: 카드 onClick 을 throttle 로 감싸 연타 → 중복 navigate 차단.
    //   DASH-002 / DASH-003 카드와 동일 헬퍼 (DashboardCardCommon.rememberDashboardCardClick)
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
                    painter = painterResource(id = R.drawable.import_contacts_24),
                    contentDescription = "학습",
                    tint = accent,
                    modifier = Modifier.size(IconSizeLarge)
                )

                Spacer(modifier = Modifier.height(SpacingXXL))
                Text(text = "학습", color = accent, style = TitleCardR)

                Spacer(modifier = Modifier.height(SpacingXXL))

                Text(
                    text = "좋은 표현을 배워봐요!",
                    style = TextExplanationR,
                    color = accent
                )

                Spacer(modifier = Modifier.weight(1f))

                if (savedFlashcards > 0) {
                    Row(
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        CardInfoChip(
                            text = "총 학습 카드 수: $savedFlashcards",
                            accent = accent
                        )
                    }
                }
            }

            if (dueFlashcards > 0) {
                Text(
                    text = "+$dueFlashcards",
                    color = accent,
                    style = PercentageDialogB,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(SpacingL)
                )
            }
        }
    }
}
