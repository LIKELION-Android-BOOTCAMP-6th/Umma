package com.example.umma.data.repository

import com.example.umma.data.model.correction.CorrectionFlashcardDto
import com.example.umma.data.repository.correction.CorrectionFlashcardStore
import com.example.umma.data.source.local.InMemoryCorrectionFlashcardLocalDataSource
import com.example.umma.data.source.remote.CorrectionFlashcardRemoteDataSource
import com.example.umma.domain.model.correction.CorrectionSaveRequest
import com.example.umma.domain.model.correction.CorrectionFlashcardSaveItem
import com.example.umma.domain.model.learningstate.LangCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionRepositoryImplTest {

    // RepositoryImpl 은 local-first 저장과 remote sync 시도만 조율한다.
    // Recording remote 는 Firestore 대신 sync 성공/실패와 DTO 내용을 관찰하기 위한 테스트 대역이다.
    private val remoteDataSource = RecordingCorrectionFlashcardRemoteDataSource()
    private val repository = CorrectionRepositoryImpl(
        CorrectionFlashcardStore(
            localDataSource = InMemoryCorrectionFlashcardLocalDataSource(),
            remoteDataSource = remoteDataSource
        )
    )

    @Test
    fun `saveFlashcards stores suggestions locally and marks them pending sync`() = runBlocking {
        // remote sync 실패는 사용자 저장 실패가 아니다.
        // local 저장 성공 ID 가 유지되고 pending sync 로만 남는지 확인한다.
        remoteDataSource.shouldFailSync = true
        val request = CorrectionSaveRequest(
            lang = LangCode.EN,
            flashcards = listOf(
                CorrectionFlashcardSaveItem(
                    suggestionId = "s-1",
                    frontText = "나는 학교에 간다",
                    backText = "I go school.",
                    explanation = "demo"
                )
            ),
            requestedAt = 123L
        )

        val result = repository.saveFlashcards(request)

        assertTrue(result.isSuccess)
        val saveResult = result.getOrThrow()
        assertEquals(listOf("s-1"), saveResult.localSavedFlashcardIds)
        assertEquals(listOf("s-1"), saveResult.pendingSyncFlashcardIds)
        assertEquals(123L, saveResult.savedAt)
    }

    @Test
    fun `saveFlashcards maps flashcard dto with firestore field contract`() = runBlocking {
        // Firestore 필드명과 값은 SRS / LearningState 문서와 맞아야 한다.
        // 특히 language 값은 enum name 이 아니라 LS-001 표준 코드(en)여야 한다.
        val request = CorrectionSaveRequest(
            lang = LangCode.EN,
            flashcards = listOf(
                CorrectionFlashcardSaveItem(
                    suggestionId = "s-field",
                    frontText = "나는 어제 박물관에 갔다.",
                    backText = "I went to the museum yesterday.",
                    explanation = "Use past tense for yesterday."
                )
            ),
            requestedAt = 500L
        )

        repository.saveFlashcards(request).getOrThrow()

        val synced = remoteDataSource.syncedFlashcards.single()
        val firestoreMap = synced.toFirestoreMap()
        assertEquals("en", synced.language)
        assertEquals("en", firestoreMap["language"])
        assertEquals("나는 어제 박물관에 갔다.", firestoreMap["frontText"])
        assertEquals("I went to the museum yesterday.", firestoreMap["backText"])
        assertEquals(500L, firestoreMap["nextReviewAt"])
        assertEquals(0, firestoreMap["interval"])
        assertEquals(2.5, firestoreMap["easeFactor"])
        assertEquals(true, firestoreMap["dirty"])
    }

    @Test
    fun `saveFlashcards clears pending sync when firestore sync succeeds`() = runBlocking {
        // remote sync 가 성공하면 pending 목록이 비어야 Dashboard/후속 sync 가 불필요한 재시도를 하지 않는다.
        val request = CorrectionSaveRequest(
            lang = LangCode.EN,
            flashcards = listOf(
                CorrectionFlashcardSaveItem(
                    suggestionId = "s-2",
                    frontText = "나는 커피를 마신다",
                    backText = "I drink coffee.",
                    explanation = "demo"
                )
            ),
            requestedAt = 124L
        )

        val result = repository.saveFlashcards(request)

        assertTrue(result.isSuccess)
        val saveResult = result.getOrThrow()
        assertEquals(listOf("s-2"), saveResult.localSavedFlashcardIds)
        assertTrue(saveResult.pendingSyncFlashcardIds.isEmpty())
        assertEquals(listOf("s-2"), remoteDataSource.syncedIds)
    }

    @Test
    fun `saveFlashcards is idempotent for the same suggestion`() = runBlocking {
        // suggestionId 를 card id 로 쓰는 정책 덕분에 같은 교정 결과를 여러 번 저장해도 중복 카드가 생기지 않는다.
        val request = CorrectionSaveRequest(
            lang = LangCode.EN,
            flashcards = listOf(
                CorrectionFlashcardSaveItem(
                    suggestionId = "s-1",
                    frontText = "나는 학교에 간다",
                    backText = "I go school.",
                    explanation = "demo"
                )
            ),
            requestedAt = 456L
        )

        val first = repository.saveFlashcards(request).getOrThrow()
        val second = repository.saveFlashcards(request).getOrThrow()

        assertEquals(listOf("s-1"), first.localSavedFlashcardIds)
        assertTrue(second.localSavedFlashcardIds.isEmpty())
        assertTrue(second.pendingSyncFlashcardIds.isEmpty())
    }

    @Test
    fun `rollbackFlashcards removes cards saved by the same request`() = runBlocking {
        // 완료 파이프라인 중 LangState 갱신이 실패하면 local 저장은 보상 rollback 되어야 한다.
        // rollback 이후 같은 요청을 다시 저장할 수 있으면 local 상태가 정리된 것으로 본다.
        val request = CorrectionSaveRequest(
            lang = LangCode.EN,
            flashcards = listOf(
                CorrectionFlashcardSaveItem(
                    suggestionId = "s-1",
                    frontText = "나는 학교에 간다",
                    backText = "I go to school.",
                    explanation = "go 뒤에는 to school 을 사용한다."
                )
            ),
            requestedAt = 789L
        )

        repository.saveFlashcards(request).getOrThrow()
        val rollback = repository.rollbackFlashcards(request)
        val afterRollback = repository.saveFlashcards(request).getOrThrow()

        assertTrue(rollback.isSuccess)
        assertEquals(listOf("s-1"), afterRollback.localSavedFlashcardIds)
    }

    private class RecordingCorrectionFlashcardRemoteDataSource :
        CorrectionFlashcardRemoteDataSource {

        var shouldFailSync: Boolean = false
        val syncedIds = mutableListOf<String>()
        val syncedFlashcards = mutableListOf<CorrectionFlashcardDto>()
        val deletedIds = mutableListOf<String>()

        override suspend fun syncFlashcards(
            flashcards: List<CorrectionFlashcardDto>
        ): Result<List<String>> {
            if (shouldFailSync) {
                return Result.failure(IllegalStateException("sync failed"))
            }

            val ids = flashcards.map { it.id }
            syncedIds += ids
            syncedFlashcards += flashcards
            return Result.success(ids)
        }

        override suspend fun deleteFlashcards(flashcardIds: List<String>): Result<Unit> {
            deletedIds += flashcardIds
            return Result.success(Unit)
        }
    }
}
