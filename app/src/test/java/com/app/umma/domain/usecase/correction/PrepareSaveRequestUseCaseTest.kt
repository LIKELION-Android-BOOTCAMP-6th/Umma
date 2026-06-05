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

    // COR-TUNE-007: 빈 nativeText는 저품질 필터로 제외한다 (저장 실패가 아닌 자연스러운 처리).
    @Test
    fun `excludes card when native text is blank`() {
        val result = useCase(
            uid = "uid-1",
            selectedSuggestions = listOf(sampleSuggestion(id = "s-1", nativeText = "   ")),
        )

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().flashcards.size)
    }

    // COR-TUNE-007: 빈 afterText는 저품질 필터로 제외한다 (저장 실패가 아닌 자연스러운 처리).
    @Test
    fun `excludes card when corrected text is blank`() {
        val result = useCase(
            uid = "uid-1",
            selectedSuggestions = listOf(sampleSuggestion(id = "s-1", afterText = "   ")),
        )

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().flashcards.size)
    }

    @Test
    fun `excludes card when correction before and after are identical after normalization`() {
        // 교정 전후가 (대소문자·구두점·공백 정규화 후) 동일하면 학습 가치가 없어 제외한다.
        val result = useCase(
            uid = "uid-1",
            selectedSuggestions = listOf(
                sampleSuggestion(id = "s-1", beforeText = "I go to school.", afterText = "I go to school."),
                sampleSuggestion(id = "s-2", beforeText = "i go to school", afterText = "I go to school!"),
                sampleSuggestion(id = "s-3"),
            ),
        )

        assertTrue(result.isSuccess)
        val flashcards = result.getOrThrow().flashcards
        // s-1(전후 동일), s-2(정규화 후 동일) 제외, s-3(정상) 유지
        assertEquals(1, flashcards.size)
        assertEquals("s-3", flashcards.first().suggestionId)
    }

    @Test
    fun `excludes card when corrected text is too short after normalization`() {
        // 정규화 후 MIN_CARD_CHAR_LENGTH 미만이면 학습 자료로 성립하지 않아 제외한다.
        val result = useCase(
            uid = "uid-1",
            selectedSuggestions = listOf(sampleSuggestion(id = "s-1", afterText = "A")),
        )

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().flashcards.size)
    }

    @Test
    fun `deduplicates cards with same front and back text after normalization`() {
        // 정규화 후 (앞면, 뒷면)이 동일한 카드는 첫 번째만 유지하고 이후는 skip한다.
        val result = useCase(
            uid = "uid-1",
            selectedSuggestions = listOf(
                sampleSuggestion(id = "s-1", nativeText = "나는 학교에 간다", afterText = "I go to school."),
                sampleSuggestion(id = "s-2", nativeText = "나는 학교에 간다", afterText = "I go to school."),
                sampleSuggestion(id = "s-3", nativeText = "나는 학교에 간다", afterText = "I GO TO SCHOOL!"),
            ),
        )

        assertTrue(result.isSuccess)
        val flashcards = result.getOrThrow().flashcards
        // s-1 유지, s-2(s-1과 정규화 후 동일) skip, s-3(정규화 후 동일) skip
        assertEquals(1, flashcards.size)
        assertEquals("s-1", flashcards.first().suggestionId)
    }

    @Test
    fun `returns success with empty flashcards when all suggestions are excluded by quality filter`() {
        // 전부 제외돼도 저장 실패로 보이지 않게 Result.success + 빈 flashcards로 반환한다.
        // CompleteCorrectionUseCase는 빈 저장 결과를 이미 안전하게 처리한다.
        val result = useCase(
            uid = "uid-1",
            selectedSuggestions = listOf(
                sampleSuggestion(id = "s-1", beforeText = "same", afterText = "same"),
                sampleSuggestion(id = "s-2", afterText = "A"),
            ),
        )

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().flashcards.size)
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
