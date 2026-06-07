package com.app.umma.domain.usecase.flashcardreview

import com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateInput
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.repository.FlashcardRepository
import com.app.umma.domain.usecase.learningstate.ApplyFlashcardSummaryUpdateUseCase
import javax.inject.Inject

/**
 * 목록 화면에서 선택된 플래시카드를 삭제하는 UseCase
 *
 */
class DeleteFlashcardsUseCase @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
    private val applyFlashcardSummaryUpdateUseCase: ApplyFlashcardSummaryUpdateUseCase
) {
    suspend operator fun invoke(
        userId: String,
        language: LangCode,
        flashcardIds: List<String>
    ): Result<Unit> {
        // 삭제 실패시 fail 반환, 종료
        flashcardRepository.deleteFlashcards(userId, flashcardIds)
            .getOrElse { return Result.failure(it) }
        // 삭제가 반영된 Room 상태를 기준으로 FlashcardSummary/DashSummary에 넣을 숫자를 다시 계산
        val now = System.currentTimeMillis()
        val snapshot = flashcardRepository.getReviewSummary(userId, language, now).getOrNull()

        // FlashcardSummary + DashSummary 는 반영
        if (snapshot != null) {
            runCatching {
                applyFlashcardSummaryUpdateUseCase(
                    FlashcardSummaryUpdateInput(
                        uid = userId,
                        lang = language,
                        dueFlashcards = snapshot.dueFlashcards,
                        notifiableDueFlashcards = snapshot.notifiableDueFlashcards,
                        savedFlashcards = snapshot.savedFlashcards,
                        sourceEventId =
                            "flashcard-delete:${flashcardIds.sorted().joinToString(",")}:$now",
                        updatedAt = now,
                    )
                )
            }
        }
        return Result.success(Unit)
    }
}
