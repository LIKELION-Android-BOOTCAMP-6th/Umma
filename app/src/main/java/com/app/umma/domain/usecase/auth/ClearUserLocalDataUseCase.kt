package com.app.umma.domain.usecase.auth

import com.app.umma.data.source.local.CorrectionFlashcardDatabase
import com.app.umma.data.source.local.SessionMemoryDatabase
import com.app.umma.data.source.local.StatisticsHistoryDatabase
import com.app.umma.domain.repository.ChatConversationAnalysisJobRepository
import com.app.umma.domain.repository.LearningStateRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 회원탈퇴 후 앱 내부 사용자 데이터를 모두 정리한다.
 */
class ClearUserLocalDataUseCase @Inject constructor(
    private val learningStateRepo: LearningStateRepo,
    private val chatConversationAnalysisJobRepository: ChatConversationAnalysisJobRepository,
    private val correctionFlashcardDatabase: CorrectionFlashcardDatabase,
    private val sessionMemoryDatabase: SessionMemoryDatabase,
    private val statisticsHistoryDatabase: StatisticsHistoryDatabase
) {

    /**
     * 로컬 DataStore와 Room 데이터를 모두 비운다.
     */
    suspend operator fun invoke(): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            learningStateRepo.clear().getOrThrow()
            // Chat 분석 pending job은 사용자별 final turn snapshot을 담고 있으므로 회원탈퇴 시 함께 지운다.
            // 이를 남기면 다음 계정 테스트에서 이전 사용자의 대화 분석이 재시도될 수 있다.
            chatConversationAnalysisJobRepository.clearAll().getOrThrow()
            correctionFlashcardDatabase.clearAllTables()
            sessionMemoryDatabase.clearAllTables()
            statisticsHistoryDatabase.clearAllTables()
        }
    }
}
