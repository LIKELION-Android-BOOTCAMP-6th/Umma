package com.example.umma.data.repository

import com.example.umma.data.model.correction.CorrectionFlashcardDto
import com.example.umma.data.source.local.CorrectionFlashcardLocalDataSource
import com.example.umma.domain.model.flashcard.Flashcard
import com.example.umma.domain.model.flashcard.FlashcardSchedule
import com.example.umma.domain.model.flashcard.FlashcardUpdateResult
import com.example.umma.domain.model.flashcard.ReviewDeckState
import com.example.umma.domain.model.flashcard.ReviewScheduleResult
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.repository.FlashcardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** Correction이 제공한 local source를 감싸는 FlashcardRepository 구현체입니다. */
@Singleton
class FlashcardRepositoryImpl @Inject constructor(
    private val localDataSource: CorrectionFlashcardLocalDataSource
) : FlashcardRepository {

    override fun observeDueFlashcards(userId: String, language: LangCode): Flow<ReviewDeckState> {
        // MVP는 첫 진입 시점의 안전한 스냅샷만 반환하고, 이후 observe 스트림으로 넓힐 수 있다.
        return flow {
            // local source 에서 due deck 을 읽어 화면이 바로 쓸 수 있는 상태로 변환한다.
            val dueDtos = localDataSource.getDueFlashcards(
                uid = userId,
                language = language.code,
                now = System.currentTimeMillis(),
                limit = 100
            )

            if (dueDtos.isEmpty()) {
                emit(ReviewDeckState.Empty)
            } else {
                emit(ReviewDeckState.Content(dueDtos.map { it.toDomain() }))
            }
        }.catch { throwable ->
            // 로컬 조회 실패는 UI에서 Retry 또는 Error 상태로 이어질 수 있게 분리한다.
            emit(ReviewDeckState.Retry(throwable))
        }
    }

    override suspend fun updateFlashcardSchedule(
        userId: String,
        cardId: String,
        result: ReviewScheduleResult
    ): Result<FlashcardUpdateResult> {
        return try {
            // review 정책에서 계산된 값만 받아 저장소 계층이 로컬 원본을 갱신한다.
            val success = localDataSource.updateReviewSchedule(
                uid = userId,
                flashcardId = cardId,
                nextReviewAt = result.nextReviewAt,
                interval = result.interval,
                easeFactor = result.easeFactor,
                updatedAt = System.currentTimeMillis()
            )

            if (success) {
                // local save 성공은 곧바로 sync 완료가 아니므로 pending 으로 남긴다.
                Result.success(
                    FlashcardUpdateResult(
                        cardId = cardId,
                        isSyncPending = true
                    )
                )
            } else {
                Result.failure(NoSuchElementException("Flashcard $cardId not found for user $userId"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun CorrectionFlashcardDto.toDomain(): Flashcard {
        // correction 이 저장한 원본 필드를 SRS 용 domain 모델로만 변환한다.
        return Flashcard(
            id = id,
            language = LangCode.fromCode(language) ?: LangCode.EN,
            frontText = frontText,
            backText = backText,
            explanation = explanation,
            hint = null,
            schedule = FlashcardSchedule(
                interval = interval,
                easeFactor = easeFactor,
                nextReviewAt = nextReviewAt
            ),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
