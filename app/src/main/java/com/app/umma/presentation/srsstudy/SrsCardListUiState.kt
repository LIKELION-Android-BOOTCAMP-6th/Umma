package com.app.umma.presentation.srsstudy

import com.app.umma.domain.model.flashcard.Flashcard

enum class SrsCardSortOrder(val label: String) {
    LATEST("최신순"), OLDEST("오래된순"), DUE_SOON("복습 임박순")
}

data class SrsCardListUiState(
    // 불러오는 중
    val isLoading: Boolean = true,
    // 조회 실패
    val hasLoadError: Boolean = false,
    // 보여줄 카드 목록
    val cards: List<Flashcard> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    // 삭제 처리 중 여부
    val isDeleting: Boolean = false,
    // 화면에 한 번 보여줄 메시지 (Toast)
    val message: String? = null,
    // 현재 정렬 기준 (기본값: 최신 추가순)
    val sortOrder: SrsCardSortOrder = SrsCardSortOrder.LATEST
) {
    // 선택된 카드 수
    val selectedCount: Int get() = selectedIds.size

    // 카드가 1개 이상이고 모두 선택됐는지
    val areAllSelected: Boolean get() = cards.isNotEmpty() && selectedIds.size == cards.size

    // 선택된 카드가 하나라도 있는지 (삭제 버튼 활성화 기준)
    val hasSelection: Boolean get() = selectedIds.isNotEmpty()

    // 목록 정렬
    val displayedCards: List<Flashcard>
        get() = when (sortOrder) {
            SrsCardSortOrder.LATEST -> cards.sortedByDescending { it.createdAt }
            SrsCardSortOrder.OLDEST -> cards.sortedBy { it.createdAt }
            // nextReviewAt이 같다면 createdAt으로 2차 정렬
            SrsCardSortOrder.DUE_SOON -> cards.sortedWith(
                compareBy(
                { it.schedule.nextReviewAt },
                { it.createdAt }))
        }
}