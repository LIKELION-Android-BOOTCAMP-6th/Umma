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

    private val remoteDataSource = RecordingCorrectionFlashcardRemoteDataSource()
    private val repository = CorrectionRepositoryImpl(
        CorrectionFlashcardStore(
            localDataSource = InMemoryCorrectionFlashcardLocalDataSource(),
            remoteDataSource = remoteDataSource
        )
    )

    @Test
    fun `saveFlashcards stores suggestions locally and marks them pending sync`() = runBlocking {
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
    fun `saveFlashcards clears pending sync when firestore sync succeeds`() = runBlocking {
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
        val deletedIds = mutableListOf<String>()

        override suspend fun syncFlashcards(
            flashcards: List<CorrectionFlashcardDto>
        ): Result<List<String>> {
            if (shouldFailSync) {
                return Result.failure(IllegalStateException("sync failed"))
            }

            val ids = flashcards.map { it.id }
            syncedIds += ids
            return Result.success(ids)
        }

        override suspend fun deleteFlashcards(flashcardIds: List<String>): Result<Unit> {
            deletedIds += flashcardIds
            return Result.success(Unit)
        }
    }
}
