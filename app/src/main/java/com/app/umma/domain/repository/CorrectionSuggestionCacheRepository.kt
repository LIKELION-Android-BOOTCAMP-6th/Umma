package com.app.umma.domain.repository

import com.app.umma.domain.model.correction.CachedCorrectionResult
import com.app.umma.domain.model.learningstate.LangCode

/**
 * Local persistence contract for unsaved Correction suggestions.
 *
 * The repository only stores and retrieves raw cache payloads. Fingerprint matching
 * remains a domain use case concern so staleness policy stays outside the data layer.
 */
interface CorrectionSuggestionCacheRepository {
    suspend fun getCachedCorrection(uid: String, language: LangCode): CachedCorrectionResult?

    suspend fun saveCachedCorrection(uid: String, cache: CachedCorrectionResult)

    suspend fun clearCachedCorrection(uid: String, language: LangCode)
}
