package com.app.umma.presentation.statistics.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.app.umma.core.theme.BackgroundPrimary
import com.app.umma.core.theme.BackgroundSecondary
import com.app.umma.core.theme.CardCornerRadius
import com.app.umma.core.theme.SpacingL
import com.app.umma.core.theme.SpacingM
import com.app.umma.core.theme.SpacingS
import com.app.umma.core.theme.TextExplanationR
import com.app.umma.core.theme.TextPrimary
import com.app.umma.core.theme.TextPrimaryR
import com.app.umma.core.theme.TextSecondaryR
import com.app.umma.core.theme.ThemePrimary
import com.app.umma.core.theme.ThemeSecondary
import com.app.umma.core.theme.TitleB
import com.app.umma.domain.model.learningstate.ConversationAbilityBand

private val GUIDE_DIALOG_MAX_WIDTH = 560.dp
private const val GUIDE_DIALOG_WIDTH_FRACTION = 0.92f

/**
 * 종합 레벨 카드 클릭 시 보여주는 단계 정의 안내 dialog다.
 *
 * 종합 레벨은 숫자 변화보다 "내 현재 단계가 어떤 언어 사용 상태인지" 이해하는 것이 중요하므로,
 * chart 대신 전체 레벨 정의를 한 번에 보여준다.
 */
@Composable
fun StatisticsConversationLevelGuideDialog(
    currentBand: ConversationAbilityBand?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(SpacingM),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = modifier
                    .fillMaxWidth(GUIDE_DIALOG_WIDTH_FRACTION)
                    .widthIn(max = GUIDE_DIALOG_MAX_WIDTH)
                    // 6개 레벨 정의가 작은 기기에서 화면 밖으로 밀리지 않도록 dialog 높이를 제한한다.
                    .heightIn(max = maxHeight * 0.90f),
                shape = RoundedCornerShape(CardCornerRadius),
                color = BackgroundPrimary,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(SpacingL)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(SpacingM)
                ) {
                    ConversationLevelGuideHeader(onDismiss = onDismiss)
                    conversationLevelGuideItems().forEach { item ->
                        // 현재 사용자 레벨은 같은 목록 안에서 강조해, 카드의 값과 정의를 바로 연결한다.
                        ConversationLevelGuideRow(
                            item = item,
                            isCurrent = item.band == currentBand
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationLevelGuideHeader(
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SpacingS)
        ) {
            Text(
                text = "종합 레벨 안내",
                style = TitleB,
                color = ThemePrimary
            )
            Text(
                text = "각 Level은 대화에서 어느 정도로 의도를 표현하고 이어갈 수 있는지를 뜻합니다.",
                style = TextSecondaryR,
                color = TextPrimary.copy(alpha = 0.72f)
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "닫기",
                tint = TextPrimary
            )
        }
    }
}

@Composable
private fun ConversationLevelGuideRow(
    item: ConversationLevelGuideItem,
    isCurrent: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardCornerRadius),
        colors = CardDefaults.cardColors(
            // 현재 레벨은 배경을 아주 약하게 강조해 목록 전체의 가독성을 해치지 않는다.
            containerColor = if (isCurrent) {
                ThemePrimary.copy(alpha = 0.08f)
            } else {
                BackgroundSecondary
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isCurrent) {
                ThemePrimary.copy(alpha = 0.42f)
            } else {
                TextPrimary.copy(alpha = 0.08f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(SpacingM),
            verticalArrangement = Arrangement.spacedBy(SpacingS)
        ) {
            Text(
                text = if (isCurrent) "${item.title} · 현재 레벨" else item.title,
                style = TextPrimaryR.copy(fontWeight = FontWeight.SemiBold),
                color = if (isCurrent) ThemePrimary else TextPrimary
            )
            Text(
                text = item.description,
                style = TextExplanationR,
                color = TextPrimary.copy(alpha = 0.82f)
            )
            Text(
                text = item.aiSupport,
                style = TextSecondaryR,
                color = ThemeSecondary.copy(alpha = 0.86f)
            )
        }
    }
}

private data class ConversationLevelGuideItem(
    // 내부 band와 화면 설명을 연결해 현재 레벨 강조가 enum 변경에도 안전하게 따라가도록 한다.
    val band: ConversationAbilityBand,
    // 카드와 동일한 사용자-facing 레벨명이다.
    val title: String,
    // 사용자가 이 레벨의 의미를 이해할 수 있도록 실제 발화 능력 중심으로 설명한다.
    val description: String,
    // Umma가 해당 단계에서 어떤 방식으로 대화를 도울지 함께 알려준다.
    val aiSupport: String
)

private fun conversationLevelGuideItems(): List<ConversationLevelGuideItem> {
    return listOf(
        ConversationLevelGuideItem(
            band = ConversationAbilityBand.IntentOnly,
            title = "시작 Level",
            description = "단어 조각, 기준언어, 짧은 소리만으로도 의도를 전달하려는 단계입니다.",
            aiSupport = "Umma는 의도를 먼저 확인하고, 필요한 경우 기준언어를 섞어 아주 짧게 이어갑니다."
        ),
        ConversationLevelGuideItem(
            band = ConversationAbilityBand.PhraseEmerging,
            title = "단어 Level",
            description = "핵심 단어나 짧은 표현으로 원하는 것, 감정, 상황을 말하기 시작하는 단계입니다.",
            aiSupport = "Umma는 쉬운 단어와 짧은 표현을 자연스럽게 되받아 주며 대화를 이어갑니다."
        ),
        ConversationLevelGuideItem(
            band = ConversationAbilityBand.SimpleSentence,
            title = "문장 Level",
            description = "짧은 문장으로 상태나 생각을 말할 수 있지만 문법과 연결은 아직 흔들리는 단계입니다.",
            aiSupport = "Umma는 사용자의 뜻을 살려 더 자연스러운 짧은 문장으로 대화를 받아 줍니다."
        ),
        ConversationLevelGuideItem(
            band = ConversationAbilityBand.BasicConversation,
            title = "대화 Level",
            description = "익숙한 일상 주제에서는 몇 턴 동안 질문하고 답하며 대화를 유지할 수 있는 단계입니다.",
            aiSupport = "Umma는 너무 어렵게 확장하지 않고, 사용자가 계속 말할 수 있는 흐름을 만듭니다."
        ),
        ConversationLevelGuideItem(
            band = ConversationAbilityBand.ConnectedExpression,
            title = "표현 Level",
            description = "이유, 순서, 감정, 취향을 연결해 조금 더 풍부하게 말할 수 있는 단계입니다.",
            aiSupport = "Umma는 더 자연스러운 구어체 표현과 작은 확장을 대화 안에 섞어 줍니다."
        ),
        ConversationLevelGuideItem(
            band = ConversationAbilityBand.NuanceControl,
            title = "능숙 Level",
            description = "상황과 뉘앙스에 맞게 말투를 조절하며 원어민스러운 표현을 시도할 수 있는 단계입니다.",
            aiSupport = "Umma는 실제 대화에서 자주 쓰는 표현, 뉘앙스, 자연스러운 반응을 중심으로 이어갑니다."
        )
    )
}
