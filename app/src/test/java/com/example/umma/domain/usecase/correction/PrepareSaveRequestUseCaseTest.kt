package com.example.umma.domain.usecase.correction

import com.example.umma.domain.model.correction.CorrectionSuggestion
import com.example.umma.domain.model.learningstate.LangCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrepareSaveRequestUseCaseTest {

    private val useCase = PrepareSaveRequestUseCase()

    @Test
    fun `prepares save request from distinct selected suggestions`() {
        val suggestions = listOf(
            CorrectionSuggestion(
                id = "s-1",
                lang = LangCode.EN,
                sourceCandidateIds = listOf("c-1"),
                sourceTurnIndex = 0,
                beforeText = "i go school",
                nativeText = "나는 학교에 간다",
                afterText = "I go school.",
                explanation = "demo"
            ),
            CorrectionSuggestion(
                id = "s-1",
                lang = LangCode.EN,
                sourceCandidateIds = listOf("c-1"),
                sourceTurnIndex = 0,
                beforeText = "i go school",
                nativeText = "나는 학교에 간다",
                afterText = "I go school.",
                explanation = "demo"
            )
        )

        val result = useCase(suggestions)

        assertTrue(result.isSuccess)
        val request = result.getOrThrow()
        assertEquals(LangCode.EN, request.lang)
        assertEquals(1, request.flashcards.size)
        assertEquals("s-1", request.flashcards.first().suggestionId)
        assertEquals("나는 학교에 간다", request.flashcards.first().frontText)
        assertEquals("I go school.", request.flashcards.first().backText)
    }

    @Test
    fun `fails when selected suggestions are empty`() {
        val result = useCase(emptyList())

        assertTrue(result.isFailure)
    }
}
