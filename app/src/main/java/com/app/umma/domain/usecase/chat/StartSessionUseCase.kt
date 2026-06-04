package com.app.umma.domain.usecase.chat

import com.app.umma.domain.repository.ChatRepository
import com.app.umma.domain.repository.LearningStateRepo
import com.app.umma.domain.repository.SessionMemoryRepository
import com.app.umma.domain.model.realtime.ChatResponseOverride
import com.app.umma.domain.model.realtime.ChatResponseOverrideProvider
import com.app.umma.domain.usecase.learningstate.BuildLearnerAdaptationProfileUseCase
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * AI Chat 세션을 초기화하고 시작하는 유스케이스입니다. (RT-001)
 *
 * 1. [LearningStateRepo]를 통해 사용자의 현재 언어 설정과 숙련도를 조회합니다.
 * 2. [BuildPromptUseCase]를 사용하여 맞춤형 시스템 지침을 생성합니다.
 * 3. [ChatRepository]를 통해 최종적으로 세션 연결을 요청합니다.
 */
class StartSessionUseCase @Inject constructor(
    private val repository: ChatRepository,
    private val learningStateRepo: LearningStateRepo,
    private val sessionMemoryRepository: SessionMemoryRepository,
    private val buildLearnerAdaptationProfileUseCase: BuildLearnerAdaptationProfileUseCase,
    private val buildChatTurnContextSignalUseCase: BuildChatTurnContextSignalUseCase,
    private val buildChatTurnAdaptationPolicyUseCase: BuildChatTurnAdaptationPolicyUseCase,
    private val buildChatSpeechSpeedUseCase: BuildChatSpeechSpeedUseCase,
    private val buildPromptUseCase: BuildPromptUseCase
) {
    /**
     * 세션을 시작합니다.
     *
     * @return 성공 시 생성된 세션 ID를 포함하는 [Result]
     */
    suspend operator fun invoke(): Result<String> {
        /** Chat 진입 경로에서도 학습 상태 cache 가 먼저 채워지도록 보장합니다. */
        learningStateRepo.preload()

        /** 사용자의 현재 학습 언어 선호도 설정을 조회합니다. */
        val userPref = learningStateRepo.observeUserPref().firstOrNull()
            ?: learningStateRepo.sync()
                .getOrNull()
                .let { learningStateRepo.observeUserPref().firstOrNull() }
            ?: return Result.failure(Exception("User preferences not found"))
            
        /** 해당 언어에 대한 사용자의 장기 학습 데이터 및 레벨 정보를 조회합니다. */
        val langState = learningStateRepo.observeLangState(userPref.selectedLang).firstOrNull()

        /** LangState raw metric 은 Chat 이 직접 해석하지 않고, LearningState domain profile 로 먼저 변환합니다. */
        val profile = buildLearnerAdaptationProfileUseCase(langState)
        /** 음성 출력 속도도 같은 profile 에서 계산해 prompt 와 실제 audio 설정이 어긋나지 않게 합니다. */
        val outputAudioSpeed = buildChatSpeechSpeedUseCase(profile)
        /** 세션 시작 prompt에 이미 반영된 기본 turn 정책을 baseline으로 저장해 중복 override를 막습니다. */
        val baseTurnPolicy = buildChatTurnAdaptationPolicyUseCase.buildBasePolicy(profile)

        /** 저장 완료된 최근 대화 context 를 조회합니다. 실패해도 세션 시작은 막지 않습니다. */
        val recentFullContext = sessionMemoryRepository
            .getSessionMemory(userPref.selectedLang)
            .getOrNull()
            ?.recentFullContext
            .orEmpty()
        
        /** 조회된 언어와 레벨을 바탕으로 개인화된 AI 대화 파트너 시스템 프롬프트를 생성합니다. */
        val prompt = buildPromptUseCase(
            profile = profile,
            primaryLang = userPref.primaryLang,
            selectedLang = userPref.selectedLang,
            recentFullContext = recentFullContext
        )
        val promptTrace = buildPromptUseCase.buildSessionPromptTrace(
            profile = profile,
            primaryLang = userPref.primaryLang,
            selectedLang = userPref.selectedLang,
            recentFullContext = recentFullContext
        )

        /** USER final transcript 이후 이번 response 에만 적용할 turn override provider 를 구성합니다. */
        val responseOverrideProvider = ChatResponseOverrideProvider { userFinalTranscript ->
            // provider 내부에서도 raw transcript 를 저장하거나 LangState 로 올리지 않고, 이번 응답 정책 계산에만 사용한다.
            val latestRecentContext = sessionMemoryRepository
                .getSessionMemory(userPref.selectedLang)
                .getOrNull()
                ?.recentFullContext
                .orEmpty()
            // 이번 USER final transcript가 최근 흐름 안에서 이어지는 말인지 압축 신호로 계산해 반복 보정을 줄인다.
            val contextSignal = buildChatTurnContextSignalUseCase(
                recentFullContext = latestRecentContext,
                userFinalTranscript = userFinalTranscript
            )
            val turnPolicy = buildChatTurnAdaptationPolicyUseCase(
                transcript = userFinalTranscript,
                contextSignal = contextSignal,
                profile = profile,
                primaryLang = userPref.primaryLang,
                selectedLang = userPref.selectedLang
            )
            // response.create.instructions 에는 세션 prompt 전체가 아니라 이번 응답의 보정값만 들어간다.
            val responseInstructions = buildPromptUseCase.buildTurnOverrideInstruction(
                basePolicy = baseTurnPolicy,
                turnPolicy = turnPolicy,
                primaryLang = userPref.primaryLang,
                selectedLang = userPref.selectedLang,
                contextSignal = contextSignal
            )
            // 실제 audio speed 는 turnPolicy 를 반영해 계산하고, repository 가 변경 필요 시 session.update 로 적용한다.
            val turnAudioSpeed = buildChatSpeechSpeedUseCase(
                profile = profile,
                turnPolicy = turnPolicy
            )
            ChatResponseOverride(
                responseInstructions = responseInstructions,
                outputAudioSpeed = turnAudioSpeed,
                debugTrace = buildPromptUseCase.buildTurnOverrideTrace(
                    basePolicy = baseTurnPolicy,
                    turnPolicy = turnPolicy,
                    contextSignal = contextSignal,
                    hasResponseInstructions = !responseInstructions.isNullOrBlank(),
                    outputAudioSpeed = turnAudioSpeed
                )
            )
        }

        /** 생성된 지침과 함께 실시간 대화 세션 연결을 저장소에 요청합니다. */
        return repository.startSession(
            langCode = userPref.selectedLang,
            systemInstruction = prompt,
            outputAudioSpeed = outputAudioSpeed,
            systemInstructionDebugTrace = promptTrace,
            responseOverrideProvider = responseOverrideProvider
        )
    }
}
