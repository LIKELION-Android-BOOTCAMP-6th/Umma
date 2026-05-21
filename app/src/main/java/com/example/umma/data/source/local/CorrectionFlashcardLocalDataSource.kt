package com.example.umma.data.source.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import com.example.umma.data.model.correction.CorrectionFlashcardDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Correction에서 생성한 Flashcard의 local-first 저장 경계입니다.
 *
 * Correction은 새 Flashcard 최초 생성/저장 책임을 가진다.
 * 이 local source는 SRS가 이후 같은 원본 카드를 조회하고 review schedule을 갱신할 수 있도록
 * Room 원본 저장, due 조회, schedule 갱신 통로를 함께 제공한다.
 */
interface CorrectionFlashcardLocalDataSource {

    /**
     * Correction 완료 시 새 Flashcard 원본을 local DB에 먼저 저장한다.
     *
     * 반환값은 이번 호출에서 "새로" 저장된 card id만 담는다.
     * 이미 같은 userId + card id로 존재하던 카드는 중복 저장으로 보고 제외한다.
     */
    suspend fun saveFlashcards(
        uid: String,
        flashcards: List<CorrectionFlashcardDto>
    ): List<String>

    /**
     * Firestore sync가 성공한 카드만 dirty=false로 전환한다.
     *
     * 최초 저장 또는 SRS review schedule 갱신 이후 sync가 실패한 카드는
     * local 성공 상태를 유지하되 dirty=true로 남아 후속 sync 대상이 된다.
     */
    suspend fun markSynced(
        uid: String,
        flashcardIds: List<String>
    )

    /**
     * SRS가 같은 Room 원본에서 현재 언어의 due deck을 만들 수 있도록 열어두는 조회 통로다.
     *
     * 실제 deck 정책과 화면 상태는 SRS의 FlashcardRepository / UseCase가 결정하고,
     * Correction-infra는 userId, language, nextReviewAt 기준으로 읽을 수 있는 원본만 제공한다.
     */
    suspend fun getDueFlashcards(
        uid: String,
        language: String,
        now: Long,
        limit: Int
    ): List<CorrectionFlashcardDto>

    /**
     * SRS review 결과를 같은 Flashcard 원본에 반영할 수 있도록 열어두는 갱신 통로다.
     *
     * interval / easeFactor / nextReviewAt 계산은 SRS domain 정책이 맡고,
     * 이 메서드는 계산된 schedule 값을 local-first로 기록한 뒤 dirty=true로 남긴다.
     * 대상 row가 없으면 false를 반환해 SRS가 삭제/충돌 카드를 Retry 또는 Skip으로 처리할 수 있게 한다.
     */
    suspend fun updateReviewSchedule(
        uid: String,
        flashcardId: String,
        nextReviewAt: Long,
        interval: Int,
        easeFactor: Double,
        updatedAt: Long
    ): Boolean

    /**
     * 완료 파이프라인 실패 시 이번 요청에서 새로 저장한 local 카드만 되돌린다.
     *
     * 중복 요청으로 이미 존재하던 카드는 이 rollback 대상에 포함되면 안 된다.
     */
    suspend fun rollbackFlashcards(
        uid: String,
        flashcardIds: List<String>
    )
}

/**
 * Correction이 최초 저장하고 SRS가 반복학습 원본으로 읽을 Flashcard Room 엔터티입니다.
 *
 * userId + id를 복합 키로 두어 같은 기기에서 계정이 바뀌어도 카드가 섞이지 않게 한다.
 */
@Entity(
    tableName = "correction_flashcards",
    primaryKeys = ["userId", "id"]
)
data class CorrectionFlashcardEntity(
    val userId: String,
    val id: String,
    val language: String,
    val sourceSuggestionId: String,
    val frontText: String,
    val backText: String,
    val explanation: String,
    // correction 외의 생성 경로가 생겨도 같은 Flashcard 테이블에서 출처를 구분하기 위한 값이다.
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
    // 최초 생성된 카드는 즉시 학습 가능하도록 생성 시각을 nextReviewAt 기본값으로 사용한다.
    val nextReviewAt: Long,
    // SRS가 이후 복습 결과를 저장하면서 갱신할 간격 값이다. 단위 해석은 SRS 정책이 담당한다.
    val interval: Int,
    val easeFactor: Double,
    // true면 최초 저장 또는 review schedule 갱신이 local에만 반영되어 remote sync가 남은 카드다.
    val dirty: Boolean
)

@Dao
interface CorrectionFlashcardDao {

    /**
     * 같은 userId + id가 이미 있으면 Room이 rowId=-1을 반환한다.
     * Store는 이 값을 보고 remote sync와 rollback 대상을 "이번에 새로 저장된 카드"로만 좁힌다.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFlashcards(flashcards: List<CorrectionFlashcardEntity>): List<Long>

    /**
     * remote sync 성공을 local에 반영하는 후속 업데이트다.
     * local-first 완료 여부를 바꾸지 않고 pending sync 상태만 정리한다.
     */
    @Query(
        """
        UPDATE correction_flashcards
        SET dirty = 0, updatedAt = :syncedAt
        WHERE userId = :userId AND id IN (:flashcardIds)
        """
    )
    suspend fun markSynced(
        userId: String,
        flashcardIds: List<String>,
        syncedAt: Long
    )

    /**
     * SRS due deck의 원천 조회다.
     * nextReviewAt 기준은 SRS 문서의 source of truth와 맞추고, 정렬은 재현 가능하게 고정한다.
     */
    @Query(
        """
        SELECT *
        FROM correction_flashcards
        WHERE userId = :userId
          AND language = :language
          AND nextReviewAt <= :now
        ORDER BY nextReviewAt ASC, createdAt ASC, id ASC
        LIMIT :limit
        """
    )
    suspend fun getDueFlashcards(
        userId: String,
        language: String,
        now: Long,
        limit: Int
    ): List<CorrectionFlashcardEntity>

    /**
     * SRS가 계산한 schedule만 갱신한다.
     * review 결과가 local에 먼저 반영되므로 Firestore sync 전까지 dirty=true로 되돌린다.
     * 반환 row count가 0이면 이미 삭제되었거나 현재 userId에 속하지 않는 카드로 본다.
     */
    @Query(
        """
        UPDATE correction_flashcards
        SET nextReviewAt = :nextReviewAt,
            interval = :interval,
            easeFactor = :easeFactor,
            updatedAt = :updatedAt,
            dirty = 1
        WHERE userId = :userId AND id = :flashcardId
        """
    )
    suspend fun updateReviewSchedule(
        userId: String,
        flashcardId: String,
        nextReviewAt: Long,
        interval: Int,
        easeFactor: Double,
        updatedAt: Long
    ): Int

    /**
     * 완료 파이프라인 보상 작업에서만 사용한다.
     * userId 조건을 함께 걸어 다른 계정의 같은 card id를 지우지 않도록 한다.
     */
    @Query("DELETE FROM correction_flashcards WHERE userId = :userId AND id IN (:flashcardIds)")
    suspend fun deleteFlashcards(
        userId: String,
        flashcardIds: List<String>
    )
}

@Database(
    entities = [CorrectionFlashcardEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CorrectionFlashcardDatabase : RoomDatabase() {
    abstract fun correctionFlashcardDao(): CorrectionFlashcardDao
}

@Singleton
class RoomCorrectionFlashcardLocalDataSource @Inject constructor(
    private val dao: CorrectionFlashcardDao
) :
    CorrectionFlashcardLocalDataSource {

    override suspend fun saveFlashcards(
        uid: String,
        flashcards: List<CorrectionFlashcardDto>
    ): List<String> {
        val entities = flashcards.map { flashcard ->
            flashcard.toEntity(userId = uid)
        }

        // Room insert IGNORE 결과가 -1이면 이미 저장된 카드다.
        // suggestionId 기반 idempotency를 DB constraint로 보장해 중복 카드를 만들지 않는다.
        return dao.insertFlashcards(entities)
            .mapIndexedNotNull { index, rowId ->
                if (rowId == -1L) null else entities[index].id
            }
    }

    override suspend fun markSynced(
        uid: String,
        flashcardIds: List<String>
    ) {
        if (flashcardIds.isEmpty()) return

        // remote sync가 성공한 카드만 dirty=false로 바꾼다.
        // 실패한 카드는 dirty=true로 남아 pending sync 대상이 된다.
        dao.markSynced(
            userId = uid,
            flashcardIds = flashcardIds,
            syncedAt = System.currentTimeMillis()
        )
    }

    override suspend fun getDueFlashcards(
        uid: String,
        language: String,
        now: Long,
        limit: Int
    ): List<CorrectionFlashcardDto> {
        if (limit <= 0) return emptyList()

        return dao.getDueFlashcards(
            userId = uid,
            language = language,
            now = now,
            limit = limit
        ).map { entity ->
            entity.toDto()
        }
    }

    override suspend fun updateReviewSchedule(
        uid: String,
        flashcardId: String,
        nextReviewAt: Long,
        interval: Int,
        easeFactor: Double,
        updatedAt: Long
    ): Boolean {
        // SRS가 계산한 schedule을 같은 원본 row에 반영한다.
        // 갱신된 카드는 다시 Firestore sync가 필요하므로 DAO에서 dirty=true로 바꾼다.
        return dao.updateReviewSchedule(
            userId = uid,
            flashcardId = flashcardId,
            nextReviewAt = nextReviewAt,
            interval = interval,
            easeFactor = easeFactor,
            updatedAt = updatedAt
        ) > 0
    }

    override suspend fun rollbackFlashcards(
        uid: String,
        flashcardIds: List<String>
    ) {
        if (flashcardIds.isEmpty()) return

        // CompleteCorrectionUseCase가 실패하면 이번 요청으로 저장한 카드만 되돌린다.
        dao.deleteFlashcards(
            userId = uid,
            flashcardIds = flashcardIds
        )
    }

    private fun CorrectionFlashcardDto.toEntity(userId: String): CorrectionFlashcardEntity {
        return CorrectionFlashcardEntity(
            userId = userId,
            id = id,
            language = language,
            sourceSuggestionId = sourceSuggestionId,
            frontText = frontText,
            backText = backText,
            explanation = explanation,
            source = source,
            createdAt = createdAt,
            updatedAt = updatedAt,
            nextReviewAt = nextReviewAt,
            interval = interval,
            easeFactor = easeFactor,
            dirty = dirty
        )
    }

    private fun CorrectionFlashcardEntity.toDto(): CorrectionFlashcardDto {
        return CorrectionFlashcardDto(
            id = id,
            language = language,
            sourceSuggestionId = sourceSuggestionId,
            frontText = frontText,
            backText = backText,
            explanation = explanation,
            source = source,
            createdAt = createdAt,
            updatedAt = updatedAt,
            nextReviewAt = nextReviewAt,
            interval = interval,
            easeFactor = easeFactor,
            dirty = dirty
        )
    }
}
