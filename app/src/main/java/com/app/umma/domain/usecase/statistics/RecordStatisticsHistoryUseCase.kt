package com.app.umma.domain.usecase.statistics

import com.app.umma.domain.model.learningstate.LearningStateUpdateResult
import com.app.umma.domain.model.statistics.StatisticsHistoryRecordResult
import com.app.umma.domain.repository.StatisticsRepository
import javax.inject.Inject

/**
 * Correction 완료 후 받은 LearningState 저장 결과를 Statistics history로 기록한다.
 *
 * history snapshot 생성과 실제 저장을 분리해 두면,
 * 저장 포맷 변경과 기록 시점 정책을 서로 독립적으로 조정할 수 있다.
 */
class RecordStatisticsHistoryUseCase @Inject constructor(
    private val buildStatisticsHistoryUseCase: BuildStatisticsHistoryUseCase,
    private val statisticsRepository: StatisticsRepository
) {

    suspend operator fun invoke(
        userId: String,
        updateResult: LearningStateUpdateResult
    ): Result<StatisticsHistoryRecordResult> {
        // build 단계와 저장 단계의 책임을 나눠 두면,
        // history 포맷 변경과 저장 정책 변경을 서로 독립적으로 다룰 수 있다.
        val history = buildStatisticsHistoryUseCase(userId, updateResult)
        return statisticsRepository.recordHistory(history)
    }
}
