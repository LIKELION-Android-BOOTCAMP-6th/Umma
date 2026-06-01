package com.app.umma.domain.usecase.flashcardreview

import com.app.umma.domain.model.flashcard.Flashcard
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.repository.FlashcardRepository
import javax.inject.Inject

/**
 * 현재 학습 언어로 저장된 플래시카드 목록 조회
 */
class GetFlashcardsUseCase @Inject constructor(
    private val flashcardRepository: FlashcardRepository
) {
    suspend operator fun invoke(
        userId: String,
        language: LangCode
    ): Result<List<Flashcard>> =
        flashcardRepository.getFlashcards(userId, language)
}