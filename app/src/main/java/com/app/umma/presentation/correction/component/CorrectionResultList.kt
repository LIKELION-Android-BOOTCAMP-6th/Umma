package com.app.umma.presentation.correction.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.app.umma.core.theme.SpacingL
import com.app.umma.core.theme.SpacingM
import com.app.umma.data.repository.correction.CorrectionSuggestionFixtures
import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.learningstate.LangCode

/**
 * 교정 결과 카드 목록.
 *
 * SSOT: COR-003_Result_Cards.md / COR-004_Card_Selection.md
 *
 * [suggestions] 리스트를 [LazyColumn] 으로 렌더링하여 카드 수가 많아질 때 스크롤이 가능하게 한다.
 * 빈 리스트는 Empty 화면 없이 그냥 빈 스크롤 영역으로 처리한다.
 * (Empty 상태 분리는 COR-002-B 에서 Phase.Empty 와 함께 다룬다.)
 *
 * COR-004:
 *  - [selectedIds] 로 각 카드의 선택 상태를 결정한다.
 *  - 카드 탭 이벤트는 [onCardClicked] 로 위임하며, 호출자(화면) 가 ViewModel 의
 *    toggleSuggestionSelection 으로 다시 위임한다.
 *
 * @param suggestions Content 상태에서 화면에 표시할 교정 결과 목록.
 * @param selectedIds 현재 선택된 카드의 CorrectionSuggestion.id 집합.
 * @param onCardClicked 카드 탭 시 호출. 파라미터는 탭된 카드의 id.
 */
@Composable
fun CorrectionResultList(
    suggestions: List<CorrectionSuggestion>,
    selectedIds: Set<String>,
    onCardClicked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = SpacingL, vertical = SpacingM),
        verticalArrangement = Arrangement.spacedBy(SpacingM)
    ) {
        items(
            items = suggestions,
            key = { suggestion -> suggestion.id }
        ) { suggestion ->
            CorrectionResultCard(
                suggestion = suggestion,
                isSelected = suggestion.id in selectedIds,
                onClick = { onCardClicked(suggestion.id) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─── Preview ────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFF8F2E5, name = "카드 목록 — 3장 스크롤")
@Composable
private fun CorrectionResultListPreview() {
    val suggestions = CorrectionSuggestionFixtures.contentSuggestions(LangCode.EN)
    CorrectionResultList(
        suggestions = suggestions,
        selectedIds = emptySet(),
        onCardClicked = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF8F2E5, name = "카드 목록 — 일부 선택")
@Composable
private fun CorrectionResultListWithSelectionPreview() {
    // COR-004: 일부만 선택된 상태의 시각 검증용.
    val suggestions = CorrectionSuggestionFixtures.contentSuggestions(LangCode.EN)
    CorrectionResultList(
        suggestions = suggestions,
        selectedIds = setOfNotNull(suggestions.firstOrNull()?.id),
        onCardClicked = {},
    )
}
