package com.app.umma.domain.usecase.chat

import com.app.umma.domain.repository.ChatRepository
import com.app.umma.domain.repository.LearningStateRepo
import com.app.umma.domain.repository.SessionMemoryRepository
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
    suspend operator fun invoke(): RetryConnectionResult {
        val activeSessionID = repository.getActiveSessionId()
            ?: return RetryConnectionResult.RequireNewSession(
                reason = "활성 대화 세션이 없어 새 대화 세션이 필요합니다."
            )

        learningStateRepo.preload()

        val userPref = learningStateRepo.observeUserPref().firstOrNull()
            ?: learningStateRepo.sync()
                .getOrNull()
                .let { learningStateRepo.observeUserPref().firstOrNull() }
            ?: return RetryConnectionResult.RequireNewSession(
                reason = "학습 언어 설정을 복구할 수 없습니다."
            )

        val langState = learningStateRepo.observeLangState(userPref.selectedLang).firstOrNull()

        val recentFullContext = sessionMemoryRepository
            .getSessionMemory(userPref.selectedLang)
            .fold(
                onSuccess = { memory ->
                    memory.recentFullContext
                },
                onFailure = {
                    return RetryConnectionResult.RequireNewSession(
                        reason = "대화 문맥을 복구할 수 없어 새 대화 세션이 필요합니다."
                    )
                }
            )

        val prompt = runCatching {
            buildPromptUseCase(
                langCode = userPref.selectedLang,
                langState = langState,
                recentFullContext = recentFullContext
            )
        }.getOrElse {
            return RetryConnectionResult.RequireNewSession(
                reason = "복구용 프롬프트를 만들 수 없어 새 대화 세션이 필요합니다."
            )
        }

        return repository.reconnectSession(prompt)
            .fold(
                onSuccess = { RetryConnectionResult.Reconnected(activeSessionID) },
                onFailure = {
                    RetryConnectionResult.Failed(
                        message = it.message ?: "다시 연결할 수 없습니다."
                    )
                }
            )
    }
}
