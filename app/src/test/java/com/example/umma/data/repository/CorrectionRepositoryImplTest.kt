package com.example.umma.data.repository

import com.example.umma.domain.model.correction.CorrectionSaveRequest
import com.example.umma.domain.model.correction.CorrectionFlashcardSaveItem
import com.example.umma.domain.model.learningstate.LangCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionRepositoryImplTest {

    private val repository = CorrectionRepositoryImpl()

    @Test
    fun `saveFlashcards stores suggestions locally and marks them pending sync`() = runBlocking {
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
        assertEquals(listOf("s-1"), saveResult.localSavedSuggestionIds)
        assertEquals(listOf("s-1"), saveResult.pendingSyncSuggestionIds)
        assertEquals(123L, saveResult.savedAt)
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

        assertEquals(listOf("s-1"), first.localSavedSuggestionIds)
        assertTrue(second.localSavedSuggestionIds.isEmpty())
        assertTrue(second.pendingSyncSuggestionIds.isEmpty())
    }
}
