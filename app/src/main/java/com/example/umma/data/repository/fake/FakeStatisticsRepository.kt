package com.example.umma.data.repository.fake

import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.SyncStatus
import com.example.umma.domain.model.learningstate.VocabLevel
import com.example.umma.domain.model.statistics.StatisticsHistory
import com.example.umma.domain.model.statistics.StatisticsHistoryState
import com.example.umma.domain.model.statistics.StatisticsHistoryRecordResult
import com.example.umma.domain.repository.StatisticsRepository
import com.example.umma.BuildConfig
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
        // Statistics 화면을 실제 실행에서 바로 확인할 수 있도록
        // 여러 언어와 여러 시점의 history를 기본으로 깔아 둔다.
        StatisticsHistory(
            id = "stats-en-1",
            userId = "user-1",
            language = LangCode.EN,
            recordedAt = 1_000L,
            vocabularyLevel = VocabLevel.A1,
            grammarAccuracy = 0.41,
            expressionRange = 2,
            fluencyScore = 0.33,
            naturalnessScore = 0.37,
            sourceEventId = "event-en-1",
            syncStatus = SyncStatus.SYNCED
        ),
        StatisticsHistory(
            id = "stats-en-2",
            userId = "user-1",
            language = LangCode.EN,
            recordedAt = 2_000L,
            vocabularyLevel = VocabLevel.A2,
            grammarAccuracy = 0.53,
            expressionRange = 3,
            fluencyScore = 0.46,
            naturalnessScore = 0.48,
            sourceEventId = "event-en-2",
            syncStatus = SyncStatus.PENDING
        ),
        StatisticsHistory(
            id = "stats-en-3",
            userId = "user-1",
            language = LangCode.EN,
            recordedAt = 3_000L,
            vocabularyLevel = VocabLevel.B1,
            grammarAccuracy = 0.64,
            expressionRange = 5,
            fluencyScore = 0.58,
            naturalnessScore = 0.57,
            sourceEventId = "event-en-3",
            syncStatus = SyncStatus.SYNCED
        ),
        StatisticsHistory(
            id = "stats-en-4",
            userId = "user-1",
            language = LangCode.EN,
            recordedAt = 4_000L,
            vocabularyLevel = VocabLevel.B2,
            grammarAccuracy = 0.71,
            expressionRange = 6,
            fluencyScore = 0.65,
            naturalnessScore = 0.63,
            sourceEventId = "event-en-4",
            syncStatus = SyncStatus.PENDING
        ),
        StatisticsHistory(
            id = "stats-en-5",
            userId = "user-1",
            language = LangCode.EN,
            recordedAt = 5_000L,
            vocabularyLevel = VocabLevel.C1,
            grammarAccuracy = 0.88,
            expressionRange = 8,
            fluencyScore = 0.81,
            naturalnessScore = 0.78,
            sourceEventId = "event-en-5",
            syncStatus = SyncStatus.SYNCED
        ),
        StatisticsHistory(
            id = "stats-en-6",
            userId = "user-1",
            language = LangCode.EN,
            recordedAt = 6_000L,
            vocabularyLevel = VocabLevel.C1,
            grammarAccuracy = 0.89,
            expressionRange = 8,
            fluencyScore = 0.83,
            naturalnessScore = 0.80,
            sourceEventId = "event-en-6",
            syncStatus = SyncStatus.SYNCED
        ),
        StatisticsHistory(
            id = "stats-en-7",
            userId = "user-1",
            language = LangCode.EN,
            recordedAt = 7_000L,
            vocabularyLevel = VocabLevel.C1,
            grammarAccuracy = 0.90,
            expressionRange = 9,
            fluencyScore = 0.84,
            naturalnessScore = 0.82,
            sourceEventId = "event-en-7",
            syncStatus = SyncStatus.PENDING
        ),
        StatisticsHistory(
            id = "stats-en-8",
            userId = "user-1",
            language = LangCode.EN,
            recordedAt = 8_000L,
            vocabularyLevel = VocabLevel.C1,
            grammarAccuracy = 0.91,
            expressionRange = 9,
            fluencyScore = 0.86,
            naturalnessScore = 0.84,
            sourceEventId = "event-en-8",
            syncStatus = SyncStatus.SYNCED
        ),
        StatisticsHistory(
            id = "stats-en-9",
            userId = "user-1",
            language = LangCode.EN,
            recordedAt = 9_000L,
            vocabularyLevel = VocabLevel.C2,
            grammarAccuracy = 0.92,
            expressionRange = 9,
            fluencyScore = 0.88,
            naturalnessScore = 0.86,
            sourceEventId = "event-en-9",
            syncStatus = SyncStatus.PENDING
        ),
        StatisticsHistory(
            id = "stats-en-10",
            userId = "user-1",
            language = LangCode.EN,
            recordedAt = 10_000L,
            vocabularyLevel = VocabLevel.C1,
            grammarAccuracy = 0.88,
            expressionRange = 8,
            fluencyScore = 0.81,
            naturalnessScore = 0.78,
            sourceEventId = "event-en-10",
            syncStatus = SyncStatus.SYNCED
        ),
        StatisticsHistory(
            id = "stats-ko-1",
            userId = "user-1",
            language = LangCode.KO,
            recordedAt = 1_500L,
            vocabularyLevel = VocabLevel.A1,
            grammarAccuracy = 0.44,
            expressionRange = 3,
            fluencyScore = 0.39,
            naturalnessScore = 0.41,
            sourceEventId = "event-ko-1",
            syncStatus = SyncStatus.SYNCED
        ),
        StatisticsHistory(
            id = "stats-ko-2",
            userId = "user-1",
            language = LangCode.KO,
            recordedAt = 2_500L,
            vocabularyLevel = VocabLevel.A2,
            grammarAccuracy = 0.58,
            expressionRange = 4,
            fluencyScore = 0.52,
            naturalnessScore = 0.49,
            sourceEventId = "event-ko-2",
            syncStatus = SyncStatus.SYNCED
        ),
        StatisticsHistory(
            id = "stats-ko-3",
            userId = "user-1",
            language = LangCode.KO,
            recordedAt = 3_500L,
            vocabularyLevel = VocabLevel.A2,
            grammarAccuracy = 0.64,
            expressionRange = 5,
            fluencyScore = 0.57,
            naturalnessScore = 0.55,
            sourceEventId = "event-ko-3",
            syncStatus = SyncStatus.PENDING
        ),
        StatisticsHistory(
            id = "stats-ja-1",
            userId = "user-1",
            language = LangCode.JA,
            recordedAt = 1_200L,
            vocabularyLevel = VocabLevel.B1,
            grammarAccuracy = 0.66,
            expressionRange = 5,
            fluencyScore = 0.57,
            naturalnessScore = 0.59,
            sourceEventId = "event-ja-1",
            syncStatus = SyncStatus.SYNCED
        ),
        StatisticsHistory(
            id = "stats-ja-2",
            userId = "user-1",
            language = LangCode.JA,
            recordedAt = 2_200L,
            vocabularyLevel = VocabLevel.B1,
            grammarAccuracy = 0.70,
            expressionRange = 5,
            fluencyScore = 0.61,
            naturalnessScore = 0.64,
            sourceEventId = "event-ja-2",
            syncStatus = SyncStatus.SYNCED
        ),
        StatisticsHistory(
            id = "stats-es-1",
            userId = "user-1",
            language = LangCode.ES,
            recordedAt = 1_700L,
            vocabularyLevel = VocabLevel.A2,
            grammarAccuracy = 0.51,
            expressionRange = 4,
            fluencyScore = 0.46,
            naturalnessScore = 0.43,
            sourceEventId = "event-es-1",
            syncStatus = SyncStatus.PENDING
        ),
        StatisticsHistory(
            id = "stats-es-2",
            userId = "user-1",
            language = LangCode.ES,
            recordedAt = 2_700L,
            vocabularyLevel = VocabLevel.B1,
            grammarAccuracy = 0.60,
            expressionRange = 5,
            fluencyScore = 0.52,
            naturalnessScore = 0.50,
            sourceEventId = "event-es-2",
            syncStatus = SyncStatus.SYNCED
        ),
        StatisticsHistory(
            id = "stats-user-2-ja",
            userId = "user-2",
            language = LangCode.JA,
            recordedAt = 3_000L,
            vocabularyLevel = VocabLevel.A1,
            grammarAccuracy = 0.30,
            expressionRange = 2,
            fluencyScore = 0.25,
            naturalnessScore = 0.20,
            sourceEventId = "event-user2-ja",
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

        val debugFallback = if (filtered.isEmpty() && BuildConfig.DEBUG) {
            // 실제 앱 실행에서 Firebase uid가 달라도 chart 예시를 바로 볼 수 있도록
            // debug에서는 언어 기준 샘플을 한 번 더 허용한다.
            histories
                .filter { it.language == language }
                .sortedBy { it.recordedAt }
        } else {
            filtered
        }

        return flowOf(
            when {
                debugFallback.isEmpty() -> StatisticsHistoryState.Empty
                else -> StatisticsHistoryState.Content(debugFallback)
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
