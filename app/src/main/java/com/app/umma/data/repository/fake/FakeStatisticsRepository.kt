package com.app.umma.data.repository.fake

import com.app.umma.BuildConfig
import com.app.umma.data.repository.fake.demo.statistics.StatisticsDemoPreset
import com.app.umma.data.repository.fake.demo.statistics.StatisticsDemoPresetConfig
import com.app.umma.data.repository.fake.demo.statistics.StatisticsHistoryFixtures
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.SyncStatus
import com.app.umma.domain.model.statistics.StatisticsHistory
import com.app.umma.domain.model.statistics.StatisticsHistoryRecordResult
import com.app.umma.domain.model.statistics.StatisticsHistoryState
import com.app.umma.domain.repository.StatisticsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * StatisticsRepository fake implementation.
 *
 * 이 클래스는 저장소처럼 observe/record/refresh/sync 동작만 담당한다.
 * 통계 데모 시나리오별 데이터는 StatisticsHistoryFixtures와 StatisticsDemoPreset에 둔다.
 */
@Singleton
class FakeStatisticsRepository @Inject constructor() : StatisticsRepository {

    private val normalHistories: List<StatisticsHistory> = StatisticsHistoryFixtures.normalHistories()
    private val histories = MutableStateFlow(normalHistories)

    var queryFailure: Throwable? = null
    var recordFailureCause: Throwable? = null
    var refreshFailureCause: Throwable? = null
    var pendingSyncFailureCause: Throwable? = null
    var refreshSeed: List<StatisticsHistory>? = null
    private var refreshNoOp: Boolean = false
    private var delayedHistoryLanguages: Set<LangCode> = emptySet()

    init {
        applyPreset(StatisticsDemoPresetConfig.activePreset)
    }

    /**
     * STAT-006 데모 시나리오 하나를 repository state로 적용한다.
     */
    fun applyPreset(preset: StatisticsDemoPreset) {
        resetPresetHooks()
        when (preset) {
            StatisticsDemoPreset.NormalStatistics -> seedHistories(normalHistories)
            StatisticsDemoPreset.ExpressionRangeOverflow -> seedHistories(
                StatisticsHistoryFixtures.expressionRangeOverflowHistories()
            )
            StatisticsDemoPreset.DelayedLanguageSwitch -> {
                seedHistories(normalHistories.filter { it.language == LangCode.EN })
                // EN 응답을 늦춰 언어 전환 후 stale result 방어를 확인한다.
                delayedHistoryLanguages = setOf(LangCode.EN)
            }
            StatisticsDemoPreset.ShortHistory -> seedHistories(
                listOf(normalHistories.first { it.language == LangCode.EN })
            )
            StatisticsDemoPreset.EmptyHistory -> seedHistories(emptyList())
            StatisticsDemoPreset.PendingSyncFailure -> {
                seedHistories(StatisticsHistoryFixtures.pendingHistories())
                setPendingSyncFailure(IllegalStateException("pending sync 실패"))
                // pending 실패 후 상태 유지가 핵심이므로 refresh가 pending row를 SYNCED로 바꾸면 안 된다.
                refreshNoOp = true
            }
            StatisticsDemoPreset.FetchFailure -> {
                seedHistories(normalHistories)
                setFetchFailure(IllegalStateException("history 조회 실패"))
            }
            StatisticsDemoPreset.RefreshFailure -> {
                seedHistories(normalHistories)
                setRefreshFailure(IllegalStateException("refresh 실패"))
            }
            StatisticsDemoPreset.InitialExternalMetrics,
            StatisticsDemoPreset.MissingSelectedLanguage,
            StatisticsDemoPreset.MissingCurrentLangState -> seedHistories(normalHistories)
            StatisticsDemoPreset.DelayedMetricSwitch -> {
                seedHistories(normalHistories)
                // repository는 metric type을 직접 받지 않으므로 history 응답 지연으로 stale chart 요청을 재현한다.
                delayedHistoryLanguages = LangCode.entries.toSet()
            }
        }
    }

    fun seedHistories(items: List<StatisticsHistory>) {
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
        refreshSeed = items
    }

    override fun observeHistory(userId: String, language: LangCode): Flow<StatisticsHistoryState> {
        queryFailure?.let { return flowOf(StatisticsHistoryState.Retry(it)) }

        if (language in delayedHistoryLanguages) {
            return flow {
                // 취소된 chart/language 요청이면 delay 이후 emit이 최신 UI에 반영되지 않아야 한다.
                delay(DELAYED_HISTORY_MS)
                emit(toHistoryState(histories.value, userId, language))
            }
        }

        return histories.map { currentHistories ->
            toHistoryState(currentHistories, userId, language)
        }
    }

    override suspend fun recordHistory(history: StatisticsHistory): Result<StatisticsHistoryRecordResult> {
        recordFailureCause?.let { return Result.failure(it) }

        val currentHistories = histories.value.toMutableList()
        val existingIndex = currentHistories.indexOfFirst { it.id == history.id }
        val applied = existingIndex == -1
        if (applied) {
            currentHistories.add(history)
        } else {
            currentHistories[existingIndex] = history
        }
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
        if (refreshNoOp) return Result.success(Unit)

        val currentHistories = histories.value.toMutableList()
        val remoteSnapshot = (refreshSeed ?: currentHistories)
            .filter { it.userId == userId && it.language == language }
            .sortedBy { it.recordedAt }

        remoteSnapshot.forEach { remoteHistory ->
            val existingIndex = currentHistories.indexOfFirst { it.id == remoteHistory.id }
            if (existingIndex == -1) {
                currentHistories.add(remoteHistory.copy(syncStatus = SyncStatus.SYNCED))
            } else {
                currentHistories[existingIndex] = remoteHistory.copy(syncStatus = SyncStatus.SYNCED)
            }
        }
        histories.value = currentHistories

        return Result.success(Unit)
    }

    override suspend fun syncPendingHistories(userId: String): Result<Int> {
        pendingSyncFailureCause?.let { return Result.failure(it) }

        var syncedCount = 0
        histories.value = histories.value.map { history ->
            if (history.userId == userId && history.syncStatus == SyncStatus.PENDING) {
                syncedCount += 1
                history.copy(syncStatus = SyncStatus.SYNCED)
            } else {
                history
            }
        }

        return Result.success(syncedCount)
    }

    private fun resetPresetHooks() {
        queryFailure = null
        recordFailureCause = null
        refreshFailureCause = null
        pendingSyncFailureCause = null
        refreshSeed = null
        refreshNoOp = false
        delayedHistoryLanguages = emptySet()
    }

    private fun toHistoryState(
        currentHistories: List<StatisticsHistory>,
        userId: String,
        language: LangCode
    ): StatisticsHistoryState {
        val filtered = currentHistories
            .filter { it.userId == userId && it.language == language }
            .sortedBy { it.recordedAt }

        val debugFallback = if (filtered.isEmpty() && BuildConfig.DEBUG) {
            // 실제 Firebase uid가 샘플 uid와 달라도 mockDebug 화면 검증은 바로 가능해야 한다.
            currentHistories
                .filter { it.language == language }
                .sortedBy { it.recordedAt }
        } else {
            filtered
        }

        return when {
            debugFallback.isEmpty() -> StatisticsHistoryState.Empty
            else -> StatisticsHistoryState.Content(debugFallback)
        }
    }

    private companion object {
        private const val DELAYED_HISTORY_MS = 1_500L
    }
}
