package com.app.umma.domain.usecase.auth

import com.app.umma.domain.repository.ChatConversationAnalysisJobRepository
import com.app.umma.domain.repository.ChatUsageRepository
import com.app.umma.domain.repository.CorrectionSuggestionCacheRepository
import com.app.umma.domain.repository.FlashcardRepository
import com.app.umma.domain.repository.LearningStateRepo
import com.app.umma.domain.repository.NotificationSettingsRepository
import com.app.umma.domain.repository.SessionMemoryRepository
import com.app.umma.domain.repository.SessionRepository
import com.app.umma.domain.repository.StatisticsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ClearUserLocalDataUseCaseTest {

    @Test
    fun `account deletion cleanup clears every user scoped local store`() = runTest {
        // 탈퇴 후 다음 계정에 이전 계정의 데이터가 보이지 않도록, 계정성 local 저장소를 모두 정리하는지 고정한다.
        val learningStateRepo = mockk<LearningStateRepo>()
        val chatConversationAnalysisJobRepository = mockk<ChatConversationAnalysisJobRepository>()
        val chatUsageRepository = mockk<ChatUsageRepository>()
        val correctionSuggestionCacheRepository = mockk<CorrectionSuggestionCacheRepository>()
        val flashcardRepository = mockk<FlashcardRepository>()
        val notificationSettingsRepository = mockk<NotificationSettingsRepository>()
        val sessionMemoryRepository = mockk<SessionMemoryRepository>()
        val sessionRepository = mockk<SessionRepository>()
        val statisticsRepository = mockk<StatisticsRepository>()

        // 모든 저장소가 성공하는 기본 경로를 구성해, 누락 호출이 있으면 verify 단계에서 바로 드러나게 한다.
        coEvery { learningStateRepo.clear() } returns Result.success(Unit)
        coEvery { chatConversationAnalysisJobRepository.clearAll() } returns Result.success(Unit)
        coEvery { chatUsageRepository.clearLocal() } returns Result.success(Unit)
        coEvery { correctionSuggestionCacheRepository.clearLocal() } returns Result.success(Unit)
        coEvery { flashcardRepository.clearLocal() } returns Result.success(Unit)
        coEvery { notificationSettingsRepository.clearLocal() } returns Result.success(Unit)
        coEvery { sessionMemoryRepository.clearLocal() } returns Result.success(Unit)
        every { sessionRepository.clearLocalAccountSessionState() } returns Unit
        coEvery { statisticsRepository.clearLocal() } returns Result.success(Unit)

        val useCase = ClearUserLocalDataUseCase(
            learningStateRepo = learningStateRepo,
            chatConversationAnalysisJobRepository = chatConversationAnalysisJobRepository,
            chatUsageRepository = chatUsageRepository,
            correctionSuggestionCacheRepository = correctionSuggestionCacheRepository,
            flashcardRepository = flashcardRepository,
            notificationSettingsRepository = notificationSettingsRepository,
            sessionMemoryRepository = sessionMemoryRepository,
            sessionRepository = sessionRepository,
            statisticsRepository = statisticsRepository,
        )

        val result = useCase()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { learningStateRepo.clear() }
        coVerify(exactly = 1) { chatConversationAnalysisJobRepository.clearAll() }
        coVerify(exactly = 1) { chatUsageRepository.clearLocal() }
        coVerify(exactly = 1) { correctionSuggestionCacheRepository.clearLocal() }
        coVerify(exactly = 1) { flashcardRepository.clearLocal() }
        coVerify(exactly = 1) { notificationSettingsRepository.clearLocal() }
        coVerify(exactly = 1) { sessionMemoryRepository.clearLocal() }
        verify(exactly = 1) { sessionRepository.clearLocalAccountSessionState() }
        coVerify(exactly = 1) { statisticsRepository.clearLocal() }
    }

    @Test
    fun `account deletion cleanup continues remaining stores after one cleanup fails`() = runTest {
        // 탈퇴 정리는 fail-fast가 아니라 best-effort여야 한다.
        // 앞쪽 저장소 실패가 뒤쪽 사용자 데이터 정리를 건너뛰는 회귀를 막는다.
        val learningStateRepo = mockk<LearningStateRepo>()
        val chatConversationAnalysisJobRepository = mockk<ChatConversationAnalysisJobRepository>()
        val chatUsageRepository = mockk<ChatUsageRepository>()
        val correctionSuggestionCacheRepository = mockk<CorrectionSuggestionCacheRepository>()
        val flashcardRepository = mockk<FlashcardRepository>()
        val notificationSettingsRepository = mockk<NotificationSettingsRepository>()
        val sessionMemoryRepository = mockk<SessionMemoryRepository>()
        val sessionRepository = mockk<SessionRepository>()
        val statisticsRepository = mockk<StatisticsRepository>()
        val cleanupFailure = IllegalStateException("usage cleanup failed")

        coEvery { learningStateRepo.clear() } returns Result.success(Unit)
        coEvery { chatConversationAnalysisJobRepository.clearAll() } returns Result.success(Unit)
        coEvery { chatUsageRepository.clearLocal() } returns Result.failure(cleanupFailure)
        coEvery { correctionSuggestionCacheRepository.clearLocal() } returns Result.success(Unit)
        coEvery { flashcardRepository.clearLocal() } returns Result.success(Unit)
        coEvery { notificationSettingsRepository.clearLocal() } returns Result.success(Unit)
        coEvery { sessionMemoryRepository.clearLocal() } returns Result.success(Unit)
        every { sessionRepository.clearLocalAccountSessionState() } returns Unit
        coEvery { statisticsRepository.clearLocal() } returns Result.success(Unit)

        val useCase = ClearUserLocalDataUseCase(
            learningStateRepo = learningStateRepo,
            chatConversationAnalysisJobRepository = chatConversationAnalysisJobRepository,
            chatUsageRepository = chatUsageRepository,
            correctionSuggestionCacheRepository = correctionSuggestionCacheRepository,
            flashcardRepository = flashcardRepository,
            notificationSettingsRepository = notificationSettingsRepository,
            sessionMemoryRepository = sessionMemoryRepository,
            sessionRepository = sessionRepository,
            statisticsRepository = statisticsRepository,
        )

        val result = useCase()

        assertTrue(result.isFailure)
        // 실패 이후 저장소까지 모두 호출되어야 탈퇴 로컬 정리가 부분 누락으로 멈추지 않는다.
        coVerify(exactly = 1) { correctionSuggestionCacheRepository.clearLocal() }
        coVerify(exactly = 1) { flashcardRepository.clearLocal() }
        coVerify(exactly = 1) { notificationSettingsRepository.clearLocal() }
        coVerify(exactly = 1) { sessionMemoryRepository.clearLocal() }
        verify(exactly = 1) { sessionRepository.clearLocalAccountSessionState() }
        coVerify(exactly = 1) { statisticsRepository.clearLocal() }
    }
}
