package com.app.umma.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.app.umma.core.theme.ChipCornerRadius
import com.app.umma.core.theme.SpacingL
import com.app.umma.core.theme.SpacingS
import com.app.umma.core.theme.ThemePrimary

/**
 * Empty 상태 안내 + CTA 버튼 공용 컴포저블.
 *
 * 학습(SRS) Empty 화면과 통계 Empty 화면이 동일한 룩앤필을 공유하기 위해 사용한다.
 * 본문 메시지·CTA 레이블은 호출부가 주입하며, 레이아웃/스타일은 이 컴포넌트가 통일한다.
 *
 * @param title 상단 안내 메인 문구.
 * @param subtitle 하단 보조 설명 문구.
 * @param ctaText CTA 버튼 레이블.
 * @param onCta CTA 버튼 클릭 콜백.
 */
@Composable
fun EmptyCtaContent(
    title: String,
    subtitle: String,
    ctaText: String,
    onCta: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpacingS)
    ) {
        Text(title)
        Text(subtitle)
        Spacer(modifier = Modifier.height(SpacingL))
        Button(
            onClick = onCta,
            shape = RoundedCornerShape(ChipCornerRadius),
            colors = ButtonDefaults.buttonColors(containerColor = ThemePrimary),
            modifier = Modifier
                .padding(top = SpacingL)
                .padding(horizontal = SpacingL, vertical = SpacingS),
        ) {
            Text(ctaText)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyCtaContentPreview() {
    EmptyCtaContent(
        title = "아직 보여줄 통계가 없어요",
        subtitle = "AI와 대화하고 교정을 받으면 실력 변화가 쌓여요",
        ctaText = "AI 교정하러 가기",
        onCta = {}
    )
}
