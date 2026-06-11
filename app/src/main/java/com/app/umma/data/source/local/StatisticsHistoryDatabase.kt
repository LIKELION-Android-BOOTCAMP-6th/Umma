package com.app.umma.data.source.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.app.umma.domain.model.learningstate.ConversationAbilityBand
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.model.learningstate.SyncStatus
import com.app.umma.domain.model.learningstate.VocabLevel
import com.app.umma.domain.model.statistics.StatisticsHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * StatisticsHistory의 local-first 저장을 담당하는 Room 스키마다.
 *
 * STI-002는 Correction 완료 이후 생성된 history를 먼저 local에 남기고,
 * Firestore sync 실패는 pending 상태로 남기는 경계를 요구한다.
 */
@Entity(tableName = "statistics_history", primaryKeys = ["userId", "id"])
data class StatisticsHistoryEntity(
    val id: String,
    val userId: String,
    val language: String,
    val recordedAt: Long,
    val conversationBand: String? = null,
    val vocabularyLevel: String,
    val grammarAccuracy: Double,
    val expressionRange: Int,
    val fluencyScore: Double,
    val naturalnessScore: Double,
    val sourceEventId: String,
    val syncStatus: String
)

@Dao
interface StatisticsHistoryDao {
    /**
     * 같은 userId + id가 이미 있으면 REPLACE로 같은 history를 idempotent하게 덮어쓴다.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: StatisticsHistoryEntity)

    /**
     * remote refresh로 내려온 history 묶음을 local cache에 한 번에 반영한다.
     *
     * REPLACE 전략을 쓰면 같은 history id는 최신 값으로 덮이고,
     * 아직 remote에 없는 local pending history는 그대로 남는다.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistories(history: List<StatisticsHistoryEntity>)

    /**
     * local cache 우선 관찰용 조회다.
     *
     * Statistics 화면은 remote sync 를 직접 기다리지 않고,
     * 이 local snapshot 만으로도 바로 렌더링 가능한 구조를 따른다.
     */
    @Query(
        """
        SELECT *
        FROM statistics_history
        WHERE userId = :userId
          AND language = :language
        ORDER BY recordedAt ASC, id ASC
        """
    )
    fun observeHistory(userId: String, language: String): Flow<List<StatisticsHistoryEntity>>

    /**
     * Firestore write-back이 아직 끝나지 않은 history만 오래된 순서로 가져온다.
     *
     * pending retry는 사용자 단위로 수행하므로, 현재 화면 언어가 아니어도 같은 사용자의 pending을 함께 정리한다.
     */
    @Query(
        """
        SELECT *
        FROM statistics_history
        WHERE userId = :userId
          AND syncStatus = :pendingStatus
        ORDER BY recordedAt ASC, id ASC
        LIMIT :limit
        """
    )
    suspend fun getPendingHistories(
        userId: String,
        pendingStatus: String,
        limit: Int
    ): List<StatisticsHistoryEntity>

    /**
     * pending sync가 끝난 history만 SYNCED로 정리한다.
     */
    @Query(
        """
        UPDATE statistics_history
        SET syncStatus = :status
        WHERE userId = :userId AND id IN (:historyIds)
        """
    )
    suspend fun updateSyncStatus(
        userId: String,
        historyIds: List<String>,
        status: String
    )
}

/**
 * StatisticsHistory용 Room 데이터베이스다.
 */
@Database(
    entities = [StatisticsHistoryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class StatisticsHistoryDatabase : RoomDatabase() {
    abstract fun statisticsHistoryDao(): StatisticsHistoryDao
}

/**
 * local-first history 저장과 pending sync 상태 갱신을 감싼다.
 */
class StatisticsHistoryLocalDataSource @Inject constructor(
    private val database: StatisticsHistoryDatabase,
    private val dao: StatisticsHistoryDao
) {
    fun observeHistory(userId: String, language: String): Flow<List<StatisticsHistoryEntity>> {
        return dao.observeHistory(userId, language)
    }

    suspend fun getPendingHistories(userId: String, limit: Int): List<StatisticsHistoryEntity> {
        // retry 대상만 읽어오고, remote write-back 직전에 domain으로 복원한다.
        return dao.getPendingHistories(
            userId = userId,
            pendingStatus = SyncStatus.PENDING.name,
            limit = limit
        )
    }

    suspend fun saveHistory(history: StatisticsHistoryEntity) {
        // history 단위 저장은 하나의 트랜잭션으로 묶어,
        // 중간 실패 시 syncStatus 만 남는 부분 완료를 막는다.
        database.withTransaction {
            dao.insertHistory(history)
        }
    }

    suspend fun saveHistories(histories: List<StatisticsHistoryEntity>) {
        if (histories.isEmpty()) return
        // refresh 결과는 여러 건이 한번에 들어오므로,
        // local cache 반영도 같은 트랜잭션으로 묶어 중간 상태가 보이지 않게 한다.
        database.withTransaction {
            dao.insertHistories(histories)
        }
    }

    suspend fun markSynced(userId: String, historyIds: List<String>) {
        if (historyIds.isEmpty()) return
        // Firestore mirror 성공 이후에만 local 상태도 SYNCED 로 정리한다.
        dao.updateSyncStatus(userId, historyIds, SyncStatus.SYNCED.name)
    }

    /**
     * 회원탈퇴 시 이 LocalDataSource 가 소유한 모든 Row 를 삭제한다.
     * domain UseCase 가 Room 구현체를 알지 않도록 정리 책임을 이 경계에 위임한다.
     */
    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }
    }
}

fun StatisticsHistoryEntity.toDomain(): StatisticsHistory {
    // 저장소 친화적인 문자열/숫자형 값을 domain enum 으로 복원한다.
    return StatisticsHistory(
        id = id,
        userId = userId,
        language = LangCode.fromCode(language) ?: LangCode.EN,
        recordedAt = recordedAt,
        conversationBand = conversationBand.toConversationAbilityBandOrNull(),
        vocabularyLevel = VocabLevel.valueOf(vocabularyLevel),
        grammarAccuracy = grammarAccuracy,
        expressionRange = expressionRange,
        fluencyScore = fluencyScore,
        naturalnessScore = naturalnessScore,
        sourceEventId = sourceEventId,
        syncStatus = SyncStatus.valueOf(syncStatus)
    )
}

private fun String?.toConversationAbilityBandOrNull(): ConversationAbilityBand? {
    // Room에 이미 저장된 값이 앱 업데이트 사이에서 깨져도 기존 5개 통계 지표는 계속 복원되어야 한다.
    return this?.let { rawValue ->
        runCatching { ConversationAbilityBand.valueOf(rawValue) }.getOrNull()
    }
}
