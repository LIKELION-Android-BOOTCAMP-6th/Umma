package com.example.umma.data.repository.fake

import com.example.umma.data.model.correction.CorrectionFlashcardDto
import com.example.umma.data.repository.correction.CorrectionAiClient
import com.example.umma.data.repository.correction.CorrectionAiResponseMapper
import com.example.umma.data.repository.correction.CorrectionFlashcardStore
import com.example.umma.data.repository.correction.CorrectionPromptBuilder
import com.example.umma.data.repository.correction.CorrectionSuggestionFixtures
import com.example.umma.data.source.local.TestCorrectionFlashcardLocalDataSource
import com.example.umma.data.source.remote.CorrectionFlashcardRemoteDataSource
import com.example.umma.domain.model.correction.CorrectionFlashcardSaveItem
import com.example.umma.domain.model.correction.CorrectionSaveRequest
import com.example.umma.domain.model.learningstate.LangCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeCorrectionRepositoryTest {

    private fun newRepository(): FakeCorrectionRepository = FakeCorrectionRepository(
        flashcardStore = CorrectionFlashcardStore(
            localDataSource = TestCorrectionFlashcardLocalDataSource(),
            remoteDataSource = NoopCorrectionFlashcardRemoteDataSource()
        ),
        // Fake 는 generateSuggestions 에서 fixture builder fallback 만 쓰므로 promptBuilder/aiClient/mapper 는 실제 호출되지 않는다.
        // 그래도 super 생성자 invariant 를 만족시키기 위해 real instance 를 넘긴다.
        promptBuilder = CorrectionPromptBuilder(),
        aiClient = ThrowingCorrectionAiClient,
        responseMapper = CorrectionAiResponseMapper()
    )

    @Test
    fun `fake repository falls back to fixture builder when override is null`() = runBlocking {
        // 토글이 비어 있으면 super 의 실제 AI 호출이 아니라 fixture builder 결과를 그대로 사용한다는 회귀 가드.
        val repository = newRepository()

        val result = repository.generateSuggestions(CorrectionSuggestionFixtures.sampleGenerateInput())

        assertTrue(result.isSuccess)
        val suggestion = result.getOrThrow().single()
        assertEquals("corr-en-1-def", suggestion.id)
        assertEquals("this is test", suggestion.nativeText)
        assertEquals("This is test.", suggestion.afterText)
    }

    @Test
    fun `suggestionsOverride exposes content state`() = runBlocking {
        val content = CorrectionSuggestionFixtures.contentSuggestions()
        val repository = newRepository().apply {
            suggestionsOverride = content
        }

        val result = repository.generateSuggestions(CorrectionSuggestionFixtures.sampleGenerateInput())

        // override 가 설정되면 super 의 fixture builder 가 아니라 화면에서 지정한 카드 목록이 그대로 나온다.
        assertEquals(content, result.getOrThrow())
    }

    @Test
    fun `suggestionsOverride empty list exposes empty state`() = runBlocking {
        val repository = newRepository().apply {
            suggestionsOverride = CorrectionSuggestionFixtures.emptySuggestions()
        }

        val result = repository.generateSuggestions(CorrectionSuggestionFixtures.sampleGenerateInput())

        // Empty 상태도 토글 한 줄로 재현 가능해야 화면 Empty CTA 분기를 검증할 수 있다.
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `generateFailure surfaces as Result failure`() = runBlocking {
        val cause = CorrectionSuggestionFixtures.generateFailure("boom")
        val repository = newRepository().apply {
            generateFailure = cause
        }

        val result = repository.generateSuggestions(CorrectionSuggestionFixtures.sampleGenerateInput())

        assertTrue(result.isFailure)
        assertEquals(cause, result.exceptionOrNull())
    }

    @Test
    fun `saveResultOverride reproduces pending sync state`() = runBlocking {
        val pending = CorrectionSuggestionFixtures.pendingSyncSaveResult(savedIds = listOf("corr-en-1-def"))
        val repository = newRepository().apply {
            saveResultOverride = pending
        }

        val result = repository.saveFlashcards(CorrectionSuggestionFixtures.sampleSaveRequest())

        val saved = result.getOrThrow()
        // local-first 계약상 local 저장은 항상 성공이고 remote sync 만 pending 으로 남는다.
        assertEquals(listOf("corr-en-1-def"), saved.localSavedFlashcardIds)
        assertEquals(listOf("corr-en-1-def"), saved.pendingSyncFlashcardIds)
    }

    @Test
    fun `saveFailure surfaces as Result failure`() = runBlocking {
        val cause = CorrectionSuggestionFixtures.saveFailure("save boom")
        val repository = newRepository().apply {
            saveFailure = cause
        }

        val result = repository.saveFlashcards(CorrectionSuggestionFixtures.sampleSaveRequest())

        assertTrue(result.isFailure)
        assertEquals(cause, result.exceptionOrNull())
    }

    @Test
    fun `rollbackFailure surfaces as Result failure`() = runBlocking {
        val cause = IllegalStateException("rollback boom")
        val repository = newRepository().apply {
            rollbackFailure = cause
        }

        val result = repository.rollbackFlashcards(CorrectionSuggestionFixtures.sampleSaveRequest())

        assertTrue(result.isFailure)
        assertEquals(cause, result.exceptionOrNull())
    }

    @Test
    fun `empty save request still fails fast`() = runBlocking {
        // 토글이 없을 때는 super 의 require(flashcards.isNotEmpty()) 가 그대로 동작해야 한다.
        val repository = newRepository()
        val emptyRequest = CorrectionSaveRequest(
            uid = CorrectionSuggestionFixtures.DEFAULT_UID,
            lang = LangCode.EN,
            flashcards = emptyList<CorrectionFlashcardSaveItem>()
        )

        val result = repository.saveFlashcards(emptyRequest)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    private class NoopCorrectionFlashcardRemoteDataSource : CorrectionFlashcardRemoteDataSource {
        override suspend fun syncFlashcards(
            flashcards: List<CorrectionFlashcardDto>
        ): Result<List<String>> = Result.success(flashcards.map { it.id })

        override suspend fun deleteFlashcards(flashcardIds: List<String>): Result<Unit> =
            Result.success(Unit)
    }

    /**
     * Fake 가 fixture builder 로 fallback 하는 동안에는 AI client 가 절대 호출되어선 안 된다.
     * 호출되면 즉시 실패해 회귀를 알리도록 throw 한다.
     */
    private object ThrowingCorrectionAiClient : CorrectionAiClient {
        override suspend fun generateJson(prompt: String): String {
            throw AssertionError("Fake fallback should not invoke real AI client")
        }
    }
}
