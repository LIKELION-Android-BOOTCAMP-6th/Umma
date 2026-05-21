package com.example.umma.domain.usecase.chat

import com.example.umma.domain.repository.ChatRepository
import com.example.umma.domain.repository.LearningStateRepo
import com.example.umma.domain.repository.SessionMemoryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull

/**
 * 자동 재연결 실패 후 같은 앱 세션으로 Live transport 를 다시 연결하는 유스케이스입니다.
 */
class RetryConnectionUseCase @Inject constructor(
    private val repository: ChatRepository,
    private val learningStateRepo: LearningStateRepo,
    private val sessionMemoryRepository: SessionMemoryRepository,
    private val buildPromptUseCase: BuildPromptUseCase
) {
    /**
     * 최신 저장 context 를 반영한 prompt 로 기존 세션의 Live transport 를 다시 연결합니다.
     *
     * @return 성공 시 유지된 세션 ID
     */
    suspend operator fun invoke(): Result<String> {
        learningStateRepo.preload()

        val userPref = learningStateRepo.observeUserPref().firstOrNull()
            ?: learningStateRepo.sync()
                .getOrNull()
                .let { learningStateRepo.observeUserPref().firstOrNull() }
            ?: return Result.failure(Exception("User preferences not found"))

        val langState = learningStateRepo.observeLangState(userPref.selectedLang).firstOrNull()
        val recentFullContext = sessionMemoryRepository
            .getSessionMemory(userPref.selectedLang)
            .getOrNull()
            ?.recentFullContext
            .orEmpty()

        val prompt = buildPromptUseCase(
            langCode = userPref.selectedLang,
            langState = langState,
            recentFullContext = recentFullContext
        )

        return repository.reconnectSession(prompt)
    }
}
