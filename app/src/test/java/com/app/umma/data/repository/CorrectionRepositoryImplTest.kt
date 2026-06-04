package com.app.umma.data.repository

import com.app.umma.data.model.correction.CorrectionFlashcardDto
import com.app.umma.data.repository.correction.CorrectionAiClient
import com.app.umma.data.repository.correction.CorrectionAiResponseMapper
import com.app.umma.data.repository.correction.CorrectionFlashcardStore
import com.app.umma.data.repository.correction.CorrectionPromptBuilder
import com.app.umma.data.source.local.TestCorrectionFlashcardLocalDataSource
import com.app.umma.data.source.remote.CorrectionFlashcardRemoteDataSource
import com.app.umma.domain.model.correction.CorrectionCandidate
import com.app.umma.domain.model.correction.CorrectionFlashcardSaveItem
import com.app.umma.domain.model.correction.CorrectionSaveRequest
import com.app.umma.domain.model.correction.GenerateSuggestionsInput
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.usecase.learningstate.BuildLearnerAdaptationProfileUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionRepositoryImplTest {

    // RepositoryImpl 은 local-first 저장과 remote sync 시도만 조율한다.
    // Recording remote 는 Firestore 대신 sync 성공/실패와 DTO 내용을 관찰하기 위한 테스트 대역이다.
    private val remoteDataSource = RecordingCorrectionFlashcardRemoteDataSource()
    private val localDataSource = TestCorrectionFlashcardLocalDataSource()
    private val aiClient = FakeCorrectionAiClient()
    private val repository = CorrectionRepositoryImpl(
        flashcardStore = CorrectionFlashcardStore(
            localDataSource = localDataSource,
            remoteDataSource = remoteDataSource
        ),
        promptBuilder = CorrectionPromptBuilder(),
        aiClient = aiClient,
        responseMapper = CorrectionAiResponseMapper()
    )

    // ──────────────────────────────────────────────────────────────
    // COR-002-A — generateSuggestions happy path / Error 분기
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `generateSuggestions returns mapped suggestions when ai response is valid`() = runBlocking {
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
        aiClient.responseJson = """
            {
              "suggestions": [
                {
                  "candidateId": "en-0-a",
                  "nativeText": "나는 학교에 간다",
                  "afterText": "I go to school.",
                  "explanation": "go 뒤에는 to school 을 사용한다."
                }
              ]
            }
        """.trimIndent()

        val result = repository.generateSuggestions(input)

        assertTrue("success expected", result.isSuccess)
        val suggestions = result.getOrThrow()
        assertEquals(1, suggestions.size)
        val suggestion = suggestions.single()
        assertEquals("corr-en-0-a", suggestion.id)
        assertEquals("i go school", suggestion.beforeText)
        assertEquals("I go to school.", suggestion.afterText)
        assertEquals("나는 학교에 간다", suggestion.nativeText)
        assertNotNull(aiClient.lastPrompt)
        assertTrue("프롬프트에 candidateId 가 포함되어야 함", aiClient.lastPrompt!!.contains("en-0-a"))
    }

    @Test
    fun `generateSuggestions returns failure when ai returns unknown candidate id`() = runBlocking {
        // AC 6: candidateId 불일치 → Error.
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
        aiClient.responseJson = """
            {
              "suggestions": [
                {
                  "candidateId": "ghost",
                  "nativeText": "나는 학교에 간다",
                  "afterText": "I go to school.",
                  "explanation": "demo"
                }
              ]
            }
        """.trimIndent()

        val result = repository.generateSuggestions(input)

        assertTrue("failure expected", result.isFailure)
        val e = result.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
    }

    @Test
    fun `generateSuggestions skips ai call when candidates list is empty`() = runBlocking {
        // 후보 0건일 때 네트워크/비용 낭비를 막는다. Empty UX 본격 처리는 -B.
        val input = GenerateSuggestionsInput(
            candidates = emptyList(),
            langState = LangState.initial(LangCode.EN),
            primaryLang = LangCode.KO,
            profile = BuildLearnerAdaptationProfileUseCase()(LangState.initial(LangCode.EN))
        )

        val result = repository.generateSuggestions(input)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        assertNull("AI client 가 호출되면 안 됨", aiClient.lastPrompt)
    }

    @Test
    fun `saveFlashcards stores suggestions locally and marks them pending sync`() = runBlocking {
        // remote sync 실패는 사용자 저장 실패가 아니다.
        // local 저장 성공 ID 가 유지되고 pending sync 로만 남는지 확인한다.
        remoteDataSource.shouldFailSync = true
        val request = CorrectionSaveRequest(
            uid = "uid-1",
            lang = LangCode.EN,
            flashcards = listOf(
                CorrectionFlashcardSaveItem(
                    suggestionId = "s-1",
                    frontText = "나는 학교에 간다",
                    backText = "I go school.",
                    explanation = "demo"
                )
            ),
            requestedAt = 123L
        )

        val result = repository.saveFlashcards(request)

        assertTrue(result.isSuccess)
        val saveResult = result.getOrThrow()
        assertEquals(listOf("s-1"), saveResult.localSavedFlashcardIds)
        assertEquals(listOf("s-1"), saveResult.pendingSyncFlashcardIds)
        assertEquals(123L, saveResult.savedAt)
    }

    @Test
    fun `saveFlashcards maps flashcard dto with firestore field contract`() = runBlocking {
        // Firestore 필드명과 값은 SRS / LearningState 문서와 맞아야 한다.
        // 특히 language 값은 enum name 이 아니라 LS-001 표준 코드(en)여야 한다.
        val request = CorrectionSaveRequest(
            uid = "uid-1",
            lang = LangCode.EN,
            flashcards = listOf(
                CorrectionFlashcardSaveItem(
                    suggestionId = "s-field",
                    frontText = "나는 어제 박물관에 갔다.",
                    backText = "I went to the museum yesterday.",
                    explanation = "Use past tense for yesterday."
                )
            ),
            requestedAt = 500L
        )

        repository.saveFlashcards(request).getOrThrow()

        val synced = remoteDataSource.syncedFlashcards.single()
        val firestoreMap = synced.toFirestoreMap()
        assertEquals("en", synced.language)
        assertEquals("en", firestoreMap["language"])
        assertEquals("나는 어제 박물관에 갔다.", firestoreMap["frontText"])
        assertEquals("I went to the museum yesterday.", firestoreMap["backText"])
        assertEquals(500L, firestoreMap["nextReviewAt"])
        assertEquals(0, firestoreMap["interval"])
        assertEquals(2.5, firestoreMap["easeFactor"])
        assertEquals(true, firestoreMap["dirty"])
    }

    @Test
    fun `saveFlashcards clears pending sync when firestore sync succeeds`() = runBlocking {
        // remote sync 가 성공하면 pending 목록이 비어야 Dashboard/후속 sync 가 불필요한 재시도를 하지 않는다.
        val request = CorrectionSaveRequest(
            uid = "uid-1",
            lang = LangCode.EN,
            flashcards = listOf(
                CorrectionFlashcardSaveItem(
                    suggestionId = "s-2",
                    frontText = "나는 커피를 마신다",
                    backText = "I drink coffee.",
                    explanation = "demo"
                )
            ),
            requestedAt = 124L
        )

        val result = repository.saveFlashcards(request)

        assertTrue(result.isSuccess)
        val saveResult = result.getOrThrow()
        assertEquals(listOf("s-2"), saveResult.localSavedFlashcardIds)
        assertTrue(saveResult.pendingSyncFlashcardIds.isEmpty())
        assertEquals(listOf("s-2"), remoteDataSource.syncedIds)
        assertEquals(listOf("s-2"), localDataSource.syncedIds)
    }

    @Test
    fun `saveFlashcards is idempotent for the same suggestion`() = runBlocking {
        // suggestionId 를 card id 로 쓰는 정책 덕분에 같은 교정 결과를 여러 번 저장해도 중복 카드가 생기지 않는다.
        val request = CorrectionSaveRequest(
            uid = "uid-1",
            lang = LangCode.EN,
            flashcards = listOf(
                CorrectionFlashcardSaveItem(
                    suggestionId = "s-1",
                    frontText = "나는 학교에 간다",
                    backText = "I go school.",
                    explanation = "demo"
                )
            ),
            requestedAt = 456L
        )

        val first = repository.saveFlashcards(request).getOrThrow()
        val second = repository.saveFlashcards(request).getOrThrow()

        assertEquals(listOf("s-1"), first.localSavedFlashcardIds)
        assertTrue(second.localSavedFlashcardIds.isEmpty())
        assertTrue(second.pendingSyncFlashcardIds.isEmpty())
    }

    @Test
    fun `saved flashcards can be queried as due deck source for srs`() = runBlocking {
        val request = CorrectionSaveRequest(
            uid = "uid-1",
            lang = LangCode.EN,
            flashcards = listOf(
                CorrectionFlashcardSaveItem(
                    suggestionId = "s-due",
                    frontText = "나는 산책한다",
                    backText = "I take a walk.",
                    explanation = "Use take a walk for 산책하다."
                )
            ),
            requestedAt = 1_000L
        )

        repository.saveFlashcards(request).getOrThrow()

        // Correction 은 새 카드 최초 저장만 담당하지만, 같은 Room 원본을 SRS 가 읽을 수 있어야 한다.
        // 새 카드는 nextReviewAt=requestedAt 으로 저장되므로 due deck 조회에 바로 포함된다.
        val dueCards = localDataSource.getDueFlashcards(
            uid = "uid-1",
            language = "en",
            now = 1_000L,
            limit = 20
        )

        assertEquals(listOf("s-due"), dueCards.map { it.id })
        assertEquals("나는 산책한다", dueCards.single().frontText)
        assertEquals("I take a walk.", dueCards.single().backText)
    }

    @Test
    fun `review schedule update moves saved flashcard out of due deck`() = runBlocking {
        val request = CorrectionSaveRequest(
            uid = "uid-1",
            lang = LangCode.EN,
            flashcards = listOf(
                CorrectionFlashcardSaveItem(
                    suggestionId = "s-review",
                    frontText = "나는 물을 마신다",
                    backText = "I drink water.",
                    explanation = "Use drink for water."
                )
            ),
            requestedAt = 2_000L
        )

        repository.saveFlashcards(request).getOrThrow()
        val updated = localDataSource.updateReviewSchedule(
            uid = "uid-1",
            flashcardId = "s-review",
            nextReviewAt = 10_000L,
            interval = 3,
            easeFactor = 2.6,
            updatedAt = 2_500L,
            // 이 테스트는 due 대상 제외 여부만 확인하므로 review 평가 메타는 비워 둔다.
            lastReviewRating = null,
            lastReviewedAt = null
        )

        val dueCards = localDataSource.getDueFlashcards(
            uid = "uid-1",
            language = "en",
            now = 2_500L,
            limit = 20
        )

        assertTrue(updated)
        // SRS 가 계산한 nextReviewAt 이 미래로 이동하면 현재 due deck 에서는 제외되어야 한다.
        assertTrue(dueCards.isEmpty())

        val futureDueCards = localDataSource.getDueFlashcards(
            uid = "uid-1",
            language = "en",
            now = 10_000L,
            limit = 20
        )

        // 같은 원본 row에 SRS schedule 값이 반영되고, remote sync 전까지 dirty 상태로 남아야 한다.
        val reviewedCard = futureDueCards.single()
        assertEquals(10_000L, reviewedCard.nextReviewAt)
        assertEquals(3, reviewedCard.interval)
        assertEquals(2.6, reviewedCard.easeFactor, 0.0)
        assertTrue(reviewedCard.dirty)
    }

    @Test
    fun `rollbackFlashcards removes cards saved by the same request`() = runBlocking {
        // 완료 파이프라인 중 LangState 갱신이 실패하면 local 저장은 보상 rollback 되어야 한다.
        // rollback 이후 같은 요청을 다시 저장할 수 있으면 local 상태가 정리된 것으로 본다.
        val request = CorrectionSaveRequest(
            uid = "uid-1",
            lang = LangCode.EN,
            flashcards = listOf(
                CorrectionFlashcardSaveItem(
                    suggestionId = "s-1",
                    frontText = "나는 학교에 간다",
                    backText = "I go to school.",
                    explanation = "go 뒤에는 to school 을 사용한다."
                )
            ),
            requestedAt = 789L
        )

        repository.saveFlashcards(request).getOrThrow()
        val rollback = repository.rollbackFlashcards(request)
        val afterRollback = repository.saveFlashcards(request).getOrThrow()

        assertTrue(rollback.isSuccess)
        assertEquals(listOf("s-1"), afterRollback.localSavedFlashcardIds)
    }

    /**
     * 테스트에서 raw JSON 응답을 직접 주입하기 위한 fake AI client.
     *
     * 실제 [com.app.umma.data.repository.correction.GeminiCorrectionAiClient] 는 Firebase 호출이 필요해 단위 테스트가 어렵다.
     * Repository 가 prompt builder → ai client → mapper 의 모양을 유지하는 한,
     * 이 fake 만으로 happy path / Error 분기를 모두 검증할 수 있다.
     */
    private class FakeCorrectionAiClient : CorrectionAiClient {
        var responseJson: String = """{"suggestions":[]}"""
        var lastPrompt: String? = null

        override suspend fun generateJson(prompt: String): String {
            lastPrompt = prompt
            return responseJson
        }
    }

    private class RecordingCorrectionFlashcardRemoteDataSource :
        CorrectionFlashcardRemoteDataSource {

        var shouldFailSync: Boolean = false
        val syncedIds = mutableListOf<String>()
        val syncedFlashcards = mutableListOf<CorrectionFlashcardDto>()
        val deletedIds = mutableListOf<String>()

        override suspend fun syncFlashcards(
            flashcards: List<CorrectionFlashcardDto>
        ): Result<List<String>> {
            if (shouldFailSync) {
                return Result.failure(IllegalStateException("sync failed"))
            }

            val ids = flashcards.map { it.id }
            syncedIds += ids
            syncedFlashcards += flashcards
            return Result.success(ids)
        }

        override suspend fun deleteFlashcards(flashcardIds: List<String>): Result<Unit> {
            deletedIds += flashcardIds
            return Result.success(Unit)
        }

        override suspend fun syncReviewSchedule(
            flashcardId: String,
            nextReviewAt: Long,
            interval: Int,
            easeFactor: Double,
            updatedAt: Long,
            lastReviewRating: String?,
            lastReviewedAt: Long?
        ): Result<Unit> = Result.success(Unit)
    }
}
