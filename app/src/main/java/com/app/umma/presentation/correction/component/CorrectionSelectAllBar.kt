package com.app.umma.presentation.correction.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.app.umma.core.theme.SpacingL
import com.app.umma.core.theme.SpacingS
import com.app.umma.core.theme.TextAnalysisR
import com.app.umma.core.theme.TextCorrectionSB
import com.app.umma.core.theme.ThemePrimary
import com.app.umma.core.theme.TitleColor

/**
 * 교정 결과 카드 목록 상단에 위치하는 전체 선택/해제 토글 행.
 *
 * SSOT: COR-004_Card_Selection.md / COR-FIX-06
 *
 * [allSelected] 가 true 이면 "전체 해제", false 이면 "전체 선택" 라벨을 표시한다.
 * 우측에는 현재 선택 수 / 전체 수를 진한 갈색([TitleColor]) 카운터로 노출해 선택 상태를 한눈에 보여준다.
 *
 * Content/Retry phase 에서만 CorrectionScreen 이 렌더링하므로 이 컴포넌트 자체에 phase 가드는 없다.
 *
 * @param totalCount 현재 화면에 표시된 교정 후보 카드 총 수.
 * @param selectedCount 현재 선택된 카드 수.
 * @param allSelected 전체 카드가 선택된 상태이면 true.
 * @param onToggleSelectAll 버튼 탭 시 호출. 전체 선택 ↔ 전체 해제를 토글한다.
 */
@Composable
fun CorrectionSelectAllBar(
    totalCount: Int,
    selectedCount: Int,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SpacingL),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onToggleSelectAll) {
            Text(
                text = if (allSelected) "전체 해제" else "전체 선택",
                style = TextCorrectionSB,
                color = ThemePrimary,
            )
        }
        Text(
            text = "$selectedCount / $totalCount",
            style = TextAnalysisR,
            color = TitleColor,
            modifier = Modifier.padding(end = SpacingS),
        )
    }
}

// ─── Preview ────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFF8F2E5, name = "전체 선택 전")
@Composable
private fun CorrectionSelectAllBarUnselectedPreview() {
    CorrectionSelectAllBar(
        totalCount = 5,
        selectedCount = 0,
        allSelected = false,
        onToggleSelectAll = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF8F2E5, name = "전체 선택 후")
@Composable
private fun CorrectionSelectAllBarAllSelectedPreview() {
    CorrectionSelectAllBar(
        totalCount = 5,
        selectedCount = 5,
        allSelected = true,
        onToggleSelectAll = {},
    )
}
