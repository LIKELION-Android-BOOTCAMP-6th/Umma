package com.app.umma.data.repository

import com.app.umma.data.source.local.ChatUsageLocalDataSource
import com.app.umma.data.source.local.toDomain
import com.app.umma.data.source.local.toEntity
import com.app.umma.data.source.remote.ChatUsageRemoteDataSource
import com.app.umma.domain.model.learningstate.SyncStatus
import com.app.umma.domain.model.realtime.ChatTokenUsage
import com.app.umma.domain.model.realtime.ChatUsageKind
import com.app.umma.domain.model.realtime.ChatUsageRecord
import com.app.umma.domain.model.realtime.ChatUsageSessionAggregate
import com.app.umma.domain.repository.ChatUsageRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Chat usage의 local-first 저장과 Firestore aggregate sync를 담당합니다.
 *
 * SessionMemory는 "사용자가 어떤 대화를 했는가"의 source of truth이고,
 * 이 repository는 "그 대화에 OpenAI 비용이 얼마나 발생했는가"를 기록하는 보조 저장소입니다.
 * 그래서 local 저장/remote sync 실패가 대화 화면이나 final turn 저장 흐름을 막지 않도록 설계합니다.
 */
@Singleton
class ChatUsageRepositoryImpl @Inject constructor(
    private val localDataSource: ChatUsageLocalDataSource,
    private val remoteDataSource: ChatUsageRemoteDataSource
) : ChatUsageRepository {

    override suspend fun recordUsage(record: ChatUsageRecord): Result<Unit> {
        return runCatching {
            // usage는 항상 local PENDING으로 먼저 남긴다.
            // Firestore write는 세션 종료 시 합산해서 수행하므로 turn마다 원격 비용을 만들지 않는다.
            localDataSource.saveUsage(record.copy(syncStatus = SyncStatus.PENDING).toEntity())
        }
    }

    override suspend fun syncSessionUsage(userId: String, sessionId: String): Result<Int> {
        return runCatching {
            // 특정 세션에 새 pending row가 있는지 먼저 확인한다.
            // pending이 없다면 Firestore에 다시 올릴 변경분이 없는 상태다.
            val pendingRecords = localDataSource
                .getPendingUsageForSession(userId = userId, sessionId = sessionId)
                .map { it.toDomain() }

            if (pendingRecords.isEmpty()) return@runCatching 0

            // Firestore aggregate 문서는 sessionId 하나로 덮어쓴다.
            // 그래서 pending row만 합산하면 이전에 sync된 turn usage가 원격 aggregate에서 사라질 수 있다.
            val sessionRecords = localDataSource
                .getUsageForSession(userId = userId, sessionId = sessionId)
                .map { it.toDomain() }
            val aggregate = sessionRecords.toSessionAggregate()

            // remote sync가 성공한 뒤에만 local 원본을 SYNCED로 바꾼다.
            // 실패하면 예외가 호출자에게 전달되고 row는 PENDING으로 남아 다음 retry 대상이 된다.
            remoteDataSource.syncSessionAggregate(aggregate).getOrThrow()
            localDataSource.markSynced(
                userId = userId,
                recordIds = pendingRecords.map { it.id },
                syncedAt = aggregate.syncedAt
            )

            pendingRecords.size
        }
    }

    override suspend fun syncPendingUsage(userId: String): Result<Int> {
        return runCatching {
            // pending session 목록만 먼저 읽어 세션 단위 aggregate 원칙을 유지한다.
            // 개별 row를 하나씩 올리면 Firestore write 비용과 원격 문서 수가 불필요하게 늘어난다.
            val pendingSessionIds = localDataSource.getPendingSessionIds(
                userId = userId,
                limit = PENDING_SESSION_SYNC_LIMIT
            )

            var syncedCount = 0
            var firstFailure: Throwable? = null

            pendingSessionIds.forEach { pendingSessionId ->
                val result = syncSessionUsage(userId = userId, sessionId = pendingSessionId)
                result
                    .onSuccess { syncedCount += it }
                    .onFailure { failure ->
                        // 하나의 세션 sync가 실패해도 다른 세션은 계속 시도한다.
                        // 이 방식이면 일부 네트워크/권한 오류가 전체 pending queue를 막지 않는다.
                        if (firstFailure == null) firstFailure = failure
                    }
            }

            if (syncedCount == 0) {
                firstFailure?.let { throw it }
            }

            syncedCount
        }
    }

    override suspend fun cleanupSyncedUsage(userId: String): Result<Int> {
        return runCatching {
            // cleanup은 local 저장공간 관리용이다.
            // remote aggregate에 반영되지 않은 PENDING row는 삭제 대상에서 제외한다.
            localDataSource.cleanupSyncedUsage(
                userId = userId,
                retentionMillis = LOCAL_SYNCED_USAGE_RETENTION_MILLIS,
                maxRows = LOCAL_USAGE_MAX_ROWS,
                now = System.currentTimeMillis()
            )
        }
    }

    override suspend fun clearLocal(): Result<Unit> = runCatching {
        // 회원탈퇴 후에는 remote aggregate 재시도보다 계정 경계 정리가 우선이므로 local usage 원본을 모두 지운다.
        localDataSource.clearAll()
    }

    private fun List<ChatUsageRecord>.toSessionAggregate(): ChatUsageSessionAggregate {
        val sortedRecords = sortedBy { it.createdAt }
        val firstRecord = sortedRecords.first()
        val lastRecord = sortedRecords.last()
        val responseRecords = sortedRecords.filter { it.kind == ChatUsageKind.RESPONSE }
        val transcriptionRecords = sortedRecords.filter { it.kind == ChatUsageKind.TRANSCRIPTION }

        return ChatUsageSessionAggregate(
            id = firstRecord.sessionId,
            userId = firstRecord.userId,
            sessionId = firstRecord.sessionId,
            language = firstRecord.language,
            // 하나의 session은 하나의 realtime model로 시작한다는 현재 계약을 기준으로 첫 record 모델을 대표값으로 둔다.
            model = firstRecord.model,
            startedAt = firstRecord.createdAt,
            endedAt = lastRecord.createdAt,
            recordCount = sortedRecords.size,
            responseCount = responseRecords.size,
            transcriptionCount = transcriptionRecords.size,
            responseUsage = responseRecords.sumUsage(),
            transcriptionUsage = transcriptionRecords.sumUsage(),
            // pricingVersion도 동일 세션에서는 같은 정책이어야 하므로 첫 non-null 값을 대표값으로 둔다.
            pricingVersion = sortedRecords.firstNotNullOfOrNull { it.pricingVersion },
            syncedAt = System.currentTimeMillis()
        )
    }

    private fun List<ChatUsageRecord>.sumUsage(): ChatTokenUsage {
        // null은 "API가 해당 breakdown을 제공하지 않음"이라는 뜻이다.
        // 모든 값이 null이면 aggregate도 null로 유지하고, 하나라도 값이 있으면 숫자만 합산한다.
        return ChatTokenUsage(
            totalTokens = sumNullable { it.usage.totalTokens },
            inputTokens = sumNullable { it.usage.inputTokens },
            outputTokens = sumNullable { it.usage.outputTokens },
            inputTextTokens = sumNullable { it.usage.inputTextTokens },
            inputAudioTokens = sumNullable { it.usage.inputAudioTokens },
            inputCachedTokens = sumNullable { it.usage.inputCachedTokens },
            outputTextTokens = sumNullable { it.usage.outputTextTokens },
            outputAudioTokens = sumNullable { it.usage.outputAudioTokens }
        )
    }

    private fun List<ChatUsageRecord>.sumNullable(
        selector: (ChatUsageRecord) -> Long?
    ): Long? {
        var hasValue = false
        val total = fold(0L) { acc, record ->
            val value = selector(record) ?: return@fold acc
            hasValue = true
            acc + value
        }
        return if (hasValue) total else null
    }

    private companion object {
        const val PENDING_SESSION_SYNC_LIMIT = 20
        const val LOCAL_SYNCED_USAGE_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1_000L
        const val LOCAL_USAGE_MAX_ROWS = 10_000
    }
}
