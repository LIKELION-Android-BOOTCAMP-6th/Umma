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
import com.example.umma.core.theme.PercentageDialogB
import com.example.umma.core.theme.SpacingL
import com.example.umma.core.theme.SpacingM
import com.example.umma.core.theme.SpacingS
import com.example.umma.core.theme.SpacingXL
import com.example.umma.core.theme.SpacingXXL
import com.example.umma.core.theme.TextCorrect
import com.example.umma.core.theme.TextExplanationR
import com.example.umma.core.theme.TitleCardR

/**
 * 대시보드 - Flashcard 학습 카드.
 *
 * SSOT: DASH-001 "Dashboard 카드 구성 → 3. Flashcard 학습 카드" / DASH-004 본 구현 대상.
 *
 * @param dueFlashcards 오늘 복습 대상 카드 수. > 0 일 때만 하단 칩 노출.
 * @param recentSavedFlashcards 최근 저장된 카드 수. > 0 일 때 우상단 "+N" 노출.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyCard(
    dueFlashcards: Int,
    recentSavedFlashcards: Int = 5, // 현재 디폴트값: 5
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = TextCorrect

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

                if (dueFlashcards > 0) {
                    Row(
                        // 좌우 padding 축소 후 offset 보정 불필요.
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        CardInfoChip(
                            text = "총 학습 카드 수: $dueFlashcards",
                            accent = accent
                        )
                    }
                }
            }

            if (recentSavedFlashcards > 0) {
                Text(
                    text = "+$recentSavedFlashcards",
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
