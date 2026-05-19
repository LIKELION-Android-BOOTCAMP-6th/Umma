package com.example.umma.domain.usecase.correction

import com.example.umma.data.repository.CorrectionRepositoryImpl
import com.example.umma.data.repository.correction.CorrectionFlashcardStore
import com.example.umma.data.source.local.InMemoryCorrectionFlashcardLocalDataSource
import com.example.umma.data.source.remote.CorrectionFlashcardRemoteDataSource
import com.example.umma.data.model.correction.CorrectionFlashcardDto
import com.example.umma.domain.model.correction.CorrectionCandidate
import com.example.umma.domain.model.correction.GenerateSuggestionsInput
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.LangState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class GenerateSuggestionsUseCaseTest {

    private val useCase = GenerateSuggestionsUseCase(
        CorrectionRepositoryImpl(
            CorrectionFlashcardStore(
                localDataSource = InMemoryCorrectionFlashcardLocalDataSource(),
                remoteDataSource = NoopCorrectionFlashcardRemoteDataSource()
            )
        )
    )

    @Test
    fun `generates suggestions from candidates and lang state`() = runBlocking {
        val input = GenerateSuggestionsInput(
            candidates = listOf(
                CorrectionCandidate(
                    id = "en-0-abc",
                    lang = LangCode.EN,
                    sourceTurnIndex = 0,
                    sourceText = "i go school"
                )
            ),
            langState = LangState.initial(LangCode.EN)
        )

        val result = useCase(input)

        assertTrue(result.isSuccess)
        val suggestions = result.getOrThrow()
        assertEquals(1, suggestions.size)
        assertEquals("corr-en-0-abc", suggestions.first().id)
        assertEquals("i go school", suggestions.first().nativeText)
        assertEquals("I go school.", suggestions.first().afterText)
    }

    private class NoopCorrectionFlashcardRemoteDataSource : CorrectionFlashcardRemoteDataSource {
        override suspend fun syncFlashcards(
            flashcards: List<CorrectionFlashcardDto>
        ): Result<List<String>> = Result.success(flashcards.map { it.id })

        override suspend fun deleteFlashcards(flashcardIds: List<String>): Result<Unit> =
            Result.success(Unit)
    }
}
