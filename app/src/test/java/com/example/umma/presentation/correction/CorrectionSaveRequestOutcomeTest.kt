package com.example.umma.presentation.correction

import com.example.umma.domain.model.correction.CorrectionFlashcardSaveItem
import com.example.umma.domain.model.correction.CorrectionSaveRequest
import com.example.umma.domain.model.correction.CorrectionSuggestion
import com.example.umma.domain.model.learningstate.LangCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * COR-005-A 저장 요청 변환 분기 회귀 테스트.
 *
 * [computeSaveRequestOutcome] / [applySaveRequestOutcome] 는 [CorrectionViewModel] 의
 * `onSaveClicked` 분기 본체를 pure function 으로 추출한 것이다. ViewModel 의 viewModelScope /
 * Main dispatcher 셋업 없이도 모든 가드(canSave, stale id, uid, success/failure) 를 즉시 회귀할 수 있다.
 */
class CorrectionSaveRequestOutcomeTest {

    private val recordedCalls = mutableListOf<Pair<String, List<CorrectionSuggestion>>>()

    @Test
    fun `returns NotSavable and leaves state untouched when canSave is false`() {
        // canSave=false 인 경우 — 선택은 있지만 phase 가 Content 가 아닌 케이스로 시연.
        val state = baseState(
            phase = CorrectionUiState.Phase.Generating,
            suggestions = listOf(sampleSuggestion("s-1")),
            selected = setOf("s-1"),
        )

        val outcome = state.computeSaveRequestOutcome(uid = "uid-1", prepare = recordingPrepare())
        val next = state.applySaveRequestOutcome(outcome)

        assertEquals(SaveRequestOutcome.NotSavable, outcome)
        // 같은 인스턴스를 그대로 돌려줘 MutableStateFlow.update 가 emit 을 생략하도록 한다.
        assertSame(state, next)
        assertTrue("prepare 가 호출되면 안 된다", recordedCalls.isEmpty())
    }

    @Test
    fun `returns NotSavable when Content but selection is empty`() {
        // AC: "선택 항목이 0개이면 완료 파이프라인을 호출하지 않는다" — 1차 가드는 canSave 가 잡는다.
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
        // AC 엣지: 선택된 카드 id 가 현재 결과 목록에 없는 경우 — 새 suggestions 로 교체된 직후의 race.
        // 1차 가드(canSave) 는 통과하지만 필터 후 0개라 변환을 시도하지 않는다.
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
        // 일부만 stale 인 경우 — 살아있는 id 만 prepare 로 넘어가야 한다.
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
        assertTrue("uid 가 없으면 prepare 를 호출하지 않는다", recordedCalls.isEmpty())
    }

    @Test
    fun `returns UidUnavailable when uid is blank`() {
        val state = readyContentState()

        val outcome = state.computeSaveRequestOutcome(uid = "   ", prepare = recordingPrepare())

        assertEquals(SaveRequestOutcome.UidUnavailable(), outcome)
    }

    @Test
    fun `apply with UidUnavailable clears saveRequest and records saveErrorReason`() {
        // 직전 시도의 saveRequest 가 살아 있는 상태에서 uid 가 사라진 경우 — stale request 가 남으면 안 된다.
        val previousRequest = sampleSaveRequest()
        val state = readyContentState().copy(saveRequest = previousRequest, saveErrorReason = null)

        val next = state.applySaveRequestOutcome(SaveRequestOutcome.UidUnavailable())

        assertNull(next.saveRequest)
        assertEquals("uid unavailable", next.saveErrorReason)
    }

    @Test
    fun `returns Prepared and apply stores the request on success`() {
        val state = readyContentState()
        val expected = sampleSaveRequest()

        val outcome = state.computeSaveRequestOutcome(
            uid = "uid-1",
            prepare = { _, _ -> Result.success(expected) },
        )
        val next = state.applySaveRequestOutcome(outcome)

        assertTrue(outcome is SaveRequestOutcome.Prepared)
        assertEquals(expected, (outcome as SaveRequestOutcome.Prepared).request)
        assertEquals(expected, next.saveRequest)
        assertNull(next.saveErrorReason)
    }

    @Test
    fun `returns Failed and apply clears previous saveRequest on failure`() {
        // 직전 시도의 saveRequest 가 살아 있는 상태에서 새 시도가 실패하면 stale request 는 비워야 한다.
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
        // message 가 null 인 Throwable 도 진단 단서가 남아야 한다.
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

    // ─── Helpers ───────────────────────────────────────────────────────────

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

    /**
     * 호출된 (uid, selected) 를 [recordedCalls] 에 기록하는 prepare 스텁.
     *
     * @param success true 면 빈 SaveRequest 를 success 로 돌려준다. 검증 대상이 outcome 분기 자체이므로
     *                request 의 정확한 필드는 [PrepareSaveRequestUseCaseTest] 가 담당하고 여기서는 신경 쓰지 않는다.
     */
    private fun recordingPrepare(success: Boolean = false): (String, List<CorrectionSuggestion>) -> Result<CorrectionSaveRequest> =
        { uid, selected ->
            recordedCalls += uid to selected
            if (success) {
                Result.success(
                    CorrectionSaveRequest(
                        uid = uid,
                        lang = selected.first().lang,
                        flashcards = emptyList(),
                        requestedAt = 0L,
                    )
                )
            } else {
                Result.failure(AssertionError("prepare 가 호출되면 안 되는 경로"))
            }
        }
}
