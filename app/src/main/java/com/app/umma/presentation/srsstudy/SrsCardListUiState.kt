package com.app.umma.presentation.srsstudy

import com.app.umma.domain.model.flashcard.Flashcard

data class SrsCardListUiState(
    // 불러오는 중
    val isLoading: Boolean = true,
    // 조회 실패
    val hasLoadError: Boolean = false,
    // 보여줄 카드 목록
    val cards: List<Flashcard> = emptyList()
)