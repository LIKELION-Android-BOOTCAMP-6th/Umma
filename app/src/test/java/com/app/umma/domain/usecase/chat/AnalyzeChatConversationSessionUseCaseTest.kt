package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.chat.ChatConversationAnalysisSession
import com.app.umma.domain.model.chat.ChatConversationAnalysisJob
import com.app.umma.domain.model.chat.ChatConversationAnalysisJobTurn
import com.app.umma.domain.model.chat.ChatConversationEvidence
import com.app.umma.domain.model.chat.ChatConversationEvidenceSource
import com.app.umma.domain.model.chat.ConversationConsistencyEvidence
import com.app.umma.domain.model.chat.ConversationSustainabilityEvidence
import com.app.umma.domain.model.chat.LanguageDependenceEvidence
import com.app.umma.domain.model.chat.ResponseDifficultyFitEvidence
import com.app.umma.domain.model.chat.TargetLanguageComprehensionEvidence
import com.app.umma.domain.model.chat.TargetLanguageProductionEvidence
import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateInput
import com.app.umma.domain.model.learningstate.CorrectionSignalUpdateResult
import com.app.umma.domain.model.learningstate.DashSummary
import com.app.umma.domain.model.learningstate.FlashcardSummary
import com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateInput
import com.app.umma.domain.model.learningstate.FlashcardSummaryUpdateResult
import com.app.umma.domain.model.learningstate.GlobalLangState
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.LangState
import com.app.umma.domain.model.learningstate.LangStateUpdateInput
import com.app.umma.domain.model.learningstate.LearningSignalSource
import com.app.umma.domain.model.learningstate.LearningStateUpdateResult
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.learningstate.UserLangPref
import com.app.umma.domain.model.realtime.AppendTurnCommand
import com.app.umma.domain.model.realtime.CompressSessionMemoryCommand
import com.app.umma.domain.model.realtime.SessionMemory
import com.app.umma.domain.model.realtime.SessionTurn
import com.app.umma.domain.model.realtime.SummarizeTopicsCommand
import com.app.umma.domain.model.realtime.TopicSummarySaveResult
import com.app.umma.domain.repository.ChatConversationAnalysisRepository
import com.app.umma.domain.repository.ChatConversationAnalysisJobRepository
import com.app.umma.domain.repository.ChatConversationEvidenceRepository
import com.app.umma.domain.repository.AuthRepository
import com.app.umma.domain.repository.LearningStateRepo
import com.app.umma.domain.repository.SessionMemoryRepository
import com.app.umma.domain.usecase.learningstate.ApplyChatSignalUpdateUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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
        val learningStateRepo = RecordingLearningStateRepo()
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
            evidenceRepository = evidenceRepository,
            authRepository = FakeAuthRepository(),
            applyChatSignalUpdateUseCase = ApplyChatSignalUpdateUseCase(learningStateRepo)
        )

        val result = useCase(selectedLang = LangCode.EN, sessionId = "session-1")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow() is ChatConversationSessionAnalysisResult.Saved)
        assertEquals("session-1", analysisRepository.lastSession!!.sessionId)
        assertEquals(listOf("hello", "Hi, sleep well?"), analysisRepository.lastSession!!.turns.map { it.text })
        assertEquals(analysisRepository.evidence, evidenceRepository.savedEvidence)
        assertTrue(learningStateRepo.lastUpdateInput!!.preparedState!!.analysisMeta.metricEvidence.values.any {
            it.sourceTypes.contains(LearningSignalSource.ChatSession)
        })
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
            evidenceRepository = evidenceRepository,
            authRepository = FakeAuthRepository(),
            applyChatSignalUpdateUseCase = ApplyChatSignalUpdateUseCase(RecordingLearningStateRepo())
        )

        val result = useCase(selectedLang = LangCode.EN, sessionId = "session-1")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow() is ChatConversationSessionAnalysisResult.Skipped)
        assertFalse(analysisRepository.called)
        assertEquals(null, evidenceRepository.savedEvidence)
    }

    @Test
    fun `pending analysis retry completes job after LangState update`() = runBlocking {
        val analysisRepository = RecordingAnalysisRepository()
        val jobRepository = RecordingAnalysisJobRepository(
            jobs = listOf(
                ChatConversationAnalysisJob(
                    userId = "user-1",
                    selectedLang = LangCode.EN,
                    sessionId = "session-1",
                    finalTurnCount = 2,
                    turns = listOf(
                        ChatConversationAnalysisJobTurn(
                            speaker = TurnSpeaker.USER,
                            text = "hello from snapshot",
                            createdAt = 20L,
                            tokenCount = 3,
                            durationMs = 1_000L,
                            confidence = 0.9
                        ),
                        ChatConversationAnalysisJobTurn(
                            speaker = TurnSpeaker.AI,
                            text = "Hi",
                            createdAt = 30L
                        )
                    ),
                    createdAt = 1_000L
                )
            )
        )
        val analyzeUseCase = AnalyzeChatConversationSessionUseCase(
            sessionMemoryRepository = MemoryRepository(
                // retry는 저장된 job snapshot만 사용해야 한다. SessionMemory가 비어 있어도 분석이 가능해야 한다.
                turns = emptyList()
            ),
            analysisRepository = analysisRepository,
            evidenceRepository = RecordingEvidenceRepository(),
            authRepository = FakeAuthRepository(),
            applyChatSignalUpdateUseCase = ApplyChatSignalUpdateUseCase(RecordingLearningStateRepo())
        )
        val retryUseCase = SyncPendingChatConversationAnalysisJobsUseCase(
            repository = jobRepository,
            analyzeChatConversationSessionUseCase = analyzeUseCase
        )

        val completedCount = retryUseCase(userId = "user-1").getOrThrow()

        // Gemini 분석과 LangState update가 끝난 job은 다음 Chat 진입에서 반복 실행되지 않게 제거한다.
        assertEquals(1, completedCount)
        assertEquals(1, jobRepository.attemptedJobs.size)
        assertEquals(listOf("session-1"), jobRepository.completedSessionIds)
        assertEquals(listOf("hello from snapshot", "Hi"), analysisRepository.lastSession!!.turns.map { it.text })
    }

    private class FakeAuthRepository : AuthRepository {
        // LangState update 입력 생성은 현재 로그인 uid가 있어야만 가능하므로 고정 uid를 제공한다.
        override val currentUserUid: Flow<String?> = flowOf("user-1")

        override suspend fun signInWithGoogle(idToken: String): Result<String> = Result.success("user-1")

        override fun getCurrentUserUid(): String = "user-1"

        override fun getCurrentUserEmail(): String? = null

        override suspend fun signOut(): Result<Unit> = Result.success(Unit)

        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)

        override suspend fun hasValidSession(): Result<Boolean> = Result.success(true)
    }

    private class RecordingLearningStateRepo : LearningStateRepo {
        // ApplyChatSignalUpdateUseCase가 실제 repository처럼 이전 LangState를 읽고 preparedState를 저장하도록 상태를 보관한다.
        private var currentState = LangState.initial(LangCode.EN, createdAt = 1L, updatedAt = 1L)
        // 분석 usecase가 Chat source evidence를 LangState update 경로로 넘겼는지 확인하기 위한 마지막 입력이다.
        var lastUpdateInput: LangStateUpdateInput? = null

        override fun observeLearningState(): Flow<GlobalLangState> = flowOf(GlobalLangState.initial())

        override fun observeUserPref(): Flow<UserLangPref?> = flowOf(null)

        override fun observeLangState(lang: LangCode): Flow<LangState?> = flowOf(currentState.takeIf { it.lang == lang })

        override fun observeDashSummary(lang: LangCode): Flow<DashSummary?> = flowOf(DashSummary.initial(lang))

        override fun observeSessionSummary(lang: LangCode): Flow<SessionSummary?> = flowOf(SessionSummary.initial(lang))

        override fun observeFlashcardSummary(lang: LangCode): Flow<FlashcardSummary?> = flowOf(FlashcardSummary.initial(lang))

        override suspend fun preload(): Result<Unit> = Result.success(Unit)

        override suspend fun changeSelectedLang(lang: LangCode): Result<Unit> = Result.success(Unit)

        override suspend fun updateLanguageState(
            input: LangStateUpdateInput
        ): Result<LearningStateUpdateResult> {
            // preparedState를 저장해 두어 다음 observeLangState 호출이 중복 방어 상태를 볼 수 있게 한다.
            lastUpdateInput = input
            currentState = input.preparedState!!
            return Result.success(
                LearningStateUpdateResult(
                    lang = input.lang,
                    savedState = currentState,
                    sourceEventId = input.analysisEventId ?: "none",
                    applied = true,
                    updatedAt = input.analyzedAt
                )
            )
        }

        override suspend fun updateFlashcardSummary(
            input: FlashcardSummaryUpdateInput
        ): Result<FlashcardSummaryUpdateResult> {
            return Result.failure(UnsupportedOperationException("flashcard summary is outside this test"))
        }

        override suspend fun updateCorrectionSignal(
            input: CorrectionSignalUpdateInput
        ): Result<CorrectionSignalUpdateResult> {
            return Result.failure(UnsupportedOperationException("correction signal is outside this test"))
        }

        override suspend fun createInitial(
            userUid: String,
            userPref: UserLangPref,
            langState: LangState,
            dashSummary: DashSummary,
            sessionSummary: SessionSummary,
            flashcardSummary: FlashcardSummary
        ): Result<Unit> = Result.success(Unit)

        override suspend fun clear(): Result<Unit> = Result.success(Unit)

        override suspend fun sync(): Result<Unit> = Result.success(Unit)
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

    private class RecordingAnalysisJobRepository(
        jobs: List<ChatConversationAnalysisJob>
    ) : ChatConversationAnalysisJobRepository {
        // in-memory queue로 retry 흐름만 재현한다. DataStore 직렬화는 별도 repository test가 담당한다.
        private val pendingJobs = jobs.toMutableList()
        // markAttempted 호출 여부는 retry가 실패 관찰 metadata를 남기는지 검증하기 위한 기록이다.
        val attemptedJobs = mutableListOf<ChatConversationAnalysisJob>()
        // 완료된 session id를 기록해 LangState 반영 성공 후 queue에서 제거되는지 확인한다.
        val completedSessionIds = mutableListOf<String>()

        override suspend fun enqueue(job: ChatConversationAnalysisJob): Result<Unit> {
            pendingJobs.removeAll { it.key == job.key }
            pendingJobs += job
            return Result.success(Unit)
        }

        override suspend fun getPendingJobs(
            userId: String,
            limit: Int
        ): Result<List<ChatConversationAnalysisJob>> {
            // 실제 repository와 동일하게 현재 사용자 job만 반환해 계정 간 pending 분석 혼선을 막는다.
            return Result.success(pendingJobs.filter { it.userId == userId }.take(limit))
        }

        override suspend fun markAttempted(
            job: ChatConversationAnalysisJob,
            attemptedAt: Long
        ): Result<Unit> {
            attemptedJobs += job
            return Result.success(Unit)
        }

        override suspend fun markCompleted(
            userId: String,
            selectedLang: LangCode,
            sessionId: String
        ): Result<Unit> {
            pendingJobs.removeAll {
                it.userId == userId && it.selectedLang == selectedLang && it.sessionId == sessionId
            }
            completedSessionIds += sessionId
            return Result.success(Unit)
        }

        override suspend fun clearAll(): Result<Unit> {
            pendingJobs.clear()
            return Result.success(Unit)
        }
    }

    private class MemoryRepository(
        private val turns: List<SessionTurn>
    ) : SessionMemoryRepository {
        override suspend fun appendTurn(command: AppendTurnCommand): Result<Unit> = Result.success(Unit)

        override fun observeRecentFullContext(language: LangCode): Flow<List<SessionTurn>> = emptyFlow()

        override suspend fun getSessionMemory(language: LangCode): Result<SessionMemory> {
            // 즉시 분석 경로는 저장 완료된 SessionMemory turn만 source로 삼는다.
            // pending retry 테스트에서는 turns를 비워 snapshot만 사용되는지 검증한다.
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
