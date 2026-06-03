package com.app.umma.domain.usecase.chat

import com.app.umma.domain.repository.ChatRepository
import com.app.umma.domain.repository.LearningStateRepo
import com.app.umma.domain.repository.SessionMemoryRepository
import com.app.umma.domain.model.realtime.ChatResponseOverride
import com.app.umma.domain.model.realtime.ChatResponseOverrideProvider
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
    private val buildChatTurnAdaptationPolicyUseCase: BuildChatTurnAdaptationPolicyUseCase,
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
        val baseTurnPolicy = buildChatTurnAdaptationPolicyUseCase.buildBasePolicy(profile)

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

        val responseOverrideProvider = ChatResponseOverrideProvider { userFinalTranscript ->
            // 재연결 후에도 startSession과 같은 turn override 정책을 써야 같은 세션에서 난이도가 흔들리지 않는다.
            val turnPolicy = buildChatTurnAdaptationPolicyUseCase(
                transcript = userFinalTranscript,
                profile = profile,
                primaryLang = userPref.primaryLang,
                selectedLang = userPref.selectedLang
            )
            // response override는 이번 응답의 짧은 보정값만 포함하고 세션 prompt 전체를 다시 보내지 않는다.
            val responseInstructions = buildPromptUseCase.buildTurnOverrideInstruction(
                basePolicy = baseTurnPolicy,
                turnPolicy = turnPolicy,
                primaryLang = userPref.primaryLang,
                selectedLang = userPref.selectedLang
            )
            // speed 역시 같은 profile/turnPolicy 조합으로 계산해 start와 retry의 정책 차이를 없앤다.
            val turnAudioSpeed = buildChatSpeechSpeedUseCase(
                profile = profile,
                turnPolicy = turnPolicy
            )
            ChatResponseOverride(
                responseInstructions = responseInstructions,
                outputAudioSpeed = turnAudioSpeed
            )
        }

        return repository.reconnectSession(
            systemInstruction = prompt,
            outputAudioSpeed = outputAudioSpeed,
            responseOverrideProvider = responseOverrideProvider
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
