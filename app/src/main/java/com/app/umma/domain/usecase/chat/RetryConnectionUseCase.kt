package com.app.umma.domain.usecase.chat

import com.app.umma.domain.repository.ChatRepository
import com.app.umma.domain.repository.LearningStateRepo
import com.app.umma.domain.repository.SessionMemoryRepository
import com.app.umma.domain.usecase.learningstate.BuildLearnerAdaptationProfileUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull

/**
 * 기존 앱 세션을 기준으로 realtime transport 재연결을 시도한다.
 */
class RetryConnectionUseCase @Inject constructor(
    private val repository: ChatRepository,
    private val learningStateRepo: LearningStateRepo,
    private val sessionMemoryRepository: SessionMemoryRepository,
    private val buildLearnerAdaptationProfileUseCase: BuildLearnerAdaptationProfileUseCase,
    private val buildChatSpeechSpeedUseCase: BuildChatSpeechSpeedUseCase,
    private val buildPromptUseCase: BuildPromptUseCase
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

        val recentFullContext = sessionMemoryRepository
            .getSessionMemory(userPref.selectedLang)
            .fold(
                onSuccess = { memory ->
                    memory.recentFullContext
                },
                onFailure = {
                    return RetryConnectionResult.RequireNewSession(
                        reason = NewSessionReason.RESTORE_UNAVAILABLE,
                        targetLang = userPref.selectedLang
                    )
                }
            )

        val prompt = runCatching {
            buildPromptUseCase(
                profile = profile,
                primaryLang = userPref.primaryLang,
                selectedLang = userPref.selectedLang,
                recentFullContext = recentFullContext
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
            recentFullContext = recentFullContext
        )

        return repository.reconnectSession(
            systemInstruction = prompt,
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
}
