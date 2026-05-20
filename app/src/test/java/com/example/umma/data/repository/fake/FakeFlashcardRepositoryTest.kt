package com.example.umma.data.repository.fake

import com.example.umma.domain.model.flashcard.ReviewDeckState
import com.example.umma.domain.model.flashcard.ReviewScheduleResult
import com.example.umma.domain.model.learningstate.LangCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeFlashcardRepositoryTest {

    @Test
    fun `can reproduce empty and retry deck states`() = runBlocking {
        // fake repository 는 화면 상태를 빠르게 만들기 위해 deckState 를 직접 바꾼다.
        // 실제 DAO 를 건드리지 않고도 Empty / Retry 화면을 붙여볼 수 있어야 한다.
        val repository = FakeFlashcardRepository()

        repository.deckState = ReviewDeckState.Empty
        assertTrue(repository.observeDueFlashcards("uid-1", LangCode.EN).first() is ReviewDeckState.Empty)

        repository.deckState = ReviewDeckState.Retry(IllegalStateException("retry"))
        val retry = repository.observeDueFlashcards("uid-1", LangCode.EN).first()

        // fake 는 UI 작업자가 저장소 장애 화면을 만들 수 있게 상태를 직접 주입한다.
        assertTrue(retry is ReviewDeckState.Retry)
        assertEquals("retry", (retry as ReviewDeckState.Retry).cause?.message)
    }

    @Test
    fun `can reproduce sync pending update result`() = runBlocking {
        // 저장소 구현이 없어도 pending sync 화면 분기를 미리 붙일 수 있어야 한다.
        // 화면 개발 단계에서 완료 후 pending 표시를 검증하는 용도다.
        val repository = FakeFlashcardRepository()
        repository.updateSyncPending = true

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
}
