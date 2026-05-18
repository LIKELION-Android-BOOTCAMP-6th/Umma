package com.example.umma.data.repository.fake

import com.example.umma.domain.model.correction.CorrectionCandidate
import com.example.umma.domain.model.correction.GenerateSuggestionsInput
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.LangState
import com.example.umma.domain.repository.CorrectionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeCorrectionRepositoryTest {

    private val repository: CorrectionRepository = FakeCorrectionRepository()

    @Test
    fun `fake repository follows correction repository suggestion contract`() = runBlocking {
        val input = GenerateSuggestionsInput(
            candidates = listOf(
                CorrectionCandidate(
                    id = "en-1-def",
                    lang = LangCode.EN,
                    sourceTurnIndex = 1,
                    sourceText = "this is test"
                )
            ),
            langState = LangState.initial(LangCode.EN)
        )

        val result = repository.generateSuggestions(input)

        assertTrue(result.isSuccess)
        val suggestion = result.getOrThrow().single()
        assertEquals("corr-en-1-def", suggestion.id)
        assertEquals("this is test", suggestion.nativeText)
        assertEquals("This is test.", suggestion.afterText)
    }
}
