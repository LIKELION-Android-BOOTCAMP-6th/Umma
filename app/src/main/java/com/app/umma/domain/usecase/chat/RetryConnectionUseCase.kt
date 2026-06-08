package com.app.umma.domain.usecase.chat

import com.app.umma.domain.repository.ChatRepository
import com.app.umma.domain.repository.LearningStateRepo
import com.app.umma.domain.repository.SessionMemoryRepository
import com.app.umma.domain.usecase.auth.GetCurrentUserUidUseCase
import com.app.umma.domain.usecase.learningstate.BuildLearnerAdaptationProfileUseCase
import com.app.umma.domain.usecase.user.GetUserProfileUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 기존 앱 세션을 기준으로 realtime transport 재연결을 시도한다.
 */
class RetryConnectionUseCase @Inject constructor(
    private val repository: ChatRepository,
    private val learningStateRepo: LearningStateRepo,
    private val sessionMemoryRepository: SessionMemoryRepository,
    private val getCurrentUserUidUseCase: GetCurrentUserUidUseCase,
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val buildLearnerAdaptationProfileUseCase: BuildLearnerAdaptationProfileUseCase,
    private val buildChatSpeechSpeedUseCase: BuildChatSpeechSpeedUseCase,
    private val buildPromptUseCase: BuildPromptUseCase,
    private val buildChatTranscriptionPromptUseCase: BuildChatTranscriptionPromptUseCase
) {
    /**
     * 최신 학습 상태와 세션 메모리를 반영해 기존 세션 복원을 시도한다.
     */
    suspend operator fun invoke(): RetryConnectionResult {
        val activeSessionId = repository.getActiveSessionId()
            ?: return RetryConnectionResult.RequireNewSession(
                reason = NewSessionReason.NO_ACTIVE_SESSION
            )

        learningStateRepo.preload()

        val userPref = learningStateRepo.observeUserPref().firstOrNull()
            ?: learningStateRepo.sync()
                .getOrNull()
                .let { learningStateRepo.observeUserPref().firstOrNull() }
            ?: return RetryConnectionResult.RequireNewSession(
                reason = NewSessionReason.RESTORE_UNAVAILABLE
            )

        val currentSessionLang = repository.getCurrentSessionLang()
        if (currentSessionLang != null && currentSessionLang != userPref.selectedLang) {
            return RetryConnectionResult.RequireNewSession(
                reason = NewSessionReason.LANG_CHANGED,
                targetLang = userPref.selectedLang
            )
        }

        val langState = learningStateRepo.observeLangState(userPref.selectedLang).firstOrNull()
        val profile = buildLearnerAdaptationProfileUseCase(langState)
        val outputAudioSpeed = buildChatSpeechSpeedUseCase(profile)

        val sessionMemory = sessionMemoryRepository
            .getSessionMemory(userPref.selectedLang)
            .fold(
                onSuccess = { memory ->
                    memory
                },
                onFailure = {
                    return RetryConnectionResult.RequireNewSession(
                        reason = NewSessionReason.RESTORE_UNAVAILABLE,
                        targetLang = userPref.selectedLang
                    )
                }
            )
        val recentFullContext = sessionMemory.recentFullContext
        // 재연결도 start와 같은 prompt 입력을 써야 화면 복귀 후 주제 리드 방식이 갑자기 달라지지 않는다.
        val recentTopicSummaries = sessionMemory.topicSummaries
        val interestTopics = loadInterestTopics()

        val prompt = runCatching {
            buildPromptUseCase(
                profile = profile,
                primaryLang = userPref.primaryLang,
                selectedLang = userPref.selectedLang,
                recentFullContext = recentFullContext,
                recentTopicSummaries = recentTopicSummaries,
                interestTopics = interestTopics
            )
        }.getOrElse {
            return RetryConnectionResult.RequireNewSession(
                reason = NewSessionReason.RESTORE_UNAVAILABLE,
                targetLang = userPref.selectedLang
            )
        }
        val promptTrace = buildPromptUseCase.buildSessionPromptTrace(
            profile = profile,
            primaryLang = userPref.primaryLang,
            selectedLang = userPref.selectedLang,
            recentFullContext = recentFullContext,
            recentTopicSummaries = recentTopicSummaries,
            interestTopics = interestTopics
        ) + " " + buildChatTranscriptionPromptUseCase.buildTrace(
            primaryLang = userPref.primaryLang,
            selectedLang = userPref.selectedLang
        )
        val transcriptionPrompt = buildChatTranscriptionPromptUseCase(
            primaryLang = userPref.primaryLang,
            selectedLang = userPref.selectedLang
        )

        return repository.reconnectSession(
            systemInstruction = prompt,
            transcriptionPrompt = transcriptionPrompt,
            outputAudioSpeed = outputAudioSpeed,
            systemInstructionDebugTrace = promptTrace
        )
            .fold(
                onSuccess = { RetryConnectionResult.Reconnected(activeSessionId) },
                onFailure = {
                    RetryConnectionResult.Failed(
                        message = it.message ?: "다시 연결할 수 없습니다."
                    )
                }
            )
    }

    private suspend fun loadInterestTopics(): List<String> {
        // 재연결은 기존 앱 세션 복원이 우선이다. 관심사 조회가 늦으면 빈 힌트로 복구한다.
        return withTimeoutOrNull(INTEREST_TOPICS_TIMEOUT_MS) {
            getCurrentUserUidUseCase.getCurrentUserUid()
                ?.let { uid -> getUserProfileUseCase(uid)?.interestTopics }
                .orEmpty()
        }.orEmpty()
    }

    private companion object {
        // StartSessionUseCase와 같은 제한을 써서 start/retry의 체감 지연 정책을 맞춘다.
        private const val INTEREST_TOPICS_TIMEOUT_MS = 500L
    }
}
