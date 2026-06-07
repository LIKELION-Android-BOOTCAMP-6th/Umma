package com.app.umma.domain.usecase.chat

import com.app.umma.domain.model.chat.ConversationConsistencyEvidence
import com.app.umma.domain.model.chat.ConversationSustainabilityEvidence
import com.app.umma.domain.model.chat.LanguageDependenceEvidence
import com.app.umma.domain.model.chat.ResponseDifficultyFitEvidence
import com.app.umma.domain.model.chat.TargetLanguageComprehensionEvidence
import com.app.umma.domain.model.chat.TargetLanguageProductionEvidence
import com.app.umma.domain.model.learningstate.ChatEvidenceSummary
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
import com.app.umma.domain.model.learningstate.LearningStateUpdateResult
import com.app.umma.domain.model.learningstate.ProfileConfidence
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.learningstate.UserLangPref
import com.app.umma.domain.model.realtime.AIEvent
import com.app.umma.domain.model.realtime.AppendTurnCommand
import com.app.umma.domain.model.realtime.CompressSessionMemoryCommand
import com.app.umma.domain.model.realtime.SessionMemory
import com.app.umma.domain.model.realtime.SessionTurn
import com.app.umma.domain.model.realtime.SummarizeTopicsCommand
import com.app.umma.domain.model.realtime.TopicSummarySaveResult
import com.app.umma.domain.repository.ChatRepository
import com.app.umma.domain.repository.LearningStateRepo
import com.app.umma.domain.repository.SessionMemoryRepository
import com.app.umma.domain.usecase.learningstate.BuildLearnerAdaptationProfileUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPromptIntegrationUseCaseTest {

    @Test
    fun `start session builds simplified prompt from selected language state`() = runBlocking {
        val chatRepository = RecordingChatRepository()
        val useCase = StartSessionUseCase(
            repository = chatRepository,
            learningStateRepo = RecordingLearningStateRepo(
                userPref = UserLangPref.initial(
                    primaryLang = LangCode.KO,
                    selectedLang = LangCode.EN
                ),
                langState = LangState.initial(LangCode.EN)
            ),
            sessionMemoryRepository = RecordingSessionMemoryRepository(memory = memory()),
            buildLearnerAdaptationProfileUseCase = BuildLearnerAdaptationProfileUseCase(),
            buildChatSpeechSpeedUseCase = BuildChatSpeechSpeedUseCase(),
            buildPromptUseCase = BuildPromptUseCase()
        )

        val result = useCase()

        // StartSessionUseCase는 selectedLang으로 transport 세션을 시작하고, 첫 세션 안전 속도를 적용한다.
        assertTrue(result.isSuccess)
        assertEquals(LangCode.EN, chatRepository.startedLang)
        assertEquals(0.8, chatRepository.startedOutputAudioSpeed!!, 0.0)

        // prompt는 새 단순 섹션과 언어쌍만 포함하고, 옛 branches/turn override 구조를 만들지 않는다.
        assertTrue(chatRepository.startedInstruction.contains("persona:"))
        assertTrue(chatRepository.startedInstruction.contains("language_use:"))
        assertTrue(chatRepository.startedInstruction.contains("conversation_principles:"))
        assertTrue(chatRepository.startedInstruction.contains("current_style:"))
        assertTrue(chatRepository.startedInstruction.contains("style_reference:"))
        assertTrue(chatRepository.startedInstruction.contains("learning_language: 영어"))
        assertTrue(chatRepository.startedInstruction.contains("support_language: 한국어"))
        assertTrue(chatRepository.startedInstruction.contains("- USER: hello"))
        assertFalse(chatRepository.startedInstruction.contains("branches:"))
        assertFalse(chatRepository.startedInstruction.contains("current_turn_override:"))

        // trace는 prompt 전문 없이 현재 버전과 섹션만 남겨 Logcat/리뷰 도구에서 구조를 확인하게 한다.
        assertTrue(chatRepository.startedPromptTrace!!.contains("promptVersion=chat_prompt_v2"))
        assertTrue(chatRepository.startedPromptTrace!!.contains("sections=persona,language_use,conversation_principles,current_style,style_reference,context"))
        assertTrue(chatRepository.startedPromptTrace!!.contains("conversationEvidence={applied=false,band=IntentOnly,source=none}"))
    }

    @Test
    fun `start session calculates chat band from LangState chat evidence summary`() = runBlocking {
        val chatRepository = RecordingChatRepository()
        val summary = ChatEvidenceSummary(
            // 사용자가 연결 발화를 안정적으로 만들었다는 근거를 주면 domain policy가 ConnectedExpression으로 계산한다.
            targetLanguageComprehension = TargetLanguageComprehensionEvidence.NaturalFlow,
            targetLanguageProduction = TargetLanguageProductionEvidence.ConnectedTurns,
            supportLanguageDependence = LanguageDependenceEvidence.None,
            aiScaffoldingDependence = LanguageDependenceEvidence.Low,
            conversationSustainability = ConversationSustainabilityEvidence.SustainedNatural,
            consistency = ConversationConsistencyEvidence.Stable,
            responseDifficultyFit = ResponseDifficultyFitEvidence.Fits,
            confidence = ProfileConfidence.High,
            observedCount = 2,
            lastObservedAt = 10_000L
        )

        val result = StartSessionUseCase(
            repository = chatRepository,
            learningStateRepo = RecordingLearningStateRepo(
                userPref = UserLangPref.initial(
                    primaryLang = LangCode.KO,
                    selectedLang = LangCode.EN
                ),
                langState = LangState.initial(LangCode.EN).copy(
                    analysisMeta = LangState.initial(LangCode.EN).analysisMeta.copy(
                        chatEvidenceSummary = summary
                    )
                )
            ),
            sessionMemoryRepository = RecordingSessionMemoryRepository(memory = memory()),
            buildLearnerAdaptationProfileUseCase = BuildLearnerAdaptationProfileUseCase(),
            buildChatSpeechSpeedUseCase = BuildChatSpeechSpeedUseCase(),
            buildPromptUseCase = BuildPromptUseCase()
        )()

        // 공식 Chat band source는 Firestore snapshot이 아니라 LangState의 chatEvidenceSummary다.
        assertTrue(result.isSuccess)
        assertTrue(chatRepository.startedPromptTrace!!.contains("conversationEvidence={applied=true,band=ConnectedExpression,source=LangStateSummary}"))
        assertTrue(chatRepository.startedInstruction.contains("생각, 이유, 상황을 어느 정도 이어 말할 수 있다."))
        assertTrue(chatRepository.startedInstruction.contains("자연스러운 영어 대화 흐름을 유지하고"))
        assertEquals(1.05, chatRepository.startedOutputAudioSpeed!!, 0.0)
    }

    @Test
    fun `retry connection uses the same simplified prompt path as start session`() = runBlocking {
        val userPref = UserLangPref.initial(
            primaryLang = LangCode.KO,
            selectedLang = LangCode.EN
        )
        val langState = LangState.initial(LangCode.EN)
        val sessionMemoryRepository = RecordingSessionMemoryRepository(memory = memory())
        val profileUseCase = BuildLearnerAdaptationProfileUseCase()
        val promptUseCase = BuildPromptUseCase()
        val startRepository = RecordingChatRepository()
        val retryRepository = RecordingChatRepository(
            activeSessionId = "session-1",
            currentLang = LangCode.EN
        )

        StartSessionUseCase(
            repository = startRepository,
            learningStateRepo = RecordingLearningStateRepo(userPref = userPref, langState = langState),
            sessionMemoryRepository = sessionMemoryRepository,
            buildLearnerAdaptationProfileUseCase = profileUseCase,
            buildChatSpeechSpeedUseCase = BuildChatSpeechSpeedUseCase(),
            buildPromptUseCase = promptUseCase
        )()

        val retryResult = RetryConnectionUseCase(
            repository = retryRepository,
            learningStateRepo = RecordingLearningStateRepo(userPref = userPref, langState = langState),
            sessionMemoryRepository = sessionMemoryRepository,
            buildLearnerAdaptationProfileUseCase = profileUseCase,
            buildChatSpeechSpeedUseCase = BuildChatSpeechSpeedUseCase(),
            buildPromptUseCase = promptUseCase
        )()

        // 같은 입력이면 start와 retry가 같은 prompt/속도 경로를 타야 재연결 후 대화 스타일이 갑자기 바뀌지 않는다.
        // 단, conversation evidence trace는 1차에서 StartSessionUseCase에만 붙고 retry에는 적용하지 않는다.
        assertTrue(retryResult is RetryConnectionResult.Reconnected)
        assertEquals(startRepository.startedInstruction, retryRepository.reconnectedInstruction)
        assertEquals(startRepository.startedOutputAudioSpeed!!, retryRepository.reconnectedOutputAudioSpeed!!, 0.0)
        assertTrue(startRepository.startedPromptTrace!!.contains("conversationEvidence="))
        assertFalse(retryRepository.reconnectedPromptTrace!!.contains("conversationEvidence="))
    }

    @Test
    fun `retry requires new session when active session language differs from selected language`() = runBlocking {
        val retryRepository = RecordingChatRepository(
            activeSessionId = "session-1",
            currentLang = LangCode.JA
        )

        val result = RetryConnectionUseCase(
            repository = retryRepository,
            learningStateRepo = RecordingLearningStateRepo(
                userPref = UserLangPref.initial(
                    primaryLang = LangCode.KO,
                    selectedLang = LangCode.EN
                ),
                langState = LangState.initial(LangCode.EN)
            ),
            sessionMemoryRepository = RecordingSessionMemoryRepository(memory = memory()),
            buildLearnerAdaptationProfileUseCase = BuildLearnerAdaptationProfileUseCase(),
            buildChatSpeechSpeedUseCase = BuildChatSpeechSpeedUseCase(),
            buildPromptUseCase = BuildPromptUseCase()
        )()

        // 세션 언어가 바뀐 경우에는 prompt를 새로 보내 재연결하지 않고 새 세션 요구를 유지한다.
        assertEquals(
            RetryConnectionResult.RequireNewSession(
                reason = NewSessionReason.LANG_CHANGED,
                targetLang = LangCode.EN
            ),
            result
        )
    }

    private class RecordingChatRepository(
        private var activeSessionId: String? = null,
        private var currentLang: LangCode? = null
    ) : ChatRepository {
        lateinit var startedInstruction: String
        lateinit var reconnectedInstruction: String
        var startedLang: LangCode? = null
        var startedOutputAudioSpeed: Double? = null
        var reconnectedOutputAudioSpeed: Double? = null
        var startedPromptTrace: String? = null
        var reconnectedPromptTrace: String? = null

        override suspend fun startSession(
            langCode: LangCode,
            systemInstruction: String,
            outputAudioSpeed: Double,
            systemInstructionDebugTrace: String?
        ): Result<String> {
            // test fake는 transport를 열지 않고, domain usecase가 넘긴 언어와 prompt만 기록한다.
            activeSessionId = "session-1"
            currentLang = langCode
            startedLang = langCode
            startedInstruction = systemInstruction
            startedOutputAudioSpeed = outputAudioSpeed
            startedPromptTrace = systemInstructionDebugTrace
            return Result.success(activeSessionId!!)
        }

        override suspend fun reconnectSession(
            systemInstruction: String,
            outputAudioSpeed: Double,
            systemInstructionDebugTrace: String?
        ): Result<String> {
            // retry 경로에서도 같은 prompt/속도 생성 결과가 들어오는지 비교하기 위해 값을 보관한다.
            reconnectedInstruction = systemInstruction
            reconnectedOutputAudioSpeed = outputAudioSpeed
            reconnectedPromptTrace = systemInstructionDebugTrace
            return Result.success(activeSessionId ?: "session-1")
        }

        override fun getActiveSessionId(): String? = activeSessionId

        override fun getCurrentSessionLang(): LangCode? = currentLang

        override suspend fun sendAudioData(audio: ByteArray) = Unit

        override fun endUserTurn(durationMs: Long?) = Unit

        override fun cancelPendingUserTurn() = Unit

        override suspend fun sendTextData(text: String) = Unit

        override fun observeAIEvent(): Flow<AIEvent> = emptyFlow()

        override suspend fun stopSession(clearAppSession: Boolean) = Unit
    }

    private class RecordingLearningStateRepo(
        private val userPref: UserLangPref,
        private val langState: LangState?
    ) : LearningStateRepo {
        override fun observeLearningState(): Flow<GlobalLangState> = flowOf(GlobalLangState.initial())

        override fun observeUserPref(): Flow<UserLangPref?> = flowOf(userPref)

        override fun observeLangState(lang: LangCode): Flow<LangState?> {
            // selectedLang 기준 상태만 반환해 start/retry가 올바른 언어를 요청하는지 확인한다.
            return flowOf(if (lang == userPref.selectedLang) langState else null)
        }

        override fun observeDashSummary(lang: LangCode): Flow<DashSummary?> = flowOf(null)

        override fun observeSessionSummary(lang: LangCode): Flow<SessionSummary?> = flowOf(null)

        override fun observeFlashcardSummary(lang: LangCode): Flow<FlashcardSummary?> = flowOf(null)

        override suspend fun preload(): Result<Unit> = Result.success(Unit)

        override suspend fun changeSelectedLang(lang: LangCode): Result<Unit> = Result.success(Unit)

        override suspend fun updateLanguageState(input: LangStateUpdateInput): Result<LearningStateUpdateResult> {
            return Result.failure(UnsupportedOperationException("not used in chat prompt integration tests"))
        }

        override suspend fun updateFlashcardSummary(
            input: FlashcardSummaryUpdateInput
        ): Result<FlashcardSummaryUpdateResult> {
            return Result.failure(UnsupportedOperationException("not used in chat prompt integration tests"))
        }

        override suspend fun updateCorrectionSignal(
            input: CorrectionSignalUpdateInput
        ): Result<CorrectionSignalUpdateResult> {
            return Result.failure(UnsupportedOperationException("not used in chat prompt integration tests"))
        }

        override suspend fun createInitial(
            userUid: String,
            userPref: UserLangPref,
            langState: LangState,
            dashSummary: DashSummary,
            sessionSummary: SessionSummary,
            flashcardSummary: FlashcardSummary
        ): Result<Unit> = Result.failure(UnsupportedOperationException("not used in chat prompt integration tests"))

        override suspend fun clear(): Result<Unit> = Result.success(Unit)

        override suspend fun sync(): Result<Unit> = Result.success(Unit)
    }

    private class RecordingSessionMemoryRepository(
        private val memory: SessionMemory
    ) : SessionMemoryRepository {
        override suspend fun appendTurn(command: AppendTurnCommand): Result<Unit> = Result.success(Unit)

        override fun observeRecentFullContext(language: LangCode): Flow<List<SessionTurn>> = flowOf(memory.recentFullContext)

        override suspend fun getSessionMemory(language: LangCode): Result<SessionMemory> {
            // Start/Retry 모두 selectedLang 기준 memory를 읽어 prompt에 같은 context를 넣어야 한다.
            return if (language == memory.language) {
                Result.success(memory)
            } else {
                Result.failure(IllegalArgumentException("unexpected language: $language"))
            }
        }

        override suspend fun compressSessionMemory(command: CompressSessionMemoryCommand): Result<Unit> = Result.success(Unit)

        override fun getCorrectionContext(language: LangCode): Flow<List<SessionTurn>> = flowOf(memory.recentFullContext)

        override fun getFlashcardContext(language: LangCode): Flow<List<SessionTurn>> = flowOf(memory.recentFullContext)

        override suspend fun syncPendingTurns(language: LangCode): Result<Unit> = Result.success(Unit)

        override suspend fun summarizeAndSaveTopics(command: SummarizeTopicsCommand): Result<TopicSummarySaveResult> {
            return Result.success(TopicSummarySaveResult(applied = false, displayTitle = null))
        }
    }

    private fun memory(
        language: LangCode = LangCode.EN,
        recentFullContext: List<SessionTurn> = listOf(
            // prompt context에 들어갈 최소 확정 turn만 둔다. session id/time은 통합 테스트의 관심사가 아니다.
            SessionTurn(
                turnId = "turn-1",
                sessionId = "session-1",
                text = "hello",
                role = TurnSpeaker.USER,
                createdAt = 1_000L
            )
        )
    ): SessionMemory {
        // 이 테스트는 selectedLang이 EN인 대표 경로만 검증하므로 fixture도 EN으로 고정한다.
        return SessionMemory(
            userId = "user-1",
            language = language,
            recentFullContext = recentFullContext,
            updatedAt = 1_000L
        )
    }

}
