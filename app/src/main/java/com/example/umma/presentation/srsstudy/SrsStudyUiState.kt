package com.example.umma.presentation.srsstudy

import com.example.umma.domain.model.flashcard.Flashcard
import com.example.umma.domain.model.flashcard.ReviewRating
import com.example.umma.domain.model.learningstate.LangCode

data class SrsStudyUiState(
    val isLoading: Boolean = true,
    // 언어 정보 불러오기 실패 시 true, "다시 시도" 버튼 표시
    val hasInitError: Boolean = false,
    // GlobalLAngState 현재 학습 언어
    val selectedLearningLanguage: LangCode? = null,

    // 오늘 복습할 카드 목록
    val cards: List<Flashcard> = emptyList(),
    // 현재 화면의 카드 번호(시작: 0번)
    val currentCardIndex: Int = 0,
    // false = 앞면(모국어 번역, 내가 말했던 틀린 답), true = 뒷면(듣기 버튼, 정답, Grammar Note)
    val isCardFlipped: Boolean = false,
    // 모든 카드 끝냈을 시 true -> 완료 화면
    val isDone: Boolean = false,
    // 현재 선택된 평가 버튼(Again, Hard, Hood, Easy, null = 아직 선택 안함)
    val selectedRating: ReviewRating? = null
) {
    /** 지금 보고 있는 카드, 없으면 null */
    val currentCard: Flashcard?
        get() = cards.getOrNull(currentCardIndex)

    /** */
    val totalCards: Int
        get() = cards.size

    val remainingCards: Int
        get() = (totalCards - currentCardIndex).coerceAtLeast(0)
}