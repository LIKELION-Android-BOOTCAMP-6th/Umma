package com.app.umma.domain.usecase.chat

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
import com.app.umma.domain.model.learningstate.SessionSummary
import com.app.umma.domain.model.learningstate.TurnSpeaker
import com.app.umma.domain.model.learningstate.UserLangPref
import com.app.umma.domain.model.realtime.AIEvent
import com.app.umma.domain.model.realtime.AppendTurnCommand
import com.app.umma.domain.model.realtime.ChatResponseOverrideProvider
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
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPromptIntegrationUseCaseTest {

    @Test
    fun `start session builds prompt from primary selected language and selected lang state`() = runBlocking {
        val chatRepository = RecordingChatRepository()
        val learningStateRepo = RecordingLearningStateRepo(
            userPref = UserLangPref.initial(
                primaryLang = LangCode.KO,
                selectedLang = LangCode.EN
            ),
            langState = LangState.initial(LangCode.EN)
        )
        val sessionMemoryRepository = RecordingSessionMemoryRepository(
            memory = memory()
        )
        val useCase = StartSessionUseCase(
            repository = chatRepository,
            learningStateRepo = learningStateRepo,
            sessionMemoryRepository = sessionMemoryRepository,
            buildLearnerAdaptationProfileUseCase = BuildLearnerAdaptationProfileUseCase(),
            buildChatTurnAdaptationPolicyUseCase = BuildChatTurnAdaptationPolicyUseCase(),
            buildChatSpeechSpeedUseCase = BuildChatSpeechSpeedUseCase(),
            buildPromptUseCase = BuildPromptUseCase()
        )

        val result = useCase()

        // StartSessionUseCase 는 selectedLang 으로 transport 세션을 시작해야 한다.
        assertTrue(result.isSuccess)
        assertEquals(LangCode.EN, chatRepository.startedLang)
        // 초기 low-confidence profile 은 첫 발화가 아주 낮은 수준일 수 있어 가장 안전한 속도로 시작해야 한다.
        assertEquals(0.8, chatRepository.startedOutputAudioSpeed!!, 0.0)
        // initial LangState 는 meaningful LangState 가 아니므로 첫 selectedLang fallback 정책으로 시작한다.
        assertTrue(chatRepository.startedInstruction.contains("- target: 영어"))
        assertTrue(chatRepository.startedInstruction.contains("- support: 한국어"))
        assertTrue(chatRepository.startedInstruction.contains("저장된 근거가 적어도 현재 발화가 이어질 수 있게 이해 가능한 반응을 우선한다."))
        assertTrue(chatRepository.startedInstruction.contains("불완전한 말에서도 사용자의 의도를 먼저 추론하고 대화를 이어간다."))
        // Chat prompt 는 단순 답변 AI 가 아니라 초보도 이해 가능한 반응을 받아 다음 말을 이어갈 수 있어야 한다.
        assertTrue(chatRepository.startedInstruction.contains("fragment: 뜻만 있는 단어 조각이면 기준언어(한국어)로 의미를 먼저 잡고 영어 핵심 표현 하나를 자연스럽게 붙인다."))
        assertTrue(chatRepository.startedInstruction.contains("필요할 때 음식, 장소, 감정, 행동처럼 실제 내용으로 짧게 답할 여지를 준다."))
        assertTrue(chatRepository.startedInstruction.contains("첫 발화가 조각나도 천천히 말하며 한 가지 의미씩 이해하게 한다."))
        // 최근 확정 대화 context 는 기존 prompt 정책처럼 유지되어야 한다.
        assertTrue(chatRepository.startedInstruction.contains("- USER: hello"))
        assertTrue(chatRepository.startedInstruction.contains("최근 맥락은 주제 이해에만 쓰고"))
        // USER final transcript 이후 response.create 전용 override를 만들 provider가 transport에 전달되어야 한다.
        val override = chatRepository.startedResponseOverrideProvider!!.build("I apple hungry")
            ?: error("response override should be created for a fragment user turn")
        // initial profile의 기본 초급 정책과 같은 turn이면 같은 "천천히/쉽게" 지시를 반복하지 않는다.
        assertEquals(null, override.responseInstructions)
        assertEquals(0.8, override.outputAudioSpeed!!, 0.0)
    }

    @Test
    fun `retry connection uses the same profile prompt path as start session`() = runBlocking {
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
            buildChatTurnAdaptationPolicyUseCase = BuildChatTurnAdaptationPolicyUseCase(),
            buildChatSpeechSpeedUseCase = BuildChatSpeechSpeedUseCase(),
            buildPromptUseCase = promptUseCase
        )()

        val retryResult = RetryConnectionUseCase(
            repository = retryRepository,
            learningStateRepo = RecordingLearningStateRepo(userPref = userPref, langState = langState),
            sessionMemoryRepository = sessionMemoryRepository,
            buildLearnerAdaptationProfileUseCase = profileUseCase,
            buildChatTurnAdaptationPolicyUseCase = BuildChatTurnAdaptationPolicyUseCase(),
            buildChatSpeechSpeedUseCase = BuildChatSpeechSpeedUseCase(),
            buildPromptUseCase = promptUseCase
        )()

        // 같은 입력이면 start 와 retry 가 같은 prompt builder 경로를 타야 난이도가 중간에 바뀌지 않는다.
        assertTrue(retryResult is RetryConnectionResult.Reconnected)
        assertEquals(startRepository.startedInstruction, retryRepository.reconnectedInstruction)
        assertEquals(startRepository.startedOutputAudioSpeed!!, retryRepository.reconnectedOutputAudioSpeed!!, 0.0)
        val startOverride = startRepository.startedResponseOverrideProvider!!.build("I apple hungry")
            ?: error("start response override should be created")
        val retryOverride = retryRepository.reconnectedResponseOverrideProvider!!.build("I apple hungry")
            ?: error("retry response override should be created")
        assertEquals(startOverride.responseInstructions, retryOverride.responseInstructions)
        assertEquals(startOverride.outputAudioSpeed!!, retryOverride.outputAudioSpeed!!, 0.0)
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
            buildChatTurnAdaptationPolicyUseCase = BuildChatTurnAdaptationPolicyUseCase(),
            buildChatSpeechSpeedUseCase = BuildChatSpeechSpeedUseCase(),
            buildPromptUseCase = BuildPromptUseCase()
        )()

        // 세션 언어가 바뀐 경우에는 prompt 를 새로 보내 재연결하지 않고 새 세션 요구를 유지한다.
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
        var startedResponseOverrideProvider: ChatResponseOverrideProvider? = null
        var reconnectedResponseOverrideProvider: ChatResponseOverrideProvider? = null

        override suspend fun startSession(
            langCode: LangCode,
            systemInstruction: String,
            outputAudioSpeed: Double,
            responseOverrideProvider: ChatResponseOverrideProvider?
        ): Result<String> {
            // test fake 는 transport 를 열지 않고, domain usecase 가 넘긴 언어와 prompt 만 기록한다.
            activeSessionId = "session-1"
            currentLang = langCode
            startedLang = langCode
            startedInstruction = systemInstruction
            startedOutputAudioSpeed = outputAudioSpeed
            startedResponseOverrideProvider = responseOverrideProvider
            return Result.success(activeSessionId!!)
        }

        override suspend fun reconnectSession(
            systemInstruction: String,
            outputAudioSpeed: Double,
            responseOverrideProvider: ChatResponseOverrideProvider?
        ): Result<String> {
            // retry 경로에서도 같은 prompt/속도 생성 결과가 들어오는지 비교하기 위해 값을 보관한다.
            reconnectedInstruction = systemInstruction
            reconnectedOutputAudioSpeed = outputAudioSpeed
            reconnectedResponseOverrideProvider = responseOverrideProvider
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
            // selectedLang 기준 상태만 반환해 start/retry 가 올바른 언어를 요청하는지 확인한다.
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
            // Start/Retry 모두 selectedLang 기준 memory 를 읽어 prompt 에 같은 context 를 넣어야 한다.
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

    private fun memory(): SessionMemory {
        // 이 테스트는 selectedLang 이 EN 인 대표 경로만 검증하므로 fixture 도 EN 으로 고정한다.
        return SessionMemory(
            userId = "user-1",
            language = LangCode.EN,
            recentFullContext = listOf(
                SessionTurn(
                    turnId = "turn-1",
                    sessionId = "session-1",
                    text = "hello",
                    role = TurnSpeaker.USER,
                    createdAt = 1_000L
                )
            ),
            updatedAt = 1_000L
        )
    }
}
