package com.app.umma.domain.usecase.correction

import com.app.umma.domain.model.correction.CorrectionSaveZeroReason
import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.learningstate.LangCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrepareSaveRequestUseCaseTest {

    private val useCase = PrepareSaveRequestUseCase(CorrectionSafetyPolicy())

    @Test
    fun `prepares save request from distinct selected suggestions`() {
        val suggestions = listOf(
            sampleSuggestion(id = "s-1"),
            sampleSuggestion(id = "s-1"),
        )

        val result = useCase(
            uid = "uid-1",
            selectedSuggestions = suggestions,
        )

        assertTrue(result.isSuccess)
        val prepared = result.getOrThrow()
        val request = prepared.request
        assertEquals("uid-1", request.uid)
        assertEquals(LangCode.EN, request.lang)
        assertEquals(1, request.flashcards.size)
        assertEquals("s-1", request.flashcards.first().suggestionId)
        assertEquals("나는 학교에 간다", request.flashcards.first().frontText)
        assertEquals("I go to school.", request.flashcards.first().backText)
    }

    @Test
    fun `maps each selected suggestion to a flashcard with trimmed front, back, and explanation`() {
        val suggestions = listOf(
            sampleSuggestion(
                id = "s-1",
                nativeText = "  나는 학교에 간다  ",
                afterText = "  I go to school.  ",
                explanation = "  3인칭 단수 ...  ",
            ),
            sampleSuggestion(
                id = "s-2",
                nativeText = "그녀는 사과를 좋아하지 않아요",
                afterText = "She doesn't like apples.",
                explanation = "She 는 3인칭 단수이므로 don't 가 아니라 doesn't.",
            ),
        )

        val request = useCase(
            uid = "uid-1",
            selectedSuggestions = suggestions,
        ).getOrThrow().request

        assertEquals(2, request.flashcards.size)
        val first = request.flashcards.first()
        assertEquals("나는 학교에 간다", first.frontText)
        assertEquals("I go to school.", first.backText)
        assertEquals("3인칭 단수 ...", first.explanation)
    }

    @Test
    fun `passes requestedAt through to the request`() {
        val fixedNow = 1_700_000_000_000L

        val request = useCase(
            uid = "uid-1",
            selectedSuggestions = listOf(sampleSuggestion(id = "s-1")),
            requestedAt = fixedNow,
        ).getOrThrow().request

        assertEquals(fixedNow, request.requestedAt)
    }

    @Test
    fun `fails when selected suggestions are empty`() {
        val result = useCase(
            uid = "uid-1",
            selectedSuggestions = emptyList(),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `fails when uid is blank`() {
        val result = useCase(
            uid = "   ",
            selectedSuggestions = listOf(sampleSuggestion(id = "s-1")),
        )

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun `fails when selected suggestions mix multiple languages`() {
        val mixed = listOf(
            sampleSuggestion(id = "s-1", lang = LangCode.EN),
            sampleSuggestion(id = "s-2", lang = LangCode.JA),
        )

        val result = useCase(
            uid = "uid-1",
            selectedSuggestions = mixed,
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `excludes card when native text is blank`() {
        val result = useCase(
            uid = "uid-1",
            selectedSuggestions = listOf(sampleSuggestion(id = "s-1", nativeText = "   ")),
        )

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().request.flashcards.size)
        assertEquals(CorrectionSaveZeroReason.QUALITY_FILTERED, result.getOrThrow().zeroReason)
    }

    @Test
    fun `excludes card when corrected text is blank`() {
        val result = useCase(
            uid = "uid-1",
            selectedSuggestions = listOf(sampleSuggestion(id = "s-1", afterText = "   ")),
        )

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().request.flashcards.size)
        assertEquals(CorrectionSaveZeroReason.QUALITY_FILTERED, result.getOrThrow().zeroReason)
    }

    @Test
    fun `excludes card when correction before and after are identical after normalization`() {
        val result = useCase(
            uid = "uid-1",
            selectedSuggestions = listOf(
                sampleSuggestion(id = "s-1", beforeText = "I go to school.", afterText = "I go to school."),
                sampleSuggestion(id = "s-2", beforeText = "i go to school", afterText = "I go to school!"),
                sampleSuggestion(id = "s-3"),
            ),
        )

        assertTrue(result.isSuccess)
        val flashcards = result.getOrThrow().request.flashcards
        assertEquals(1, flashcards.size)
        assertEquals("s-3", flashcards.first().suggestionId)
    }

    @Test
    fun `excludes card when corrected text is too short after normalization`() {
        val result = useCase(
            uid = "uid-1",
            selectedSuggestions = listOf(sampleSuggestion(id = "s-1", afterText = "A")),
        )

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().request.flashcards.size)
    }

    @Test
    fun `deduplicates cards with same front and back text after normalization`() {
        val result = useCase(
            uid = "uid-1",
            selectedSuggestions = listOf(
                sampleSuggestion(id = "s-1", nativeText = "나는 학교에 간다", afterText = "I go to school."),
                sampleSuggestion(id = "s-2", nativeText = "나는 학교에 간다", afterText = "I go to school."),
                sampleSuggestion(id = "s-3", nativeText = "나는 학교에 간다", afterText = "I GO TO SCHOOL!"),
            ),
        )

        assertTrue(result.isSuccess)
        val flashcards = result.getOrThrow().request.flashcards
        assertEquals(1, flashcards.size)
        assertEquals("s-1", flashcards.first().suggestionId)
    }

    @Test
    fun `returns success with empty flashcards when all suggestions are excluded by quality filter`() {
        val result = useCase(
            uid = "uid-1",
            selectedSuggestions = listOf(
                sampleSuggestion(id = "s-1", beforeText = "same", afterText = "same"),
                sampleSuggestion(id = "s-2", afterText = "A"),
            ),
        )

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().request.flashcards.size)
        assertEquals(CorrectionSaveZeroReason.QUALITY_FILTERED, result.getOrThrow().zeroReason)
    }

    @Test
    fun `excludes harmful suggestion from save request and marks safety zero reason`() {
        val result = useCase(
            uid = "uid-1",
            selectedSuggestions = listOf(
                sampleSuggestion(id = "s-1", beforeText = "how to make a bomb", afterText = "How do I make a bomb?"),
            ),
        )

        assertTrue(result.isSuccess)
        val prepared = result.getOrThrow()
        assertTrue(prepared.request.flashcards.isEmpty())
        assertEquals(listOf("s-1"), prepared.safetyBlockedSuggestionIds)
        assertEquals(CorrectionSaveZeroReason.SAFETY_BLOCKED, prepared.zeroReason)
    }

    @Test
    fun `keeps safe suggestions when only some are blocked by safety policy`() {
        val result = useCase(
            uid = "uid-1",
            selectedSuggestions = listOf(
                sampleSuggestion(id = "safe-1"),
                sampleSuggestion(id = "blocked-1", beforeText = "I want to kill myself", afterText = "I want to kill myself."),
            ),
        )

        assertTrue(result.isSuccess)
        val prepared = result.getOrThrow()
        assertEquals(1, prepared.request.flashcards.size)
        assertEquals("safe-1", prepared.request.flashcards.single().suggestionId)
        assertEquals(listOf("blocked-1"), prepared.safetyBlockedSuggestionIds)
        assertEquals(null, prepared.zeroReason)
    }

    private fun sampleSuggestion(
        id: String,
        lang: LangCode = LangCode.EN,
        beforeText: String = "i go school",
        nativeText: String = "나는 학교에 간다",
        afterText: String = "I go to school.",
        explanation: String = "demo explanation",
    ): CorrectionSuggestion = CorrectionSuggestion(
        id = id,
        lang = lang,
        sourceCandidateIds = listOf("c-$id"),
        sourceTurnIndex = 0,
        beforeText = beforeText,
        nativeText = nativeText,
        afterText = afterText,
        explanation = explanation,
    )
}
