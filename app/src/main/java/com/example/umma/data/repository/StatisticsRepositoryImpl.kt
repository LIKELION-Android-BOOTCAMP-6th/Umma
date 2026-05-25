package com.example.umma.data.repository

import com.example.umma.data.source.local.StatisticsHistoryEntity
import com.example.umma.data.source.local.StatisticsHistoryLocalDataSource
import com.example.umma.data.source.local.toDomain
import com.example.umma.data.model.statistics.toDomain as dtoToDomain
import com.example.umma.data.source.remote.StatisticsHistoryRemoteDataSource
import com.example.umma.domain.model.learningstate.LangCode
import com.example.umma.domain.model.learningstate.SyncStatus
import com.example.umma.domain.model.statistics.StatisticsHistory
import com.example.umma.domain.model.statistics.StatisticsHistoryRecordResult
import com.example.umma.domain.model.statistics.StatisticsHistoryState
import com.example.umma.domain.repository.StatisticsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Statistics history의 local-first 저장과 조회를 담당하는 repository 구현체다.
 */
@Singleton
class StatisticsRepositoryImpl @Inject constructor(
    private val localDataSource: StatisticsHistoryLocalDataSource,
    private val remoteDataSource: StatisticsHistoryRemoteDataSource
) : StatisticsRepository {

    override fun observeHistory(userId: String, language: LangCode): Flow<StatisticsHistoryState> {
        return localDataSource.observeHistory(userId, language.code)
            .map { entities ->
                // local cache 에 저장된 entity 를 domain snapshot 으로 바꾼 뒤,
                // 화면은 조회 결과의 존재 여부만 보고 Empty/Content 를 나눈다.
                val histories = entities.map { it.toDomain() }
                when {
                    histories.isEmpty() -> StatisticsHistoryState.Empty
                    else -> StatisticsHistoryState.Content(histories)
                }
            }
            .catch { throwable ->
                emit(StatisticsHistoryState.Retry(throwable))
            }
    }

    override suspend fun recordHistory(
        history: StatisticsHistory
    ): Result<StatisticsHistoryRecordResult> {
        return try {
            // 1) local-first 원칙에 따라 먼저 PENDING 상태로 캐시에 남긴다.
            //    이 단계가 끝나야 화면/재진입이 history 를 즉시 읽을 수 있다.
            val pendingHistory = history.copy(syncStatus = SyncStatus.PENDING)
            localDataSource.saveHistory(pendingHistory.toEntity())

            // 2) local 저장이 끝난 뒤에만 Firestore mirror 로 보낸다.
            //    remote sync 는 실패할 수 있으므로 반환 결과와 local pending 상태를 분리한다.
            val remoteResult = remoteDataSource.syncHistory(history.copy(syncStatus = SyncStatus.SYNCED))
            var isSyncPending = remoteResult.isFailure

            if (remoteResult.isSuccess) {
                // remote 가 성공하면 local 상태도 SYNCED 로 정리한다.
                // 여기서 실패해도 이미 저장된 history 자체는 유지하고 pending 으로 남긴다.
                runCatching {
                    localDataSource.markSynced(history.userId, listOf(history.id))
                }.onFailure {
                    // local markSynced 실패는 remote가 성공했더라도 local pending 상태를 유지하게 한다.
                    isSyncPending = true
                }
            }

            Result.success(
                StatisticsHistoryRecordResult(
                    historyId = history.id,
                    sourceEventId = history.sourceEventId,
                    applied = true,
                    isSyncPending = isSyncPending,
                    recordedAt = history.recordedAt
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refreshHistory(
        userId: String,
        language: LangCode
    ): Result<Unit> {
        return try {
            // refresh는 Firestore 최신 스냅샷을 local cache에 보정하는 역할만 한다.
            // local pending history는 삭제하지 않고, remote에 있는 최신 row만 덮어쓴다.
            val remoteHistories = remoteDataSource.fetchHistory(userId, language).getOrThrow()
            val entities = remoteHistories
                // remote DTO는 먼저 domain snapshot으로 바꿔서 syncStatus 같은 정책 값을
                // 저장 계층과 분리한 뒤 다시 entity 로 내려보낸다.
                .map { it.dtoToDomain().copy(syncStatus = SyncStatus.SYNCED) }
                .map { it.toEntity() }

            localDataSource.saveHistories(entities)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private fun StatisticsHistory.toEntity(): StatisticsHistoryEntity {
    return StatisticsHistoryEntity(
        id = id,
        userId = userId,
        language = language.code,
        recordedAt = recordedAt,
        vocabularyLevel = vocabularyLevel.name,
        grammarAccuracy = grammarAccuracy,
        expressionRange = expressionRange,
        fluencyScore = fluencyScore,
        naturalnessScore = naturalnessScore,
        sourceEventId = sourceEventId,
        syncStatus = syncStatus.name
    )
}
