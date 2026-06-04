package com.app.umma.domain.usecase.flashcardreview

import com.app.umma.domain.repository.FlashcardRepository
import javax.inject.Inject

/**
 * 목록 화면에서 선택된 플래시카드를 삭제하는 UseCase
 */
class DeleteFlashcardsUseCase @Inject constructor(
    private val flashcardRepository: FlashcardRepository
) {
    suspend operator fun invoke(
        userId: String,
        flashcardIds: List<String>
    ): Result<Unit> =
        flashcardRepository.deleteFlashcards(userId, flashcardIds)
}
