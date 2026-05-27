package com.app.umma.data.repository.fake

import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.SyncStatus
import com.app.umma.domain.model.learningstate.VocabLevel
import com.app.umma.domain.model.statistics.StatisticsHistory
import com.app.umma.domain.model.statistics.StatisticsHistoryState
import com.app.umma.domain.model.statistics.StatisticsHistoryRecordResult
import com.app.umma.domain.repository.StatisticsRepository
import com.app.umma.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

    private val histories = MutableStateFlow(
        listOf(
        // Statistics 화면을 실제 실행에서 바로 확인할 수 있도록
        // 여러 언어와 여러 시점의 history를 기본으로 깔아 둔다.
        // 날짜를 분산해 둔 이유는 x축이 1/1로 뭉개지는지 바로 확인하기 위해서다.
        StatisticsHistory(
            id = "stats-en-1",
            userId = "user-1",
            language = LangCode.EN,
            recordedAt = JAN_01_2025_KST,
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
            recordedAt = JAN_07_2025_KST,
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
            recordedAt = JAN_14_2025_KST,
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
            recordedAt = JAN_21_2025_KST,
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
            recordedAt = JAN_28_2025_KST,
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
            recordedAt = FEB_04_2025_KST,
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
            recordedAt = FEB_11_2025_KST,
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
            recordedAt = FEB_18_2025_KST,
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
            recordedAt = FEB_25_2025_KST,
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
            recordedAt = MAR_04_2025_KST,
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
            recordedAt = JAN_03_2025_KST,
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
            recordedAt = JAN_10_2025_KST,
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
            recordedAt = JAN_17_2025_KST,
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
            recordedAt = JAN_05_2025_KST,
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
            recordedAt = JAN_12_2025_KST,
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
            recordedAt = JAN_08_2025_KST,
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
            recordedAt = JAN_15_2025_KST,
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
            recordedAt = FEB_01_2025_KST,
            vocabularyLevel = VocabLevel.A1,
            grammarAccuracy = 0.30,
            expressionRange = 2,
            fluencyScore = 0.25,
            naturalnessScore = 0.20,
            sourceEventId = "event-user2-ja",
            syncStatus = SyncStatus.SYNCED
        )
        )
    )

    var queryFailure: Throwable? = null
    var recordFailureCause: Throwable? = null
    var refreshFailureCause: Throwable? = null
    var pendingSyncFailureCause: Throwable? = null
    var refreshSeed: List<StatisticsHistory>? = null

    fun seedHistories(items: List<StatisticsHistory>) {
        // 테스트에서 특정 경계 상태만 보고 싶을 때 기본 seed 를 덮어쓴다.
        histories.value = items
    }

    fun setFetchFailure(cause: Throwable?) {
        queryFailure = cause
    }

    fun setRecordFailure(cause: Throwable?) {
        recordFailureCause = cause
    }

    fun setRefreshFailure(cause: Throwable?) {
        refreshFailureCause = cause
    }

    fun setPendingSyncFailure(cause: Throwable?) {
        pendingSyncFailureCause = cause
    }

    fun seedRefreshHistories(items: List<StatisticsHistory>) {
        // refresh 결과는 local cache 보정용이라, 테스트에서 remote 최신본만 따로 주입할 수 있어야 한다.
        refreshSeed = items
    }

    override fun observeHistory(userId: String, language: LangCode): Flow<StatisticsHistoryState> {
        // fake도 실제 repository처럼 userId + language 조합으로만 보여준다.
        // fetchFailure가 설정되면 화면이 Retry 상태를 재현할 수 있어야 한다.
        queryFailure?.let { return flowOf(StatisticsHistoryState.Retry(it)) }

        // Room observe처럼 저장소 변경이 다시 emit되어야 STAT-004 refresh 흐름을 검증할 수 있다.
        return histories.map { currentHistories ->
            val filtered = currentHistories
                .filter { it.userId == userId && it.language == language }
                .sortedBy { it.recordedAt }

            val debugFallback = if (filtered.isEmpty() && BuildConfig.DEBUG) {
                // 실제 앱 실행에서 Firebase uid가 달라도 chart 예시를 바로 볼 수 있도록
                // debug에서는 언어 기준 샘플을 한 번 더 허용한다.
                currentHistories
                    .filter { it.language == language }
                    .sortedBy { it.recordedAt }
            } else {
                filtered
            }

            when {
                debugFallback.isEmpty() -> StatisticsHistoryState.Empty
                else -> StatisticsHistoryState.Content(debugFallback)
            }
        }
    }

    override suspend fun recordHistory(history: StatisticsHistory): Result<StatisticsHistoryRecordResult> {
        recordFailureCause?.let { return Result.failure(it) }

        val currentHistories = histories.value.toMutableList()
        val existingIndex = currentHistories.indexOfFirst { it.id == history.id }
        // 같은 id 가 다시 들어오면 새 row 를 추가하지 않고 덮어써서,
        // idempotent 하게 저장되는 것처럼 행동한다.
        val applied = existingIndex == -1
        if (applied) {
            currentHistories.add(history)
        } else {
            currentHistories[existingIndex] = history
        }
        // 실제 Room과 같이 local write 이후 observeHistory 구독자에게 새 snapshot을 흘린다.
        histories.value = currentHistories

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

    override suspend fun refreshHistory(userId: String, language: LangCode): Result<Unit> {
        refreshFailureCause?.let { return Result.failure(it) }

        val currentHistories = histories.value.toMutableList()
        val remoteSnapshot = (refreshSeed ?: currentHistories)
            .filter { it.userId == userId && it.language == language }
            .sortedBy { it.recordedAt }

        remoteSnapshot.forEach { remoteHistory ->
            val existingIndex = currentHistories.indexOfFirst { it.id == remoteHistory.id }
            // refresh는 local pending row를 지우지 않고, remote에 있는 최신 스냅샷만 덮어쓴다.
            if (existingIndex == -1) {
                currentHistories.add(remoteHistory.copy(syncStatus = SyncStatus.SYNCED))
            } else {
                currentHistories[existingIndex] = remoteHistory.copy(syncStatus = SyncStatus.SYNCED)
            }
        }
        // remote -> local 보정 결과가 기존 observe stream에 다시 전달되도록 한다.
        histories.value = currentHistories

        return Result.success(Unit)
    }

    override suspend fun syncPendingHistories(userId: String): Result<Int> {
        pendingSyncFailureCause?.let { return Result.failure(it) }

        var syncedCount = 0
        histories.value = histories.value.map { history ->
            // real repository처럼 같은 userId의 PENDING row만 Firestore write-back 성공 상태로 바꾼다.
            if (history.userId == userId && history.syncStatus == SyncStatus.PENDING) {
                syncedCount += 1
                history.copy(syncStatus = SyncStatus.SYNCED)
            } else {
                history
            }
        }

        return Result.success(syncedCount)
    }

    private companion object {
        private const val DAY = 24 * 60 * 60 * 1_000L
        private const val JAN_01_2025_KST = 1_735_657_200_000L
        private const val JAN_03_2025_KST = JAN_01_2025_KST + 2 * DAY
        private const val JAN_05_2025_KST = JAN_01_2025_KST + 4 * DAY
        private const val JAN_07_2025_KST = JAN_01_2025_KST + 6 * DAY
        private const val JAN_08_2025_KST = JAN_01_2025_KST + 7 * DAY
        private const val JAN_10_2025_KST = JAN_01_2025_KST + 9 * DAY
        private const val JAN_12_2025_KST = JAN_01_2025_KST + 11 * DAY
        private const val JAN_14_2025_KST = JAN_01_2025_KST + 13 * DAY
        private const val JAN_15_2025_KST = JAN_01_2025_KST + 14 * DAY
        private const val JAN_17_2025_KST = JAN_01_2025_KST + 16 * DAY
        private const val JAN_21_2025_KST = JAN_01_2025_KST + 20 * DAY
        private const val JAN_28_2025_KST = JAN_01_2025_KST + 27 * DAY
        private const val FEB_01_2025_KST = JAN_01_2025_KST + 31 * DAY
        private const val FEB_04_2025_KST = JAN_01_2025_KST + 34 * DAY
        private const val FEB_11_2025_KST = JAN_01_2025_KST + 41 * DAY
        private const val FEB_18_2025_KST = JAN_01_2025_KST + 48 * DAY
        private const val FEB_25_2025_KST = JAN_01_2025_KST + 55 * DAY
        private const val MAR_04_2025_KST = JAN_01_2025_KST + 62 * DAY
    }
}
