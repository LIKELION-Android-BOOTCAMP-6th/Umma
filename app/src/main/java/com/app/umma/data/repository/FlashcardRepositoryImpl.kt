package com.app.umma.data.repository

import com.app.umma.data.model.correction.CorrectionFlashcardDto
import com.app.umma.data.source.local.CorrectionFlashcardLocalDataSource
import com.app.umma.data.source.remote.CorrectionFlashcardRemoteDataSource
import com.app.umma.domain.model.flashcard.Flashcard
import com.app.umma.domain.model.flashcard.FlashcardReviewSummary
import com.app.umma.domain.model.flashcard.FlashcardSchedule
import com.app.umma.domain.model.flashcard.FlashcardUpdateResult
import com.app.umma.domain.model.flashcard.ReviewDeckState
import com.app.umma.domain.model.flashcard.ReviewRating
import com.app.umma.domain.model.flashcard.ReviewScheduleResult
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.repository.FlashcardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** Correction이 제공한 local source를 감싸는 FlashcardRepository 구현체입니다. */
@Singleton
class FlashcardRepositoryImpl @Inject constructor(
    private val localDataSource: CorrectionFlashcardLocalDataSource,
    private val remoteDataSource: CorrectionFlashcardRemoteDataSource
) : FlashcardRepository {

    override fun observeDueFlashcards(userId: String, language: LangCode): Flow<ReviewDeckState> {
        // MVP는 첫 진입 시점의 안전한 스냅샷만 반환하고, 이후 observe 스트림으로 넓힐 수 있다.
        return flow {
            val now = System.currentTimeMillis()
            // local source 에서 due deck 을 읽어 화면이 바로 쓸 수 있는 상태로 변환한다.
            var dueDtos = localDataSource.getDueFlashcards(
                uid = userId,
                language = language.code,
                now = now,
                limit = 100
            )

            // due가 없을 때, "오늘 복습할 게 없음"인지 "원본이 통째로 없음인지 구분
            // 후자일 때만 Firestore에서 1회 복원 후 다시 조회
            if (dueDtos.isEmpty()) {
                restoreFlashcardsIfEmpty(userId, language.code)
                dueDtos = localDataSource.getDueFlashcards(
                    uid = userId,
                    language = language.code,
                    now = now,
                    limit = 100
                )
            }
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
        result: ReviewScheduleResult,
        lastReviewRating: ReviewRating?,
        lastReviewedAt: Long?
    ): Result<FlashcardUpdateResult> {
        return try {
            val updatedAt = System.currentTimeMillis()

            // 복습 평가 결과를 로컬 DB에 먼저 저장한다.
            val success = localDataSource.updateReviewSchedule(
                uid = userId,
                flashcardId = cardId,
                nextReviewAt = result.nextReviewAt,
                interval = result.interval,
                easeFactor = result.easeFactor,
                updatedAt = updatedAt,
                lastReviewRating = lastReviewRating?.name,
                lastReviewedAt = lastReviewedAt
            )

            if (!success) {
                return Result.failure(
                    NoSuchElementException("Flashcard cardId : $cardId  userId : $userId")
                )
            }

            val syncResult = remoteDataSource.syncReviewSchedule(
                flashcardId = cardId,
                nextReviewAt = result.nextReviewAt,
                interval = result.interval,
                easeFactor = result.easeFactor,
                updatedAt = updatedAt,
                lastReviewRating = lastReviewRating?.name,
                lastReviewedAt = lastReviewedAt
            )


            if (syncResult.isSuccess) {
                // Firestore sync 성공 -> 로컬 pending 표시 제거, isSyncPending=false 반환
                localDataSource.markSynced(uid = userId, flashcardIds = listOf(cardId))
                Result.success(
                    FlashcardUpdateResult(
                        cardId = cardId,
                        isSyncPending = false
                    )
                )
            } else {
                // Firestore sync 실패 -> 로컬 저장은 완료, isSyncPending=true로 나중에 재시도
                Result.success(
                    FlashcardUpdateResult(
                        cardId = cardId,
                        isSyncPending = true
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getReviewSummary(
        userId: String,
        language: LangCode,
        now: Long
    ): Result<FlashcardReviewSummary> {
        return try {
            // schedule update 이후 같은 local 원본에서 다시 count를 계산해야 Summary와 deck이 엇갈리지 않는다.
            val dueCount = localDataSource.countDueFlashcards(
                uid = userId,
                language = language.code,
                now = now
            )
            val notifiableDueCount = localDataSource.countNotifiableDueFlashcards(
                uid = userId,
                language = language.code,
                now = now
            )
            val savedCount = localDataSource.countFlashcards(
                uid = userId,
                language = language.code
            )

            Result.success(
                FlashcardReviewSummary(
                    dueFlashcards = dueCount,
                    notifiableDueFlashcards = notifiableDueCount,
                    savedFlashcards = savedCount
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * dirty 상태로 남은 카드를 Firestore에 일괄 재시도 sync
     * 성공: 카드 수 반환
     * 실패: Result.failure
     */
    override suspend fun syncDirtyFlashcards(userId: String): Result<Int> {
        return try {
            val dirtyCards = localDataSource.getDirtyFlashcards(uid = userId)
            // dirty 카드 없으면 Firestore 바로 반환
            if (dirtyCards.isEmpty()) return Result.success(0)

            // syncFlashcards는 batch set이라 문서 없으면 새로 생성
            // 이미 있으면 전체 필드 덮어씀
            val syncedIds = remoteDataSource.syncFlashcards(dirtyCards)
                .getOrElse { emptyList() }
            // sync 성공한 카드만 dirty=false 로 전환
            if (syncedIds.isNotEmpty()) {
                localDataSource.markSynced(uid = userId, flashcardIds = syncedIds)
            }
            Result.success(syncedIds.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 목록 화면용: 현재 언어로 저장된 카드 전체 조회
     */
    override suspend fun getFlashcards(
        userId: String,
        language: LangCode
    ): Result<List<Flashcard>> {
        return try {
            var cards = localDataSource.getFlashcards(
                uid = userId,
                language = language.code
            )
            // 목록도 재설치 등으로 비었으면 Firestore에서 1회 복원 후 다시 조회한다.
            if (cards.isEmpty()) {
                restoreFlashcardsIfEmpty(userId, language.code)
                cards = localDataSource.getFlashcards(
                    uid = userId,
                    language = language.code
                )
            }

            Result.success(cards.map { it.toDomain() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 선택된 카드를 로컬에서 먼저 지우고, 원격(Firestore)에도 반영한다.
     */
    override suspend fun deleteFlashcards(
        userId: String,
        flashcardIds: List<String>
    ): Result<Unit> {
        return try {
            // 선택된 카드가 없으면 아무 것도 하지 않고 성공 처리
            if (flashcardIds.isEmpty()) return Result.success(Unit)

            // 로컬 삭제
            localDataSource.deleteFlashcards(uid = userId, flashcardIds = flashcardIds)

            // Firestore - 실패하면 예외로 올려 화면에서 Toast 안내
            remoteDataSource.deleteFlashcards(flashcardIds).getOrThrow()

            Result.success(Unit)
        } catch (e: Exception) {
            // 삭제 중 예외 발생 시 화면에서 Toast로 안내할 수 있게 실패로 반환
            Result.failure(e)
        }
    }

    /**
     * Room이 통째로 비었을 때만(재설치 등) Firestore에서 원본을 1회 복원
     *
     * "오늘 due가 없음"과 "원본이 없음"을 구분하기 위해 due 수가 아니라 전체 수(countFlashcards)로 판단
     * Room에 카드가 하나라도 있으면 평상시로 보고 Firestore를 호출하지 않는다
     * fetch 실패는 복원만 건너뛰고 예외로 올리지 않아 다음 진입 때 자연스럽게 재시도
     */
    private suspend fun restoreFlashcardsIfEmpty(userId: String, languageCode: String) {
        if (localDataSource.countFlashcards(userId, languageCode) > 0) return

        val remoteCards = remoteDataSource.fetchFlashcards(languageCode).getOrNull()
        if (!remoteCards.isNullOrEmpty()) {
            // saveFlashcards는 onConflict IGNORE라 빈 Room 복원에 안전하다.
            localDataSource.saveFlashcards(userId, remoteCards)
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
            updatedAt = updatedAt,
            lastReviewRating = ReviewRating.fromName(lastReviewRating),
            lastReviewedAt = lastReviewedAt
        )
    }
}
