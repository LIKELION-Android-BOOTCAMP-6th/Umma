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

    private companion object {
        const val TAG = "CorrectionCacheRepo"

        val json = Json {
            ignoreUnknownKeys = false
            encodeDefaults = true
            explicitNulls = true
        }
    }
}
