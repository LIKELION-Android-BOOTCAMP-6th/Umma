package com.app.umma.data.repository

import com.app.umma.data.model.correction.CorrectionFlashcardDto
import com.app.umma.data.source.local.CorrectionFlashcardLocalDataSource
import com.app.umma.domain.model.flashcard.ReviewDeckState
import com.app.umma.domain.model.flashcard.ReviewScheduleResult
import com.app.umma.domain.model.learningstate.LangCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashcardRepositoryImplTest {

    @Test
    fun `observeDueFlashcards emits empty when local source has no cards`() = runBlocking {
        // due deck 이 비어 있으면 ViewModel 은 Empty 상태로 바로 전환해야 한다.
        // 이 경계가 빠지면 화면이 로딩 상태에만 머물 수 있다.
        val repository = FlashcardRepositoryImpl(FakeLocalDataSource())

        val state = repository.observeDueFlashcards("uid-1", LangCode.EN).first()

        assertTrue(state is ReviewDeckState.Empty)
    }

    @Test
    fun `observeDueFlashcards emits retry when local source fails`() = runBlocking {
        // 로컬 조회 예외는 UI 가 Retry / Error 분기를 만들 수 있도록 상태로 노출한다.
        // 예외를 던져도 Flow 자체가 죽지 않고 상태로 흘러야 한다.
        val repository = FlashcardRepositoryImpl(
            FakeLocalDataSource(
                dueFlashcards = { throw IllegalStateException("boom") }
            )
        )

        val state = repository.observeDueFlashcards("uid-1", LangCode.EN).first()

        assertTrue(state is ReviewDeckState.Retry)
        assertEquals("boom", (state as ReviewDeckState.Retry).cause?.message)
    }

    @Test
    fun `updateFlashcardSchedule returns sync pending when local update succeeds`() = runBlocking {
        // local update 성공과 remote sync pending 을 분리해서 반환하는지 확인한다.
        // 저장 성공이 곧바로 동기화 완료를 뜻하지 않기 때문이다.
        val repository = FlashcardRepositoryImpl(
            FakeLocalDataSource(
                dueFlashcards = { emptyList() },
                updateSchedule = { true }
            )
        )

        val result = repository.updateFlashcardSchedule(
            userId = "uid-1",
            cardId = "card-1",
            result = ReviewScheduleResult(
                interval = 1_440,
                easeFactor = 2.5,
                nextReviewAt = 1_000L
            )
        )

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isSyncPending)
        assertEquals("card-1", result.getOrThrow().cardId)
    }

    @Test
    fun `getReviewSummary counts due and saved cards from local source`() = runBlocking {
        // Summary count는 Dashboard 숫자의 원천이 되므로 due deck 조회와 같은 local source에서 계산해야 한다.
        val repository = FlashcardRepositoryImpl(
            FakeLocalDataSource(
                savedCount = { 5 },
                dueCount = { 2 }
            )
        )

        val summary = repository.getReviewSummary(
            userId = "uid-1",
            language = LangCode.EN,
            now = 10_000L
        ).getOrThrow()

        assertEquals(2, summary.dueFlashcards)
        assertEquals(5, summary.savedFlashcards)
    }

    private class FakeLocalDataSource(
        private val dueFlashcards: suspend () -> List<CorrectionFlashcardDto> = { emptyList() },
        private val updateSchedule: suspend () -> Boolean = { true },
        private val savedCount: suspend () -> Int = { 0 },
        private val dueCount: suspend () -> Int = { 0 }
    ) : CorrectionFlashcardLocalDataSource {
        override suspend fun saveFlashcards(
            uid: String,
            flashcards: List<CorrectionFlashcardDto>
        ): List<String> = emptyList()

        override suspend fun markSynced(
            uid: String,
            flashcardIds: List<String>
        ) = Unit

        override suspend fun getDueFlashcards(
            uid: String,
            language: String,
            now: Long,
            limit: Int
        ): List<CorrectionFlashcardDto> {
            // 테스트에서는 실제 DAO 대신 입력 함수만 바꿔 상태 분기를 만든다.
            return dueFlashcards()
        }

        override suspend fun updateReviewSchedule(
            uid: String,
            flashcardId: String,
            nextReviewAt: Long,
            interval: Int,
            easeFactor: Double,
            updatedAt: Long
        ): Boolean {
            // schedule 저장 성공/실패를 이 함수 하나로 제어한다.
            return updateSchedule()
        }

        override suspend fun countFlashcards(
            uid: String,
            language: String
        ): Int {
            // repository가 local source에서 saved count를 그대로 읽는지 확인하기 위한 테스트 hook이다.
            return savedCount()
        }

        override suspend fun countDueFlashcards(
            uid: String,
            language: String,
            now: Long
        ): Int {
            // repository가 local source에서 due count를 그대로 읽는지 확인하기 위한 테스트 hook이다.
            return dueCount()
        }

        override suspend fun rollbackFlashcards(
            uid: String,
            flashcardIds: List<String>
        ) = Unit
    }
}
