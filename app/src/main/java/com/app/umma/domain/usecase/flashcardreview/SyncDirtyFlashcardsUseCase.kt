package com.app.umma.domain.usecase.flashcardreview

import com.app.umma.domain.repository.FlashcardRepository
import javax.inject.Inject

/**
 * pending dirty flashcard 를 Firestore 에 일괄 재시도 sync 하는 UseCase
 * 실패: 다음 호출때 재시도
 */
class SyncDirtyFlashcardsUseCase @Inject constructor(
    private val flashcardRepository: FlashcardRepository
) {
    suspend operator fun invoke(userId: String): Result<Int> {
        if (userId.isBlank()) return Result.success(0)
        return flashcardRepository.syncDirtyFlashcards(userId)
    }
}