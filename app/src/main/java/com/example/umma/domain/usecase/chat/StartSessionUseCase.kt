package com.example.umma.domain.usecase.chat

import com.example.umma.domain.repository.ChatRepository
import com.example.umma.domain.repository.LearningStateRepo
import com.example.umma.domain.repository.SessionMemoryRepository
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

        /** 저장 완료된 최근 대화 context 를 조회합니다. 실패해도 세션 시작은 막지 않습니다. */
        val recentFullContext = sessionMemoryRepository
            .getSessionMemory(userPref.selectedLang)
            .getOrNull()
            ?.recentFullContext
            .orEmpty()
        
        /** 조회된 언어와 레벨을 바탕으로 개인화된 AI 튜터 시스템 프롬프트를 생성합니다. */
        val prompt = buildPromptUseCase(
            langCode = userPref.selectedLang,
            langState = langState,
            recentFullContext = recentFullContext
        )

        /** 생성된 지침과 함께 실시간 대화 세션 연결을 저장소에 요청합니다. */
        return repository.startSession(langCode = userPref.selectedLang, systemInstruction = prompt)
    }
}
