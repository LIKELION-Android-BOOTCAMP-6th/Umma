package com.app.umma.domain.usecase.auth

import com.app.umma.data.source.local.CorrectionFlashcardDatabase
import com.app.umma.data.source.local.SessionMemoryDatabase
import com.app.umma.data.source.local.StatisticsHistoryDatabase
import com.app.umma.domain.repository.LearningStateRepo
import javax.inject.Inject

/**
 * 회원탈퇴 후 앱 내부 사용자 데이터를 모두 정리한다.
 */
class ClearUserLocalDataUseCase @Inject constructor(
    private val learningStateRepo: LearningStateRepo,
    private val correctionFlashcardDatabase: CorrectionFlashcardDatabase,
    private val sessionMemoryDatabase: SessionMemoryDatabase,
    private val statisticsHistoryDatabase: StatisticsHistoryDatabase
) {

    /**
     * 로컬 DataStore와 Room 데이터를 모두 비운다.
     */
    suspend operator fun invoke(): Result<Unit> = runCatching {
        learningStateRepo.clear().getOrThrow()
        correctionFlashcardDatabase.clearAllTables()
        sessionMemoryDatabase.clearAllTables()
        statisticsHistoryDatabase.clearAllTables()
    }
}
