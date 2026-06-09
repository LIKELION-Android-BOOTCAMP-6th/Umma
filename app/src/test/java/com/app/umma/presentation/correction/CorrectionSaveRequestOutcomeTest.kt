package com.app.umma.presentation.correction

import com.app.umma.domain.model.correction.CorrectionFlashcardSaveItem
import com.app.umma.domain.model.correction.CorrectionSaveRequest
import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.correction.PrepareCorrectionSaveRequestResult
import com.app.umma.domain.model.learningstate.LangCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CorrectionSaveRequestOutcomeTest {

    private val recordedCalls = mutableListOf<Pair<String, List<CorrectionSuggestion>>>()

    @Test
    fun `returns NotSavable and leaves state untouched when canSave is false`() {
        val state = baseState(
            phase = CorrectionUiState.Phase.Generating,
            suggestions = listOf(sampleSuggestion("s-1")),
            selected = setOf("s-1"),
        )

        val outcome = state.computeSaveRequestOutcome(uid = "uid-1", prepare = recordingPrepare())
        val next = state.applySaveRequestOutcome(outcome)

        assertEquals(SaveRequestOutcome.NotSavable, outcome)
        assertSame(state, next)
        assertTrue(recordedCalls.isEmpty())
    }

    @Test
    fun `returns NotSavable when Content but selection is empty`() {
        val state = baseState(
            phase = CorrectionUiState.Phase.Content,
            suggestions = listOf(sampleSuggestion("s-1")),
            selected = emptySet(),
        )

        val outcome = state.computeSaveRequestOutcome(uid = "uid-1", prepare = recordingPrepare())

        assertEquals(SaveRequestOutcome.NotSavable, outcome)
        assertTrue(recordedCalls.isEmpty())
    }

    @Test
    fun `returns NoMatchingSuggestions when all selected ids are stale`() {
        val state = baseState(
            phase = CorrectionUiState.Phase.Content,
            suggestions = listOf(sampleSuggestion("s-current")),
            selected = setOf("s-stale"),
        )

        val outcome = state.computeSaveRequestOutcome(uid = "uid-1", prepare = recordingPrepare())
        val next = state.applySaveRequestOutcome(outcome)

        assertEquals(SaveRequestOutcome.NoMatchingSuggestions, outcome)
        assertSame(state, next)
        assertTrue(recordedCalls.isEmpty())
    }

    @Test
    fun `filters stale ids before calling prepare`() {
        val live = sampleSuggestion("s-live")
        val state = baseState(
            phase = CorrectionUiState.Phase.Content,
            suggestions = listOf(live),
            selected = setOf("s-live", "s-stale"),
        )

        state.computeSaveRequestOutcome(uid = "uid-1", prepare = recordingPrepare(success = true))

        assertEquals(1, recordedCalls.size)
        assertEquals(listOf(live), recordedCalls.single().second)
    }

    @Test
    fun `returns UidUnavailable when uid is null`() {
        val state = readyContentState()

        val outcome = state.computeSaveRequestOutcome(uid = null, prepare = recordingPrepare())

        assertEquals(SaveRequestOutcome.UidUnavailable(), outcome)
        assertTrue(recordedCalls.isEmpty())
    }

    @Test
    fun `returns UidUnavailable when uid is blank`() {
        val state = readyContentState()

        val outcome = state.computeSaveRequestOutcome(uid = "   ", prepare = recordingPrepare())

        assertEquals(SaveRequestOutcome.UidUnavailable(), outcome)
    }

    @Test
    fun `apply with UidUnavailable clears saveRequest and records saveErrorReason`() {
        val previousRequest = sampleSaveRequest()
        val state = readyContentState().copy(saveRequest = previousRequest, saveErrorReason = null)

        val next = state.applySaveRequestOutcome(SaveRequestOutcome.UidUnavailable())

        assertNull(next.saveRequest)
        assertEquals("uid unavailable", next.saveErrorReason)
    }

    @Test
    fun `returns Prepared and apply stores the request on success`() {
        val state = readyContentState()
        val expected = samplePreparedResult()

        val outcome = state.computeSaveRequestOutcome(
            uid = "uid-1",
            prepare = { _, _ -> Result.success(expected) },
        )
        val next = state.applySaveRequestOutcome(outcome)

        assertTrue(outcome is SaveRequestOutcome.Prepared)
        assertEquals(expected, (outcome as SaveRequestOutcome.Prepared).result)
        assertEquals(expected.request, next.saveRequest)
        assertNull(next.saveErrorReason)
    }

    @Test
    fun `returns Failed and apply clears previous saveRequest on failure`() {
        val previousRequest = sampleSaveRequest()
        val state = readyContentState().copy(saveRequest = previousRequest, saveErrorReason = null)

        val outcome = state.computeSaveRequestOutcome(
            uid = "uid-1",
            prepare = { _, _ -> Result.failure(IllegalArgumentException("uid must not be blank")) },
        )
        val next = state.applySaveRequestOutcome(outcome)

        when (outcome) {
            is SaveRequestOutcome.Failed -> assertEquals("uid must not be blank", outcome.reason)
            else -> fail("expected Failed, got $outcome")
        }
        assertNull(next.saveRequest)
        assertEquals("uid must not be blank", next.saveErrorReason)
    }

    @Test
    fun `Failed falls back to exception class name when message is null`() {
        val state = readyContentState()

        val outcome = state.computeSaveRequestOutcome(
            uid = "uid-1",
            prepare = { _, _ -> Result.failure(RuntimeException()) },
        )

        when (outcome) {
            is SaveRequestOutcome.Failed -> assertEquals("RuntimeException", outcome.reason)
            else -> fail("expected Failed, got $outcome")
        }
    }

    @Test
    fun `returns AlreadyInFlight when isSavePreparing is true and leaves state untouched`() {
        val state = readyContentState().copy(isSavePreparing = true)

        val outcome = state.computeSaveRequestOutcome(uid = "uid-1", prepare = recordingPrepare())
        val next = state.applySaveRequestOutcome(outcome)

        assertEquals(SaveRequestOutcome.AlreadyInFlight, outcome)
        assertSame(state, next)
        assertTrue(recordedCalls.isEmpty())
    }

    @Test
    fun `canSave is false while isSavePreparing`() {
        val state = readyContentState().copy(isSavePreparing = true)

        assertFalse(state.canSave)
    }

    @Test
    fun `apply with Prepared clears isSavePreparing`() {
        val state = readyContentState().copy(isSavePreparing = true)
        val expected = samplePreparedResult()

        val next = state.applySaveRequestOutcome(SaveRequestOutcome.Prepared(expected))

        assertFalse(next.isSavePreparing)
        assertEquals(expected.request, next.saveRequest)
    }

    @Test
    fun `apply with Failed clears isSavePreparing and surfaces reason`() {
        val state = readyContentState().copy(isSavePreparing = true)

        val next = state.applySaveRequestOutcome(SaveRequestOutcome.Failed("nativeText must not be blank"))

        assertFalse(next.isSavePreparing)
        assertNull(next.saveRequest)
        assertEquals("nativeText must not be blank", next.saveErrorReason)
    }

    @Test
    fun `apply with UidUnavailable clears isSavePreparing`() {
        val state = readyContentState().copy(isSavePreparing = true)

        val next = state.applySaveRequestOutcome(SaveRequestOutcome.UidUnavailable())

        assertFalse(next.isSavePreparing)
    }

    @Test
    fun `apply with NotSavable closes preparing window when it was opened`() {
        val state = readyContentState().copy(isSavePreparing = true)

        val next = state.applySaveRequestOutcome(SaveRequestOutcome.NotSavable)

        assertFalse(next.isSavePreparing)
    }

    private fun readyContentState(): CorrectionUiState = baseState(
        phase = CorrectionUiState.Phase.Content,
        suggestions = listOf(sampleSuggestion("s-1"), sampleSuggestion("s-2")),
        selected = setOf("s-1", "s-2"),
    )

    private fun baseState(
        phase: CorrectionUiState.Phase,
        suggestions: List<CorrectionSuggestion>,
        selected: Set<String>,
    ): CorrectionUiState = CorrectionUiState(
        phase = phase,
        suggestions = suggestions,
        selectedSuggestionIds = selected,
    )

    private fun sampleSuggestion(
        id: String,
        lang: LangCode = LangCode.EN,
    ): CorrectionSuggestion = CorrectionSuggestion(
        id = id,
        lang = lang,
        sourceCandidateIds = listOf("c-$id"),
        sourceTurnIndex = 0,
        beforeText = "i go school",
        nativeText = "나는 학교에 간다",
        afterText = "I go to school.",
        explanation = "demo",
    )

    private fun sampleSaveRequest(): CorrectionSaveRequest = CorrectionSaveRequest(
        uid = "uid-1",
        lang = LangCode.EN,
        flashcards = listOf(
            CorrectionFlashcardSaveItem(
                suggestionId = "s-1",
                frontText = "나는 학교에 간다",
                backText = "I go to school.",
                explanation = "demo",
            ),
        ),
        requestedAt = 1_700_000_000_000L,
    )

    private fun samplePreparedResult(): PrepareCorrectionSaveRequestResult = PrepareCorrectionSaveRequestResult(
        request = sampleSaveRequest(),
        saveableSuggestionIds = listOf("s-1"),
        qualityFilteredSuggestionIds = emptyList(),
        safetyBlockedSuggestionIds = emptyList(),
        zeroReason = null,
    )

    private fun recordingPrepare(success: Boolean = false): (String, List<CorrectionSuggestion>) -> Result<PrepareCorrectionSaveRequestResult> =
        { uid, selected ->
            recordedCalls += uid to selected
            if (success) {
                Result.success(
                    PrepareCorrectionSaveRequestResult(
                        request = CorrectionSaveRequest(
                            uid = uid,
                            lang = selected.first().lang,
                            flashcards = emptyList(),
                            requestedAt = 0L,
                        ),
                        saveableSuggestionIds = selected.map { it.id },
                        qualityFilteredSuggestionIds = emptyList(),
                        safetyBlockedSuggestionIds = emptyList(),
                        zeroReason = null,
                    )
                )
            } else {
                Result.failure(AssertionError("prepare 가 호출되면 안 되는 경로"))
            }
        }
}
