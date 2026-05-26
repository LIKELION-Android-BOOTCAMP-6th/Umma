package com.app.umma.presentation.dashboard.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.app.umma.core.theme.BackgroundSecondary
import com.app.umma.core.theme.SpacingXL
import com.app.umma.core.theme.SpacingXS
import com.app.umma.core.theme.ThemePrimary
import com.app.umma.domain.model.learningstate.LangCode

/**
 * 학습 언어 selector.
 *
 * SSOT: DASH-006_Language_Selector.md
 *
 * 와이어프레임 정합:
 *  - AppBar 우측에 노출되는 ThemePrimary 채워진 알약(pill) 버튼.
 *  - 가로로 길쭉하고 세로는 얇은 형태(Material Button 의 minHeight=40dp 제약을 피하기
 *    위해 Box + clickable 로 구성).
 *  - 클릭 시 dropdown 이 아닌 학습 언어 선택 다이얼로그가 열린다 (다이얼로그 본체는
 *    호출자 DashboardScreen 이 관리). selector 는 단일 onClick 콜백만 노출.
 *
 * @param selectedLang 현재 선택된 학습 언어. 버튼 라벨로 코드(예: "EN") 가 표시된다.
 * @param onClick 버튼 탭 콜백. 호출자가 학습 언어 선택 다이얼로그를 열도록 연결한다.
 * @param isLoading 변경 진행 중 여부. true 면 클릭 무시.
 */
@Composable
fun LearningLanguageSelector(
    selectedLang: LangCode,
    onClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .widthIn(min = 88.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(ThemePrimary)
            .clickable(enabled = !isLoading) { onClick() }
            // 가로/세로 패딩 분리: 가로는 SpacingXL 로 길쭉하게, 세로는 SpacingXS 로 얇게.
            .padding(horizontal = SpacingXL, vertical = SpacingXS),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = selectedLang.code.uppercase(),
            color = BackgroundSecondary
        )
    }
}
