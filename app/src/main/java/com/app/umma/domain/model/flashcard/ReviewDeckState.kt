package com.app.umma.domain.model.flashcard

/** 복습 덱 조회 결과를 UI 친화적으로 표현한 상태입니다. */
sealed interface ReviewDeckState {
    data object Empty : ReviewDeckState

    data class Content(val cards: List<Flashcard>) : ReviewDeckState

    data class Retry(val cause: Throwable? = null) : ReviewDeckState

    data class Error(val cause: Throwable? = null) : ReviewDeckState
}
