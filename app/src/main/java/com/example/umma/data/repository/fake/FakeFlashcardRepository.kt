package com.example.umma.data.repository.fake

import com.example.umma.domain.model.flashcard.Flashcard
import com.example.umma.domain.model.flashcard.FlashcardSchedule
import com.example.umma.domain.model.flashcard.FlashcardUpdateResult
import com.example.umma.domain.model.flashcard.ReviewDeckState
import com.example.umma.domain.model.flashcard.ReviewScheduleResult
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.repository.FlashcardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/** UI 개발과 테스트용 [FlashcardRepository] 가짜 구현체입니다. */
@Singleton
class FakeFlashcardRepository @Inject constructor() : FlashcardRepository {

    private val fakeCards = listOf(
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
        result: ReviewScheduleResult
    ): Result<FlashcardUpdateResult> {
        // syncPending / failure 를 바꿔 저장 성공, pending sync, retry 화면을 검증한다.
        // fake 는 저장 payload 자체보다 화면 분기 재현이 더 중요하다.
        return updateFailure?.let { Result.failure(it) }
            ?: Result.success(FlashcardUpdateResult(cardId, updateSyncPending))
    }
}
