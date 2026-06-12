package com.app.umma.data.repository.correction

import android.util.Log
import com.app.umma.data.model.correction.toCacheEnvelopeDto
import com.app.umma.data.model.correction.toDomain
import com.app.umma.data.source.local.CorrectionSuggestionCacheLocalDataSource
import com.app.umma.data.source.local.CorrectionSuggestionCacheLocalDto
import com.app.umma.domain.model.correction.CachedCorrectionResult
import com.app.umma.domain.model.learningstate.LangCode
import com.app.umma.domain.repository.CorrectionSuggestionCacheRepository
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CorrectionSuggestionCacheRepositoryImpl @Inject constructor(
    private val localDataSource: CorrectionSuggestionCacheLocalDataSource,
) : CorrectionSuggestionCacheRepository {

    override suspend fun getCachedCorrection(uid: String, language: LangCode): CachedCorrectionResult? {
        val cached = localDataSource.getCache(userId = uid, language = language.code) ?: return null
        return try {
            json.decodeFromString(
                com.app.umma.data.model.correction.CorrectionSuggestionCacheEnvelopeDto.serializer(),
                cached.suggestionsJson,
            ).toDomain()
        } catch (error: Throwable) {
            Log.w(TAG, "failed to decode correction cache for ${language.code}", error)
            runCatching {
                localDataSource.deleteCache(userId = uid, language = language.code)
            }.onFailure { deleteError ->
                Log.w(TAG, "failed to delete corrupted correction cache for ${language.code}", deleteError)
            }
            null
        }
    }

    override suspend fun saveCachedCorrection(uid: String, cache: CachedCorrectionResult) {
        val payload = json.encodeToString(
            com.app.umma.data.model.correction.CorrectionSuggestionCacheEnvelopeDto.serializer(),
            cache.toCacheEnvelopeDto(),
        )
        localDataSource.saveCache(
            CorrectionSuggestionCacheLocalDto(
                userId = uid,
                language = cache.language.code,
                suggestionsJson = payload,
                sessionFingerprint = cache.sessionFingerprint,
                primaryLanguage = cache.primaryLanguage?.code,
                cachedAt = cache.cachedAt,
            ),
        )
    }

    override suspend fun clearCachedCorrection(uid: String, language: LangCode) {
        localDataSource.deleteCache(userId = uid, language = language.code)
    }

    override suspend fun clearLocal(): Result<Unit> = runCatching {
        // 캐시는 uid를 포함하지만 삭제 시점에는 auth 상태가 바뀔 수 있으므로 local cache 전체를 비운다.
        localDataSource.clearAll()
    }

    private companion object {
        const val TAG = "CorrectionCacheRepo"

        val json = Json {
            ignoreUnknownKeys = false
            encodeDefaults = true
            explicitNulls = true
        }
    }
}
