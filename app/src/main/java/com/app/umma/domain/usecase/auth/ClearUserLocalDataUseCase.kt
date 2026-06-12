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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 회원탈퇴 후 앱 내부 사용자 데이터를 모두 정리한다.
 *
 * 각 Repository 의 clearLocal() 계약을 통해 정리하므로 domain 이 Room 구현체를 직접 알지 않는다.
 * 의존성 방향: domain → domain(Repository 인터페이스) ← data(Impl).
 */
class ClearUserLocalDataUseCase @Inject constructor(
    private val learningStateRepo: LearningStateRepo,
    private val chatConversationAnalysisJobRepository: ChatConversationAnalysisJobRepository,
    private val chatUsageRepository: ChatUsageRepository,
    private val correctionSuggestionCacheRepository: CorrectionSuggestionCacheRepository,
    private val flashcardRepository: FlashcardRepository,
    private val notificationSettingsRepository: NotificationSettingsRepository,
    private val sessionMemoryRepository: SessionMemoryRepository,
    private val sessionRepository: SessionRepository,
    private val statisticsRepository: StatisticsRepository,
) {

    /**
     * 로컬 DataStore와 Room 데이터를 모두 비운다.
     */
    suspend operator fun invoke(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val failures = mutableListOf<Throwable>()

            // 탈퇴 정리는 어느 한 저장소가 실패해도 나머지 저장소 정리를 계속 시도해야 한다.
            // 그래야 새로 추가된 정리 작업이 기존 로컬 데이터 삭제를 건너뛰는 회귀를 만들지 않는다.
            fun collectFailure(result: Result<Unit>) {
                result.exceptionOrNull()?.let(failures::add)
            }

            collectFailure(learningStateRepo.clear())
            // Chat 분석 pending job은 사용자별 final turn snapshot을 담고 있으므로 회원탈퇴 시 함께 지운다.
            // 이를 남기면 다음 계정 테스트에서 이전 사용자의 대화 분석이 재시도될 수 있다.
            collectFailure(chatConversationAnalysisJobRepository.clearAll())
            // Chat usage는 운영 비용 집계 원본이지만 userId/sessionId를 포함하므로 탈퇴 후 재시도 대상에서 제거한다.
            collectFailure(chatUsageRepository.clearLocal())
            // 저장 전 교정 제안 캐시는 원문 후보를 담으므로 다음 계정에서 복원되면 안 된다.
            collectFailure(correctionSuggestionCacheRepository.clearLocal())
            collectFailure(flashcardRepository.clearLocal())
            // 알림 설정 DataStore cache도 사용자 설정이므로 계정 경계에서 비운다.
            collectFailure(notificationSettingsRepository.clearLocal())
            collectFailure(sessionMemoryRepository.clearLocal())
            // 일반 로그아웃과 달리 회원탈퇴는 강제 로그아웃 안내 잔여 플래그까지 함께 제거한다.
            collectFailure(runCatching { sessionRepository.clearLocalAccountSessionState() })
            collectFailure(statisticsRepository.clearLocal())

            return@withContext if (failures.isEmpty()) {
                Result.success(Unit)
            } else {
                // 호출자는 이미 best-effort cleanup 실패를 로그로 남긴다. 여기서는 첫 원인을 대표 실패로 돌려준다.
                Result.failure(failures.first())
            }
        }
    }
}
