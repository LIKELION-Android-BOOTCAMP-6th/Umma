package com.example.umma.data.repository.correction

import com.example.umma.domain.model.correction.CorrectionCandidate
import com.example.umma.domain.model.correction.GenerateSuggestionsInput
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.LangState
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class CorrectionAiResponseMapperTest {

    private val mapper = CorrectionAiResponseMapper()

    @Test
    fun `maps valid AI response into correction suggestions`() {
        val rawJson = """
            {
              "suggestions": [
                {
                  "candidateId": "en-0-a",
                  "nativeText": "나는 학교에 간다",
                  "afterText": "I go to school.",
                  "explanation": "Use 'go to school' instead of 'go school'."
                }
              ]
            }
        """.trimIndent()

        val suggestions = mapper.map(rawJson, baseInput())

        assertEquals(1, suggestions.size)
        val suggestion = suggestions.single()
        assertEquals("corr-en-0-a", suggestion.id)
        assertEquals(LangCode.EN, suggestion.lang)
        assertEquals(listOf("en-0-a"), suggestion.sourceCandidateIds)
        assertEquals(0, suggestion.sourceTurnIndex)
        assertEquals("i go school", suggestion.beforeText)
        assertEquals("나는 학교에 간다", suggestion.nativeText)
        assertEquals("I go to school.", suggestion.afterText)
        assertEquals("Use 'go to school' instead of 'go school'.", suggestion.explanation)
    }

    @Test
    fun `returns empty list when AI response has no suggestions`() {
        val suggestions = mapper.map("""{"suggestions":[]}""", baseInput())

        assertEquals(emptyList<Any>(), suggestions)
    }

    @Test
    fun `fails when AI response references unknown candidate`() {
        val rawJson = """
            {
              "suggestions": [
                {
                  "candidateId": "missing",
                  "nativeText": "나는 학교에 간다",
                  "afterText": "I go to school.",
                  "explanation": "demo"
                }
              ]
            }
        """.trimIndent()

        assertThrowsIllegalArgument {
            mapper.map(rawJson, baseInput())
        }
    }

    @Test
    fun `fails when required text field is blank`() {
        val rawJson = """
            {
              "suggestions": [
                {
                  "candidateId": "en-0-a",
                  "nativeText": "나는 학교에 간다",
                  "afterText": "   ",
                  "explanation": "demo"
                }
              ]
            }
        """.trimIndent()

        assertThrowsIllegalArgument {
            mapper.map(rawJson, baseInput())
        }
    }

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected path: invalid AI response should fail before reaching the domain layer.
        }
    }

    private fun baseInput(): GenerateSuggestionsInput {
        return GenerateSuggestionsInput(
            candidates = listOf(
                CorrectionCandidate(
                    id = "en-0-a",
                    lang = LangCode.EN,
                    sourceTurnIndex = 0,
                    sourceText = "i go school"
                )
            ),
            langState = LangState.initial(LangCode.EN)
        )
    }
}
