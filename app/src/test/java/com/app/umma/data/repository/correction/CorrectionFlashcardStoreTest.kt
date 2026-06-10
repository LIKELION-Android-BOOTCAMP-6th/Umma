package com.app.umma.data.repository.correction

import com.app.umma.data.model.correction.CorrectionFlashcardDto
import com.app.umma.data.source.local.TestCorrectionFlashcardLocalDataSource
import com.app.umma.data.source.remote.CorrectionFlashcardRemoteDataSource
import com.app.umma.domain.model.correction.CorrectionFlashcardSaveItem
import com.app.umma.domain.model.correction.CorrectionSaveRequest
import com.app.umma.domain.model.learningstate.LangCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CorrectionFlashcardStore.save() 의 멱등성 계약을 회귀 테스트로 고정한다.
 *
 * 위험 창(Flashcard commit ~ 캐시 정리 사이 프로세스 사망) 재진입 시
 * 동일 요청으로 save() 가 다시 호출되어도 중복 행이 생기지 않음을 보증한다.
 *
 * 참고: [COR-FIX-012 #370]
 */
class CorrectionFlashcardStoreTest {

    private val localDataSource = TestCorrectionFlashcardLocalDataSource()

    // remote는 항상 성공(all synced) — 멱등성 검증은 local 계층에 집중한다.
    private val remoteDataSource = object : CorrectionFlashcardRemoteDataSource {
        override suspend fun syncFlashcards(flashcards: List<CorrectionFlashcardDto>): Result<List<String>> =
            Result.success(flashcards.map { it.id })

        override suspend fun deleteFlashcards(flashcardIds: List<String>): Result<Unit> =
            Result.success(Unit)

        override suspend fun fetchFlashcards(language: String): Result<List<CorrectionFlashcardDto>> =
            Result.success(emptyList())

        override suspend fun syncReviewSchedule(
            flashcardId: String,
            nextReviewAt: Long,
            interval: Int,
            easeFactor: Double,
            updatedAt: Long,
            lastReviewRating: String?,
            lastReviewedAt: Long?
        ): Result<Unit> = Result.success(Unit)
    }

    private val store = CorrectionFlashcardStore(
        localDataSource = localDataSource,
        remoteDataSource = remoteDataSource,
    )

    private val request = CorrectionSaveRequest(
        uid = "uid-idem-1",
        lang = LangCode.EN,
        flashcards = listOf(
            CorrectionFlashcardSaveItem(
                suggestionId = "s-a",
                frontText = "나는 학교에 간다",
                backText = "I go to school.",
                explanation = "전치사 to 추가",
            ),
            CorrectionFlashcardSaveItem(
                suggestionId = "s-b",
                frontText = "이것은 테스트다",
                backText = "This is a test.",
                explanation = "관사 a 추가",
            ),
        ),
        requestedAt = 1_700_000_000_000L,
    )

    @Test
    fun `1회차 저장 시 모든 suggestionId 가 localSavedFlashcardIds 에 포함된다`() = runBlocking {
        val result = store.save(request)

        // 새로 저장된 카드 id 목록이 요청의 suggestionId 집합과 일치해야 한다.
        assertEquals(setOf("s-a", "s-b"), result.localSavedFlashcardIds.toSet())
    }

    @Test
    fun `동일 요청 2회 호출 시 2회차 localSavedFlashcardIds 가 비어 있다`() = runBlocking {
        // 1회차: 정상 저장
        store.save(request)

        // 2회차: 완료 재진입 시뮬레이션 — 같은 요청으로 다시 save
        val secondResult = store.save(request)

        // INSERT IGNORE 계약: 이미 존재하는 (userId, id) 는 -1 rowId 반환 → localSavedFlashcardIds 비어 있음
        assertTrue(
            "2회차 재저장은 새로 insert된 카드가 없어야 한다",
            secondResult.localSavedFlashcardIds.isEmpty(),
        )
        // remote sync도 새로 예약되면 안 된다
        assertTrue(
            "2회차 재저장은 pendingSyncFlashcardIds도 비어 있어야 한다",
            secondResult.pendingSyncFlashcardIds.isEmpty(),
        )
    }

    @Test
    fun `동일 요청 2회 호출 후 저장된 카드는 단일 row 를 유지한다`() = runBlocking {
        store.save(request)
        store.save(request)

        // TestCorrectionFlashcardLocalDataSource 가 Room IGNORE 를 재현하므로 getFlashcards 로 확인
        val cards = localDataSource.getFlashcards(uid = "uid-idem-1", language = LangCode.EN.code)

        // 두 번 저장해도 카드 개수는 2(요청 개수)여야 한다 — 4개가 되면 dedup 실패
        assertEquals(
            "중복 저장 후 카드 수는 요청의 카드 수(2)와 같아야 한다",
            2,
            cards.size,
        )
        assertEquals(setOf("s-a", "s-b"), cards.map { it.id }.toSet())
    }

    @Test
    fun `rollback 대상은 이번 호출에서 새로 저장된 카드만으로 좁혀진다`() = runBlocking {
        // 1회차 저장: s-a, s-b 모두 신규 저장
        val firstResult = store.save(request)

        // 2회차 저장: 이미 존재하는 카드라 localSavedFlashcardIds 비어 있음
        val secondResult = store.save(request)

        // 2회차 결과로 rollback 호출 시 필터링된 flashcards 가 0개여야 새로 저장한 카드를 지우지 않는다.
        // (CompleteCorrectionUseCase.rollbackLocalChanges 는 localSavedFlashcardIds 기준으로 좁힘)
        assertTrue(
            "2회차 결과의 localSavedFlashcardIds 가 비어 있어야 기존 카드를 rollback 하지 않는다",
            secondResult.localSavedFlashcardIds.isEmpty(),
        )
        // 1회차 카드는 여전히 남아 있어야 한다
        val remaining = localDataSource.getFlashcards(uid = "uid-idem-1", language = LangCode.EN.code)
        assertEquals(2, remaining.size)
    }

    @Test
    fun `1회차 저장 후 부분 실패 재시도 시 미저장 카드만 새로 저장된다`() = runBlocking {
        // 'only-a' 카드만 먼저 저장된 상태를 만든다 (s-b 저장 전 실패 시뮬레이션)
        val partialRequest = CorrectionSaveRequest(
            uid = "uid-idem-1",
            lang = LangCode.EN,
            flashcards = listOf(request.flashcards.first()), // s-a 만
            requestedAt = 1_700_000_000_000L,
        )
        store.save(partialRequest)

        // 이후 전체 요청으로 재시도
        val retryResult = store.save(request)

        // s-a 는 이미 저장됐으므로 새로 insert 안 됨, s-b 만 새로 insert 됨
        assertEquals(
            "부분 저장 후 재시도 시 미저장 카드만 localSavedFlashcardIds 에 포함된다",
            listOf("s-b"),
            retryResult.localSavedFlashcardIds,
        )
        val cards = localDataSource.getFlashcards(uid = "uid-idem-1", language = LangCode.EN.code)
        assertEquals(2, cards.size)
    }
}
