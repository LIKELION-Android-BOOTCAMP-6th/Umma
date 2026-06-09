package com.app.umma.presentation.correction.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.app.umma.core.theme.BackgroundHighlight
import com.app.umma.core.theme.BackgroundSecondary
import com.app.umma.core.theme.CardCornerRadius
import com.app.umma.core.theme.CardElevation
import com.app.umma.core.theme.ChipCornerRadius
import com.app.umma.core.theme.IconSizeSmall
import com.app.umma.core.theme.SpacingL
import com.app.umma.core.theme.SpacingM
import com.app.umma.core.theme.SpacingS
import com.app.umma.core.theme.TextCorrect
import com.app.umma.core.theme.TextCorrectionSB
import com.app.umma.core.theme.TextExplanationR
import com.app.umma.core.theme.TextLogout
import com.app.umma.core.theme.TextPrimary
import com.app.umma.core.theme.TextWrong
import com.app.umma.core.theme.ThemePrimary
import com.app.umma.core.theme.TitleColor
import com.app.umma.data.repository.correction.CorrectionSuggestionFixtures
import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.learningstate.LangCode

/**
 * 교정 결과 카드 한 장.
 *
 * SSOT: COR-003_Result_Cards.md / COR-004_Card_Selection.md
 *
 * 표시 영역:
 *  - 헤더: nativeText (인용 + 정적 스피커 아이콘 — TTS 클릭은 후속 backlog 에서 연결)
 *  - Before 행: beforeText (빨간 X, 흐린 색상)
 *  - After 행: afterText (청록 체크 + 강조 배경 박스)
 *  - Explanation 행: explanation (보라 Info 아이콘)
 *
 * 선택 상태 (COR-004):
 *  - [onClick] 이 카드 전체 탭 영역에서 호출된다 (좌측 별도 체크박스가 아닌 카드 전체 토글).
 *  - [isSelected] 가 true 면 [ThemePrimary] 색 border 로 시각적으로 강조한다.
 *
 * 비범위:
 *  - 좌측 별도 체크박스 위젯 + "전체 선택" 토글 — COR-004 다음 백로그.
 *  - 스피커 클릭/TTS 동작 (후속 backlog).
 *  - 내부 후보(CorrectionCandidate) 데이터 노출 없음.
 *
 * @param suggestion 화면에 표시할 교정 결과 계약. [CorrectionSuggestion.nativeText],
 *                   [CorrectionSuggestion.beforeText], [CorrectionSuggestion.afterText],
 *                   [CorrectionSuggestion.explanation] 만 참조한다.
 * @param isSelected 사용자가 저장 대상으로 골랐는지 여부. ViewModel 의 selectedSuggestionIds 에서 파생.
 * @param onClick 카드 전체 탭 시 호출. 호출자가 suggestion.id 를 바인딩해서 toggleSuggestionSelection 으로 위임한다.
 */
@Composable
fun CorrectionResultCard(
    suggestion: CorrectionSuggestion,
    isSpeaking: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        // clickable 은 Card 자체에 걸어 카드 본문 어디를 눌러도 토글되게 한다.
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(CardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = BackgroundSecondary),
        elevation = CardDefaults.cardElevation(defaultElevation = CardElevation),
        // 선택 시 ThemePrimary border 강조 — ChatScreen.TopicButton 의 강조 패턴과 일관.
        border = if (isSelected) BorderStroke(1.5.dp, ThemePrimary) else null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingL),
            verticalArrangement = Arrangement.spacedBy(SpacingM)
        ) {
            // ─── 헤더: nativeText + 정적 스피커 아이콘 ───────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "\"${suggestion.nativeText}\"",
                    style = TextCorrectionSB,
                    color = TitleColor,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(SpacingS))
                // 정적 아이콘 — TTS 동작은 후속 backlog 에서 연결.
                IconButton(onClick = onSpeak) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "발음 듣기",
                        tint = if (isSpeaking) ThemePrimary else TitleColor,
                        modifier = Modifier.size(IconSizeSmall)
                    )
                }
            }

            // ─── Before 행: 교정 전 문장 ─────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(SpacingS),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Filled.Cancel,
                    contentDescription = "교정 전",
                    tint = TextLogout,
                    modifier = Modifier.size(IconSizeSmall)
                )
                Text(
                    text = suggestion.beforeText,
                    style = TextCorrectionSB,
                    color = TextWrong
                )
            }

            // ─── After 행: 교정 후 문장 (강조 배경) ─────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = BackgroundHighlight,
                        shape = RoundedCornerShape(ChipCornerRadius)
                    )
                    .padding(horizontal = SpacingL, vertical = SpacingM),
                horizontalArrangement = Arrangement.spacedBy(SpacingS),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "교정 후",
                    tint = TextCorrect,
                    modifier = Modifier.size(IconSizeSmall)
                )
                Text(
                    text = suggestion.afterText,
                    style = TextCorrectionSB,
                    color = TextCorrect
                )
            }

            // ─── Explanation 행: 교정 설명 ───────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(SpacingS),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "설명",
                    tint = TitleColor,
                    modifier = Modifier
                        .size(18.dp)
                        .offset(y = (-2).dp)
                )
                Text(
                    text = suggestion.explanation,
                    style = TextExplanationR,
                    color = TextPrimary,
                    modifier = Modifier.offset(y = (-1).dp)
                )
            }
        }
    }
}

// ─── Preview ────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFF8F2E5, name = "카드 — 정상")
@Composable
private fun CorrectionResultCardPreview() {
    val suggestion = CorrectionSuggestionFixtures.contentSuggestions(LangCode.EN).first()
    CorrectionResultCard(
        suggestion = suggestion,
        isSpeaking = false,
        isSelected = false,
        onClick = {},
        onSpeak = {},
        modifier = Modifier.padding(SpacingL)
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF8F2E5, name = "카드 — 선택됨")
@Composable
private fun CorrectionResultCardSelectedPreview() {
    // COR-004: 선택된 카드의 ThemePrimary border 강조 시각 검증용.
    val suggestion = CorrectionSuggestionFixtures.contentSuggestions(LangCode.EN).first()
    CorrectionResultCard(
        suggestion = suggestion,
        isSpeaking = true,
        isSelected = true,
        onClick = {},
        onSpeak = {},
        modifier = Modifier.padding(SpacingL)
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF8F2E5, name = "카드 — 긴 설명")
@Composable
private fun CorrectionResultCardLongExplanationPreview() {
    val base = CorrectionSuggestionFixtures.contentSuggestions(LangCode.EN).first()
    val longExplanation = base.copy(
        explanation = "3인칭 단수가 아닐 때는 동사 원형을 사용해요. " +
            "'I'는 1인칭이므로 'goes' 대신 'go'를 씁니다. " +
            "이 규칙은 현재 시제에서 주어가 3인칭 단수(he/she/it)일 때만 동사에 -s/-es를 붙이는 영어 문법 규칙에 근거합니다."
    )
    CorrectionResultCard(
        suggestion = longExplanation,
        isSpeaking = false,
        isSelected = false,
        onClick = {},
        onSpeak = {},
        modifier = Modifier.padding(SpacingL)
    )
}
