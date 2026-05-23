package com.example.umma.domain.usecase.correction

import com.example.umma.domain.model.correction.CorrectionCandidate
import com.example.umma.domain.model.correction.CorrectionSaveRequest
import com.example.umma.domain.model.correction.CorrectionSaveResult
import com.example.umma.domain.model.correction.CorrectionSuggestion
import com.example.umma.domain.model.correction.GenerateSuggestionsInput
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.LangState
import com.example.umma.domain.repository.CorrectionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * UseCase 는 repository 로 위임만 한다.
 *
 * AI 호출 흐름과 happy path 는 [com.example.umma.data.repository.CorrectionRepositoryImplTest] 가 검증한다.
 * 여기서는 UseCase 가 입력을 변형하지 않고 repository 결과를 그대로 돌려준다는 점만 본다.
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
            langState = LangState.initial(LangCode.EN)
        )

        val actual = useCase(input)

        assertSame(repository.lastInput, input)
        assertEquals(expected.getOrThrow(), actual.getOrThrow())
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
