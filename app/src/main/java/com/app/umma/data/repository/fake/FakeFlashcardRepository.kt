package com.app.umma.data.repository.fake

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
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/** UI 개발과 테스트용 [FlashcardRepository] 가짜 구현체입니다. */
@Singleton
class FakeFlashcardRepository @Inject constructor() : FlashcardRepository {

    private val fakeCards = mutableListOf(
        Flashcard(
            id = "f1",
            language = LangCode.EN,
            frontText = "나는 학교에 간다",
            backText = "I go to school",
            explanation = "이동의 목적지 앞에는 to가 붙습니다.",
            schedule = FlashcardSchedule(0, 2.5, System.currentTimeMillis()),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        ),
        Flashcard(
            id = "f2",
            language = LangCode.EN,
            frontText = "그녀는 사과를 먹는다",
            backText = "She eats an apple",
            explanation = "3인칭 단수 주어 뒤의 동사에는 s가 붙습니다.",
            schedule = FlashcardSchedule(0, 2.5, System.currentTimeMillis()),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    )

    var deckState: ReviewDeckState = ReviewDeckState.Content(fakeCards)
    var updateSyncPending: Boolean = false
    var updateFailure: Throwable? = null

    override fun observeDueFlashcards(userId: String, language: LangCode): Flow<ReviewDeckState> {
        // 화면 개발자는 deckState 만 바꿔 due / empty / retry 상태를 빠르게 재현할 수 있다.
        // Content 일 때는 현재 선택 언어만 남기고 나머지는 실제 repository 처럼 필터링한다.
        val state = when (val current = deckState) {
            is ReviewDeckState.Content -> {
                val filtered = current.cards.filter { it.language == language }
                if (filtered.isEmpty()) ReviewDeckState.Empty else ReviewDeckState.Content(filtered)
            }

            else -> current
        }

        return flowOf(state)
    }

    override suspend fun updateFlashcardSchedule(
        userId: String,
        cardId: String,
        result: ReviewScheduleResult,
        lastReviewRating: ReviewRating?,
        lastReviewedAt: Long?
    ): Result<FlashcardUpdateResult> {
        // syncPending / failure 를 바꿔 저장 성공, pending sync, retry 화면을 검증한다.
        // fake 는 저장 payload 자체보다 화면 분기 재현이 더 중요하다.
        updateFailure?.let { return Result.failure(it) }

        val index = fakeCards.indexOfFirst { it.id == cardId }
        if (index == -1) {
            return Result.failure(NoSuchElementException("Flashcard $cardId not found"))
        }

        // fake도 schedule을 실제로 바꿔 getReviewSummary가 production과 같은 의미의 count를 돌려주게 한다.
        val current = fakeCards[index]
        fakeCards[index] = current.copy(
            schedule = current.schedule.copy(
                interval = result.interval,
                easeFactor = result.easeFactor,
                nextReviewAt = result.nextReviewAt
            ),
            updatedAt = System.currentTimeMillis(),
            lastReviewRating = lastReviewRating,
            lastReviewedAt = lastReviewedAt
        )

        return Result.success(FlashcardUpdateResult(cardId, updateSyncPending))
    }

    override suspend fun getReviewSummary(
        userId: String,
        language: LangCode,
        now: Long
    ): Result<FlashcardReviewSummary> {
        val languageCards = fakeCards.filter { it.language == language }
        // fake도 production과 같은 의미의 due/saved count를 제공해 SRS 화면 개발이 Summary 연동을 미리 볼 수 있게 한다.
        return Result.success(
            FlashcardReviewSummary(
                dueFlashcards = languageCards.count { it.schedule.nextReviewAt <= now },
                savedFlashcards = languageCards.size
            )
        )
    }

    /**
     * dirty 상태로 남은 카드를 Firestore에 일괄 재시도 sync
     * 성공: 카드 수 반환
     * 실패: Result.failure
     */
    override suspend fun syncDirtyFlashcards(userId: String): Result<Int> {
        return Result.success(0)
    }

    /**
     * 목록 화면용: 현재 언어로 저장된 카드 전체 조회
     */
    override suspend fun getFlashcards(
        userId: String,
        language: LangCode
    ): Result<List<Flashcard>> {
        val cards = fakeCards.filter { it.language == language }
        return Result.success(cards)
    }

    /**
     * 선택된 카드를 fake 목록에서 제거한다 (메모리 리스트에서 삭제)
     */
    override suspend fun deleteFlashcards(
        userId: String,
        flashcardIds: List<String>
    ): Result<Unit> {
        fakeCards.removeAll { it.id in flashcardIds }
        return Result.success(Unit)
    }
}
