package com.app.umma.domain.usecase.flashcardreview

import com.app.umma.domain.model.flashcard.ReviewDeckState
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.repository.FlashcardRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** 현재 언어의 복습 대상 카드 스트림을 노출합니다. */
class ObserveReviewDeckUseCase @Inject constructor(
    private val flashcardRepository: FlashcardRepository
) {
    /**
     * SRS 화면은 이 스트림만 구독하고 카드 순서는 Repository 기준을 따른다.
     */
    operator fun invoke(
        userId: String,
        language: LangCode
    ): Flow<ReviewDeckState> {
        // 화면은 이 UseCase 의 결과만 구독하고, deck 필터와 상태 분기는 아래 repository 에 위임한다.
        return flashcardRepository.observeDueFlashcards(userId, language)
    }
}
