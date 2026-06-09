package com.app.umma.data.repository

import com.app.umma.data.model.correction.CorrectionFlashcardDto
import com.app.umma.data.source.local.CorrectionFlashcardLocalDataSource
import com.app.umma.data.source.remote.CorrectionFlashcardRemoteDataSource
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
        // 복습할 카드가 없으면 Empty 상태를 내려야 한다.
        // 이 처리가 없으면 화면이 로딩에서 멈춘다.
        val repository = FlashcardRepositoryImpl(FakeLocalDataSource(), FakeRemoteDataSource())

        val state = repository.observeDueFlashcards("uid-1", LangCode.EN).first()

        assertTrue(state is ReviewDeckState.Empty)
    }

    @Test
    fun `observeDueFlashcards emits retry when local source fails`() = runBlocking {
        // 로컬 DB 조회 중 에러가 나도 Flow가 죽지 않고 Retry 상태로 흘러야 한다.
        val repository = FlashcardRepositoryImpl(
            FakeLocalDataSource(
                dueFlashcards = { throw IllegalStateException("boom") }
            ),
            FakeRemoteDataSource()
        )

        val state = repository.observeDueFlashcards("uid-1", LangCode.EN).first()

        assertTrue(state is ReviewDeckState.Retry)
        assertEquals("boom", (state as ReviewDeckState.Retry).cause?.message)
    }

    @Test
    fun `updateFlashcardSchedule returns sync pending when local update succeeds`() = runBlocking {
        // 로컬 저장 후 Firestore sync까지 성공하면 isSyncPending=false를 반환해야 한다.
        val repository = FlashcardRepositoryImpl(
            FakeLocalDataSource(updateSchedule = { true }),
            FakeRemoteDataSource(syncResult = { Result.success(Unit) })
        )

        val result = repository.updateFlashcardSchedule(
            userId = "uid-1",
            cardId = "card-1",
            result = ReviewScheduleResult(
                interval = 1_440,
                easeFactor = 2.5,
                nextReviewAt = 1_000L
            ),
            // 이 테스트는 local/remote sync pending 여부만 검증하므로 review 평가 메타는 비워 둔다.
            lastReviewRating = null,
            lastReviewedAt = null
        )

        assertTrue(result.isSuccess)
        assertEquals(false, result.getOrThrow().isSyncPending)
        assertEquals("card-1", result.getOrThrow().cardId)
    }

    @Test
    fun `getReviewSummary counts due and saved cards from local source`() = runBlocking {
        // Summary의 숫자는 로컬 DB에서 직접 읽어야 한다.
        val repository = FlashcardRepositoryImpl(
            FakeLocalDataSource(
                savedCount = { 5 },
                dueCount = { 2 }
            ),
            FakeRemoteDataSource()
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
            updatedAt: Long,
            lastReviewRating: String?,
            lastReviewedAt: Long?
        ): Boolean {
            // schedule 저장 성공/실패를 이 함수 하나로 제어한다.
            return updateSchedule()
        }

        override suspend fun getFlashcards(
            uid: String,
            language: String
        ): List<CorrectionFlashcardDto> {
            // 목록 조회는 이 repository 단위 테스트의 검증 대상이 아니므로 빈 목록으로 계약만 맞춘다.
            return emptyList()
        }

        override suspend fun deleteFlashcards(
            uid: String,
            flashcardIds: List<String>
        ) {
            // 삭제 흐름은 이 테스트의 검증 대상이 아니므로 local source 계약만 맞춘다.
            // 실제 삭제 동작은 저장형 fake를 쓰는 테스트에서 별도로 검증할 수 있다.
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

        override suspend fun countNotifiableDueFlashcards(
            uid: String,
            language: String,
            now: Long
        ): Int {
            // 알림 가능 due count는 이 테스트의 직접 검증 대상이 아니므로 due count와 같은 hook을 재사용한다.
            // 인터페이스 계약만 맞춰 unit test 컴파일이 최신 local source 변경에 끊기지 않게 한다.
            return dueCount()
        }

        /**
         * dirty=true 인 카드 모두 반환
         * @return emptyList()
         */
        override suspend fun getDirtyFlashcards(uid: String): List<CorrectionFlashcardDto> {
            return emptyList()
        }

        override suspend fun rollbackFlashcards(
            uid: String,
            flashcardIds: List<String>
        ) = Unit
    }

    private class FakeRemoteDataSource(
        private val syncResult: suspend () -> Result<Unit> = { Result.success(Unit) }
    ) : CorrectionFlashcardRemoteDataSource {

        override suspend fun syncFlashcards(
            flashcards: List<CorrectionFlashcardDto>
        ): Result<List<String>> = Result.success(flashcards.map { it.id })

        override suspend fun deleteFlashcards(
            flashcardIds: List<String>
        ): Result<Unit> = Result.success(Unit)

        override suspend fun fetchFlashcards(
            language: String
        ): Result<List<CorrectionFlashcardDto>> = Result.success(emptyList())

        override suspend fun syncReviewSchedule(
            flashcardId: String,
            nextReviewAt: Long,
            interval: Int,
            easeFactor: Double,
            updatedAt: Long,
            lastReviewRating: String?,
            lastReviewedAt: Long?
        ): Result<Unit> {
            // 테스트는 syncResult lambda로 성공/실패를 제어해 pending 분기를 검증
            return syncResult()
        }
    }
}
