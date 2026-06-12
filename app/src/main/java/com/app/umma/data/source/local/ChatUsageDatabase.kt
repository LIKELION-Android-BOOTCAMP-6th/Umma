package com.app.umma.data.source.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.SyncStatus
import com.app.umma.domain.model.realtime.ChatTokenUsage
import com.app.umma.domain.model.realtime.ChatUsageKind
import com.app.umma.domain.model.realtime.ChatUsageRecord
import javax.inject.Inject

/**
 * AI Chat usage의 local-first 원본 row입니다.
 *
 * Firestore에는 세션 단위 aggregate만 올리지만, 비용 재계산과 sync 재시도를 위해 local에는
 * response/transcription usage 이벤트를 원본에 가깝게 보존합니다.
 */
@Entity(tableName = "chat_usage_records", primaryKeys = ["userId", "id"])
data class ChatUsageRecordEntity(
    val id: String,
    val userId: String,
    val sessionId: String,
    val turnId: String?,
    val language: String,
    val kind: String,
    val model: String,
    val transcriptionModel: String?,
    val createdAt: Long,
    val totalTokens: Long?,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val inputTextTokens: Long?,
    val inputAudioTokens: Long?,
    val inputCachedTokens: Long?,
    val outputTextTokens: Long?,
    val outputAudioTokens: Long?,
    val pricingVersion: String?,
    val syncStatus: String,
    val syncedAt: Long?
)

@Dao
interface ChatUsageDao {
    /**
     * 같은 usage id가 다시 들어오면 기존 row를 유지한다.
     *
     * Realtime callback 재전달이나 ViewModel 재구독이 생겨도 중복 row를 만들지 않고,
     * 이미 SYNCED 처리된 row를 다시 PENDING으로 되돌리지 않는 idempotent 경계입니다.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUsage(record: ChatUsageRecordEntity)

    /**
     * 특정 세션에서 아직 Firestore aggregate에 반영되지 않은 usage만 조회합니다.
     */
    @Query(
        """
        SELECT *
        FROM chat_usage_records
        WHERE userId = :userId
          AND sessionId = :sessionId
          AND syncStatus = :pendingStatus
        ORDER BY createdAt ASC, id ASC
        """
    )
    suspend fun getPendingUsageForSession(
        userId: String,
        sessionId: String,
        pendingStatus: String
    ): List<ChatUsageRecordEntity>

    /**
     * 특정 세션의 전체 usage를 조회합니다.
     *
     * Firestore session aggregate는 문서 하나를 덮어쓰는 방식이므로,
     * pending row만 합산하면 이전에 sync된 usage가 원격 aggregate에서 사라질 수 있습니다.
     */
    @Query(
        """
        SELECT *
        FROM chat_usage_records
        WHERE userId = :userId
          AND sessionId = :sessionId
        ORDER BY createdAt ASC, id ASC
        """
    )
    suspend fun getUsageForSession(
        userId: String,
        sessionId: String
    ): List<ChatUsageRecordEntity>

    /**
     * 사용자 단위 pending sessionId를 조회해 이전 세션의 밀린 sync를 재시도할 수 있게 합니다.
     */
    @Query(
        """
        SELECT DISTINCT sessionId
        FROM chat_usage_records
        WHERE userId = :userId
          AND syncStatus = :pendingStatus
        ORDER BY sessionId ASC
        LIMIT :limit
        """
    )
    suspend fun getPendingSessionIds(
        userId: String,
        pendingStatus: String,
        limit: Int
    ): List<String>

    /**
     * Firestore aggregate sync에 성공한 local 원본 row만 SYNCED로 정리합니다.
     */
    @Query(
        """
        UPDATE chat_usage_records
        SET syncStatus = :status,
            syncedAt = :syncedAt
        WHERE userId = :userId
          AND id IN (:recordIds)
        """
    )
    suspend fun markSynced(
        userId: String,
        recordIds: List<String>,
        status: String,
        syncedAt: Long
    )

    /**
     * sync가 끝난 뒤 보관 기간을 넘긴 원본 usage row를 삭제합니다.
     *
     * PENDING row는 remote aggregate에 아직 반영되지 않은 원본이므로 이 쿼리의 대상이 아닙니다.
     */
    @Query(
        """
        DELETE FROM chat_usage_records
        WHERE userId = :userId
          AND syncStatus = :syncedStatus
          AND syncedAt IS NOT NULL
          AND syncedAt < :cutoffSyncedAt
        """
    )
    suspend fun deleteSyncedBefore(
        userId: String,
        syncedStatus: String,
        cutoffSyncedAt: Long
    ): Int

    /**
     * 사용자별 전체 usage row 수를 계산합니다.
     *
     * 보관 기간 조건만으로는 비정상적으로 많은 테스트/대화가 쌓이는 상황을 막기 어려워
     * 최대 row 수 제한을 함께 적용합니다.
     */
    @Query(
        """
        SELECT COUNT(*)
        FROM chat_usage_records
        WHERE userId = :userId
        """
    )
    suspend fun countRows(userId: String): Int

    /**
     * 최대 row 수를 넘긴 경우 오래된 SYNCED row부터 삭제합니다.
     *
     * PENDING row는 보존해야 하므로, 삭제 가능한 SYNCED row만 오래된 순서로 제한 삭제합니다.
     */
    @Query(
        """
        DELETE FROM chat_usage_records
        WHERE userId = :userId
          AND id IN (
              SELECT id
              FROM chat_usage_records
              WHERE userId = :userId
                AND syncStatus = :syncedStatus
              ORDER BY syncedAt ASC, createdAt ASC, id ASC
              LIMIT :deleteCount
          )
        """
    )
    suspend fun deleteOldestSyncedRows(
        userId: String,
        syncedStatus: String,
        deleteCount: Int
    ): Int

    /**
     * 계정 삭제 후 설치 내부에 남은 usage 원본을 모두 제거합니다.
     *
     * 탈퇴 시점에는 원격 사용자 문서가 이미 정리 대상이므로 PENDING row도 더 이상 sync하지 않는다.
     */
    @Query("DELETE FROM chat_usage_records")
    suspend fun clearAll()
}

/**
 * Chat usage 전용 Room database입니다.
 *
 * 기존 SessionMemory/Statistics DB와 분리해 usage schema 변경이 대화 저장소나 통계 저장소 migration에
 * 영향을 주지 않게 합니다.
 */
@Database(
    entities = [ChatUsageRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ChatUsageDatabase : RoomDatabase() {
    abstract fun chatUsageDao(): ChatUsageDao
}

/**
 * Chat usage local 저장과 pending 상태 변경을 감싸는 data source입니다.
 */
class ChatUsageLocalDataSource @Inject constructor(
    private val database: ChatUsageDatabase,
    private val dao: ChatUsageDao
) {
    suspend fun saveUsage(record: ChatUsageRecordEntity) {
        // usage record 저장은 사용자의 대화 UX를 막지 않는 보조 데이터지만,
        // local 원본이 반쯤 저장되는 상태는 피하기 위해 transaction으로 감싼다.
        database.withTransaction {
            dao.insertUsage(record)
        }
    }

    suspend fun getPendingUsageForSession(
        userId: String,
        sessionId: String
    ): List<ChatUsageRecordEntity> {
        return dao.getPendingUsageForSession(
            userId = userId,
            sessionId = sessionId,
            pendingStatus = SyncStatus.PENDING.name
        )
    }

    suspend fun getUsageForSession(
        userId: String,
        sessionId: String
    ): List<ChatUsageRecordEntity> {
        return dao.getUsageForSession(
            userId = userId,
            sessionId = sessionId
        )
    }

    suspend fun getPendingSessionIds(userId: String, limit: Int): List<String> {
        return dao.getPendingSessionIds(
            userId = userId,
            pendingStatus = SyncStatus.PENDING.name,
            limit = limit
        )
    }

    suspend fun markSynced(userId: String, recordIds: List<String>, syncedAt: Long) {
        if (recordIds.isEmpty()) return
        // aggregate sync가 성공한 row만 SYNCED로 바꾼다. 실패 row는 PENDING으로 남아 재시도된다.
        dao.markSynced(
            userId = userId,
            recordIds = recordIds,
            status = SyncStatus.SYNCED.name,
            syncedAt = syncedAt
        )
    }

    suspend fun cleanupSyncedUsage(
        userId: String,
        retentionMillis: Long,
        maxRows: Int,
        now: Long
    ): Int {
        return database.withTransaction {
            // 1차 정리: 보관 기간을 넘긴 SYNCED row를 삭제한다.
            // 아직 원격 반영이 안 된 PENDING row는 절대 삭제하지 않는다.
            var deletedCount = dao.deleteSyncedBefore(
                userId = userId,
                syncedStatus = SyncStatus.SYNCED.name,
                cutoffSyncedAt = now - retentionMillis
            )

            // 2차 정리: 테스트나 장시간 사용으로 row 수가 과도해진 경우 오래된 SYNCED row를 추가 삭제한다.
            // PENDING row가 많아 maxRows를 넘는 경우에는 데이터 유실을 피하기 위해 더 삭제하지 않는다.
            val overflowCount = (dao.countRows(userId) - maxRows).coerceAtLeast(0)
            if (overflowCount > 0) {
                deletedCount += dao.deleteOldestSyncedRows(
                    userId = userId,
                    syncedStatus = SyncStatus.SYNCED.name,
                    deleteCount = overflowCount
                )
            }

            deletedCount
        }
    }

    suspend fun clearAll() {
        // userId가 없는 정리 시점에도 안전하게 전체 테이블을 비우기 위해 DAO clear를 감싼다.
        database.withTransaction {
            dao.clearAll()
        }
    }
}

fun ChatUsageRecordEntity.toDomain(): ChatUsageRecord {
    return ChatUsageRecord(
        id = id,
        userId = userId,
        sessionId = sessionId,
        turnId = turnId,
        language = LangCode.fromCode(language) ?: LangCode.EN,
        kind = ChatUsageKind.valueOf(kind),
        model = model,
        transcriptionModel = transcriptionModel,
        createdAt = createdAt,
        usage = ChatTokenUsage(
            totalTokens = totalTokens,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            inputTextTokens = inputTextTokens,
            inputAudioTokens = inputAudioTokens,
            inputCachedTokens = inputCachedTokens,
            outputTextTokens = outputTextTokens,
            outputAudioTokens = outputAudioTokens
        ),
        pricingVersion = pricingVersion,
        syncStatus = SyncStatus.valueOf(syncStatus)
    )
}

fun ChatUsageRecord.toEntity(): ChatUsageRecordEntity {
    return ChatUsageRecordEntity(
        id = id,
        userId = userId,
        sessionId = sessionId,
        turnId = turnId,
        language = language.code,
        kind = kind.name,
        model = model,
        transcriptionModel = transcriptionModel,
        createdAt = createdAt,
        totalTokens = usage.totalTokens,
        inputTokens = usage.inputTokens,
        outputTokens = usage.outputTokens,
        inputTextTokens = usage.inputTextTokens,
        inputAudioTokens = usage.inputAudioTokens,
        inputCachedTokens = usage.inputCachedTokens,
        outputTextTokens = usage.outputTextTokens,
        outputAudioTokens = usage.outputAudioTokens,
        pricingVersion = pricingVersion,
        syncStatus = syncStatus.name,
        syncedAt = null
    )
}
