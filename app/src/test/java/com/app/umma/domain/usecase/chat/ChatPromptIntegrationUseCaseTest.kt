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
            buildPromptUseCase = BuildPromptUseCase()
        )

        val result = useCase()

        // StartSessionUseCase 는 selectedLang 으로 transport 세션을 시작해야 한다.
        assertTrue(result.isSuccess)
        assertEquals(LangCode.EN, chatRepository.startedLang)
        // initial LangState 는 low-confidence profile 로 해석되어 primaryLang 보조 설명을 허용한다.
        assertTrue(chatRepository.startedInstruction.contains("Speak primarily in English."))
        assertTrue(chatRepository.startedInstruction.contains("Korean"))
        // 최근 확정 대화 context 는 기존 prompt 정책처럼 유지되어야 한다.
        assertTrue(chatRepository.startedInstruction.contains("Use this confirmed recent conversation context"))
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
            buildPromptUseCase = promptUseCase
        )()

        val retryResult = RetryConnectionUseCase(
            repository = retryRepository,
            learningStateRepo = RecordingLearningStateRepo(userPref = userPref, langState = langState),
            sessionMemoryRepository = sessionMemoryRepository,
            buildLearnerAdaptationProfileUseCase = profileUseCase,
            buildPromptUseCase = promptUseCase
        )()

        // 같은 입력이면 start 와 retry 가 같은 prompt builder 경로를 타야 난이도가 중간에 바뀌지 않는다.
        assertTrue(retryResult is RetryConnectionResult.Reconnected)
        assertEquals(startRepository.startedInstruction, retryRepository.reconnectedInstruction)
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

        override suspend fun startSession(langCode: LangCode, systemInstruction: String): Result<String> {
            // test fake 는 transport 를 열지 않고, domain usecase 가 넘긴 언어와 prompt 만 기록한다.
            activeSessionId = "session-1"
            currentLang = langCode
            startedLang = langCode
            startedInstruction = systemInstruction
            return Result.success(activeSessionId!!)
        }

        override suspend fun reconnectSession(systemInstruction: String): Result<String> {
            // retry 경로에서도 같은 prompt 생성 결과가 들어오는지 비교하기 위해 instruction 을 보관한다.
            reconnectedInstruction = systemInstruction
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
