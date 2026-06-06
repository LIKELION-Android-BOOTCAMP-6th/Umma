package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.chat.ChatConversationAnalysisSession
import com.app.umma.domain.model.chat.ChatConversationEvidence
import com.app.umma.domain.model.chat.ChatConversationEvidenceSource
import com.app.umma.domain.model.chat.ConversationConsistencyEvidence
import com.app.umma.domain.model.chat.ConversationSustainabilityEvidence
import com.app.umma.domain.model.chat.LanguageDependenceEvidence
import com.app.umma.domain.model.chat.ResponseDifficultyFitEvidence
import com.app.umma.domain.model.chat.TargetLanguageComprehensionEvidence
import com.app.umma.domain.model.chat.TargetLanguageProductionEvidence
import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.realtime.AppendTurnCommand
import com.app.umma.domain.model.realtime.CompressSessionMemoryCommand
import com.app.umma.domain.model.realtime.SessionMemory
import com.app.umma.domain.model.realtime.SessionTurn
import com.app.umma.domain.model.realtime.SummarizeTopicsCommand
import com.app.umma.domain.model.realtime.TopicSummarySaveResult
import com.app.umma.domain.repository.ChatConversationAnalysisRepository
import com.app.umma.domain.repository.ChatConversationEvidenceRepository
import com.app.umma.domain.repository.SessionMemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzeChatConversationSessionUseCaseTest {

    @Test
    fun `session end analysis uses only persisted turns for the requested session`() = runBlocking {
        val analysisRepository = RecordingAnalysisRepository()
        val evidenceRepository = RecordingEvidenceRepository()
        val useCase = AnalyzeChatConversationSessionUseCase(
            sessionMemoryRepository = MemoryRepository(
                turns = listOf(
                    // 다른 세션의 turn이 recentFullContext에 같이 있어도 현재 세션 evidence에 섞이면 안 된다.
                    turn(sessionId = "old-session", speaker = TurnSpeaker.USER, text = "old"),
                    turn(sessionId = "session-1", speaker = TurnSpeaker.USER, text = "hello", createdAt = 20L),
                    turn(sessionId = "session-1", speaker = TurnSpeaker.AI, text = "Hi, sleep well?", createdAt = 30L)
                )
            ),
            analysisRepository = analysisRepository,
            evidenceRepository = evidenceRepository
        )

        val result = useCase(selectedLang = LangCode.EN, sessionId = "session-1")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow() is ChatConversationSessionAnalysisResult.Saved)
        assertEquals("session-1", analysisRepository.lastSession!!.sessionId)
        assertEquals(listOf("hello", "Hi, sleep well?"), analysisRepository.lastSession!!.turns.map { it.text })
        assertEquals(analysisRepository.evidence, evidenceRepository.savedEvidence)
    }

    @Test
    fun `session end analysis skips when final turns are not enough`() = runBlocking {
        val analysisRepository = RecordingAnalysisRepository()
        val evidenceRepository = RecordingEvidenceRepository()
        val useCase = AnalyzeChatConversationSessionUseCase(
            sessionMemoryRepository = MemoryRepository(
                turns = listOf(
                    // USER 단독 turn은 대화 지속 능력 근거가 부족하므로 Gemini 호출 없이 skip해야 한다.
                    turn(sessionId = "session-1", speaker = TurnSpeaker.USER, text = "hello")
                )
            ),
            analysisRepository = analysisRepository,
            evidenceRepository = evidenceRepository
        )

        val result = useCase(selectedLang = LangCode.EN, sessionId = "session-1")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow() is ChatConversationSessionAnalysisResult.Skipped)
        assertFalse(analysisRepository.called)
        assertEquals(null, evidenceRepository.savedEvidence)
    }

    private class RecordingAnalysisRepository : ChatConversationAnalysisRepository {
        var called = false
        var lastSession: ChatConversationAnalysisSession? = null
        val evidence = ChatConversationEvidence(
            selectedLang = LangCode.EN,
            targetLanguageComprehension = TargetLanguageComprehensionEvidence.SimpleSentence,
            targetLanguageProduction = TargetLanguageProductionEvidence.SimpleSentences,
            supportLanguageDependence = LanguageDependenceEvidence.Low,
            aiScaffoldingDependence = LanguageDependenceEvidence.Low,
            conversationSustainability = ConversationSustainabilityEvidence.SustainedSimple,
            consistency = ConversationConsistencyEvidence.Mixed,
            responseDifficultyFit = ResponseDifficultyFitEvidence.Fits,
            confidence = ProfileConfidence.Medium,
            source = ChatConversationEvidenceSource.GeminiConversationAnalysis,
            sourceSessionId = "session-1",
            debugRecommendedBand = ConversationAbilityBand.SimpleSentence
        )

        override suspend fun analyze(session: ChatConversationAnalysisSession): Result<ChatConversationEvidence> {
            // usecase가 넘긴 session snapshot을 그대로 저장해 필터링/정렬 결과를 검증한다.
            called = true
            lastSession = session
            return Result.success(evidence)
        }
    }

    private class RecordingEvidenceRepository : ChatConversationEvidenceRepository {
        var savedEvidence: ChatConversationEvidence? = null

        override suspend fun getEvidence(selectedLang: LangCode): Result<ChatConversationEvidence?> {
            return Result.success(savedEvidence?.takeIf { it.selectedLang == selectedLang })
        }

        override suspend fun saveEvidence(evidence: ChatConversationEvidence): Result<Unit> {
            // 저장 호출 여부와 저장 대상 evidence를 함께 검증하기 위해 마지막 값을 보관한다.
            savedEvidence = evidence
            return Result.success(Unit)
        }
    }

    private class MemoryRepository(
        private val turns: List<SessionTurn>
    ) : SessionMemoryRepository {
        override suspend fun appendTurn(command: AppendTurnCommand): Result<Unit> = Result.success(Unit)

        override fun observeRecentFullContext(language: LangCode): Flow<List<SessionTurn>> = emptyFlow()

        override suspend fun getSessionMemory(language: LangCode): Result<SessionMemory> {
            return Result.success(
                SessionMemory(
                    userId = "user-1",
                    language = language,
                    recentFullContext = turns,
                    updatedAt = 100L
                )
            )
        }

        override suspend fun compressSessionMemory(command: CompressSessionMemoryCommand): Result<Unit> {
            return Result.success(Unit)
        }

        override fun getCorrectionContext(language: LangCode): Flow<List<SessionTurn>> = emptyFlow()

        override fun getFlashcardContext(language: LangCode): Flow<List<SessionTurn>> = emptyFlow()

        override suspend fun syncPendingTurns(language: LangCode): Result<Unit> = Result.success(Unit)

        override suspend fun summarizeAndSaveTopics(
            command: SummarizeTopicsCommand
        ): Result<TopicSummarySaveResult> {
            return Result.failure(UnsupportedOperationException("topic summary is outside this test"))
        }
    }

    private fun turn(
        sessionId: String,
        speaker: TurnSpeaker,
        text: String,
        createdAt: Long = 10L
    ): SessionTurn {
        return SessionTurn(
            turnId = "$sessionId-${speaker.name}-$createdAt",
            sessionId = sessionId,
            text = text,
            role = speaker,
            createdAt = createdAt
        )
    }
}
