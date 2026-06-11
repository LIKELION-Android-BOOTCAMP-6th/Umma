package com.app.umma.data.source.local

import androidx.room.withTransaction
import com.app.umma.domain.model.learningstate.TurnSpeaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Session Memory Room 접근을 캡슐화하는 LocalDataSource 입니다.
 */
class SessionMemoryLocalDataSource @Inject constructor(
    private val database: SessionMemoryDatabase,
    private val turnDao: SessionTurnDao,
    private val metadataDao: SessionMetadataDao
) {
    /**
     * append 이후 결과입니다.
     *
     * @property inserted 신규 insert 여부
     */
    data class AppendResult(
        val inserted: Boolean
    )

    /**
     * 확정 turn 을 append 하고 메타데이터를 갱신합니다.
     *
     * - duplicate turnId 는 ignore + success
     * - recentFullContext 는 최대 [maxRecentTurns] 개만 유지
     * - user turn 이 하나라도 존재하면 correctionAvailable = true (추후 AI 응답으로 변경)
     * - append 성공 시 pendingTurnSync 를 true 로 남깁니다.
     *
     * @param turn 저장할 turn 엔터티
     * @param maxRecentTurns 유지할 최대 recent turn 수
     * @return append 결과
     */
    suspend fun appendTurn(
        turn: SessionTurnEntity, maxRecentTurns: Int
    ): AppendResult {
        return database.withTransaction {
            val insertResult = turnDao.insertTurn(turn)
            val inserted = insertResult != -1L

            if (inserted) {
                trimOverflowTurns(
                    userId = turn.userId, language = turn.language, maxRecentTurns = maxRecentTurns
                )

                val metaId = "${turn.userId}_${turn.language}"
                val currentMeta = metadataDao.getMetadata(turn.userId, turn.language)
                val updatedMeta = (currentMeta ?: SessionMetadataEntity(
                    id = metaId,
                    userId = turn.userId,
                    language = turn.language,
                    recentTopicsJson = "[]",
                    topicSummariesJson = "[]",
                    topicKeySentencesJson = "[]",
                    correctionAvailable = false,
                    lastCompressedAt = null,
                    updatedAt = turn.createdAt,
                    isPendingTurnSync = false,
                    isPendingCompressionSync = false
                )).copy(
                    correctionAvailable = currentMeta?.correctionAvailable == true || turn.role == TurnSpeaker.USER.name,
                    updatedAt = turn.createdAt,
                    isPendingTurnSync = true // remote sync 대기중
                )

                metadataDao.insertOrUpdateMetadata(updatedMeta)
            }

            AppendResult(inserted = inserted)
        }
    }

    /**
     * 특정 언어의 turn 목록을 구독합니다.
     *
     * @param userId 사용자 UID
     * @param language 학습 언어 코드
     * @return turn 목록 Flow
     */
    fun observeTurns(userId: String, language: String): Flow<List<SessionTurnEntity>> {
        return turnDao.observeTurns(userId, language)
    }

    /**
     * 특정 언어의 turn 목록을 조회합니다.
     *
     * @param userId 사용자 UID
     * @param language 학습 언어 코드
     * @return turn 목록
     */
    suspend fun getTurns(userId: String, language: String): List<SessionTurnEntity> {
        return turnDao.getTurns(userId, language)
    }

    /**
     * 특정 언어의 미동기화 turn 목록을 조회합니다.
     *
     * @param userId 사용자 UID
     * @param language 학습 언어 코드
     * @return 미동기화 turn 목록
     */
    suspend fun getPendingTurns(userId: String, language: String): List<SessionTurnEntity> {
        return turnDao.getPendingTurns(userId, language)
    }

    /**
     * 지정한 turn 의 sync 상태를 갱신합니다.
     *
     * @param turnIds 갱신 대상 turn ID 목록
     * @param status 적용할 sync 상태
     */
    suspend fun updateSyncStatus(turnIds: List<String>, status: String) {
        turnDao.updateSyncStatus(turnIds, status)
    }

    /**
     * 특정 언어의 turn 목록을 비우고 메타데이터를 갱신합니다.
     *
     * - local buffer 는 즉시 비움
     * - compression sync 실패는 pendingCompressionSync 로 남김
     *
     * @param metadata 저장할 최신 메타데이터
     */
    suspend fun compress(metadata: SessionMetadataEntity) {
        database.withTransaction {
            turnDao.clearTurns(metadata.userId, metadata.language)
            metadataDao.insertOrUpdateMetadata(metadata)
        }
    }

    /**
     * 특정 언어의 메타데이터를 조회합니다.
     *
     * @param userId 사용자 UID
     * @param language 학습 언어 코드
     * @return 메타데이터, 없으면 null
     */
    suspend fun getMetadata(userId: String, language: String): SessionMetadataEntity? {
        return metadataDao.getMetadata(userId, language)
    }

    /**
     * 특정 언어의 메타데이터를 저장합니다.
     *
     * @param metadata 저장할 메타데이터
     */
    suspend fun saveMetadata(metadata: SessionMetadataEntity) {
        metadataDao.insertOrUpdateMetadata(metadata)
    }

    /**
     * topicSummariesJson 만 교체합니다.
     *
     * 교정 완료 직후 AI 로 요약된 주제 목록을 기존 메타데이터를 파괴하지 않고 보강한다. (#162-C)
     * 메타데이터가 없으면 기본값으로 새로 생성한다.
     *
     * @param userId 사용자 UID
     * @param language 학습 언어 코드
     * @param summariesJson JSON 배열 문자열 (`["...", "..."]` 형태)
     * @param updatedAt 갱신 시각
     */
    suspend fun updateTopicSummaries(
        userId: String,
        language: String,
        summariesJson: String,
        updatedAt: Long
    ) {
        val metaId = "${userId}_${language}"
        val current = metadataDao.getMetadata(userId, language)
        val updated = (current ?: SessionMetadataEntity(
            id = metaId,
            userId = userId,
            language = language,
            recentTopicsJson = "[]",
            topicSummariesJson = "[]",
            topicKeySentencesJson = "[]",
            correctionAvailable = false,
            lastCompressedAt = null,
            updatedAt = updatedAt,
            isPendingTurnSync = false,
            isPendingCompressionSync = false
        )).copy(
            topicSummariesJson = summariesJson,
            updatedAt = updatedAt
        )
        metadataDao.insertOrUpdateMetadata(updated)
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

    /**
     * 오래된 turn 을 삭제해 recent turn 개수를 제한합니다.
     *
     * @param userId 사용자 UID
     * @param language 학습 언어 코드
     * @param maxRecentTurns 유지할 최대 turn 수
     */
    private suspend fun trimOverflowTurns(
        userId: String,
        language: String,
        maxRecentTurns: Int
    ) {
        val turns = turnDao.getTurns(userId, language)
        if (turns.size <= maxRecentTurns) return

        val overflowTurnIds = turns.take(turns.size - maxRecentTurns).map { it.turnId }

        if (overflowTurnIds.isNotEmpty()) {
            turnDao.deleteTurnsByIds(overflowTurnIds)
        }
    }
}
