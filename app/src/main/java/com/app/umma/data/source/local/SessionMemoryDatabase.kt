package com.app.umma.data.source.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * 로컬 Room 에 저장되는 확정 turn이 Remote에 Sync가 되었는지 판단합니다.
 * @property PENDING 아직 싱크 안됨
 * @property SYNCED 싱크됨
 */
object RemoteSyncStatus {
    const val PENDING = "PENDING"
    const val SYNCED = "SYNCED"
}
/**
 * 로컬 Room 에 저장되는 확정 turn 엔터티입니다.
 */
@Entity(tableName = "session_turns")
data class SessionTurnEntity(
    @PrimaryKey val turnId: String,
    val sessionId: String,
    val userId: String,
    val language: String,
    val text: String,
    val role: String,
    val createdAt: Long,
    val durationMs: Long?,
    val tokenCount: Int?,
    val confidence: Double?,
    val syncStatus: String
)

/**
 * 언어별 Session Memory 메타데이터 엔터티입니다.
 *
 * @property isPendingTurnSync recentFullContext append 이후 원격 sync 필요 여부
 * @property isPendingCompressionSync compression 이후 원격 sync 필요 여부
 */
@Entity(tableName = "session_metadata")
data class SessionMetadataEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val language: String,
    val recentTopicsJson: String,
    val topicSummariesJson: String,
    val topicKeySentencesJson: String,
    val correctionAvailable: Boolean,
    val lastCompressedAt: Long?,
    val updatedAt: Long,
    val isPendingTurnSync: Boolean,
    val isPendingCompressionSync: Boolean
)

/**
 * Session turn DAO 입니다.
 */
@Dao
interface SessionTurnDao {
    /**
     * 확정 turn 을 insert 합니다.
     *
     * @param turn 저장할 turn 엔터티
     * @return 신규 insert rowId, 중복이면 -1
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTurn(turn: SessionTurnEntity): Long

    /**
     * 특정 언어의 turn 목록을 구독합니다.
     *
     * @param userId 사용자 UID
     * @param language 학습 언어 코드
     * @return 최근 turn 목록 Flow
     */
    @Query("SELECT * FROM session_turns WHERE userId = :userId AND language = :language ORDER BY createdAt ASC")
    fun observeTurns(userId: String, language: String): Flow<List<SessionTurnEntity>>

    /**
     * 특정 언어의 turn 목록을 조회합니다.
     *
     * @param userId 사용자 UID
     * @param language 학습 언어 코드
     * @return 최근 turn 목록
     */
    @Query("SELECT * FROM session_turns WHERE userId = :userId AND language = :language ORDER BY createdAt ASC")
    suspend fun getTurns(userId: String, language: String): List<SessionTurnEntity>

    /**
     * 아직 동기화되지 않은 turn 목록을 조회합니다.
     *
     * @param userId 사용자 UID
     * @param language 학습 언어 코드
     * @return 미동기화 turn 목록
     */
    @Query("SELECT * FROM session_turns WHERE userId = :userId AND language = :language AND syncStatus != 'SYNCED'")
    suspend fun getPendingTurns(userId: String, language: String): List<SessionTurnEntity>

    /**
     * syncStatus 를 갱신합니다.
     *
     * @param turnIds 갱신 대상 turn ID 목록
     * @param status 적용할 sync 상태
     */
    @Query("UPDATE session_turns SET syncStatus = :status WHERE turnId IN (:turnIds)")
    suspend fun updateSyncStatus(turnIds: List<String>, status: String)

    /**
     * 특정 turn 목록을 삭제합니다.
     *
     * @param turnIds 삭제할 turn ID 목록
     */
    @Query("DELETE FROM session_turns WHERE turnId IN (:turnIds)")
    suspend fun deleteTurnsByIds(turnIds: List<String>)

    /**
     * 특정 언어의 모든 turn 을 삭제합니다.
     *
     * @param userId 사용자 UID
     * @param language 학습 언어 코드
     */
    @Query("DELETE FROM session_turns WHERE userId = :userId AND language = :language")
    suspend fun clearTurns(userId: String, language: String)
}

/**
 * Session metadata DAO 입니다.
 */
@Dao
interface SessionMetadataDao {
    /**
     * 메타데이터를 upsert 합니다.
     *
     * @param metadata 저장할 메타데이터
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMetadata(metadata: SessionMetadataEntity)

    /**
     * 특정 언어의 메타데이터를 조회합니다.
     *
     * @param userId 사용자 UID
     * @param language 학습 언어 코드
     * @return 메타데이터, 없으면 null
     */
    @Query("SELECT * FROM session_metadata WHERE userId = :userId AND language = :language")
    suspend fun getMetadata(userId: String, language: String): SessionMetadataEntity?
}

/**
 * Session Memory Room 데이터베이스입니다.
 */
@Database(
    entities = [SessionTurnEntity::class, SessionMetadataEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SessionMemoryDatabase : RoomDatabase() {
    /**
     * Session turn DAO 를 반환합니다.
     *
     * @return SessionTurnDao
     */
    abstract fun sessionTurnDao(): SessionTurnDao

    /**
     * Session metadata DAO 를 반환합니다.
     *
     * @return SessionMetadataDao
     */
    abstract fun sessionMetadataDao(): SessionMetadataDao
}
