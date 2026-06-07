package com.app.umma.domain.usecase.chat

import com.app.umma.domain.repository.ChatRepository
import com.app.umma.domain.repository.LearningStateRepo
import com.app.umma.domain.repository.SessionMemoryRepository
import com.app.umma.domain.usecase.auth.GetCurrentUserUidUseCase
import com.app.umma.domain.usecase.learningstate.BuildLearnerAdaptationProfileUseCase
import com.app.umma.domain.usecase.user.GetUserProfileUseCase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
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
    private val getCurrentUserUidUseCase: GetCurrentUserUidUseCase,
    private val getUserProfileUseCase: GetUserProfileUseCase,
    private val buildLearnerAdaptationProfileUseCase: BuildLearnerAdaptationProfileUseCase,
    private val buildChatSpeechSpeedUseCase: BuildChatSpeechSpeedUseCase,
    private val buildPromptUseCase: BuildPromptUseCase,
    private val buildChatTranscriptionPromptUseCase: BuildChatTranscriptionPromptUseCase
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
            
        /** 해당 언어에 대한 사용자의 장기 학습 데이터와 Chat 대화 능력 summary를 조회합니다. */
        val langState = learningStateRepo.observeLangState(userPref.selectedLang).firstOrNull()

        /** Chat band는 LangState.analysisMeta.chatEvidenceSummary를 공식 source로 사용합니다. */
        val profile = buildLearnerAdaptationProfileUseCase(langState)

        /** 음성 출력 속도도 최종 profile 에서 계산해 prompt 와 실제 audio 설정이 어긋나지 않게 합니다. */
        val outputAudioSpeed = buildChatSpeechSpeedUseCase(profile)

        /** 저장 완료된 최근 대화 context 를 조회합니다. 실패해도 세션 시작은 막지 않습니다. */
        val sessionMemory = sessionMemoryRepository
            .getSessionMemory(userPref.selectedLang)
            .getOrNull()
        val recentFullContext = sessionMemory?.recentFullContext.orEmpty()
        // topicSummaries는 교정 완료 후 남는 압축 주제이므로 대화 시작 후보로만 짧게 전달한다.
        val recentTopicSummaries = sessionMemory?.topicSummaries.orEmpty()
        // 관심사는 사용자 프로필의 장기 취향이다. 조회 실패/로그아웃 경합은 대화 시작을 막지 않고 빈 힌트로 낮춘다.
        val interestTopics = loadInterestTopics()
        
        /** 조회된 언어와 레벨을 바탕으로 개인화된 AI 대화 파트너 시스템 프롬프트를 생성합니다. */
        val prompt = buildPromptUseCase(
            profile = profile,
            primaryLang = userPref.primaryLang,
            selectedLang = userPref.selectedLang,
            recentFullContext = recentFullContext,
            recentTopicSummaries = recentTopicSummaries,
            interestTopics = interestTopics
        )
        val transcriptionPrompt = buildChatTranscriptionPromptUseCase(
            primaryLang = userPref.primaryLang,
            selectedLang = userPref.selectedLang
        )
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
        ) + " conversationEvidence={applied=${langState?.analysisMeta?.chatEvidenceSummary != null}," +
            "band=${profile.chatPolicy.conversationBand}," +
            "source=${if (langState?.analysisMeta?.chatEvidenceSummary != null) "LangStateSummary" else "none"}}"

        /** 생성된 지침과 함께 실시간 대화 세션 연결을 저장소에 요청합니다. */
        return repository.startSession(
            langCode = userPref.selectedLang,
            systemInstruction = prompt,
            transcriptionPrompt = transcriptionPrompt,
            outputAudioSpeed = outputAudioSpeed,
            systemInstructionDebugTrace = promptTrace
        )
    }

    private suspend fun loadInterestTopics(): List<String> {
        // 관심사는 대화 리드 품질을 높이는 보조 힌트일 뿐이므로 네트워크 지연으로 세션 시작을 늦추지 않는다.
        return withTimeoutOrNull(INTEREST_TOPICS_TIMEOUT_MS) {
            getCurrentUserUidUseCase.getCurrentUserUid()
                ?.let { uid -> getUserProfileUseCase(uid)?.interestTopics }
                .orEmpty()
        }.orEmpty()
    }

    private companion object {
        // 프로필 조회가 느려도 음성 대화 진입 자체를 막지 않기 위한 best-effort 제한이다.
        private const val INTEREST_TOPICS_TIMEOUT_MS = 500L
    }
}
