package com.app.umma.domain.usecase.correction

import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.learningstate.LangCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * COR-005-A 변환 계약 회귀 테스트.
 *
 * 여기서 require 로 차단해야 하는 invariant 들이 위로 새면 ViewModel/UseCase 호출자에서
 * 동일한 가드 코드가 또 필요해진다. 이 테스트가 유일한 SSOT 가 되도록 모든 분기를 덮는다.
 */
class PrepareSaveRequestUseCaseTest {

    private val useCase = PrepareSaveRequestUseCase()

    @Test
    fun `prepares save request from distinct selected suggestions`() {
        // 사용자가 같은 카드를 두 번 토글하는 식으로 중복 id 가 들어와도 저장 카드는 하나만 만들어야 한다.
        val suggestions = listOf(
            sampleSuggestion(id = "s-1"),
            sampleSuggestion(id = "s-1"),
        )

        val result = useCase(
            uid = "uid-1",
            selectedSuggestions = suggestions,
        )

        assertTrue(result.isSuccess)
        val request = result.getOrThrow()
        assertEquals("uid-1", request.uid)
        assertEquals(LangCode.EN, request.lang)
        assertEquals(1, request.flashcards.size)
        assertEquals("s-1", request.flashcards.first().suggestionId)
        assertEquals("나는 학교에 간다", request.flashcards.first().frontText)
        assertEquals("I go to school.", request.flashcards.first().backText)
    }

    @Test
    fun `maps each selected suggestion to a flashcard with trimmed front, back, and explanation`() {
        // SYS-CORRECTION-INFRA 저장 계약: 앞면=모국어(nativeText), 뒷면=교정 문장(afterText), 설명 동반.
        // 어느 한 필드라도 매핑 규칙이 흔들리면 Flashcard 뒷면이 비거나 잘못된 텍스트가 표시되므로 명시적으로 검증한다.
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
        ).getOrThrow()

        assertEquals(2, request.flashcards.size)
        val first = request.flashcards.first()
        assertEquals("나는 학교에 간다", first.frontText)
        assertEquals("I go to school.", first.backText)
        assertEquals("3인칭 단수 ...", first.explanation)
    }

    @Test
    fun `passes requestedAt through to the request`() {
        // 완료 파이프라인 안에서 saveResult.savedAt 과 LangState analyzedAt 이 같은 기준 시각을 보려면
        // 화면에서 넘긴 requestedAt 이 변형 없이 그대로 전달돼야 한다.
        val fixedNow = 1_700_000_000_000L

        val request = useCase(
            uid = "uid-1",
            selectedSuggestions = listOf(sampleSuggestion(id = "s-1")),
            requestedAt = fixedNow,
        ).getOrThrow()

        assertEquals(fixedNow, request.requestedAt)
    }

    @Test
    fun `fails when selected suggestions are empty`() {
        // AC: 저장 대상이 0개이면 완료 파이프라인을 호출하지 않는다.
        // ViewModel 가드가 뚫리는 경우에도 도메인 require 가 마지막 방어선이 된다.
        val result = useCase(
            uid = "uid-1",
            selectedSuggestions = emptyList(),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `fails when uid is blank`() {
        // Room 저장이 uid+cardId 복합키라 blank uid 로 저장하면 계정 경계가 무너진다.
        val result = useCase(
            uid = "   ",
            selectedSuggestions = listOf(sampleSuggestion(id = "s-1")),
        )

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun `fails when selected suggestions mix multiple languages`() {
        // 저장 요청은 단일 언어 단위로 묶는 것이 SYS-CORRECTION-INFRA 계약이다.
        // 화면에서 선택 직전 언어가 바뀌었다면 stale suggestion 이 섞일 수 있어 도메인에서 차단한다.
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
    fun `fails when nativeText is blank`() {
        // 앞면이 비면 Flashcard 가 모국어 단서를 잃는다.
        val result = useCase(
            uid = "uid-1",
            selectedSuggestions = listOf(sampleSuggestion(id = "s-1", nativeText = "   ")),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `fails when afterText is blank`() {
        // 뒷면 교정 문장이 비면 학습 자료 자체가 성립하지 않는다.
        val result = useCase(
            uid = "uid-1",
            selectedSuggestions = listOf(sampleSuggestion(id = "s-1", afterText = "   ")),
        )

        assertTrue(result.isFailure)
    }

    private fun sampleSuggestion(
        id: String,
        lang: LangCode = LangCode.EN,
        nativeText: String = "나는 학교에 간다",
        afterText: String = "I go to school.",
        explanation: String = "demo explanation",
    ): CorrectionSuggestion = CorrectionSuggestion(
        id = id,
        lang = lang,
        sourceCandidateIds = listOf("c-$id"),
        sourceTurnIndex = 0,
        beforeText = "i go school",
        nativeText = nativeText,
        afterText = afterText,
        explanation = explanation,
    )
}
