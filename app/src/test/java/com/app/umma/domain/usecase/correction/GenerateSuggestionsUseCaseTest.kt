package com.app.umma.domain.usecase.correction

import com.app.umma.domain.model.correction.CorrectionCandidate
import com.app.umma.domain.model.correction.CorrectionSaveRequest
import com.app.umma.domain.model.correction.CorrectionSaveResult
import com.app.umma.domain.model.correction.CorrectionSuggestion
import com.app.umma.domain.model.correction.GenerateSuggestionsInput
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.repository.CorrectionRepository
import com.app.umma.domain.usecase.learningstate.BuildLearnerAdaptationProfileUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * UseCase 는 repository 호출을 감싸되, domain 정책인 교정 결과 개수 상한을 최종 보증한다.
 *
 * AI 호출 흐름과 happy path 는 [com.app.umma.data.repository.CorrectionRepositoryImplTest] 가 검증한다.
 * 여기서는 UseCase 가 입력을 그대로 위임하면서도, 성공 결과를 최대 10개로 수렴시키는지 확인한다.
 */
class GenerateSuggestionsUseCaseTest {

    @Test
    fun `delegates to repository and forwards its result`() = runBlocking {
        val expected = Result.success(
            listOf(
                CorrectionSuggestion(
                    id = "corr-en-0-a",
                    lang = LangCode.EN,
                    sourceCandidateIds = listOf("en-0-a"),
                    sourceTurnIndex = 0,
                    beforeText = "i go school",
                    nativeText = "나는 학교에 간다",
                    afterText = "I go to school.",
                    explanation = "demo"
                )
            )
        )
        val repository = RecordingCorrectionRepository(expected)
        val useCase = GenerateSuggestionsUseCase(repository)
        val input = GenerateSuggestionsInput(
            candidates = listOf(
                CorrectionCandidate(
                    id = "en-0-a",
                    lang = LangCode.EN,
                    sourceTurnIndex = 0,
                    sourceText = "i go school"
                )
            ),
            langState = LangState.initial(LangCode.EN),
            primaryLang = LangCode.KO,
            profile = BuildLearnerAdaptationProfileUseCase()(LangState.initial(LangCode.EN))
        )

        val actual = useCase(input)

        assertSame(repository.lastInput, input)
        assertEquals(expected.getOrThrow(), actual.getOrThrow())
    }

    @Test
    fun `caps repository suggestions to 10 when more than 10 are returned`() = runBlocking {
        val repository = RecordingCorrectionRepository(
            Result.success((0 until 11).map { index -> suggestionOf(index) })
        )
        val useCase = GenerateSuggestionsUseCase(repository)

        val actual = useCase(inputOf(candidateCount = 11)).getOrThrow()

        assertEquals(10, actual.size)
        assertEquals("corr-en-0-a", actual.first().id)
        assertEquals("corr-en-9-a", actual.last().id)
    }

    @Test
    fun `keeps exactly 10 suggestions unchanged`() = runBlocking {
        val expectedSuggestions = (0 until 10).map { index -> suggestionOf(index) }
        val repository = RecordingCorrectionRepository(Result.success(expectedSuggestions))
        val useCase = GenerateSuggestionsUseCase(repository)

        val actual = useCase(inputOf(candidateCount = 10)).getOrThrow()

        assertEquals(expectedSuggestions, actual)
    }

    @Test
    fun `keeps fewer than 10 suggestions unchanged`() = runBlocking {
        val expectedSuggestions = (0 until 5).map { index -> suggestionOf(index) }
        val repository = RecordingCorrectionRepository(Result.success(expectedSuggestions))
        val useCase = GenerateSuggestionsUseCase(repository)

        val actual = useCase(inputOf(candidateCount = 5)).getOrThrow()

        assertEquals(expectedSuggestions, actual)
    }

    @Test
    fun `keeps empty suggestions result unchanged`() = runBlocking {
        val repository = RecordingCorrectionRepository(Result.success(emptyList()))
        val useCase = GenerateSuggestionsUseCase(repository)

        val actual = useCase(inputOf(candidateCount = 0)).getOrThrow()

        assertTrue(actual.isEmpty())
    }

    private fun inputOf(candidateCount: Int): GenerateSuggestionsInput {
        val lang = LangCode.EN
        return GenerateSuggestionsInput(
            candidates = (0 until candidateCount).map { index ->
                CorrectionCandidate(
                    id = "en-$index-a",
                    lang = lang,
                    sourceTurnIndex = index,
                    sourceText = "source-$index"
                )
            },
            langState = LangState.initial(lang),
            primaryLang = LangCode.KO,
            profile = BuildLearnerAdaptationProfileUseCase()(LangState.initial(lang))
        )
    }

    private fun suggestionOf(index: Int): CorrectionSuggestion {
        return CorrectionSuggestion(
            id = "corr-en-$index-a",
            lang = LangCode.EN,
            sourceCandidateIds = listOf("en-$index-a"),
            sourceTurnIndex = index,
            beforeText = "before-$index",
            nativeText = "native-$index",
            afterText = "after-$index",
            explanation = "explanation-$index"
        )
    }

    private class RecordingCorrectionRepository(
        private val result: Result<List<CorrectionSuggestion>>
    ) : CorrectionRepository {
        var lastInput: GenerateSuggestionsInput? = null
            private set

        override suspend fun generateSuggestions(
            input: GenerateSuggestionsInput
        ): Result<List<CorrectionSuggestion>> {
            lastInput = input
            return result
        }

        override suspend fun saveFlashcards(request: CorrectionSaveRequest): Result<CorrectionSaveResult> {
            error("not used in this test")
        }

        override suspend fun rollbackFlashcards(request: CorrectionSaveRequest): Result<Unit> {
            error("not used in this test")
        }
    }
}
