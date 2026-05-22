package com.example.umma.data.repository.fake

import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.SyncStatus
import com.example.umma.domain.model.learningstate.VocabLevel
import com.example.umma.domain.model.statistics.StatisticsHistory
import com.example.umma.domain.model.statistics.StatisticsHistoryState
import com.example.umma.domain.model.statistics.StatisticsHistoryRecordResult
import com.example.umma.domain.repository.StatisticsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Statistics 화면과 단위 테스트에서 사용할 가짜 repository다.
 *
 * local cache 있음 / history 부족 / fetch 실패 / pending sync 상태를
 * 코드 레벨에서 빠르게 재현할 수 있게 만든다.
 */
@Singleton
class FakeStatisticsRepository @Inject constructor() : StatisticsRepository {

    private val histories = mutableListOf(
        StatisticsHistory(
            id = "stats-1",
            userId = "user-1",
            language = LangCode.EN,
            recordedAt = 1_000L,
            vocabularyLevel = VocabLevel.A2,
            grammarAccuracy = 0.62,
            expressionRange = 4,
            fluencyScore = 0.48,
            naturalnessScore = 0.55,
            sourceEventId = "event-1",
            syncStatus = SyncStatus.SYNCED
        ),
        StatisticsHistory(
            id = "stats-2",
            userId = "user-1",
            language = LangCode.EN,
            recordedAt = 2_000L,
            vocabularyLevel = VocabLevel.B1,
            grammarAccuracy = 0.71,
            expressionRange = 5,
            fluencyScore = 0.63,
            naturalnessScore = 0.60,
            sourceEventId = "event-2",
            syncStatus = SyncStatus.PENDING
        ),
        StatisticsHistory(
            id = "stats-3",
            userId = "user-2",
            language = LangCode.JA,
            recordedAt = 3_000L,
            vocabularyLevel = VocabLevel.A1,
            grammarAccuracy = 0.30,
            expressionRange = 2,
            fluencyScore = 0.25,
            naturalnessScore = 0.20,
            sourceEventId = "event-3",
            syncStatus = SyncStatus.SYNCED
        )
    )

    var queryFailure: Throwable? = null
    var recordFailureCause: Throwable? = null

    fun seedHistories(items: List<StatisticsHistory>) {
        // 테스트에서 특정 경계 상태만 보고 싶을 때 기본 seed 를 덮어쓴다.
        histories.clear()
        histories.addAll(items)
    }

    fun setFetchFailure(cause: Throwable?) {
        queryFailure = cause
    }

    fun setRecordFailure(cause: Throwable?) {
        recordFailureCause = cause
    }

    override fun observeHistory(userId: String, language: LangCode): Flow<StatisticsHistoryState> {
        // fake도 실제 repository처럼 userId + language 조합으로만 보여준다.
        // fetchFailure가 설정되면 화면이 Retry 상태를 재현할 수 있어야 한다.
        queryFailure?.let { return flowOf(StatisticsHistoryState.Retry(it)) }

        val filtered = histories
            .filter { it.userId == userId && it.language == language }
            .sortedBy { it.recordedAt }

        return flowOf(
            when {
                filtered.isEmpty() -> StatisticsHistoryState.Empty
                else -> StatisticsHistoryState.Content(filtered)
            }
        )
    }

    override suspend fun recordHistory(history: StatisticsHistory): Result<StatisticsHistoryRecordResult> {
        recordFailureCause?.let { return Result.failure(it) }

        val existingIndex = histories.indexOfFirst { it.id == history.id }
        // 같은 id 가 다시 들어오면 새 row 를 추가하지 않고 덮어써서,
        // idempotent 하게 저장되는 것처럼 행동한다.
        val applied = existingIndex == -1
        if (applied) {
            histories.add(history)
        } else {
            histories[existingIndex] = history
        }

        return Result.success(
            StatisticsHistoryRecordResult(
                historyId = history.id,
                sourceEventId = history.sourceEventId,
                applied = applied,
                isSyncPending = history.syncStatus != SyncStatus.SYNCED,
                recordedAt = history.recordedAt
            )
        )
    }
}
