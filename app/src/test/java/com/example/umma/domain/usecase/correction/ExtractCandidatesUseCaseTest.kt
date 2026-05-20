package com.example.umma.domain.usecase.correction

import com.example.umma.domain.model.learningstate.ConversationTurn
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.TurnSpeaker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractCandidatesUseCaseTest {

    private val useCase = ExtractCandidatesUseCase()

    @Test
    fun `session language mismatch returns empty list`() {
        val result = useCase(
            ExtractCandidatesInput(
                selectedLang = LangCode.EN,
                sessionLang = LangCode.JA,
                recentFullContext = listOf(
                    ConversationTurn(TurnSpeaker.USER, "hello")
                )
            )
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `extracts user turns only and attaches nearby assistant context`() {
        val result = useCase(
            ExtractCandidatesInput(
                selectedLang = LangCode.EN,
                sessionLang = LangCode.EN,
                recentFullContext = listOf(
                    ConversationTurn(TurnSpeaker.AI, "Let's talk about your trip."),
                    ConversationTurn(TurnSpeaker.USER, "I go to Paris yesterday."),
                    ConversationTurn(TurnSpeaker.AI, "You can say I went to Paris yesterday."),
                    ConversationTurn(TurnSpeaker.USER, " "),
                    ConversationTurn(TurnSpeaker.USER, "It was very fun.")
                )
            )
        )

        assertEquals(2, result.size)
        assertEquals("I go to Paris yesterday.", result[0].sourceText)
        assertEquals("Let's talk about your trip.\nYou can say I went to Paris yesterday.", result[0].assistantContext)
        assertEquals("It was very fun.", result[1].sourceText)
        assertEquals("You can say I went to Paris yesterday.", result[1].assistantContext)
    }

    @Test
    fun `limits source turns to latest 100 entries`() {
        val context = buildList {
            repeat(101) { index ->
                add(ConversationTurn(TurnSpeaker.USER, "turn-$index"))
            }
        }

        val result = useCase(
            ExtractCandidatesInput(
                selectedLang = LangCode.EN,
                sessionLang = LangCode.EN,
                recentFullContext = context
            )
        )

        assertEquals(100, result.size)
        assertEquals("turn-1", result.first().sourceText)
        assertEquals(1, result.first().sourceTurnIndex)
    }

    @Test
    fun `returns empty when there is no user turn`() {
        val result = useCase(
            ExtractCandidatesInput(
                selectedLang = LangCode.EN,
                sessionLang = LangCode.EN,
                recentFullContext = listOf(
                    ConversationTurn(TurnSpeaker.AI, "Only assistant text")
                )
            )
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `candidate id is stable enough for current turn ordering`() {
        val result = useCase(
            ExtractCandidatesInput(
                selectedLang = LangCode.EN,
                sessionLang = LangCode.EN,
                recentFullContext = listOf(
                    ConversationTurn(TurnSpeaker.USER, "I need help with grammar")
                )
            )
        )

        assertEquals(1, result.size)
        assertTrue(result.first().id.startsWith("en-0-"))
        assertNull(result.first().sourceTurnId)
    }
}
