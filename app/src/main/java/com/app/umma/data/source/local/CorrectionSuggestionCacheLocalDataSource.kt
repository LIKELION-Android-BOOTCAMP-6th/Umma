package com.app.umma.data.source.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists unsaved Correction suggestions so the same session can restore them
 * without invoking AI again.
 */
interface CorrectionSuggestionCacheLocalDataSource {
    suspend fun getCache(userId: String, language: String): CorrectionSuggestionCacheLocalDto?

    suspend fun saveCache(cache: CorrectionSuggestionCacheLocalDto)

    suspend fun deleteCache(userId: String, language: String)

    suspend fun clearAll()
}

data class CorrectionSuggestionCacheLocalDto(
    val userId: String,
    val language: String,
    val suggestionsJson: String,
    val sessionFingerprint: Long,
    val primaryLanguage: String?,
    val cachedAt: Long,
)

@Entity(
    tableName = "correction_suggestion_cache",
    primaryKeys = ["userId", "language"],
)
data class CorrectionSuggestionCacheEntity(
    val userId: String,
    val language: String,
    val suggestionsJson: String,
    val sessionFingerprint: Long,
    val primaryLanguage: String?,
    val cachedAt: Long,
)

@Dao
interface CorrectionSuggestionCacheDao {
    @Query(
        """
        SELECT *
        FROM correction_suggestion_cache
        WHERE userId = :userId AND language = :language
        LIMIT 1
        """
    )
    suspend fun getCache(userId: String, language: String): CorrectionSuggestionCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCache(cache: CorrectionSuggestionCacheEntity)

    @Query("DELETE FROM correction_suggestion_cache WHERE userId = :userId AND language = :language")
    suspend fun deleteCache(userId: String, language: String)

    /**
     * 회원탈퇴 후 저장 전 교정 후보 원문이 다음 계정에서 복원되지 않게 전체 캐시를 제거한다.
     */
    @Query("DELETE FROM correction_suggestion_cache")
    suspend fun clearAll()
}

@Database(
    entities = [CorrectionSuggestionCacheEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class CorrectionSuggestionCacheDatabase : RoomDatabase() {
    abstract fun correctionSuggestionCacheDao(): CorrectionSuggestionCacheDao
}

@Singleton
class RoomCorrectionSuggestionCacheLocalDataSource @Inject constructor(
    private val dao: CorrectionSuggestionCacheDao,
) : CorrectionSuggestionCacheLocalDataSource {
    override suspend fun getCache(userId: String, language: String): CorrectionSuggestionCacheLocalDto? =
        dao.getCache(userId = userId, language = language)?.toDto()

    override suspend fun saveCache(cache: CorrectionSuggestionCacheLocalDto) {
        dao.upsertCache(cache.toEntity())
    }

    override suspend fun deleteCache(userId: String, language: String) {
        dao.deleteCache(userId = userId, language = language)
    }

    override suspend fun clearAll() {
        dao.clearAll()
    }
}

private fun CorrectionSuggestionCacheEntity.toDto(): CorrectionSuggestionCacheLocalDto =
    CorrectionSuggestionCacheLocalDto(
        userId = userId,
        language = language,
        suggestionsJson = suggestionsJson,
        sessionFingerprint = sessionFingerprint,
        primaryLanguage = primaryLanguage,
        cachedAt = cachedAt,
    )

private fun CorrectionSuggestionCacheLocalDto.toEntity(): CorrectionSuggestionCacheEntity =
    CorrectionSuggestionCacheEntity(
        userId = userId,
        language = language,
        suggestionsJson = suggestionsJson,
        sessionFingerprint = sessionFingerprint,
        primaryLanguage = primaryLanguage,
        cachedAt = cachedAt,
    )
